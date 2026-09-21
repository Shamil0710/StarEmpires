package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftTacticalEncounterService.EncounterResult;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalCombatant;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalOutcome;
import com.spacesim.world.SmallCraftTacticalEncounterService.LocalFlightState;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * M22.8H production bridge for one combined carrier-wing / ordinary-fleet Stage-19 encounter.
 *
 * <p>The bridge reuses the accepted generated-world physical authorities. Ordinary strategic ships
 * remain ordinary {@link FleetId} entities with Stage-20 kinematics; small craft remain persistent
 * {@link SmallCraftId} assets. One synchronous M22.8E/Stage-19 resolver receives detached exact
 * engineering for both families. Survivor state returns to the same identities, ordinary fleet
 * destruction uses the existing world destruction authority, and small-craft destruction remains
 * owned by {@link SmallCraftTacticalEncounterService}.</p>
 *
 * <p>No local combat state survives this call. The supplied strategic operation must already own a
 * fresh actor-bounded contact, so this bridge cannot select hidden targets or create a second
 * strategic operation lifecycle.</p>
 */
public final class GeneratedWorldCarrierEncounterService {
    private final LiveRuntime runtime;
    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final CarrierStrategicTacticalEncounterService tactical;
    private final StrategicOperationService operations = new StrategicOperationService();

    /**
     * Creates the production bridge using the accepted combined M22.8E / Stage-19 tactical runtime.
     *
     * @param runtime ordinary generated-world physical authority
     * @param craftRegistry individual physical small-craft authority
     * @param hangars physical embarked occupancy authority
     * @return combined generated-world carrier encounter bridge
     */
    public static GeneratedWorldCarrierEncounterService production(
            LiveRuntime runtime,
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars) {
        SmallCraftTacticalEncounterService exact =
                SmallCraftTacticalEncounterService.production(
                        Objects.requireNonNull(craftRegistry, "craftRegistry"),
                        Objects.requireNonNull(hangars, "hangars"));
        return new GeneratedWorldCarrierEncounterService(
                Objects.requireNonNull(runtime, "runtime"),
                craftRegistry,
                hangars,
                new CarrierStrategicTacticalEncounterService(exact));
    }

    GeneratedWorldCarrierEncounterService(
            LiveRuntime runtime,
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            CarrierStrategicTacticalEncounterService tactical) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.tactical = Objects.requireNonNull(tactical, "tactical");
    }

    /**
     * Resolves and commits one exact carrier-wing encounter at the current authoritative world tick.
     *
     * @param strategicState current Stage-21E operation registry
     * @param operationId CONTACT_CONFIRMED operation to materialize
     * @param encounterId positive caller-owned encounter identity
     * @param wing explicit ordinary carrier / individual-wing association
     * @param missionState current persistent individual mission state
     * @param deployedCraft exact current local physical states of deployed wing participants
     * @param currentTick exact authoritative world tick
     * @param maximumTicks positive bounded Stage-19 tactical horizon
     * @return resolved strategic lifecycle plus exact individual and ordinary physical outcomes
     */
    public StrategicEncounterResult resolve(
            StrategicOperationState strategicState,
            long operationId,
            long encounterId,
            CarrierWingAssignment wing,
            SmallCraftMissionState missionState,
            Collection<DeployedCraftPhysicalState> deployedCraft,
            long currentTick,
            long maximumTicks) {
        StrategicOperationState strategic =
                Objects.requireNonNull(strategicState, "strategicState");
        CarrierWingAssignment assignment = Objects.requireNonNull(wing, "wing");
        SmallCraftMissionState missions = Objects.requireNonNull(missionState, "missionState");
        Objects.requireNonNull(deployedCraft, "deployedCraft");
        if (encounterId <= 0L) {
            throw new IllegalArgumentException("encounterId must be positive");
        }
        if (currentTick < 0L || currentTick != runtime.world().getAuthoritativeWorldTick()) {
            throw new IllegalStateException(
                    "carrier tactical handoff must use the current authoritative world tick");
        }
        if (maximumTicks <= 0L) {
            throw new IllegalArgumentException("maximumTicks must be positive");
        }

        OperationState operation = strategic.requireOperation(operationId);
        if (operation.status() != OperationStatus.CONTACT_CONFIRMED
                || operation.contact() == null) {
            throw new IllegalStateException(
                    "combined carrier encounter requires confirmed actor-bounded Stage-21 contact");
        }
        ContactState contact = operation.contact();
        if (!contact.currentAt(currentTick)) {
            throw new IllegalStateException("carrier strategic contact is stale");
        }
        if (!operation.participantFleetIds().contains(assignment.carrierFleetId())) {
            throw new IllegalArgumentException(
                    "carrier-wing association does not belong to the admitted Stage-21 operation");
        }

        OrdinaryBindingSet ordinary = bindOrdinaryCombatants(operation, contact);
        LocalPhysicalPosition anchor = ordinary.anchor();
        ArrayList<SmallCraftParticipant> craftParticipants =
                bindSmallCraftParticipants(assignment, deployedCraft, ordinary.systemId(), anchor);
        List<ExternalCombatant> external = ordinary.externalCombatants();

        TacticalEncounterState encounter = new TacticalEncounterState(
                encounterId,
                contact.targetFleetId(),
                ordinary.systemId(),
                currentTick,
                -1L);
        StrategicOperationState engaged =
                operations.markEngaged(strategic, operationId, encounter, currentTick);

        EncounterResult tacticalResult = tactical.resolve(
                engaged.requireOperation(operationId),
                assignment.carrierFleetId(),
                assignment,
                missions,
                craftParticipants,
                external,
                maximumTicks);

        validateOrdinaryCommitBoundary(ordinary, tacticalResult.externalCombatants());
        commitOrdinaryOutcomes(ordinary, tacticalResult.externalCombatants());

        SmallCraftMissionState finalMissions = tacticalResult.missionState();
        if (runtime.world().findFleet(assignment.carrierFleetId()).isEmpty()) {
            finalMissions = destroyEmbarkedWithCarrier(
                    assignment.hostStableId(),
                    finalMissions);
        }

        StrategicOperationState resolved =
                operations.resolveEngagement(engaged, operationId, currentTick);
        EncounterResult finalTactical = finalMissions.equals(tacticalResult.missionState())
                ? tacticalResult
                : new EncounterResult(
                        finalMissions,
                        tacticalResult.ticksExecuted(),
                        tacticalResult.termination(),
                        tacticalResult.smallCraft(),
                        tacticalResult.externalCombatants());
        return new StrategicEncounterResult(
                resolved,
                finalTactical,
                ordinary.systemId(),
                encounterId);
    }

    private OrdinaryBindingSet bindOrdinaryCombatants(
            OperationState operation,
            ContactState contact) {
        var world = runtime.world();
        FleetPlacementState targetPlacement = world.findFleet(contact.targetFleetId())
                .orElseThrow(() -> new IllegalStateException(
                        "confirmed carrier-operation target no longer exists"));
        if (targetPlacement.locationKind() != FleetLocationKind.IN_SYSTEM
                || targetPlacement.localEntityId() == null
                || !contact.observedSystemId().equals(targetPlacement.systemId())) {
            throw new IllegalStateException(
                    "confirmed target is no longer physically materialized at the actor-known contact");
        }
        StarSystemId systemId = targetPlacement.systemId();
        var session = world.findSession(systemId)
                .orElseThrow(() -> new IllegalStateException(
                        "carrier tactical system has no ordinary local session"));

        ArrayList<BoundOrdinaryFleet> bound = new ArrayList<>();
        for (FleetId fleetId : operation.participantFleetIds()) {
            FleetPlacementState placement = world.findFleet(fleetId)
                    .orElseThrow(() -> new IllegalStateException(
                            "carrier operation participant disappeared before tactical import: " + fleetId));
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || placement.localEntityId() == null
                    || !systemId.equals(placement.systemId())) {
                throw new IllegalStateException(
                        "carrier operation participants have not physically met the contact");
            }
            Entity entity = session.getEntityRegistry().require(placement.localEntityId());
            EntityState state = EntityStateMapper.capture(entity);
            if (state.faction() == null || state.faction().factionId() != operation.factionId()) {
                throw new IllegalStateException(
                        "carrier operation participant allegiance changed: " + fleetId);
            }
            bound.add(bindOrdinary(
                    fleetId,
                    Side.ALPHA,
                    placement,
                    entity,
                    state,
                    systemId));
        }

        Entity targetEntity = session.getEntityRegistry().require(targetPlacement.localEntityId());
        EntityState targetState = EntityStateMapper.capture(targetEntity);
        if (targetState.faction() == null) {
            throw new IllegalStateException("carrier-operation target lacks ordinary faction state");
        }
        if (targetState.faction().factionId() == operation.factionId()) {
            throw new IllegalStateException("carrier operation cannot target its own ordinary fleet");
        }
        bound.add(bindOrdinary(
                contact.targetFleetId(),
                Side.BETA,
                targetPlacement,
                targetEntity,
                targetState,
                systemId));
        bound.sort(Comparator.comparing(BoundOrdinaryFleet::fleetId));
        LocalPhysicalPosition anchor = bound.get(0).physical().position();

        ArrayList<ExternalCombatant> external = new ArrayList<>();
        for (BoundOrdinaryFleet value : bound) {
            LocalPhysicalPosition.Displacement delta =
                    anchor.displacementTo(value.physical().position());
            EngineeringComponent engineering =
                    value.entity().getComponent(EngineeringComponent.class);
            external.add(new ExternalCombatant(
                    externalReference(value.fleetId()),
                    value.side(),
                    value.stableFactionId(),
                    new EngineeringComponent(
                            engineering.fit,
                            engineering.runtimeState,
                            engineering.instanceState),
                    new LocalFlightState(
                            delta.deltaXM(),
                            delta.deltaYM(),
                            value.physical().velocityXMps(),
                            value.physical().velocityYMps())));
        }
        return new OrdinaryBindingSet(
                systemId,
                anchor,
                List.copyOf(bound),
                new ArrayList<>(external));
    }

    private BoundOrdinaryFleet bindOrdinary(
            FleetId fleetId,
            Side side,
            FleetPlacementState placement,
            Entity entity,
            EntityState state,
            StarSystemId systemId) {
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        if (engineering == null) {
            throw new IllegalStateException(
                    "carrier tactical ordinary fleet lacks engineering: " + fleetId);
        }
        String faction = runtime.world().findFactionStableId(
                        state.faction().factionId())
                .orElseThrow(() -> new IllegalStateException(
                        "carrier tactical ordinary faction lacks stable identity"));
        LocalPhysicalKinematics physical = runtime.arrival().materialization(systemId)
                .physicalState(placement.localEntityId())
                .orElseThrow(() -> new IllegalStateException(
                        "carrier tactical ordinary fleet lacks Stage-20 physical kinematics: "
                                + fleetId));
        return new BoundOrdinaryFleet(
                fleetId,
                side,
                faction,
                placement,
                entity,
                state,
                physical);
    }

    private ArrayList<SmallCraftParticipant> bindSmallCraftParticipants(
            CarrierWingAssignment wing,
            Collection<DeployedCraftPhysicalState> deployed,
            StarSystemId systemId,
            LocalPhysicalPosition anchor) {
        Map<SmallCraftId, DeployedCraftPhysicalState> canonical = new TreeMap<>();
        for (DeployedCraftPhysicalState row : deployed) {
            DeployedCraftPhysicalState checked =
                    Objects.requireNonNull(row, "deployed craft");
            if (!wing.craftIds().contains(checked.craftId())) {
                throw new IllegalArgumentException(
                        "deployed craft is outside current strategic carrier wing: "
                                + checked.craftId());
            }
            if (!systemId.equals(checked.systemId())) {
                throw new IllegalStateException(
                        "deployed carrier craft is outside the exact tactical system");
            }
            if (canonical.putIfAbsent(checked.craftId(), checked) != null) {
                throw new IllegalArgumentException(
                        "duplicate deployed craft physical state: " + checked.craftId());
            }
        }
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException(
                    "combined carrier encounter requires at least one deployed small craft");
        }

        ArrayList<SmallCraftParticipant> participants = new ArrayList<>();
        for (DeployedCraftPhysicalState row : canonical.values()) {
            LocalPhysicalPosition.Displacement delta =
                    anchor.displacementTo(row.position());
            participants.add(new SmallCraftParticipant(
                    row.craftId(),
                    Side.ALPHA,
                    new LocalFlightState(
                            delta.deltaXM(),
                            delta.deltaYM(),
                            row.velocityXMps(),
                            row.velocityYMps())));
        }
        return participants;
    }

    private void validateOrdinaryCommitBoundary(
            OrdinaryBindingSet ordinary,
            List<ExternalOutcome> outcomes) {
        Map<String, ExternalOutcome> byReference = new HashMap<>();
        for (ExternalOutcome outcome : outcomes) {
            if (byReference.put(outcome.referenceId(), outcome) != null) {
                throw new IllegalStateException(
                        "duplicate ordinary external outcome: " + outcome.referenceId());
            }
        }
        if (byReference.size() != ordinary.fleets().size()) {
            throw new IllegalStateException(
                    "combined Stage-19 result changed ordinary fleet participant count");
        }

        for (BoundOrdinaryFleet before : ordinary.fleets()) {
            ExternalOutcome outcome = byReference.get(externalReference(before.fleetId()));
            if (outcome == null) {
                throw new IllegalStateException(
                        "combined Stage-19 result omitted ordinary fleet: " + before.fleetId());
            }
            FleetPlacementState placement = runtime.world().findFleet(before.fleetId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ordinary FleetId disappeared during detached carrier tactical resolution"));
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !ordinary.systemId().equals(placement.systemId())
                    || !placement.localEntityId().equals(before.placement().localEntityId())) {
                throw new IllegalStateException(
                        "ordinary carrier encounter placement changed during detached resolution");
            }
            Entity current = runtime.world().findSession(ordinary.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            if (!EntityStateMapper.capture(current).equals(before.entityState())) {
                throw new IllegalStateException(
                        "ordinary carrier encounter payload changed during detached resolution");
            }
            EngineeringComponent engineering = current.getComponent(EngineeringComponent.class);
            if (engineering == null || !engineering.fit.equals(outcome.engineering().fit)) {
                throw new IllegalStateException(
                        "combined tactical outcome attempted to replace ordinary installed fit");
            }
        }
    }

    private void commitOrdinaryOutcomes(
            OrdinaryBindingSet ordinary,
            List<ExternalOutcome> outcomes) {
        Map<String, ExternalOutcome> byReference = new HashMap<>();
        outcomes.forEach(value -> byReference.put(value.referenceId(), value));
        var materialization = runtime.arrival().materialization(ordinary.systemId());

        for (BoundOrdinaryFleet before : ordinary.fleets()) {
            ExternalOutcome after = byReference.get(externalReference(before.fleetId()));
            if (after.destroyed()) {
                continue;
            }
            EngineeringComponent engineering =
                    before.entity().getComponent(EngineeringComponent.class);
            engineering.setRuntimeState(after.engineering().runtimeState);
            engineering.setInstanceState(after.engineering().instanceState);
            LocalPhysicalPosition position = ordinary.anchor().translated(
                    after.finalFlight().xM(),
                    after.finalFlight().yM());
            LocalPhysicalKinematics physical = new LocalPhysicalKinematics(
                    position,
                    after.finalFlight().velocityXMps(),
                    after.finalFlight().velocityYMps());
            materialization.updatePhysicalState(before.placement().localEntityId(), physical);
            TransformComponent transform = before.entity().getComponent(TransformComponent.class);
            if (transform != null) {
                transform.position.set(
                        exactFloat(position.offsetXM(), "carrier tactical legacy X"),
                        exactFloat(position.offsetYM(), "carrier tactical legacy Y"));
                transform.velocity.set(
                        exactFloat(after.finalFlight().velocityXMps(),
                                "carrier tactical legacy velocity X"),
                        exactFloat(after.finalFlight().velocityYMps(),
                                "carrier tactical legacy velocity Y"));
            }
        }

        for (BoundOrdinaryFleet before : ordinary.fleets()) {
            ExternalOutcome after = byReference.get(externalReference(before.fleetId()));
            if (!after.destroyed()) {
                continue;
            }
            runtime.world().destroyEntity(
                    ordinary.systemId(),
                    before.placement().localEntityId(),
                    DestructionPolicy.destroyAll());
            materialization.releasePhysicalStateForWorldTransfer(
                    before.placement().localEntityId());
        }
    }

    private SmallCraftMissionState destroyEmbarkedWithCarrier(
            String hostStableId,
            SmallCraftMissionState missions) {
        SmallCraftMissionState updated = missions;
        ArrayList<SmallCraftHangarRegistry.Assignment> doomed = new ArrayList<>();
        for (SmallCraftHangarRegistry.Assignment assignment : hangars.snapshot()) {
            if (assignment.bayId().hostStableId().equals(hostStableId)) {
                doomed.add(assignment);
            }
        }
        doomed.sort(Comparator.naturalOrder());
        for (SmallCraftHangarRegistry.Assignment assignment : doomed) {
            SmallCraftId craftId = assignment.craftId();
            hangars.release(craftId);
            if (craftRegistry.find(craftId).isPresent()) {
                craftRegistry.removeDestroyedCraft(craftId);
            }
            var active = updated.activeMissionFor(craftId);
            if (active.isPresent()) {
                MissionOrder mission = active.orElseThrow();
                updated = updated.replace(mission.withStatus(MissionStatus.FAILED));
            }
        }
        return updated;
    }

    private static String externalReference(FleetId fleetId) {
        return "fleet:" + Objects.requireNonNull(fleetId, "fleetId").value();
    }

    private static float exactFloat(double value, String label) {
        float projected = (float) value;
        if (!Float.isFinite(projected)) {
            throw new IllegalStateException(
                    label + " is outside the legacy ECS projection range");
        }
        return projected;
    }

    /**
     * Exact deployed local physical state supplied by the current small-craft movement authority.
     *
     * @param craftId persistent individual craft identity
     * @param systemId exact ordinary system containing the craft
     * @param position hierarchical Stage-20-compatible local physical position
     * @param velocityXMps local X velocity in SI meters per second
     * @param velocityYMps local Y velocity in SI meters per second
     */
    public record DeployedCraftPhysicalState(
            SmallCraftId craftId,
            StarSystemId systemId,
            LocalPhysicalPosition position,
            double velocityXMps,
            double velocityYMps) {
        /** Validates one exact deployed physical state. */
        public DeployedCraftPhysicalState {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(position, "position");
            if (!Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)) {
                throw new IllegalArgumentException(
                        "deployed small-craft velocity must be finite");
            }
        }
    }

    /** Complete synchronous H-layer result after both identity families have committed. */
    public record StrategicEncounterResult(
            StrategicOperationState strategicState,
            EncounterResult tacticalResult,
            StarSystemId systemId,
            long encounterId) {
        /** Validates one complete exact carrier encounter result. */
        public StrategicEncounterResult {
            Objects.requireNonNull(strategicState, "strategicState");
            Objects.requireNonNull(tacticalResult, "tacticalResult");
            Objects.requireNonNull(systemId, "systemId");
            if (encounterId <= 0L) {
                throw new IllegalArgumentException("encounterId must be positive");
            }
        }
    }

    private record BoundOrdinaryFleet(
            FleetId fleetId,
            Side side,
            String stableFactionId,
            FleetPlacementState placement,
            Entity entity,
            EntityState entityState,
            LocalPhysicalKinematics physical) {
        private BoundOrdinaryFleet {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(stableFactionId, "stableFactionId");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(entityState, "entityState");
            Objects.requireNonNull(physical, "physical");
        }
    }

    private record OrdinaryBindingSet(
            StarSystemId systemId,
            LocalPhysicalPosition anchor,
            List<BoundOrdinaryFleet> fleets,
            List<ExternalCombatant> externalCombatants) {
        private OrdinaryBindingSet {
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(anchor, "anchor");
            fleets = List.copyOf(Objects.requireNonNull(fleets, "fleets"));
            externalCombatants =
                    List.copyOf(Objects.requireNonNull(externalCombatants, "externalCombatants"));
        }
    }
}

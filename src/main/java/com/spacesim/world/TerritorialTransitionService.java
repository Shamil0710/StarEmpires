package com.spacesim.world;

import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-21F composition layer between physical Stage-21E invasions and Stage-17 territory law.
 *
 * <p>The service never writes controller flags, moves fleets, changes allegiance, creates resistance
 * forces or grants supplies. It observes exact ordinary {@link FleetForceRegistry} entries, persists
 * only occupation progress, and delegates political claim creation/withdrawal to the existing
 * {@link WorldSimulation} Stage-17 territorial authority. Stabilization and control acquisition/loss
 * remain owned by that Stage-17 runtime.</p>
 */
public final class TerritorialTransitionService {
    /** Sustained supplied/security time required before an invasion has occupation evidence. */
    public static final long REQUIRED_OCCUPATION_TICKS = 300L;
    /** Continuous unsupported time after which accumulated occupation evidence collapses. */
    public static final long OCCUPATION_COLLAPSE_GRACE_TICKS = 600L;

    /**
     * Advances one INVASION occupation attempt from ordinary physical fleet facts.
     *
     * <p>Territorial opposition stalls the occupation clock rather than creating synthetic resistance.
     * Missing, displaced, under-ready or under-supplied participants start an unsupported deadline,
     * decay accumulated progress and withdraw any still-unestablished invasion claim through the
     * existing Stage-17 authority. This prevents infrastructure alone from continuing a military
     * stabilization after its occupation security/supply prerequisites disappear.</p>
     *
     * <p>Only after the sustained occupation threshold is reached is an absent political claim
     * declared through Stage 17. Once Stage 17 actually establishes control, the occupation remembers
     * that fact but stops pretending the original invasion fleet is itself the sovereignty authority.
     * A later foreign controller is then a real liberation outcome, again observed from Stage-17 law
     * rather than manufactured here.</p>
     *
     * @param state persistent Stage-21F occupation metadata
     * @param world authoritative world/Stage-17 territorial boundary
     * @param operations persistent Stage-21E operation registry
     * @param forces ordinary physical fleet reconstruction
     * @param identities authoritative stable/runtime faction identity directory
     * @param operationId INVASION operation to reconcile
     * @param currentTick authoritative non-negative tick; must equal the world's current authoritative tick
     * @return updated transition/operation state and audit facts
     */
    public AdvanceResult advance(
            TerritorialTransitionState state,
            WorldSimulation world,
            StrategicOperationState operations,
            FleetForceRegistry forces,
            FactionIdentityResolver identities,
            long operationId,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(forces, "forces");
        Objects.requireNonNull(identities, "identities");
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");
        if (currentTick != world.getAuthoritativeWorldTick()) {
            throw new IllegalArgumentException("currentTick must equal the authoritative world tick");
        }

        OperationState operation = operations.requireOperation(operationId);
        if (operation.type() != OperationType.INVASION) {
            throw new IllegalArgumentException("territorial occupation requires an INVASION operation");
        }
        String factionId = identities.stableId(operation.factionId())
                .orElseThrow(() -> new IllegalStateException("invasion faction is absent from Stage-17 identity authority"));
        StarSystemId systemId = operation.objectiveSystemId();

        OccupationState previous = state.occupationFor(factionId, systemId).orElse(null);
        if (previous != null && previous.operationId() != operationId
                && previous.status() != OccupationStatus.COLLAPSED
                && previous.status() != OccupationStatus.LIBERATED) {
            throw new IllegalStateException("another occupation operation already owns this faction/system transition");
        }
        if (previous == null || previous.operationId() != operationId) {
            previous = new OccupationState(
                    factionId, systemId, operationId, currentTick, currentTick, 0L, -1L, false,
                    OccupationStatus.OCCUPYING);
        }
        if (currentTick < previous.lastEvaluatedTick()) {
            throw new IllegalArgumentException("occupation evaluation cannot move backwards in time");
        }
        long elapsed = currentTick - previous.lastEvaluatedTick();

        String controller = world.controllingFaction(systemId).orElse(null);
        if (factionId.equals(controller)) {
            OccupationState established = replacement(
                    previous,
                    currentTick,
                    Math.max(previous.securedTicks(), REQUIRED_OCCUPATION_TICKS),
                    -1L,
                    true,
                    OccupationStatus.SECURED);
            return new AdvanceResult(state.upsert(established), operations, established, false, true, true, false);
        }
        if (previous.controlEverEstablished() && controller != null && !controller.equals(factionId)) {
            OccupationState liberated = replacement(
                    previous, currentTick, previous.securedTicks(), -1L, true, OccupationStatus.LIBERATED);
            return new AdvanceResult(state.upsert(liberated), operations, liberated, false, false, false, false);
        }

        if (operation.status() == OperationStatus.FAILED) {
            withdrawUnestablishedClaim(world, factionId, systemId);
            OccupationState collapsed = replacement(
                    previous, currentTick, 0L, currentTick, previous.controlEverEstablished(), OccupationStatus.COLLAPSED);
            return new AdvanceResult(state.upsert(collapsed), operations, collapsed, false, false, false, false);
        }
        if (operation.status() == OperationStatus.WITHDRAWING) {
            withdrawUnestablishedClaim(world, factionId, systemId);
            long unsupportedSince = previous.unsupportedSinceTick() >= 0L
                    ? previous.unsupportedSinceTick() : currentTick;
            long progress = decay(previous.securedTicks(), elapsed);
            OccupationState withdrawing = replacement(
                    previous, currentTick, progress, unsupportedSince, previous.controlEverEstablished(),
                    OccupationStatus.WITHDRAWING);
            return new AdvanceResult(state.upsert(withdrawing), operations, withdrawing, false, false, false, false);
        }

        PhysicalReview physical = reviewPhysical(operation, forces, world, identities);
        if (physical.rivalFleetPresent()) {
            withdrawUnestablishedClaim(world, factionId, systemId);
            OccupationState contested = replacement(
                    previous, currentTick, previous.securedTicks(), -1L, previous.controlEverEstablished(),
                    OccupationStatus.CONTESTED);
            return new AdvanceResult(state.upsert(contested), operations, contested, false,
                    physical.securityReady(), physical.supplyReady(), true);
        }

        boolean supported = physical.securityReady() && physical.supplyReady();
        if (!supported) {
            withdrawUnestablishedClaim(world, factionId, systemId);
            long unsupportedSince = previous.unsupportedSinceTick() >= 0L
                    ? previous.unsupportedSinceTick() : currentTick;
            long progress = decay(previous.securedTicks(), elapsed);
            OccupationStatus status = currentTick - unsupportedSince >= OCCUPATION_COLLAPSE_GRACE_TICKS
                    ? OccupationStatus.COLLAPSED : OccupationStatus.OCCUPYING;
            if (status == OccupationStatus.COLLAPSED) progress = 0L;
            OccupationState unsupported = replacement(
                    previous, currentTick, progress, unsupportedSince, previous.controlEverEstablished(), status);
            return new AdvanceResult(state.upsert(unsupported), operations, unsupported, false,
                    physical.securityReady(), physical.supplyReady(), false);
        }

        long progress = Math.min(REQUIRED_OCCUPATION_TICKS, safeAdd(previous.securedTicks(), elapsed));
        boolean secured = progress >= REQUIRED_OCCUPATION_TICKS;
        boolean claimCreated = false;
        StrategicOperationState nextOperations = operations;
        OccupationStatus status = secured ? OccupationStatus.SECURED : OccupationStatus.OCCUPYING;
        if (secured) {
            FactionStrategicState strategy = world.findFactionStrategicState(factionId)
                    .orElseThrow(() -> new IllegalStateException("occupation faction has no Stage-17 strategic state"));
            if (strategy.claimFor(systemId) == null) {
                world.declareTerritorialClaim(factionId, systemId);
                claimCreated = true;
            }
            if (operation.status().active()) {
                OperationState completed = operation.withLifecycle(
                        OperationStatus.COMPLETED,
                        currentTick,
                        -1L,
                        operation.contact(),
                        resolvedEncounter(operation.encounter(), currentTick));
                nextOperations = operations.replace(completed);
            }
        }
        OccupationState next = replacement(
                previous, currentTick, progress, -1L, previous.controlEverEstablished(), status);
        return new AdvanceResult(state.upsert(next), nextOperations, next, claimCreated,
                physical.securityReady(), physical.supplyReady(), false);
    }

    /**
     * Produces the Stage-21F global-map territorial layer without becoming authority.
     *
     * @param world current authoritative world
     * @param transitions persistent occupation metadata
     * @param factionContentId assessed stable faction ID
     * @param systemId assessed system
     * @return read-only phase distinguishing claim/occupation/stabilization/control/recognition
     */
    public TerritorialProjection project(
            WorldSimulation world,
            TerritorialTransitionState transitions,
            String factionContentId,
            StarSystemId systemId) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(transitions, "transitions");
        FactionTerritoryView territory = FactionTerritoryService.assess(world, systemId, factionContentId);
        OccupationState occupation = transitions.occupationFor(factionContentId, systemId).orElse(null);

        ProjectionPhase phase;
        if (territory.controlledByFaction()) {
            phase = territory.recognitionCount() > 0
                    ? ProjectionPhase.RECOGNIZED_CONTROL : ProjectionPhase.CONTROL;
        } else if (occupation != null && occupation.status() == OccupationStatus.LIBERATED) {
            phase = ProjectionPhase.LIBERATED;
        } else if (occupation != null && occupation.status() == OccupationStatus.CONTESTED) {
            phase = ProjectionPhase.CONTESTED;
        } else if (territory.claimStatus() == TerritorialClaimState.Status.CONTESTED) {
            phase = ProjectionPhase.CONTESTED;
        } else if (territory.claimStatus() == TerritorialClaimState.Status.STABILIZING) {
            phase = ProjectionPhase.STABILIZATION;
        } else if (occupation != null
                && (occupation.status() == OccupationStatus.OCCUPYING
                || occupation.status() == OccupationStatus.SECURED
                || occupation.status() == OccupationStatus.WITHDRAWING)) {
            phase = ProjectionPhase.OCCUPATION;
        } else if (territory.claimedByFaction()) {
            phase = ProjectionPhase.CLAIM;
        } else if (territory.physicalPresence()) {
            phase = ProjectionPhase.PRESENCE;
        } else {
            phase = ProjectionPhase.UNCLAIMED;
        }
        return new TerritorialProjection(
                systemId,
                factionContentId.strip(),
                phase,
                territory.controllingFactionContentId(),
                territory.claimStatus(),
                territory.stabilizationTicks(),
                occupation == null ? 0L : occupation.securedTicks(),
                occupation == null ? -1L : occupation.unsupportedSinceTick(),
                territory.recognitionCount());
    }

    private static PhysicalReview reviewPhysical(
            OperationState operation,
            FleetForceRegistry forces,
            WorldSimulation world,
            FactionIdentityResolver identities) {
        List<FleetForceRegistry.Entry> survivors = new ArrayList<>();
        for (FleetId fleetId : operation.participantFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId).orElse(null);
            if (force == null) continue;
            if (force.factionId() != operation.factionId()) {
                throw new IllegalStateException("invasion participant changed allegiance outside ordinary authority: " + fleetId);
            }
            survivors.add(force);
        }
        boolean securityReady = !survivors.isEmpty();
        boolean supplyReady = !survivors.isEmpty();
        for (FleetForceRegistry.Entry force : survivors) {
            boolean atObjective = force.locationKind() == FleetLocationKind.IN_SYSTEM
                    && operation.objectiveSystemId().equals(force.systemId());
            securityReady &= atObjective
                    && force.readiness().missionCapable(operation.supplyPolicy().minimumMissionReadinessBps());
            supplyReady &= atObjective
                    && force.readiness().supplyAccessBps() >= operation.supplyPolicy().minimumSupplyAccessBps();
        }
        boolean rivalFleetPresent = forces.entries().stream()
                .filter(force -> force.factionId() >= 0 && force.factionId() != operation.factionId())
                .filter(force -> force.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(force -> operation.objectiveSystemId().equals(force.systemId()))
                .anyMatch(force -> territorialOpponent(
                        force.factionId(), operation.objectiveSystemId(), world, identities));
        return new PhysicalReview(securityReady, supplyReady, rivalFleetPresent);
    }

    private static boolean territorialOpponent(
            int runtimeFactionId,
            StarSystemId systemId,
            WorldSimulation world,
            FactionIdentityResolver identities) {
        String stableFactionId = identities.stableId(runtimeFactionId).orElse(null);
        if (stableFactionId == null) return false;
        if (stableFactionId.equals(world.controllingFaction(systemId).orElse(null))) return true;
        return world.findFactionStrategicState(stableFactionId)
                .map(strategy -> strategy.claimFor(systemId) != null)
                .orElse(false);
    }

    private static void withdrawUnestablishedClaim(
            WorldSimulation world,
            String factionContentId,
            StarSystemId systemId) {
        FactionStrategicState strategy = world.findFactionStrategicState(factionContentId)
                .orElseThrow(() -> new IllegalStateException("occupation faction has no Stage-17 strategic state"));
        if (!strategy.controls(systemId) && strategy.claimFor(systemId) != null) {
            world.withdrawTerritorialClaim(factionContentId, systemId);
        }
    }

    private static OccupationState replacement(
            OccupationState previous,
            long currentTick,
            long securedTicks,
            long unsupportedSinceTick,
            boolean controlEverEstablished,
            OccupationStatus status) {
        return new OccupationState(
                previous.factionContentId(), previous.systemId(), previous.operationId(), previous.startedTick(),
                currentTick, securedTicks, unsupportedSinceTick, controlEverEstablished, status);
    }

    private static long decay(long value, long elapsed) {
        return Math.max(0L, value - Math.min(value, elapsed));
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static StrategicOperationState.TacticalEncounterState resolvedEncounter(
            StrategicOperationState.TacticalEncounterState encounter,
            long currentTick) {
        if (encounter == null || !encounter.active()) return encounter;
        return new StrategicOperationState.TacticalEncounterState(
                encounter.encounterId(), encounter.targetFleetId(), encounter.systemId(),
                encounter.materializedAtTick(), currentTick);
    }

    private record PhysicalReview(boolean securityReady, boolean supplyReady, boolean rivalFleetPresent) {}

    /** Read-only audit result of one occupation reconciliation. */
    public record AdvanceResult(
            TerritorialTransitionState transitions,
            StrategicOperationState operations,
            OccupationState occupation,
            boolean claimCreated,
            boolean securityReady,
            boolean supplyReady,
            boolean rivalFleetPresent) {
        /** Validates a complete Stage-21F reconciliation result. */
        public AdvanceResult {
            Objects.requireNonNull(transitions, "transitions");
            Objects.requireNonNull(operations, "operations");
            Objects.requireNonNull(occupation, "occupation");
        }
    }

    /** Global-map territorial phase; this is presentation/read state only. */
    public enum ProjectionPhase {
        UNCLAIMED,
        PRESENCE,
        CLAIM,
        OCCUPATION,
        STABILIZATION,
        CONTESTED,
        CONTROL,
        RECOGNIZED_CONTROL,
        LIBERATED
    }

    /**
     * Read-only Stage-21F global-map projection.
     *
     * @param systemId assessed system
     * @param factionContentId assessed faction
     * @param phase derived presentation phase
     * @param controllingFactionContentId actual Stage-17 controller, or null
     * @param claimStatus actual Stage-17 claim status, or null
     * @param stabilizationTicks actual Stage-17 stabilization progress
     * @param occupationTicks Stage-21F sustained physical occupation progress
     * @param unsupportedSinceTick Stage-21F unsupported occupation deadline watermark, or -1
     * @param recognitionCount directed political recognitions relevant to current claim/control
     */
    public record TerritorialProjection(
            StarSystemId systemId,
            String factionContentId,
            ProjectionPhase phase,
            String controllingFactionContentId,
            TerritorialClaimState.Status claimStatus,
            long stabilizationTicks,
            long occupationTicks,
            long unsupportedSinceTick,
            int recognitionCount) {
        /** Validates one read-only projection. */
        public TerritorialProjection {
            Objects.requireNonNull(systemId, "systemId");
            factionContentId = Objects.requireNonNull(factionContentId, "factionContentId").strip();
            if (factionContentId.isEmpty()) throw new IllegalArgumentException("factionContentId cannot be blank");
            Objects.requireNonNull(phase, "phase");
            if (stabilizationTicks < 0L || occupationTicks < 0L || recognitionCount < 0) {
                throw new IllegalArgumentException("territorial projection metrics cannot be negative");
            }
            if (unsupportedSinceTick < -1L) throw new IllegalArgumentException("invalid unsupported occupation tick");
        }
    }
}

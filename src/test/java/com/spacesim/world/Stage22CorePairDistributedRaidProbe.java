package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only M22.6 B06 authority probe for distributed low-intensity raids.
 *
 * <p>Three independent RAID operations are backed by three exact Stage-22 persistent engineering
 * payloads per faction. The ordinary Stage-21E supply/readiness authority decides whether each raid
 * may continue. Two lanes retain observed physical supply access; one lane loses it and must submit
 * an ordinary withdrawal. Removing a raid's only physical FleetId fails closed as no survivors.</p>
 *
 * <p>The fixture deliberately does not invent raid damage, income penalties, patrol percentages or
 * faction combat modifiers. Tactical patrol coverage is exercised separately by the common Stage-19
 * patrol runtime in the B06 machine-evidence acceptance test.</p>
 */
public final class Stage22CorePairDistributedRaidProbe {
    private static final int CREW_AVAILABLE = 100_000;
    private static final int REQUIRED_SUPPLY_ACCESS_BPS = 5_000;
    private static final int LANE_COUNT = 3;

    private Stage22CorePairDistributedRaidProbe() {
        throw new AssertionError("utility class");
    }

    /** Runs mirrored exact-core distributed raid supply authority for both factions. */
    public static Result run(Permutation permutation) {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empire = engineering(
                duel, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent union = engineering(
                duel, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        return new Result(
                evaluateFaction(empire, evaluator, 1, 22_606_100L),
                evaluateFaction(union, evaluator, 2, 22_606_200L));
    }

    private static EngineeringComponent engineering(
            Stage22CorePairTacticalFactory.DestroyerDuel duel,
            long entityId) {
        return duel.weapons().battleState().combatants().stream()
                .filter(actor -> actor.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing exact core combatant " + entityId))
                .engineering();
    }

    private static FactionResult evaluateFaction(
            EngineeringComponent engineering,
            FleetReadinessEvaluator evaluator,
            int factionId,
            long fleetBase) {
        ArrayList<SupplyDecision> decisions = new ArrayList<>();
        ArrayList<FleetReadinessState> readiness = new ArrayList<>();
        StrategicOperationService service = new StrategicOperationService();

        for (int lane = 0; lane < LANE_COUNT; lane++) {
            FleetId fleetId = new FleetId(fleetBase + lane);
            StarSystemId objective = new StarSystemId(22_606L + lane);
            EntityState entity = persistentEngineering(engineering, fleetBase + 10_000L + lane);
            int supplyAccessBps = lane == LANE_COUNT - 1 ? 0 : FleetReadinessState.FULL;
            FleetReadinessState laneReadiness = evaluator.evaluate(
                    entity,
                    new FleetOperationalAvailability(CREW_AVAILABLE, supplyAccessBps));
            readiness.add(laneReadiness);

            OperationState operation = raidOperation(lane + 1L, factionId, fleetId, objective);
            StrategicOperationState state = new StrategicOperationState(LANE_COUNT + 1L, List.of(operation));
            FleetForceRegistry forces = new FleetForceRegistry(List.of(entry(
                    fleetId, factionId, objective, entity, laneReadiness)));
            decisions.add(service.reviewSupplyAndReadiness(state, operation.id(), forces, 1L).decision());
        }

        FleetId missingFleet = new FleetId(fleetBase + 100L);
        StarSystemId missingObjective = new StarSystemId(22_616L);
        OperationState missingOperation = raidOperation(100L, factionId, missingFleet, missingObjective);
        SupplyDecision missingForceDecision = service.reviewSupplyAndReadiness(
                new StrategicOperationState(101L, List.of(missingOperation)),
                missingOperation.id(),
                new FleetForceRegistry(List.of()),
                1L).decision();

        long continuing = decisions.stream().filter(value -> value == SupplyDecision.CONTINUE).count();
        long withdrawing = decisions.stream()
                .filter(value -> value == SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER)
                .count();
        return new FactionResult(
                List.copyOf(readiness),
                List.copyOf(decisions),
                continuing,
                withdrawing,
                missingForceDecision);
    }

    private static OperationState raidOperation(
            long operationId,
            int factionId,
            FleetId fleetId,
            StarSystemId objective) {
        return new OperationState(
                operationId,
                OperationType.RAID,
                operationId,
                operationId,
                factionId,
                List.of(fleetId),
                objective,
                objective,
                "system:" + objective.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, REQUIRED_SUPPLY_ACCESS_BPS, 0L),
                new WithdrawalPolicy(objective, 0, false, false),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            EntityState entity,
            FleetReadinessState readiness) {
        return new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                entity,
                readiness);
    }

    private static EntityState persistentEngineering(EngineeringComponent source, long entityId) {
        Entity entity = new Entity()
                .add(new EntityIdComponent(new EntityId(entityId)))
                .add(new EngineeringComponent(source.fit, source.runtimeState, source.instanceState));
        return EntityStateMapper.capture(entity);
    }

    /** One paired exact-core distributed-raid observation. */
    public record Result(FactionResult empire, FactionResult union) { }

    /** One faction's three-lane physical raid/supply observations. */
    public record FactionResult(
            List<FleetReadinessState> readiness,
            List<SupplyDecision> decisions,
            long continuingRaids,
            long withdrawingRaids,
            SupplyDecision missingForceDecision) { }
}

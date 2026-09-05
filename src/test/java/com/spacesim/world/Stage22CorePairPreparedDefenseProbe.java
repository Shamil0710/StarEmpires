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

import java.util.List;

/**
 * Test-only M22.6 B09 probe crossing exact Stage-22 engineering state into the ordinary
 * Stage-21D readiness and Stage-21E reinforcement/supply authorities.
 *
 * <p>The probe authors only a bounded prepared-defense fixture. Readiness is derived from the exact
 * persistent engineering payload; reinforcement admission is rejected until the reserve is physically
 * located in the objective system; and loss of observed supply access is handled by the ordinary
 * operation withdrawal decision. No faction-specific readiness or reinforcement modifier exists here.</p>
 */
public final class Stage22CorePairPreparedDefenseProbe {
    private static final StarSystemId STAGING_SYSTEM = new StarSystemId(22_609L);
    private static final StarSystemId OBJECTIVE_SYSTEM = new StarSystemId(22_610L);
    private static final int PREPARED_CREW_AVAILABLE = 100_000;
    private static final int MINIMUM_MISSION_READINESS_BPS = 1_000;
    private static final int MINIMUM_SUPPLY_ACCESS_BPS = 5_000;

    private Stage22CorePairPreparedDefenseProbe() {
        throw new AssertionError("utility class");
    }

    /**
     * Executes the same prepared-defense authority chain for both exact core fits.
     *
     * @param permutation mirrored Stage-22 assignment used to source the exact physical fits
     * @return immutable per-faction operational observations
     */
    public static Result run(Permutation permutation) {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empire = duel.weapons().battleState().combatants().stream()
                .filter(actor -> actor.spec().entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Empire core combatant"))
                .engineering();
        EngineeringComponent union = duel.weapons().battleState().combatants().stream()
                .filter(actor -> actor.spec().entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Industrial Union core combatant"))
                .engineering();
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        return new Result(
                evaluateFaction(empire, evaluator, 1, 22_609_101L),
                evaluateFaction(union, evaluator, 2, 22_609_201L));
    }

    private static FactionResult evaluateFaction(
            EngineeringComponent engineering,
            FleetReadinessEvaluator evaluator,
            int factionId,
            long fleetBase) {
        FleetId defenderId = new FleetId(fleetBase);
        FleetId reserveId = new FleetId(fleetBase + 1L);
        EntityState defenderState = persistentEngineering(engineering, fleetBase + 10_000L);
        EntityState reserveState = persistentEngineering(engineering, fleetBase + 10_001L);
        FleetOperationalAvailability prepared = new FleetOperationalAvailability(
                PREPARED_CREW_AVAILABLE, FleetReadinessState.FULL);
        FleetReadinessState defenderReadiness = evaluator.evaluate(defenderState, prepared);
        FleetReadinessState reserveReadiness = evaluator.evaluate(reserveState, prepared);

        OperationState operation = new OperationState(
                1L,
                OperationType.DEFENSE,
                1L,
                1L,
                factionId,
                List.of(defenderId),
                STAGING_SYSTEM,
                OBJECTIVE_SYSTEM,
                "system:" + OBJECTIVE_SYSTEM.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(
                        MINIMUM_MISSION_READINESS_BPS,
                        MINIMUM_SUPPLY_ACCESS_BPS,
                        0L),
                new WithdrawalPolicy(STAGING_SYSTEM, 0, true, true),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
        StrategicOperationState base = new StrategicOperationState(2L, List.of(operation));
        Stage21EReinforcementService reinforcements = new Stage21EReinforcementService();

        FleetForceRegistry beforeArrival = new FleetForceRegistry(List.of(
                entry(defenderId, factionId, OBJECTIVE_SYSTEM, defenderState, defenderReadiness),
                entry(reserveId, factionId, STAGING_SYSTEM, reserveState, reserveReadiness)));
        boolean rejectedBeforePhysicalArrival = false;
        try {
            reinforcements.attachArrived(base, 1L, reserveId, beforeArrival, 1L);
        } catch (IllegalStateException expected) {
            rejectedBeforePhysicalArrival = true;
        }

        FleetForceRegistry arrived = new FleetForceRegistry(List.of(
                entry(defenderId, factionId, OBJECTIVE_SYSTEM, defenderState, defenderReadiness),
                entry(reserveId, factionId, OBJECTIVE_SYSTEM, reserveState, reserveReadiness)));
        StrategicOperationState reinforced = reinforcements.attachArrived(base, 1L, reserveId, arrived, 2L);
        boolean attachedAfterPhysicalArrival = reinforced.requireOperation(1L).participantFleetIds().contains(reserveId);
        SupplyDecision preparedDecision = new StrategicOperationService()
                .reviewSupplyAndReadiness(reinforced, 1L, arrived, 2L)
                .decision();

        FleetOperationalAvailability unsupportedAvailability = new FleetOperationalAvailability(
                PREPARED_CREW_AVAILABLE, 0);
        FleetReadinessState defenderUnsupported = evaluator.evaluate(defenderState, unsupportedAvailability);
        FleetReadinessState reserveUnsupported = evaluator.evaluate(reserveState, unsupportedAvailability);
        FleetForceRegistry unsupported = new FleetForceRegistry(List.of(
                entry(defenderId, factionId, OBJECTIVE_SYSTEM, defenderState, defenderUnsupported),
                entry(reserveId, factionId, OBJECTIVE_SYSTEM, reserveState, reserveUnsupported)));
        SupplyDecision unsupportedDecision = new StrategicOperationService()
                .reviewSupplyAndReadiness(reinforced, 1L, unsupported, 3L)
                .decision();

        return new FactionResult(
                defenderReadiness.overallBps(),
                reserveReadiness.overallBps(),
                rejectedBeforePhysicalArrival,
                attachedAfterPhysicalArrival,
                reinforced.requireOperation(1L).participantFleetIds().size(),
                preparedDecision,
                unsupportedDecision);
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            EntityState entityState,
            FleetReadinessState readiness) {
        return new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                entityState,
                readiness);
    }

    private static EntityState persistentEngineering(EngineeringComponent source, long entityId) {
        Entity entity = new Entity()
                .add(new EntityIdComponent(new EntityId(entityId)))
                .add(new EngineeringComponent(source.fit, source.runtimeState, source.instanceState));
        return EntityStateMapper.capture(entity);
    }

    /** One paired exact-core prepared-defense observation. */
    public record Result(FactionResult empire, FactionResult union) { }

    /** One faction's ordinary operational-authority observations. */
    public record FactionResult(
            int defenderReadinessBps,
            int reserveReadinessBps,
            boolean rejectedBeforePhysicalArrival,
            boolean attachedAfterPhysicalArrival,
            int committedParticipantCount,
            SupplyDecision preparedDecision,
            SupplyDecision unsupportedDecision) { }
}

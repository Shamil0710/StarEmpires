package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationService.SupplyReview;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M22.6 B01/B06/B11 final-format campaign-continuation evidence for the Stage-22 core pair.
 *
 * <p>This acceptance slice deliberately reuses the accepted generated-world, command, operation,
 * engineering and persistence authorities. Two opposing ordinary FleetIds receive the exact core-pair
 * engineering packages, enter real Stage-21E RAID operations through {@link StrategicOperationService},
 * and are captured inside the complete Stage-21A..I checkpoint chain. After production Stage-21I
 * encode/decode and ordinary Stage-20 runtime restore, the same operation identities must make the same
 * supply/readiness continuation decisions. No test-owned campaign state or outcome simulator is used.</p>
 */
class Stage22CorePairFinalCheckpointOperationContinuationAcceptanceTest {
    private static final int CREW_AVAILABLE = 100_000;
    private static final int REQUIRED_SUPPLY_ACCESS_BPS = 5_000;

    @Test
    void b06CorePairRaidsContinueIdenticallyAcrossFinalStage21ICheckpoint() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<MilitaryFleet> military = opposingMilitaryPair(runtime);
        MilitaryFleet supplied = military.get(0);
        MilitaryFleet cutOff = military.get(1);

        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        entity(runtime, supplied).add(copy(engineering(
                duel, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)));
        entity(runtime, cutOff).add(copy(engineering(
                duel, Stage22CorePairTacticalFactory.UNION_ENTITY_ID)));

        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        Map<FleetId, FleetOperationalAvailability> availability = Map.of(
                supplied.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL),
                cutOff.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, 0));
        FleetForceRegistry directForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);

        long checkpointTick = runtime.world().getAuthoritativeWorldTick();
        FleetCommandState commands = commands(supplied, cutOff, checkpointTick);
        StrategicOperationService service = new StrategicOperationService();
        StrategicOperationState operations = StrategicOperationState.empty();
        SupplyPolicy supplyPolicy = new SupplyPolicy(0, REQUIRED_SUPPLY_ACCESS_BPS, 0L);
        operations = service.beginFromActiveOrder(
                operations,
                commands,
                directForces,
                1L,
                checkpointTick,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supplyPolicy,
                new WithdrawalPolicy(supplied.systemId(), 0, false, false));
        operations = service.beginFromActiveOrder(
                operations,
                commands,
                directForces,
                2L,
                checkpointTick,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supplyPolicy,
                new WithdrawalPolicy(cutOff.systemId(), 0, false, false));

        Stage21IGeneratedWorldRuntimePersistentState checkpoint = finalCheckpoint(
                runtime.captureState(), commands, operations, checkpointTick);
        byte[] encoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage21IGeneratedWorldRuntimePersistentState decoded =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertArrayEquals(encoded, Stage21IGeneratedWorldRuntimePersistenceCodec.encode(decoded),
                "final Stage-21 core-pair campaign checkpoint must be byte-stable");

        Stage21EGeneratedWorldRuntimePersistentState restoredStage21E = decoded.stage21HRuntime()
                .stage21GRuntime().stage21FRuntime().stage21ERuntime();
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restoredRuntime = Stage20GeneratedWorldRuntimeBridge.restore(
                restoredStage21E.stage21DRuntime().stage21CRuntime().stage21BRuntime()
                        .stage21ARuntime().stage20Runtime());
        FleetForceRegistry restoredForces = FleetForceRegistry.reconstruct(
                restoredRuntime.world().snapshot(), evaluator, availability);

        assertEquals(
                directForces.find(supplied.fleetId()).orElseThrow().entityState().engineering(),
                restoredForces.find(supplied.fleetId()).orElseThrow().entityState().engineering(),
                "Empire core engineering must survive the final checkpoint");
        assertEquals(
                directForces.find(cutOff.fleetId()).orElseThrow().entityState().engineering(),
                restoredForces.find(cutOff.fleetId()).orElseThrow().entityState().engineering(),
                "Union core engineering must survive the final checkpoint");
        assertEquals(operations, restoredStage21E.operationState(),
                "the final checkpoint must retain the exact active Stage-21E operation registry");

        long reviewTick = checkpointTick + 1L;
        ReviewPair direct = reviewBoth(service, operations, directForces, reviewTick);
        ReviewPair restored = reviewBoth(service, restoredStage21E.operationState(), restoredForces, reviewTick);

        assertEquals(SupplyDecision.CONTINUE, direct.suppliedDecision());
        assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, direct.cutOffDecision());
        assertEquals(direct, restored,
                "final save/load must not change continuation decisions or persistent operation state");
        assertEquals(1L, restored.state().requireOperation(1L).id());
        assertEquals(2L, restored.state().requireOperation(2L).id());
        assertEquals(
                StrategicOperationState.OperationStatus.ACTIVE,
                restored.state().requireOperation(1L).status());
        assertEquals(
                StrategicOperationState.OperationStatus.WITHDRAWING,
                restored.state().requireOperation(2L).status());
    }

    private static ReviewPair reviewBoth(
            StrategicOperationService service,
            StrategicOperationState initial,
            FleetForceRegistry forces,
            long reviewTick) {
        SupplyReview supplied = service.reviewSupplyAndReadiness(initial, 1L, forces, reviewTick);
        SupplyReview cutOff = service.reviewSupplyAndReadiness(
                supplied.state(), 2L, forces, reviewTick);
        return new ReviewPair(supplied.decision(), cutOff.decision(), cutOff.state());
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finalCheckpoint(
            Stage20GeneratedWorldRuntimePersistentState stage20,
            FleetCommandState commands,
            StrategicOperationState operations,
            long tick) {
        List<FactionLivingActorState> actors = stage20.worldState().factions().stream()
                .map(faction -> FactionLivingActorState.initial(faction.factionContentId(), tick + 1L))
                .toList();
        Stage21AGeneratedWorldRuntimePersistentState stage21A =
                new Stage21AGeneratedWorldRuntimePersistentState(
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage20,
                        actors);
        List<FactionStrategicIntentState> intents = actors.stream()
                .map(actor -> FactionStrategicIntentState.initial(actor.factionContentId()))
                .toList();
        Stage21BGeneratedWorldRuntimePersistentState stage21B =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21A,
                        intents);
        Stage21CGeneratedWorldRuntimePersistentState stage21C =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21B,
                        DiplomaticLifecycleState.empty(tick),
                        Stage19ConflictState.empty(tick));
        Stage21DGeneratedWorldRuntimePersistentState stage21D =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21C, commands);
        Stage21EGeneratedWorldRuntimePersistentState stage21E =
                Stage21EGeneratedWorldRuntimePersistentState.compose(stage21D, operations);
        Stage21FGeneratedWorldRuntimePersistentState stage21F =
                Stage21FGeneratedWorldRuntimePersistentState.compose(stage21E, TerritorialTransitionState.empty());
        Stage21GGeneratedWorldRuntimePersistentState stage21G =
                Stage21GGeneratedWorldRuntimePersistentState.compose(stage21F, SettlementRecoveryState.empty(tick));
        Stage21HGeneratedWorldRuntimePersistentState stage21H =
                Stage21HGeneratedWorldRuntimePersistentState.compose(stage21G, Stage21HNpcMissionState.empty(tick));
        return Stage21IGeneratedWorldRuntimePersistentState.compose(stage21H);
    }

    private static FleetCommandState commands(MilitaryFleet first, MilitaryFleet second, long tick) {
        CommandGroupState firstGroup = group(1L, first);
        CommandGroupState secondGroup = group(2L, second);
        return new FleetCommandState(
                3L,
                3L,
                List.of(firstGroup, secondGroup),
                List.of(
                        order(1L, firstGroup.id(), first.systemId(), tick),
                        order(2L, secondGroup.id(), second.systemId(), tick)));
    }

    private static CommandGroupState group(long id, MilitaryFleet fleet) {
        return new CommandGroupState(
                id,
                fleet.factionId(),
                "M22.6 final-checkpoint raid group " + id,
                List.of(fleet.fleetId()),
                fleet.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
    }

    private static FleetOrderState order(long id, long groupId, StarSystemId systemId, long tick) {
        return new FleetOrderState(
                id,
                groupId,
                OrderType.RAID,
                OrderSource.AI,
                systemId,
                List.of(systemId),
                0,
                tick,
                tick + 10L,
                OrderStatus.ACTIVE);
    }

    private static List<MilitaryFleet> opposingMilitaryPair(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        MilitaryFleet first = result.stream().findFirst()
                .orElseThrow(() -> new AssertionError("generated world lacks military fleets"));
        MilitaryFleet second = result.stream()
                .filter(value -> value.factionId() != first.factionId())
                .findFirst()
                .orElseThrow(() -> new AssertionError("generated world lacks an opposing military fleet"));
        return List.of(first, second);
    }

    private static Entity entity(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime, MilitaryFleet fleet) {
        FleetPlacementState placement = runtime.world().findFleet(fleet.fleetId()).orElseThrow();
        return entity(runtime, placement);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static EngineeringComponent engineering(Stage22CorePairTacticalFactory.Duel duel, long entityId) {
        return duel.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record ReviewPair(
            SupplyDecision suppliedDecision,
            SupplyDecision cutOffDecision,
            StrategicOperationState state) { }
}

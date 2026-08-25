package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetCommandStateCodec;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.StrategicOperationStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21EGeneratedWorldTacticalExecutionAcceptanceTest {
    private static final int ACCEPTANCE_TACTICAL_TICKS = 240;

    @Test
    void exactGeneratedWorldExchangeCommitsPhysicalEffectsCleansLossesAndRoundTripsDeterministically() {
        ScenarioResult first = runScenario();
        ScenarioResult second = runScenario();

        assertArrayEquals(first.stage20Bytes(), second.stage20Bytes(),
                "same generated physical setup and Stage-19 ticks must produce byte-identical ordinary world state");
        assertArrayEquals(first.commandBytes(), second.commandBytes(),
                "post-loss command cleanup must be deterministic");
        assertArrayEquals(first.operationBytes(), second.operationBytes(),
                "resolved operation/encounter metadata must be deterministic");
        assertArrayEquals(first.stage21eBytes(), second.stage21eBytes(),
                "full post-battle Stage-21E checkpoint must be byte-identical");

        assertFalse(first.encounterActive(),
                "synchronous generated-world tactical execution must never leave hidden active battle runtime");
        assertTrue(first.anyPhysicalEffect(),
                "exact Stage-19 exchange must commit actual engineering/store/damage or destruction state");
        for (FleetId loss : first.losses()) {
            assertFalse(first.remainingFleetIds().contains(loss),
                    "every reported loss must be absent from ordinary world FleetId authority");
        }
        assertEquals(first.beforeFleetCount() - first.losses().size(), first.afterFleetCount(),
                "tactical execution may remove destroyed fleets but must not allocate replacements");
    }

    private static ScenarioResult runScenario() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<MilitaryFleet> military = militaryFleets(runtime);
        MilitaryFleet attacker = military.get(0);
        MilitaryFleet target = military.stream()
                .filter(value -> value.factionId() != attacker.factionId())
                .findFirst().orElseThrow();

        moveFleetByOrdinaryRoute(runtime, target.fleetId(), attacker.systemId());
        FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement = runtime.world().findFleet(target.fleetId()).orElseThrow();
        assertEquals(attacker.systemId(), targetPlacement.systemId());

        LocalPhysicalKinematics attackerPhysical = runtime.arrival().materialization(attacker.systemId())
                .physicalState(attackerPlacement.localEntityId()).orElseThrow();
        LocalPhysicalKinematics targetPhysical = new LocalPhysicalKinematics(
                attackerPhysical.position().translated(1_430d, 0d), 0d, 0d);
        runtime.arrival().materialization(attacker.systemId())
                .updatePhysicalState(targetPlacement.localEntityId(), targetPhysical);

        Map<FleetId, FleetOperationalAvailability> availability = Map.of(
                attacker.fleetId(), fullAvailability(),
                target.fleetId(), fullAvailability());
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(
                Stage175ICombatTestContentPack.loadDoctrines());
        FleetForceRegistry before = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        long now = runtime.world().getAuthoritativeWorldTick();

        CommandGroupState group = new CommandGroupState(
                1L,
                attacker.factionId(),
                "Stage21E exact tactical acceptance",
                List.of(attacker.fleetId()),
                attacker.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.INTERCEPT,
                OrderSource.AI,
                attacker.systemId(),
                List.of(attacker.systemId()),
                0,
                now,
                now + 100L,
                OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        ContactState contact = new ContactState(
                target.fleetId(),
                attacker.systemId(),
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "acceptance:exact-contact:" + target.fleetId().value(),
                now,
                now + 100L);
        OperationState operation = new OperationState(
                1L,
                OperationType.INTERCEPTION,
                group.id(),
                order.id(),
                attacker.factionId(),
                List.of(attacker.fleetId()),
                attacker.systemId(),
                attacker.systemId(),
                "system:" + attacker.systemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(attacker.systemId(), 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                now,
                now,
                -1L,
                contact,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(operation));

        int beforeFleetCount = runtime.world().getFleetPlacements().size();
        Stage21EGeneratedWorldStage19Authority authority = new Stage21EGeneratedWorldStage19Authority(
                runtime, new Stage19ExactTacticalEncounterResolver(), ACCEPTANCE_TACTICAL_TICKS);
        Stage21EGeneratedWorldTacticalExecutionService.ExecutionResult executed =
                new Stage21EGeneratedWorldTacticalExecutionService(authority)
                        .execute(command, operations, operation.id(), before, now);

        FleetForceRegistry after = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        Stage21EPhysicalConsequenceService.ConsequenceReport consequences =
                new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);
        boolean anyPhysicalEffect = consequences.fleets().stream()
                .anyMatch(row -> row.destroyed() || row.physicalPayloadChanged());

        Set<FleetId> remaining = new HashSet<>();
        runtime.world().getFleetPlacements().forEach(value -> remaining.add(value.id()));
        for (FleetId loss : consequences.losses()) {
            assertTrue(runtime.world().findFleet(loss).isEmpty());
            assertTrue(executed.commandState().groups().stream()
                    .noneMatch(current -> current.memberFleetIds().contains(loss)),
                    "destroyed FleetId must be removed from live command membership");
        }
        OperationState finalOperation = executed.operationState().requireOperation(operation.id());
        assertNotNull(finalOperation.encounter());
        assertFalse(finalOperation.encounter().active());
        if (executed.owningGroupDestroyed()) {
            assertEquals(OperationStatus.FAILED, finalOperation.status());
            assertTrue(executed.commandState().groups().stream().noneMatch(value -> value.id() == group.id()));
        } else {
            assertTrue(executed.commandState().requireGroup(group.id()).memberFleetIds().stream()
                    .allMatch(remaining::contains));
        }

        byte[] exactPostBattleStage20 = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        Stage21DGeneratedWorldRuntimePersistentState stage21d = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21c(runtime), executed.commandState());
        Stage21EGeneratedWorldRuntimePersistentState stage21e = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21d, executed.operationState());
        byte[] encoded = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(stage21e);
        Stage21EGeneratedWorldRuntimePersistentState decoded =
                Stage21EGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertArrayEquals(encoded, Stage21EGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(
                exactPostBattleStage20,
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                        decoded.stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime()
                                .stage20Runtime()),
                "post-battle checkpoint must embed the exact already-committed ordinary physical world");

        return new ScenarioResult(
                exactPostBattleStage20,
                FleetCommandStateCodec.encode(executed.commandState()),
                StrategicOperationStateCodec.encode(executed.operationState()),
                encoded,
                beforeFleetCount,
                runtime.world().getFleetPlacements().size(),
                consequences.losses(),
                Set.copyOf(remaining),
                anyPhysicalEffect,
                finalOperation.encounter().active());
    }

    private static List<MilitaryFleet> militaryFleets(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (result.size() < 2) throw new AssertionError("generated world lacks military fleets");
        return List.copyOf(result);
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            runtime.world().requestFleetJump(fleetId, route.get(index));
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary target movement must finish every topology hop");
            assertEquals(route.get(index), runtime.world().findFleet(fleetId).orElseThrow().systemId());
            if (index + 1 < route.size()) {
                awaitFittedCooldown(runtime, fleetId);
            }
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 400; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) {
                return;
            }
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary fitted FTL cooldown did not clear through simulation time");
    }

    private static List<StarSystemId> route(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId origin,
            StarSystemId destination) {
        if (origin.equals(destination)) return List.of(origin);
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : runtime.world().getTopology().neighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> reverse = new ArrayList<>();
                    StarSystemId cursor = destination;
                    while (cursor != null) {
                        reverse.add(cursor);
                        cursor = previous.get(cursor);
                    }
                    java.util.Collections.reverse(reverse);
                    return List.copyOf(reverse);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no route between military fleets");
    }

    private static FleetOperationalAvailability fullAvailability() {
        return new FleetOperationalAvailability(Integer.MAX_VALUE, FleetReadinessState.FULL);
    }

    private static Stage21CGeneratedWorldRuntimePersistentState stage21c(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20) {
        var captured = stage20.captureState();
        String first = captured.worldState().factions().get(0).factionContentId();
        String second = captured.worldState().factions().get(1).factionContentId();
        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21b = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21a.captureState(),
                List.of(FactionStrategicIntentState.initial(first), FactionStrategicIntentState.initial(second)));
        long now = stage20.world().getAuthoritativeWorldTick();
        return new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21b,
                DiplomaticLifecycleState.empty(now),
                Stage19ConflictState.empty(now));
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record ScenarioResult(
            byte[] stage20Bytes,
            byte[] commandBytes,
            byte[] operationBytes,
            byte[] stage21eBytes,
            int beforeFleetCount,
            int afterFleetCount,
            List<FleetId> losses,
            Set<FleetId> remainingFleetIds,
            boolean anyPhysicalEffect,
            boolean encounterActive) { }
}

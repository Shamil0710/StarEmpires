package com.spacesim.world.generation;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionState;
import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage21FGeneratedWorldRuntimePersistenceAcceptanceTest {
    @Test
    void occupationRoundTripsDeterministicallyInsideCompleteStage21ECheckpoint() {
        Fixture fixture = fixture();
        OccupationState occupation = new OccupationState(
                fixture.stableFactionId(),
                fixture.local().systemId(),
                1L,
                fixture.now(),
                fixture.now(),
                120L,
                -1L,
                false,
                OccupationStatus.OCCUPYING);
        Stage21FGeneratedWorldRuntimePersistentState expected = Stage21FGeneratedWorldRuntimePersistentState.compose(
                fixture.stage21E(), new TerritorialTransitionState(List.of(occupation)));

        byte[] first = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(expected);
        Stage21FGeneratedWorldRuntimePersistentState decoded =
                Stage21FGeneratedWorldRuntimePersistenceCodec.decode(first);
        byte[] second = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(occupation,
                decoded.territorialTransitions()
                        .occupationFor(fixture.stableFactionId(), fixture.local().systemId())
                        .orElseThrow());
        assertEquals(fixture.operation(), decoded.stage21ERuntime().operationState().requireOperation(1L));
        assertEquals(fixture.local().id(),
                decoded.stage21ERuntime().stage21DRuntime().fleetCommandState()
                        .requireGroup(1L).memberFleetIds().get(0));
    }

    @Test
    void occupationMustReferenceExactPersistedInvasionObjectiveAndFaction() {
        Fixture fixture = fixture();
        OccupationState unknownOperation = new OccupationState(
                fixture.stableFactionId(), fixture.local().systemId(), 99L,
                fixture.now(), fixture.now(), 0L, -1L, false, OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistentState.compose(
                        fixture.stage21E(), new TerritorialTransitionState(List.of(unknownOperation))));

        StarSystemId anotherSystem = fixture.stage21E().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime().worldState().topology().systems().stream()
                .map(system -> system.id())
                .filter(systemId -> !systemId.equals(fixture.local().systemId()))
                .findFirst().orElseThrow();
        OccupationState wrongObjective = new OccupationState(
                fixture.stableFactionId(), anotherSystem, 1L,
                fixture.now(), fixture.now(), 0L, -1L, false, OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistentState.compose(
                        fixture.stage21E(), new TerritorialTransitionState(List.of(wrongObjective))));

        String differentExistingFaction = fixture.stage21E().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime().worldState().factionStrategies().stream()
                .map(strategy -> strategy.factionContentId())
                .filter(factionId -> !factionId.equals(fixture.stableFactionId()))
                .findFirst().orElseThrow();
        OccupationState wrongFaction = new OccupationState(
                differentExistingFaction, fixture.local().systemId(), 1L,
                fixture.now(), fixture.now(), 0L, -1L, false, OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistentState.compose(
                        fixture.stage21E(), new TerritorialTransitionState(List.of(wrongFaction))));

        OperationState blockade = new OperationState(
                1L,
                OperationType.BLOCKADE,
                fixture.operation().commandGroupId(),
                fixture.operation().sourceOrderId(),
                fixture.operation().factionId(),
                fixture.operation().participantFleetIds(),
                fixture.operation().stagingSystemId(),
                fixture.operation().objectiveSystemId(),
                fixture.operation().objectiveId(),
                fixture.operation().rulesOfEngagement(),
                fixture.operation().supplyPolicy(),
                fixture.operation().withdrawalPolicy(),
                fixture.operation().status(),
                fixture.operation().createdAtTick(),
                fixture.operation().lastTransitionTick(),
                fixture.operation().unsupportedSinceTick(),
                fixture.operation().contact(),
                fixture.operation().encounter());
        Stage21EGeneratedWorldRuntimePersistentState wrongType = Stage21EGeneratedWorldRuntimePersistentState.compose(
                fixture.stage21E().stage21DRuntime(), new StrategicOperationState(2L, List.of(blockade)));
        OccupationState referencesBlockade = new OccupationState(
                fixture.stableFactionId(), fixture.local().systemId(), 1L,
                fixture.now(), fixture.now(), 0L, -1L, false, OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistentState.compose(
                        wrongType, new TerritorialTransitionState(List.of(referencesBlockade))));
    }

    @Test
    void unknownStrategicFactionAndFutureOrCorruptTopLevelPayloadFailClosed() {
        Fixture fixture = fixture();
        OccupationState unknownFaction = new OccupationState(
                "faction.stage21f.unknown",
                fixture.local().systemId(),
                1L,
                fixture.now(),
                fixture.now(),
                0L,
                -1L,
                false,
                OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistentState.compose(
                        fixture.stage21E(), new TerritorialTransitionState(List.of(unknownFaction))));

        Stage21FGeneratedWorldRuntimePersistentState state = Stage21FGeneratedWorldRuntimePersistentState.compose(
                fixture.stage21E(), TerritorialTransitionState.empty());
        byte[] valid = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(state);

        byte[] futureFile = valid.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistenceCodec.decode(futureFile));

        byte[] futureSchema = valid.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21FGeneratedWorldRuntimePersistentState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistenceCodec.decode(futureSchema));

        byte[] corrupt = valid.clone();
        corrupt[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistenceCodec.decode(corrupt));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistenceCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21FGeneratedWorldRuntimePersistenceCodec.decode(trailing));
    }

    private static Fixture fixture() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FleetPlacementState local = stage20.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> authoritativeFactionId(stage20, value) >= 0)
                .findFirst().orElseThrow();
        int factionId = authoritativeFactionId(stage20, local);
        String stableFactionId = FactionIdentityResolver.createDefault(
                        ContentCatalogLoader.loadDefault(), stage20.world().snapshot().factionIdentities())
                .stableId(factionId).orElseThrow();
        long now = stage20.world().getAuthoritativeWorldTick();

        CommandGroupState group = new CommandGroupState(
                1L, factionId, "Occupation Group", List.of(local.id()), local.systemId(),
                false, false, FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L, group.id(), OrderType.INVADE, OrderSource.AI, local.systemId(),
                List.of(local.systemId()), 0, now, now + 100L, OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        Stage21DGeneratedWorldRuntimePersistentState stage21D = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21C(stage20), command);
        OperationState operation = new OperationState(
                1L,
                OperationType.INVASION,
                group.id(),
                order.id(),
                factionId,
                List.of(local.id()),
                local.systemId(),
                local.systemId(),
                "system:" + local.systemId().value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(3_000, 1_000, 200L),
                new WithdrawalPolicy(local.systemId(), 2_000, true, true),
                OperationStatus.ACTIVE,
                now,
                now,
                -1L,
                null,
                null);
        Stage21EGeneratedWorldRuntimePersistentState stage21E = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21D, new StrategicOperationState(2L, List.of(operation)));
        return new Fixture(stage21E, local, operation, stableFactionId, now);
    }

    private static Stage21CGeneratedWorldRuntimePersistentState stage21C(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20) {
        var captured = stage20.captureState();
        String first = captured.worldState().factions().get(0).factionContentId();
        String second = captured.worldState().factions().get(1).factionContentId();
        var stage21A = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21B = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A.captureState(),
                List.of(FactionStrategicIntentState.initial(first), FactionStrategicIntentState.initial(second)));
        long now = stage20.world().getAuthoritativeWorldTick();
        return new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                DiplomaticLifecycleState.empty(now),
                Stage19ConflictState.empty(now));
    }

    private static int authoritativeFactionId(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
            return placement.transitState() == null || placement.transitState().entityState().faction() == null
                    ? -1 : placement.transitState().entityState().faction().factionId();
        }
        var session = runtime.world().findSession(placement.systemId()).orElseThrow();
        var captured = EntityStateMapper.capture(session.getEntityRegistry().require(placement.localEntityId()));
        return captured.faction() == null ? -1 : captured.faction().factionId();
    }

    private record Fixture(
            Stage21EGeneratedWorldRuntimePersistentState stage21E,
            FleetPlacementState local,
            OperationState operation,
            String stableFactionId,
            long now) {
    }
}

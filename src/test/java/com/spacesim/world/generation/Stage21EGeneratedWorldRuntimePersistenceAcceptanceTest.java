package com.spacesim.world.generation;

import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage21EGeneratedWorldRuntimePersistenceAcceptanceTest {
    @Test
    void activePhysicalOperationRoundTripsDeterministicallyWithCompleteStage21DWorld() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FleetPlacementState local = stage20.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> authoritativeFactionId(stage20, value) >= 0)
                .findFirst().orElseThrow();
        int factionId = authoritativeFactionId(stage20, local);
        long now = stage20.world().getAuthoritativeWorldTick();

        CommandGroupState group = new CommandGroupState(
                1L, factionId, "Blockade Group", List.of(local.id()), local.systemId(),
                false, false, FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L, group.id(), OrderType.BLOCKADE, OrderSource.AI, local.systemId(),
                List.of(local.systemId()), 0, now, now + 100L, OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        Stage21DGeneratedWorldRuntimePersistentState stage21d = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21c(stage20), command);
        OperationState operation = new OperationState(
                1L, OperationType.BLOCKADE, group.id(), order.id(), factionId, List.of(local.id()),
                local.systemId(), local.systemId(), "system:" + local.systemId().value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(3_000, 1_000, 200L),
                new WithdrawalPolicy(local.systemId(), 2_000, true, true),
                OperationStatus.ACTIVE, now, now, -1L, null, null);
        Stage21EGeneratedWorldRuntimePersistentState expected = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21d, new StrategicOperationState(2L, List.of(operation)));

        byte[] first = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(expected);
        Stage21EGeneratedWorldRuntimePersistentState decoded = Stage21EGeneratedWorldRuntimePersistenceCodec.decode(first);
        byte[] second = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(operation, decoded.operationState().requireOperation(1L));
        assertEquals(local.id(), decoded.stage21DRuntime().fleetCommandState().requireGroup(1L).memberFleetIds().get(0));
    }

    @Test
    void activeOperationCannotPersistMissingParticipantButTerminalHistoryMayRetainIt() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FleetPlacementState local = stage20.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> authoritativeFactionId(stage20, value) >= 0)
                .findFirst().orElseThrow();
        int factionId = authoritativeFactionId(stage20, local);
        long now = stage20.world().getAuthoritativeWorldTick();
        FleetId missing = new FleetId(Long.MAX_VALUE);

        CommandGroupState group = new CommandGroupState(
                1L, factionId, "Persistence Guard", List.of(local.id()), local.systemId(),
                false, false, FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L, group.id(), OrderType.BLOCKADE, OrderSource.AI, local.systemId(),
                List.of(local.systemId()), 0, now, now + 100L, OrderStatus.ACTIVE);
        Stage21DGeneratedWorldRuntimePersistentState stage21d = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21c(stage20), new FleetCommandState(2L, 2L, List.of(group), List.of(order)));

        OperationState active = operation(
                group, order, factionId, List.of(local.id(), missing), local, now, OperationStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21d, new StrategicOperationState(2L, List.of(active))));

        OperationState terminal = operation(
                group, order, factionId, List.of(local.id(), missing), local, now, OperationStatus.FAILED);
        Stage21EGeneratedWorldRuntimePersistentState historical = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21d, new StrategicOperationState(2L, List.of(terminal)));
        assertEquals(List.of(local.id(), missing), historical.operationState().requireOperation(1L).participantFleetIds());
    }

    @Test
    void futureFileAndSchemaVersionsFailClosed() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Stage21DGeneratedWorldRuntimePersistentState stage21d = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21c(stage20), FleetCommandState.empty());
        Stage21EGeneratedWorldRuntimePersistentState state = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21d, StrategicOperationState.empty());
        byte[] encoded = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(state);

        byte[] futureFile = encoded.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21EGeneratedWorldRuntimePersistenceCodec.decode(futureFile));

        byte[] futureSchema = encoded.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21EGeneratedWorldRuntimePersistentState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21EGeneratedWorldRuntimePersistenceCodec.decode(futureSchema));
    }

    private static OperationState operation(
            CommandGroupState group,
            FleetOrderState order,
            int factionId,
            List<FleetId> participants,
            FleetPlacementState local,
            long now,
            OperationStatus status) {
        return new OperationState(
                1L, OperationType.BLOCKADE, group.id(), order.id(), factionId, participants,
                local.systemId(), local.systemId(), "system:" + local.systemId().value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(3_000, 1_000, 200L),
                new WithdrawalPolicy(local.systemId(), 2_000, true, true),
                status, now, now, -1L, null, null);
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
                stage21b, DiplomaticLifecycleState.empty(now), Stage19ConflictState.empty(now));
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
}

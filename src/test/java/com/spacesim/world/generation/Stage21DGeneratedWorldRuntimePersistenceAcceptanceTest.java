package com.spacesim.world.generation;

import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.FleetTransferStateMapper;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetCommandStateCodec;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21DGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void midTransitGeneratedFleetAndActiveCommandRoundTripThenCompleteThroughOrdinaryArrivalAuthority() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FleetId fleetId = stage20.freight().capture().freighters().stream()
                .map(value -> value.fleetId())
                .filter(id -> {
                    FleetPlacementState placement = stage20.world().findFleet(id).orElseThrow();
                    return placement.locationKind() == FleetLocationKind.IN_SYSTEM
                            && !stage20.world().getTopology().neighbors(placement.systemId()).isEmpty();
                })
                .findFirst().orElseThrow();
        FleetPlacementState local = stage20.world().findFleet(fleetId).orElseThrow();
        StarSystemId origin = local.systemId();
        StarSystemId destination = stage20.world().getTopology().neighbors(origin).get(0);

        stage20.world().requestFleetJump(fleetId, destination);
        advanceUntilPhase(stage20, fleetId, FleetJumpPhase.IN_TRANSIT);
        FleetPlacementState inTransit = stage20.world().findFleet(fleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_TRANSIT, inTransit.locationKind());
        assertNotNull(inTransit.transitState());
        var exactTransitPayload = inTransit.transitState().entityState();

        Stage21CGeneratedWorldRuntimePersistentState stage21c = stage21c(stage20);
        int factionId = exactTransitPayload.faction() == null ? -1 : exactTransitPayload.faction().factionId();
        CommandGroupState group = new CommandGroupState(
                1L,
                factionId,
                "Generated Transit Group",
                List.of(fleetId),
                origin,
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.STAGE,
                OrderSource.AI,
                destination,
                List.of(origin, destination),
                0,
                stage20.world().getAuthoritativeWorldTick(),
                stage20.world().getAuthoritativeWorldTick() + 100L,
                OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        Stage21DGeneratedWorldRuntimePersistentState stage21d =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21c, command);

        byte[] encoded = Stage21DGeneratedWorldRuntimePersistenceCodec.encode(stage21d);
        Stage21DGeneratedWorldRuntimePersistentState decoded =
                Stage21DGeneratedWorldRuntimePersistenceCodec.decode(encoded);

        assertArrayEquals(encoded, Stage21DGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(
                Stage21CGeneratedWorldRuntimePersistenceCodec.encode(stage21c),
                Stage21CGeneratedWorldRuntimePersistenceCodec.encode(decoded.stage21CRuntime()));
        assertArrayEquals(
                FleetCommandStateCodec.encode(command),
                FleetCommandStateCodec.encode(decoded.fleetCommandState()));
        assertEquals(order, decoded.fleetCommandState().requireOrder(order.id()));

        var decodedStage20 = decoded.stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        FleetPlacementState persistedTransit = decodedStage20.worldState().fleets().stream()
                .filter(value -> value.id().equals(fleetId))
                .findFirst().orElseThrow();
        assertEquals(inTransit, persistedTransit);
        assertEquals(exactTransitPayload, persistedTransit.transitState().entityState());
        assertEquals(FleetJumpPhase.IN_TRANSIT, decodedStage20.worldState().fleetJumps().stream()
                .filter(value -> value.fleetId().equals(fleetId))
                .findFirst().orElseThrow().phase());

        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored =
                Stage20GeneratedWorldRuntimeBridge.restore(decodedStage20);
        advanceUntilArrived(restored, fleetId);
        FleetPlacementState arrived = restored.world().findFleet(fleetId).orElseThrow();

        assertEquals(fleetId, arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(destination, arrived.systemId());
        assertEquals(1L, restored.world().getFleetPlacements().stream()
                .filter(value -> value.id().equals(fleetId)).count());
        assertTrue(restored.arrival().materialization(destination)
                .physicalState(arrived.localEntityId()).isPresent(),
                "restored Stage-20 arrival authority must own exact destination kinematics");
        var arrivedEntity = restored.world().findSession(destination).orElseThrow()
                .getEntityRegistry().require(arrived.localEntityId());
        assertEquals(
                FleetTransferStateMapper.sanitize(exactTransitPayload),
                FleetTransferStateMapper.sanitize(EntityStateMapper.capture(arrivedEntity)),
                "fit, damage, cargo and every supported physical payload field must survive the ordinary transit handoff");
    }

    @Test
    void compositionRejectsUnknownFleetAndUnknownRouteSystem() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Stage21CGeneratedWorldRuntimePersistentState stage21c = stage21c(stage20);
        FleetPlacementState local = stage20.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst().orElseThrow();
        StarSystemId home = local.systemId();

        FleetCommandState unknownFleet = new FleetCommandState(
                2L,
                1L,
                List.of(new CommandGroupState(
                        1L, 1, "Unknown Fleet", List.of(new FleetId(Long.MAX_VALUE)), home,
                        false, false, FleetReadinessState.FULL)),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> Stage21DGeneratedWorldRuntimePersistentState.compose(stage21c, unknownFleet));

        StarSystemId unknownSystem = new StarSystemId(Long.MAX_VALUE);
        CommandGroupState validGroup = new CommandGroupState(
                1L, 1, "Known Fleet", List.of(local.id()), home,
                false, false, FleetReadinessState.FULL);
        FleetOrderState invalidOrder = new FleetOrderState(
                1L, validGroup.id(), OrderType.PATROL, OrderSource.PLAYER,
                unknownSystem, List.of(home, unknownSystem), 0, 0L, 1L, OrderStatus.STAGING);
        FleetCommandState unknownRoute = new FleetCommandState(
                2L, 2L, List.of(validGroup), List.of(invalidOrder));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21DGeneratedWorldRuntimePersistentState.compose(stage21c, unknownRoute));
    }

    @Test
    void futureFileAndSchemaVersionsFailClosed() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Stage21CGeneratedWorldRuntimePersistentState stage21c = stage21c(stage20);
        Stage21DGeneratedWorldRuntimePersistentState state =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21c, FleetCommandState.empty());
        byte[] encoded = Stage21DGeneratedWorldRuntimePersistenceCodec.encode(state);

        byte[] futureFile = encoded.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21DGeneratedWorldRuntimePersistenceCodec.decode(futureFile));

        byte[] futureSchema = encoded.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21DGeneratedWorldRuntimePersistentState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21DGeneratedWorldRuntimePersistenceCodec.decode(futureSchema));
    }

    private static Stage21CGeneratedWorldRuntimePersistentState stage21c(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime stage20) {
        var captured = stage20.captureState();
        String first = captured.worldState().factions().get(0).factionContentId();
        String second = captured.worldState().factions().get(1).factionContentId();
        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21b =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21a.captureState(),
                        List.of(
                                FactionStrategicIntentState.initial(first),
                                FactionStrategicIntentState.initial(second)));
        long now = stage20.world().getAuthoritativeWorldTick();
        return new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21b,
                DiplomaticLifecycleState.empty(now),
                Stage19ConflictState.empty(now));
    }

    private static void advanceUntilPhase(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            FleetJumpPhase phase) {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (runtime.world().findFleetJump(fleetId).orElseThrow().phase() == phase) {
                return;
            }
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary generated-world jump did not reach phase " + phase);
    }

    private static void advanceUntilArrived(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 200 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
            runtime.advanceFrame(0.25f);
        }
        assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(), "ordinary generated-world jump did not complete");
    }
}

package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetOrderExecutionServiceIntegrationTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);

    @Test
    void strategicMovementDispatchesTheExistingJumpFsmAndReconcilesOnlyAfterPhysicalArrival() {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = fleetIn(world, ALPHA);
        FleetOrderExecutionService service = new FleetOrderExecutionService(world.getTopology());
        FleetCommandState state = movementState(source.id(), List.of(ALPHA, BETA));

        FleetCommandState dispatched = service.dispatchMovementHop(
                world, state, forces(source, ALPHA), sourceOrderId());

        assertEquals(OrderStatus.ACTIVE, dispatched.requireOrder(sourceOrderId()).status());
        assertTrue(world.findFleetJump(source.id()).isPresent(),
                "Stage 21D must delegate movement to the existing jump FSM");
        assertEquals(FleetLocationKind.IN_SYSTEM, world.findFleet(source.id()).orElseThrow().locationKind(),
                "requesting a strategic hop must not teleport the fleet");

        assertThrows(IllegalStateException.class,
                () -> service.dispatchMovementHop(world, dispatched, forces(source, ALPHA), sourceOrderId()),
                "a second dispatch while the ordinary jump is active must be rejected");

        world.advanceFrame(6.0f);
        assertTrue(world.findFleetJump(source.id()).isEmpty());
        FleetPlacementState arrived = world.findFleet(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());

        FleetCommandState reconciled = service.reconcilePhysicalArrival(
                dispatched, forces(arrived, BETA), sourceOrderId());
        assertEquals(1, reconciled.requireOrder(sourceOrderId()).routeCursor());
        assertEquals(OrderStatus.COMPLETE, reconciled.requireOrder(sourceOrderId()).status());

        FleetCommandState secondReconcile = service.reconcilePhysicalArrival(
                reconciled, forces(arrived, BETA), sourceOrderId());
        assertEquals(reconciled, secondReconcile);
        assertEquals(1L, world.getFleetPlacements().stream()
                .filter(placement -> placement.id().equals(source.id()))
                .count(), "arrival reconciliation must never duplicate an ordinary fleet");
    }

    @Test
    void serviceOrdersAreRequestsOnlyAndCannotGrantFreeRepairOrRearm() {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = fleetIn(world, ALPHA);
        FleetOrderExecutionService service = new FleetOrderExecutionService(world.getTopology());
        CommandGroupState group = group(source.id());
        FleetOrderState repair = new FleetOrderState(
                1L, group.id(), OrderType.REPAIR, OrderSource.PLAYER, ALPHA,
                List.of(ALPHA), 0, 10L, 20L, OrderStatus.SERVICE_PENDING);
        FleetCommandState state = new FleetCommandState(2L, 2L, List.of(group), List.of(repair));

        List<FleetOrderExecutionService.Operation> operations = service.planNextOperations(
                state, forces(source, ALPHA), repair.id());

        assertEquals(1, operations.size());
        assertInstanceOf(FleetOrderExecutionService.ServiceOperation.class, operations.get(0));
        assertThrows(IllegalStateException.class,
                () -> service.dispatchMovementHop(world, state, forces(source, ALPHA), repair.id()));
        assertTrue(world.findFleetJump(source.id()).isEmpty());
        assertEquals(ALPHA, world.findFleet(source.id()).orElseThrow().systemId());
    }

    @Test
    void persistedNonNeighborHopFailsClosedInsteadOfCreatingStrategicTeleport() {
        WorldSimulation world = WorldSimulation.restore(initialWorld(), CONTENT, ALPHA, 10, 2);
        FleetPlacementState source = fleetIn(world, ALPHA);
        FleetOrderExecutionService service = new FleetOrderExecutionService(world.getTopology());
        FleetCommandState corruptRoute = movementState(source.id(), List.of(ALPHA, GAMMA));

        assertFalse(world.getTopology().neighbors(ALPHA).contains(GAMMA));
        assertThrows(IllegalStateException.class,
                () -> service.planNextOperations(corruptRoute, forces(source, ALPHA), sourceOrderId()));
        assertTrue(world.findFleetJump(source.id()).isEmpty());
    }

    private static long sourceOrderId() {
        return 1L;
    }

    private static FleetCommandState movementState(FleetId fleetId, List<StarSystemId> route) {
        CommandGroupState group = group(fleetId);
        FleetOrderState order = new FleetOrderState(
                sourceOrderId(), group.id(), OrderType.STAGE, OrderSource.AI,
                route.get(route.size() - 1), route, 0, 10L, 20L, OrderStatus.STAGING);
        return new FleetCommandState(2L, 2L, List.of(group), List.of(order));
    }

    private static CommandGroupState group(FleetId fleetId) {
        return new CommandGroupState(1L, 1, "Execution Group", List.of(fleetId), ALPHA,
                false, false, FleetReadinessState.FULL);
    }

    private static FleetForceRegistry forces(FleetPlacementState placement, StarSystemId systemId) {
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                placement.id(),
                1,
                placement.locationKind(),
                systemId,
                placement.transitState() == null ? null : placement.transitState().originSystemId(),
                placement.transitState() == null ? null : placement.transitState().destinationSystemId(),
                new EntityState(new EntityId(placement.id().value()), null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null),
                new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000))));
    }

    private static FleetPlacementState fleetIn(WorldSimulation world, StarSystemId systemId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        return world.getFleetPlacements().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> systemId.equals(placement.systemId()))
                .filter(placement -> session.getEntityRegistry().find(placement.localEntityId()) != null)
                .findFirst()
                .orElseThrow();
    }

    private static WorldState initialWorld() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        StarSystemNode gamma = new StarSystemNode(GAMMA, "Gamma", 200d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(21L),
                "Stage 21D Execution Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta, gamma))),
                List.of(new JumpConnection(ALPHA, BETA), new JumpConnection(BETA, GAMMA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ALPHA, SimulationSession.createDemo(0xA110L, CONTENT).snapshot()),
                        new StarSystemSimulationState(BETA, SimulationSession.createDemo(0xBE70L, CONTENT).snapshot()),
                        new StarSystemSimulationState(GAMMA, SimulationSession.createDemo(0x6A660L, CONTENT).snapshot())));
    }
}

package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetJumpServiceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);

    @Test
    void directJumpUsesExactDeterministicPhaseBoundaries() {
        Fixture fixture = fixture(new JumpTransitTiming(2L, 3L, 2L, 10d));
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);

        FleetJumpState requested = fixture.jumps().requestJump(source.id(), BETA, 0L, 50f, -25f);
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, requested.phase());
        assertEquals(2L, requested.phaseEndsTick());
        assertTrue(fixture.sessions().get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        fixture.jumps().advance(2L);
        assertEquals(FleetJumpPhase.JUMP_PENDING,
                fixture.jumps().find(source.id()).orElseThrow().phase());
        assertEquals(5L, fixture.jumps().find(source.id()).orElseThrow().phaseEndsTick());

        fixture.jumps().advance(5L);
        FleetJumpState transit = fixture.jumps().find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.IN_TRANSIT, transit.phase());
        assertEquals(105L, transit.phaseEndsTick());
        assertEquals(FleetLocationKind.IN_TRANSIT,
                fixture.fleets().find(source.id()).orElseThrow().locationKind());
        assertFalse(fixture.sessions().get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        fixture.jumps().advance(105L);
        FleetJumpState arriving = fixture.jumps().find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.ARRIVING, arriving.phase());
        assertEquals(107L, arriving.phaseEndsTick());
        FleetPlacementState destination = fixture.fleets().find(source.id()).orElseThrow();
        assertEquals(BETA, destination.systemId());
        assertEquals(FleetLocationKind.IN_SYSTEM, destination.locationKind());
        assertTrue(fixture.sessions().get(BETA).getEntityRegistry().contains(destination.localEntityId()));

        fixture.jumps().advance(107L);
        assertTrue(fixture.jumps().find(source.id()).isEmpty());
        assertEquals(source.id(),
                fixture.fleets().findByLocal(BETA, destination.localEntityId()).orElseThrow());
    }

    @Test
    void coarseAdvanceProcessesEveryExpiredPhaseWithoutFrameDependence() {
        Fixture fixture = fixture(new JumpTransitTiming(1L, 1L, 1L, 1000d));
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);

        fixture.jumps().requestJump(source.id(), BETA, 10L, 1f, 2f);
        fixture.jumps().advance(100L);

        assertTrue(fixture.jumps().find(source.id()).isEmpty());
        FleetPlacementState arrived = fixture.fleets().find(source.id()).orElseThrow();
        assertEquals(BETA, arrived.systemId());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
    }

    @Test
    void jumpRequiresDirectTopologyConnection() {
        Fixture fixture = fixture(JumpTransitTiming.DEFAULT);
        FleetPlacementState source = fleetIn(fixture.fleets(), ALPHA);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.jumps().requestJump(source.id(), GAMMA, 0L, 0f, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> JumpTransitTiming.DEFAULT.transitTicks(
                        fixture.state().topology(), ALPHA, GAMMA, 0.1f));
        assertEquals(FleetLocationKind.IN_SYSTEM,
                fixture.fleets().find(source.id()).orElseThrow().locationKind());
    }

    private static FleetPlacementState fleetIn(FleetWorldService fleets, StarSystemId systemId) {
        return fleets.snapshots().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> systemId.equals(placement.systemId()))
                .findFirst()
                .orElseThrow();
    }

    private static Fixture fixture(JumpTransitTiming timing) {
        WorldState state = worldState();
        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        for (StarSystemSimulationState system : state.systems()) {
            sessions.put(system.systemId(), SimulationSession.restore(system.simulationState(), CONTENT));
        }
        FleetWorldService fleets = new FleetWorldService(
                sessions, state.nextFleetIdValue(), state.fleets());
        FleetJumpService jumps = new FleetJumpService(
                state.topology(), sessions, fleets, timing, List.of());
        return new Fixture(state, sessions, fleets, jumps);
    }

    private static WorldState worldState() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        StarSystemNode gamma = new StarSystemNode(GAMMA, "Gamma", 200d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Jump Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta, gamma))),
                List.of(new JumpConnection(ALPHA, BETA), new JumpConnection(BETA, GAMMA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(
                                ALPHA, SimulationSession.createDemo(0xA110L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                BETA, SimulationSession.createDemo(0xBE70L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                GAMMA, SimulationSession.createDemo(0x6A660L, CONTENT).snapshot())));
    }

    private record Fixture(
            WorldState state,
            Map<StarSystemId, SimulationSession> sessions,
            FleetWorldService fleets,
            FleetJumpService jumps) {
    }
}

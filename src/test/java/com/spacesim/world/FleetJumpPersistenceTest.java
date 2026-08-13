package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FleetJumpPersistenceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @Test
    void activeTransitPhaseHasDeterministicSchemaV7RoundTrip() {
        WorldState base = world();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(base);
        FleetWorldService fleets = new FleetWorldService(
                sessions, base.nextFleetIdValue(), base.fleets());
        FleetJumpService jumps = new FleetJumpService(
                base.topology(), sessions, fleets,
                new JumpTransitTiming(1L, 1L, 1L, 10d), List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst().orElseThrow();

        jumps.requestJump(source.id(), BETA, 0L, 40f, -15f);
        jumps.advance(2L);
        assertEquals(FleetJumpPhase.IN_TRANSIT, jumps.find(source.id()).orElseThrow().phase());
        assertEquals(FleetLocationKind.IN_TRANSIT, fleets.find(source.id()).orElseThrow().locationKind());
        assertFalse(sessions.get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        WorldState transit = snapshot(base, sessions, fleets, jumps);
        byte[] first = WorldStateCodec.encode(transit);
        WorldState decoded = WorldStateCodec.decode(first);

        assertEquals(transit, decoded);
        assertEquals(List.of(jumps.find(source.id()).orElseThrow()), decoded.fleetJumps());
        assertEquals(FleetLocationKind.IN_TRANSIT,
                decoded.fleets().stream()
                        .filter(placement -> placement.id().equals(source.id()))
                        .findFirst().orElseThrow().locationKind());
        assertArrayEquals(first, WorldStateCodec.encode(decoded));
    }

    private static WorldState snapshot(
            WorldState template,
            Map<StarSystemId, SimulationSession> sessions,
            FleetWorldService fleets,
            FleetJumpService jumps) {
        List<StarSystemSimulationState> systems = new ArrayList<>();
        for (StarSystemSimulationState system : template.systems()) {
            systems.add(new StarSystemSimulationState(
                    system.systemId(), sessions.get(system.systemId()).snapshot()));
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                template.topology(),
                List.copyOf(systems),
                template.factions(),
                template.factionStrategies(),
                template.nextConstructionProjectIdValue(),
                template.constructionProjects(),
                template.factionEconomicPressures(),
                fleets.nextIdValue(),
                fleets.snapshots(),
                jumps.snapshots());
    }

    private static Map<StarSystemId, SimulationSession> restoreSessions(WorldState state) {
        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        for (StarSystemSimulationState system : state.systems()) {
            sessions.put(system.systemId(), SimulationSession.restore(system.simulationState(), CONTENT));
        }
        return sessions;
    }

    private static WorldState world() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Jump Persistence Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(
                                ALPHA, SimulationSession.createDemo(0xA110L, CONTENT).snapshot()),
                        new StarSystemSimulationState(
                                BETA, SimulationSession.createDemo(0xBE70L, CONTENT).snapshot())));
    }
}

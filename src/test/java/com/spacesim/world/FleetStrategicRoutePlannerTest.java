package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetStrategicRoutePlannerTest {
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);
    private static final StarSystemId DELTA = new StarSystemId(4L);

    @Test
    void equivalentTopologyInputOrderProducesTheSameDeterministicShortestRoute() {
        FleetStrategicRoutePlanner first = new FleetStrategicRoutePlanner(topology(false));
        FleetStrategicRoutePlanner second = new FleetStrategicRoutePlanner(topology(true));
        FleetStrategicRoutePlanner.TransitAccessPolicy allowed =
                (factionId, from, to, tick, destination) -> true;

        var firstRoute = first.plan(7, ALPHA, DELTA, 100L, allowed).orElseThrow();
        var secondRoute = second.plan(7, ALPHA, DELTA, 100L, allowed).orElseThrow();

        assertEquals(List.of(ALPHA, BETA, DELTA), firstRoute.systems());
        assertEquals(firstRoute, secondRoute);
        assertEquals(2, firstRoute.hopCount());
    }

    @Test
    void legalAccessCanForceAnAlternateNeighborOnlyRouteWithoutCreatingTeleport() {
        FleetStrategicRoutePlanner planner = new FleetStrategicRoutePlanner(topology(false));
        FleetStrategicRoutePlanner.TransitAccessPolicy denyBeta =
                (factionId, from, to, tick, destination) -> !to.equals(BETA);

        var route = planner.plan(7, ALPHA, DELTA, 100L, denyBeta).orElseThrow();

        assertEquals(List.of(ALPHA, GAMMA, DELTA), route.systems());
        for (int index = 0; index + 1 < route.systems().size(); index++) {
            assertTrue(topology(false).neighbors(route.systems().get(index)).contains(route.systems().get(index + 1)));
        }
    }

    @Test
    void deniedDestinationAndUnknownSystemsFailClosed() {
        FleetStrategicRoutePlanner planner = new FleetStrategicRoutePlanner(topology(false));
        FleetStrategicRoutePlanner.TransitAccessPolicy denyDestination =
                (factionId, from, to, tick, destination) -> !destination;

        assertTrue(planner.plan(7, ALPHA, DELTA, 100L, denyDestination).isEmpty());
        assertTrue(planner.plan(7, ALPHA, new StarSystemId(999L), 100L,
                (factionId, from, to, tick, destination) -> true).isEmpty());
    }

    private static GalaxyTopology topology(boolean reverseConnections) {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, -50d);
        StarSystemNode gamma = new StarSystemNode(GAMMA, "Gamma", 100d, 50d);
        StarSystemNode delta = new StarSystemNode(DELTA, "Delta", 200d, 0d);
        List<JumpConnection> connections = List.of(
                new JumpConnection(ALPHA, GAMMA),
                new JumpConnection(GAMMA, DELTA),
                new JumpConnection(ALPHA, BETA),
                new JumpConnection(BETA, DELTA));
        if (reverseConnections) {
            connections = List.of(
                    new JumpConnection(BETA, DELTA),
                    new JumpConnection(ALPHA, BETA),
                    new JumpConnection(GAMMA, DELTA),
                    new JumpConnection(ALPHA, GAMMA));
        }
        return new GalaxyTopology(
                new GalaxyId(21L),
                "Stage 21D Route Test",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(delta, gamma, beta, alpha))),
                connections);
    }
}

package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalacticPathPlannerTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private static final StarSystemId C = new StarSystemId(3L);
    private static final StarSystemId D = new StarSystemId(4L);

    @Test
    void findsMinimumAuthoritativeJumpTimeAcrossMultipleHops() {
        GalaxyTopology topology = topology(
                List.of(
                        new StarSystemNode(A, "A", 0d, 0d),
                        new StarSystemNode(B, "B", 100d, 0d),
                        new StarSystemNode(C, "C", 25d, 0d),
                        new StarSystemNode(D, "D", 50d, 50d)),
                List.of(
                        new JumpConnection(A, C),
                        new JumpConnection(C, B),
                        new JumpConnection(A, D),
                        new JumpConnection(D, B)));
        GalacticPathPlanner planner = new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, 0.1f);
        GalacticPath path = planner.findPath(A, B).orElseThrow();

        assertEquals(List.of(A, C, B), path.systems());
        assertEquals(2, path.jumpCount());
        assertEquals(planner.directEdgeTicks(A, C) + planner.directEdgeTicks(C, B), path.totalJumpTicks());
        assertEquals(path.totalJumpTicks() * (double) 0.1f, path.totalJumpSeconds(), 0d);
        assertEquals(100d, path.strategicDistance(), 1e-9);
    }

    @Test
    void resolvesEqualTimeAlternativesByLexicographicSystemPath() {
        GalaxyTopology topology = topology(
                List.of(
                        new StarSystemNode(A, "A", 0d, 0d),
                        new StarSystemNode(B, "B", 100d, 0d),
                        new StarSystemNode(C, "C", 50d, 0d),
                        new StarSystemNode(D, "D", 50d, 0d)),
                List.of(
                        new JumpConnection(A, C),
                        new JumpConnection(C, B),
                        new JumpConnection(A, D),
                        new JumpConnection(D, B)));
        GalacticPathPlanner planner = new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, 0.1f);

        assertEquals(List.of(A, C, B), planner.findPath(A, B).orElseThrow().systems());
    }

    @Test
    void supportsZeroHopAndDisconnectedCases() {
        GalaxyTopology topology = topology(
                List.of(new StarSystemNode(A, "A", 0d, 0d), new StarSystemNode(B, "B", 10d, 0d)),
                List.of());
        GalacticPathPlanner planner = new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, 0.1f);

        GalacticPath local = planner.findPath(A, A).orElseThrow();
        assertEquals(List.of(A), local.systems());
        assertEquals(0L, local.totalJumpTicks());
        assertEquals(0d, local.totalJumpSeconds());
        assertTrue(planner.findPath(A, B).isEmpty());
    }

    @Test
    void directEdgeDurationUsesStage10BStructuralBarriers() {
        GalaxyTopology topology = topology(
                List.of(new StarSystemNode(A, "A", 0d, 0d), new StarSystemNode(B, "B", 20d, 0d)),
                List.of(new JumpConnection(A, B)));
        JumpTransitTiming timing = new JumpTransitTiming(2L, 3L, 4L, 20d);
        GalacticPathPlanner planner = new GalacticPathPlanner(topology, timing, 0.1f);

        long transitOnly = timing.transitTicks(topology, A, B, 0.1f);
        assertEquals(transitOnly + 9L, planner.directEdgeTicks(A, B));
    }

    @Test
    void rejectsUnknownSystems() {
        GalaxyTopology topology = topology(List.of(new StarSystemNode(A, "A", 0d, 0d)), List.of());
        GalacticPathPlanner planner = new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, 0.1f);

        assertThrows(IllegalArgumentException.class, () -> planner.findPath(A, B));
    }

    private static GalaxyTopology topology(List<StarSystemNode> systems, List<JumpConnection> connections) {
        return new GalaxyTopology(
                new GalaxyId(90L),
                "Stage 10C Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", systems)),
                connections);
    }
}

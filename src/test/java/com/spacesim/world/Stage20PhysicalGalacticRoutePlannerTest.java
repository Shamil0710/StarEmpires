package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20PhysicalGalacticRoutePlannerTest {
    @Test
    void multiHopRouteUsesOnlyExplicitEdgesAndReportsPhysicalConsequences() {
        StarSystemNode a = new StarSystemNode(new StarSystemId(1), "A", 0, 0);
        StarSystemNode b = new StarSystemNode(new StarSystemId(2), "B", 1, 0);
        StarSystemNode c = new StarSystemNode(new StarSystemId(3), "C", 2, 0);
        StarSystemNode d = new StarSystemNode(new StarSystemId(4), "D", 3, 0);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1),
                "test",
                List.of(new SectorNode(new SectorId(1), "s", List.of(a, b, c, d))),
                List.of(
                        new JumpConnection(a.id(), b.id()),
                        new JumpConnection(b.id(), c.id()),
                        new JumpConnection(c.id(), d.id())));
        JumpPlan plan = fittedPlan(100, 20, 5, 1_000, 50);

        Stage20PhysicalGalacticRoute route = new Stage20PhysicalGalacticRoutePlanner(topology, plan)
                .findPath(a.id(), d.id())
                .orElseThrow();

        assertEquals(List.of(a.id(), b.id(), c.id(), d.id()), route.systems());
        assertEquals(3, route.jumpCount());
        assertEquals(355d, route.estimatedArrivalSeconds(), 1e-9);
        assertEquals(375d, route.estimatedReadyAgainSeconds(), 1e-9);
        assertEquals(3_000d, route.grossJumpEnergyJ(), 1e-9);
        assertEquals(150d, route.cumulativeJumpHeatJ(), 1e-9);
        assertTrue(route.perHopRevalidationRequired());
        assertEquals(0d, route.edges().get(0).cooldownBeforeSeconds(), 1e-9);
        assertEquals(20d, route.edges().get(1).cooldownBeforeSeconds(), 1e-9);
    }

    @Test
    void physicalTransitProviderCanChooseMoreHopsWhenPhysicalArrivalIsFaster() {
        StarSystemNode a = new StarSystemNode(new StarSystemId(1), "A", 0, 0);
        StarSystemNode b = new StarSystemNode(new StarSystemId(2), "B", 1, 0);
        StarSystemNode d = new StarSystemNode(new StarSystemId(4), "D", 2, 0);
        JumpConnection ad = new JumpConnection(a.id(), d.id());
        JumpConnection ab = new JumpConnection(a.id(), b.id());
        JumpConnection bd = new JumpConnection(b.id(), d.id());
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1),
                "test",
                List.of(new SectorNode(new SectorId(1), "s", List.of(a, b, d))),
                List.of(ad, ab, bd));
        JumpPlan plan = fittedPlan(10, 5, 10, 1_000, 50);

        Stage20PhysicalGalacticRoute route = new Stage20PhysicalGalacticRoutePlanner(
                topology,
                plan,
                edge -> edge.equals(ad) ? 1_000d : 10d)
                .findPath(a.id(), d.id())
                .orElseThrow();

        assertEquals(List.of(a.id(), b.id(), d.id()), route.systems());
        assertEquals(2, route.jumpCount());
        assertEquals(45d, route.estimatedArrivalSeconds(), 1e-9);
        assertTrue(route.edges().stream().map(Stage20PhysicalGalacticRoute.EdgeEstimate::connection)
                .allMatch(topology.connections()::contains));
    }

    @Test
    void rejectedFittedPlanCannotProducePhysicalRouteEstimate() {
        JumpPlan rejected = new JumpPlan(
                false,
                JumpFailure.COOLDOWN_ACTIVE,
                "ftl",
                10_000,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
        GalaxyTopology topology = WorldTopologyDefaults.singleSystem();
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20PhysicalGalacticRoutePlanner(topology, rejected));
    }

    private static JumpPlan fittedPlan(
            double spoolSeconds,
            double cooldownSeconds,
            double edgeTransitSeconds,
            double requiredEnergyJ,
            double heatJ) {
        return new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl",
                10_000,
                requiredEnergyJ,
                requiredEnergyJ,
                0,
                requiredEnergyJ / spoolSeconds,
                spoolSeconds,
                edgeTransitSeconds,
                cooldownSeconds,
                heatJ);
    }
}

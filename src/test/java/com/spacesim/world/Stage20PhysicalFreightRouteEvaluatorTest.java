package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.EndpointCycleProfile;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20PhysicalFreightRouteEvaluatorTest {
    @Test
    void repeatedFreightCycleUsesForwardAndReturnPhysicalFtlReadyTimes() {
        GalaxyTopology topology = chainTopology(true);
        Stage20PhysicalGalacticRoutePlanner loaded = new Stage20PhysicalGalacticRoutePlanner(
                topology, fittedPlan(10d, 20d, 5d));
        Stage20PhysicalGalacticRoutePlanner returned = new Stage20PhysicalGalacticRoutePlanner(
                topology, fittedPlan(10d, 20d, 5d));
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                loaded,
                returned,
                new FreightFleetProfile("test", 1_000d, 2, "test.fixture", false),
                (origin, destination) -> Optional.of(new EndpointCycleProfile(
                        30d, 40d, 100d, 50d, "test.endpoint")));

        Stage20EconomicBootstrapValidator.RouteAssessment route = evaluator
                .assess(new StarSystemId(1L), new StarSystemId(3L))
                .orElseThrow();

        assertEquals(List.of(new StarSystemId(1L), new StarSystemId(2L), new StarSystemId(3L)),
                route.orderedSystems());
        assertEquals(110d, route.travelTimeS(), 1e-9);
        assertEquals(2_000d / 240d, route.sustainableCargoThroughputKgPerSecond(), 1e-9);
    }

    @Test
    void boundedAllocationUsesSameCycleWithoutInventingAdditionalFreighters() {
        GalaxyTopology topology = chainTopology(true);
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(10d, 20d, 5d)),
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(10d, 20d, 5d)),
                new FreightFleetProfile("test", 1_000d, 4, "test.fixture", false),
                (origin, destination) -> Optional.of(new EndpointCycleProfile(
                        30d, 40d, 100d, 50d, "test.endpoint")));

        var one = evaluator.assessWithAllocatedFreighters(
                new StarSystemId(1L), new StarSystemId(3L), 1).orElseThrow();
        var two = evaluator.assessWithAllocatedFreighters(
                new StarSystemId(1L), new StarSystemId(3L), 2).orElseThrow();
        var full = evaluator.assess(new StarSystemId(1L), new StarSystemId(3L)).orElseThrow();

        assertEquals(one.travelTimeS(), two.travelTimeS(), 0d);
        assertEquals(one.travelTimeS(), full.travelTimeS(), 0d);
        assertEquals(one.sustainableCargoThroughputKgPerSecond() * 2d,
                two.sustainableCargoThroughputKgPerSecond(), 1e-9);
        assertEquals(one.sustainableCargoThroughputKgPerSecond() * 4d,
                full.sustainableCargoThroughputKgPerSecond(), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.assessWithAllocatedFreighters(
                        new StarSystemId(1L), new StarSystemId(3L), 0));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.assessWithAllocatedFreighters(
                        new StarSystemId(1L), new StarSystemId(3L), 5));
    }

    @Test
    void sourceHandlingCanBeTheSustainableBottleneckInsteadOfShipCycle() {
        GalaxyTopology topology = chainTopology(true);
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(1d, 1d, 1d)),
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(1d, 1d, 1d)),
                new FreightFleetProfile("test", 1_000d, 100, "test.fixture", false),
                (origin, destination) -> Optional.of(new EndpointCycleProfile(
                        0d, 0d, 2d, 1_000d, "test.endpoint")));

        Stage20EconomicBootstrapValidator.RouteAssessment route = evaluator
                .assess(new StarSystemId(1L), new StarSystemId(3L))
                .orElseThrow();

        assertEquals(2d, route.sustainableCargoThroughputKgPerSecond(), 1e-9);
    }

    @Test
    void sustainableRouteRequiresARealReturnPath() {
        GalaxyTopology forwardTopology = chainTopology(true);
        GalaxyTopology disconnectedReturnTopology = chainTopology(false);
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                new Stage20PhysicalGalacticRoutePlanner(forwardTopology, fittedPlan(1d, 1d, 1d)),
                new Stage20PhysicalGalacticRoutePlanner(disconnectedReturnTopology, fittedPlan(1d, 1d, 1d)),
                new FreightFleetProfile("test", 1_000d, 1, "test.fixture", false),
                (origin, destination) -> Optional.of(new EndpointCycleProfile(
                        1d, 1d, 100d, 100d, "test.endpoint")));

        assertTrue(evaluator.assess(new StarSystemId(1L), new StarSystemId(3L)).isEmpty());
    }

    @Test
    void sameSystemStillPaysExplicitLocalAndHandlingTimeInsteadOfZeroTimeDelivery() {
        GalaxyTopology topology = singleSystemTopology();
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(10d, 20d, 5d)),
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(10d, 20d, 5d)),
                new FreightFleetProfile("test", 1_000d, 1, "test.fixture", false),
                (origin, destination) -> Optional.of(new EndpointCycleProfile(
                        50d, 60d, 100d, 50d, "test.endpoint")));

        Stage20EconomicBootstrapValidator.RouteAssessment route = evaluator
                .assess(new StarSystemId(1L), new StarSystemId(1L))
                .orElseThrow();

        assertEquals(List.of(new StarSystemId(1L)), route.orderedSystems());
        assertEquals(80d, route.travelTimeS(), 1e-9);
        assertEquals(1_000d / 140d, route.sustainableCargoThroughputKgPerSecond(), 1e-9);
    }

    @Test
    void unresolvedEndpointFactsDoNotBecomeFreeLocalTransfer() {
        GalaxyTopology topology = chainTopology(true);
        Stage20PhysicalFreightRouteEvaluator evaluator = new Stage20PhysicalFreightRouteEvaluator(
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(1d, 1d, 1d)),
                new Stage20PhysicalGalacticRoutePlanner(topology, fittedPlan(1d, 1d, 1d)),
                new FreightFleetProfile("test", 1_000d, 1, "test.fixture", false),
                (origin, destination) -> Optional.empty());

        assertTrue(evaluator.assess(new StarSystemId(1L), new StarSystemId(3L)).isEmpty());
    }

    private static JumpPlan fittedPlan(double spoolSeconds, double cooldownSeconds, double transitSeconds) {
        return new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl",
                10_000d,
                1_000d,
                1_000d,
                0d,
                100d,
                spoolSeconds,
                transitSeconds,
                cooldownSeconds,
                50d);
    }

    private static GalaxyTopology chainTopology(boolean connected) {
        StarSystemNode a = new StarSystemNode(new StarSystemId(1L), "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(new StarSystemId(2L), "B", 1d, 0d);
        StarSystemNode c = new StarSystemNode(new StarSystemId(3L), "C", 2d, 0d);
        return new GalaxyTopology(
                new GalaxyId(1L),
                "freight-test",
                List.of(new SectorNode(new SectorId(1L), "sector", List.of(a, b, c))),
                connected
                        ? List.of(new JumpConnection(a.id(), b.id()), new JumpConnection(b.id(), c.id()))
                        : List.of());
    }

    private static GalaxyTopology singleSystemTopology() {
        StarSystemNode a = new StarSystemNode(new StarSystemId(1L), "A", 0d, 0d);
        return new GalaxyTopology(
                new GalaxyId(1L),
                "freight-test-single",
                List.of(new SectorNode(new SectorId(1L), "sector", List.of(a))),
                List.of());
    }
}

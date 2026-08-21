package com.spacesim.world;

import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.FailureReason;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.PlanReport;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.Status;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CoordinatedWholePlacementFreightPlannerTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void choosesAlternativeSupplierMixInsteadOfRejectingConflictingIndependentOptima() {
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));
        var routes = routes(Map.of(
                new RouteKey(SUPPLIER_C, START_A), 6d,
                new RouteKey(SUPPLIER_D, START_A), 6d,
                new RouteKey(SUPPLIER_C, START_B), 6d));

        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A, START_B),
                supply,
                List.of(requirement(WATER, 6d)),
                1,
                100,
                routes);

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(2, report.totalRemoteFreightersUsed());
        assertEquals(2, report.producerUsage().size());
        assertTrue(report.producerUsage().stream().allMatch(value ->
                Math.abs(value.capacityKgPerSecond() - value.reservedKgPerSecond()) < 1e-9));
        var alpha = report.starts().stream().filter(value -> value.stableFactionId().equals(FACTION_A)).findFirst().orElseThrow();
        var beta = report.starts().stream().filter(value -> value.stableFactionId().equals(FACTION_B)).findFirst().orElseThrow();
        assertEquals(SUPPLIER_D, alpha.demands().get(0).commitments().get(0).producerSystemId());
        assertEquals(SUPPLIER_C, beta.demands().get(0).commitments().get(0).producerSystemId());
    }

    @Test
    void canDivertLocalProducerToRemoteStartAndImportForTheLocalConsumer() {
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, START_A), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));
        var routes = routes(Map.of(
                new RouteKey(START_A, START_B), 6d,
                new RouteKey(SUPPLIER_D, START_A), 6d));

        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A, START_B),
                supply,
                List.of(requirement(WATER, 6d)),
                1,
                100,
                routes);

        assertEquals(Status.ACCEPTED, report.status());
        var alpha = report.starts().stream().filter(value -> value.stableFactionId().equals(FACTION_A)).findFirst().orElseThrow();
        var beta = report.starts().stream().filter(value -> value.stableFactionId().equals(FACTION_B)).findFirst().orElseThrow();
        assertEquals(SUPPLIER_D, alpha.demands().get(0).commitments().get(0).producerSystemId());
        assertEquals(START_A, beta.demands().get(0).commitments().get(0).producerSystemId());
        assertTrue(beta.demands().get(0).commitments().get(0).route().isPresent());
        assertEquals(2, report.totalRemoteFreightersUsed());
    }

    @Test
    void preservesOneFiniteFleetAcrossCommoditiesWithinEachStart() {
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 5d,
                new SupplyKey(ORE, SUPPLIER_D), 5d));

        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A),
                supply,
                List.of(requirement(WATER, 5d), requirement(ORE, 5d)),
                1,
                100,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 5d,
                        new RouteKey(SUPPLIER_D, START_A), 5d)));

        assertEquals(Status.INFEASIBLE, report.status());
        assertEquals(Optional.of(FailureReason.SINGLE_START_INFEASIBLE), report.failureReason());
        assertEquals(0, report.searchNodesVisited());
    }

    @Test
    void rejectsWholePlacementWhenGlobalProducerCapacityIsActuallyInsufficient() {
        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A, START_B),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 10d)),
                List.of(requirement(WATER, 6d)),
                2,
                100,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals(Status.INFEASIBLE, report.status());
        assertEquals(Optional.of(FailureReason.GLOBAL_PRODUCER_CAPACITY_INSUFFICIENT), report.failureReason());
        assertEquals(0, report.searchNodesVisited());
    }

    @Test
    void searchBudgetExhaustionRemainsUnresolvedRatherThanBecomingSeedInfeasibility() {
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));

        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A, START_B),
                supply,
                List.of(requirement(WATER, 6d)),
                1,
                1,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_D, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals(Status.UNRESOLVED_SEARCH_BUDGET, report.status());
        assertEquals(Optional.of(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED), report.failureReason());
        assertEquals(1, report.searchNodesVisited());
    }

    @Test
    void routePrefixCountsAreExplicitAndCanUseSeveralShipsOnOneRoute() {
        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true),
                placement(START_A),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 7d)),
                List.of(requirement(WATER, 7d)),
                2,
                100,
                routes(Map.of(new RouteKey(SUPPLIER_C, START_A), 4d)));

        assertEquals(Status.ACCEPTED, report.status());
        var commitment = report.starts().get(0).demands().get(0).commitments().get(0);
        assertEquals(2, commitment.allocatedFreighters());
        assertEquals(7d, commitment.deliveredKgPerSecond(), 1e-9);
    }

    @Test
    void supplyMapOrderCannotChangeTheCoordinatedPlan() {
        LinkedHashMap<SupplyKey, Double> first = new LinkedHashMap<>();
        first.put(new SupplyKey(WATER, SUPPLIER_D), 6d);
        first.put(new SupplyKey(WATER, SUPPLIER_C), 6d);
        LinkedHashMap<SupplyKey, Double> second = new LinkedHashMap<>();
        second.put(new SupplyKey(WATER, SUPPLIER_C), 6d);
        second.put(new SupplyKey(WATER, SUPPLIER_D), 6d);
        var evaluator = routes(Map.of(
                new RouteKey(SUPPLIER_C, START_A), 6d,
                new RouteKey(SUPPLIER_D, START_A), 6d,
                new RouteKey(SUPPLIER_C, START_B), 6d));

        PlanReport firstReport = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true), placement(START_A, START_B), supply(first),
                List.of(requirement(WATER, 6d)), 1, 100, evaluator);
        PlanReport secondReport = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(true), placement(START_A, START_B), supply(second),
                List.of(requirement(WATER, 6d)), 1, 100, evaluator);

        assertEquals(firstReport, secondReport);
    }

    @Test
    void revalidatesPhysicalRoutesAndRejectsNonNeighborShortcuts() {
        GalaxyTopology withoutDirectCtoB = topology(false);
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CoordinatedWholePlacementFreightPlanner.plan(
                        withoutDirectCtoB,
                        placement(START_B),
                        supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 6d)),
                        List.of(requirement(WATER, 6d)),
                        1,
                        100,
                        (origin, destination, ships) -> Optional.of(new RouteAssessment(
                                List.of(origin, destination), 100d, 6d))));
    }

    @Test
    void rejectsNonConcaveAllocatedRouteCurves() {
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CoordinatedWholePlacementFreightPlanner.plan(
                        topology(true),
                        placement(START_A),
                        supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 100d)),
                        List.of(requirement(WATER, 5d)),
                        2,
                        100,
                        (origin, destination, ships) -> Optional.of(new RouteAssessment(
                                List.of(origin, destination), 100d, ships == 1 ? 2d : 5d))));
    }

    private static Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator routes(
            Map<RouteKey, Double> oneShipThroughput) {
        return (origin, destination, ships) -> {
            Double throughput = oneShipThroughput.get(new RouteKey(origin, destination));
            if (throughput == null) {
                return Optional.empty();
            }
            return Optional.of(new RouteAssessment(
                    List.of(origin, destination),
                    100d,
                    throughput * ships));
        };
    }

    private static CommodityRequirement requirement(String commodityId, double rate) {
        return new CommodityRequirement(commodityId, 1_000d, rate);
    }

    private static SupplyThroughputReport supply(Map<SupplyKey, Double> capacities) {
        return new SupplyThroughputReport("test.supply", capacities, Set.of(), List.of());
    }

    private static PlacementResult placement(StarSystemId... starts) {
        java.util.ArrayList<Assignment> assignments = new java.util.ArrayList<>();
        for (int index = 0; index < starts.length; index++) {
            assignments.add(new Assignment(index == 0 ? FACTION_A : FACTION_B, starts[index], 0d));
        }
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile",
                PlacementStatus.ACCEPTED,
                assignments,
                assignments.size(),
                Optional.empty());
    }

    private static GalaxyTopology topology(boolean directCtoB) {
        StarSystemNode a = new StarSystemNode(START_A, "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(START_B, "B", 1d, 0d);
        StarSystemNode c = new StarSystemNode(SUPPLIER_C, "C", 2d, 0d);
        StarSystemNode d = new StarSystemNode(SUPPLIER_D, "D", 3d, 0d);
        java.util.ArrayList<JumpConnection> edges = new java.util.ArrayList<>();
        edges.add(new JumpConnection(START_A, START_B));
        edges.add(new JumpConnection(START_A, SUPPLIER_C));
        edges.add(new JumpConnection(START_A, SUPPLIER_D));
        edges.add(new JumpConnection(START_B, SUPPLIER_D));
        if (directCtoB) {
            edges.add(new JumpConnection(START_B, SUPPLIER_C));
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "coordinated-planner-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(a, b, c, d))),
                edges);
    }

    private record RouteKey(StarSystemId origin, StarSystemId destination) {
    }
}
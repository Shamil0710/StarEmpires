package com.spacesim.world;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage20CoordinatedWholePlacementFreightPlannerV2SearchTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void mostConstrainedDemandOrderingFindsAlternativeMixWithinThreeNodes() {
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));

        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(),
                placement(START_A, START_B),
                supply,
                List.of(requirement(6d)),
                1,
                3,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_D, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals("stage20e.coordinated-whole-placement-freight-planner.v2", report.version());
        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(3, report.searchNodesVisited());
        var alpha = report.starts().stream()
                .filter(value -> value.stableFactionId().equals(FACTION_A))
                .findFirst()
                .orElseThrow();
        var beta = report.starts().stream()
                .filter(value -> value.stableFactionId().equals(FACTION_B))
                .findFirst()
                .orElseThrow();
        assertEquals(SUPPLIER_D, alpha.demands().get(0).commitments().get(0).producerSystemId());
        assertEquals(SUPPLIER_C, beta.demands().get(0).commitments().get(0).producerSystemId());
    }

    @Test
    void optimisticFleetUpperBoundDoesNotPruneAnExactFitPrefixPlan() {
        PlanReport report = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                topology(),
                placement(START_A),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 8d)),
                List.of(requirement(8d)),
                2,
                3,
                routes(Map.of(new RouteKey(SUPPLIER_C, START_A), 4d)));

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(3, report.searchNodesVisited());
        assertEquals(2, report.totalRemoteFreightersUsed());
        assertEquals(8d,
                report.starts().get(0).demands().get(0).deliveredKgPerSecond(),
                1e-9);
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

    private static CommodityRequirement requirement(double rate) {
        return new CommodityRequirement(WATER, 1_000d, rate);
    }

    private static SupplyThroughputReport supply(Map<SupplyKey, Double> capacities) {
        return new SupplyThroughputReport("test.supply.v2-search", capacities, Set.of(), List.of());
    }

    private static PlacementResult placement(StarSystemId... starts) {
        ArrayList<Assignment> assignments = new ArrayList<>();
        for (int index = 0; index < starts.length; index++) {
            assignments.add(new Assignment(index == 0 ? FACTION_A : FACTION_B, starts[index], 0d));
        }
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile.v2-search",
                PlacementStatus.ACCEPTED,
                assignments,
                assignments.size(),
                Optional.empty());
    }

    private static GalaxyTopology topology() {
        StarSystemNode a = new StarSystemNode(START_A, "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(START_B, "B", 1d, 0d);
        StarSystemNode c = new StarSystemNode(SUPPLIER_C, "C", 2d, 0d);
        StarSystemNode d = new StarSystemNode(SUPPLIER_D, "D", 3d, 0d);
        return new GalaxyTopology(
                new GalaxyId(1L),
                "coordinated-planner-v2-search-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(a, b, c, d))),
                List.of(
                        new JumpConnection(START_A, START_B),
                        new JumpConnection(START_A, SUPPLIER_C),
                        new JumpConnection(START_A, SUPPLIER_D),
                        new JumpConnection(START_B, SUPPLIER_C),
                        new JumpConnection(START_B, SUPPLIER_D)));
    }

    private record RouteKey(StarSystemId origin, StarSystemId destination) {
    }
}

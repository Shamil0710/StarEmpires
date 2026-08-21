package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound.Assessment;
import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound.Status;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage20CommodityFrontierSharedProducerBoundTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void sharedProducerCapacityCanProveCapVectorImpossibleBeforeExactSearch() {
        Assessment result = Stage20CommodityFrontierSharedProducerBound.assess(
                topology(),
                placement(false),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 10d)),
                requirement(6d),
                Map.of(FACTION_A, 1, FACTION_B, 1),
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals(Stage20CommodityFrontierSharedProducerBound.CURRENT_VERSION, result.version());
        assertEquals(Status.PROVED_INFEASIBLE, result.status());
        assertEquals(12d, result.requiredTotalKgPerSecond(), 0d);
        assertEquals(10d, result.optimisticMaxFlowKgPerSecond(), 1.0e-9d);
    }

    @Test
    void relaxedShipSharingNeverClaimsConcreteAcceptance() {
        Assessment result = Stage20CommodityFrontierSharedProducerBound.assess(
                topology(),
                placement(false),
                supply(Map.of(
                        new SupplyKey(WATER, SUPPLIER_C), 6d,
                        new SupplyKey(WATER, SUPPLIER_D), 6d,
                        new SupplyKey(WATER, START_B), 10d)),
                requirement(10d),
                Map.of(FACTION_A, 1, FACTION_B, 0),
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_D, START_A), 6d)));

        assertEquals(Status.POSSIBLY_FEASIBLE, result.status());
        assertEquals(20d, result.optimisticMaxFlowKgPerSecond(), 1.0e-9d);
    }

    @Test
    void capMapAndAssignmentIterationOrderAreCanonical() {
        LinkedHashMap<String, Integer> reversedCaps = new LinkedHashMap<>();
        reversedCaps.put(FACTION_B, 1);
        reversedCaps.put(FACTION_A, 1);
        var routeEvaluator = routes(Map.of(
                new RouteKey(SUPPLIER_C, START_A), 6d,
                new RouteKey(SUPPLIER_C, START_B), 6d));
        var supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 10d));

        Assessment canonical = Stage20CommodityFrontierSharedProducerBound.assess(
                topology(), placement(false), supply, requirement(6d),
                Map.of(FACTION_A, 1, FACTION_B, 1), routeEvaluator);
        Assessment reversed = Stage20CommodityFrontierSharedProducerBound.assess(
                topology(), placement(true), supply, requirement(6d), reversedCaps, routeEvaluator);

        assertEquals(canonical, reversed);
    }

    @Test
    void nonNeighborRouteIsRejectedRatherThanUsedAsOptimisticCapacity() {
        var invalidRoutes = (Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator) (origin, destination, ships) -> {
            if (origin.equals(SUPPLIER_C) && destination.equals(START_A)) {
                return Optional.of(new RouteAssessment(
                        List.of(origin, SUPPLIER_D, destination),
                        100d,
                        6d * ships));
            }
            return Optional.empty();
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20CommodityFrontierSharedProducerBound.assess(
                        topologyWithoutSupplierCrossLink(),
                        placement(false),
                        supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 20d)),
                        requirement(6d),
                        Map.of(FACTION_A, 1, FACTION_B, 1),
                        invalidRoutes));
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
        return new SupplyThroughputReport(
                "test.water.shared-producer-bound",
                capacities,
                Set.of(),
                List.of());
    }

    private static PlacementResult placement(boolean reverse) {
        ArrayList<Assignment> assignments = new ArrayList<>();
        Assignment alpha = new Assignment(FACTION_A, START_A, 0d);
        Assignment beta = new Assignment(FACTION_B, START_B, 0d);
        if (reverse) {
            assignments.add(beta);
            assignments.add(alpha);
        } else {
            assignments.add(alpha);
            assignments.add(beta);
        }
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile.shared-producer-bound",
                PlacementStatus.ACCEPTED,
                assignments,
                assignments.size(),
                Optional.empty());
    }

    private static GalaxyTopology topology() {
        return topology(true);
    }

    private static GalaxyTopology topologyWithoutSupplierCrossLink() {
        return topology(false);
    }

    private static GalaxyTopology topology(boolean includeSupplierCrossLink) {
        StarSystemNode a = new StarSystemNode(START_A, "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(START_B, "B", 1d, 0d);
        StarSystemNode c = new StarSystemNode(SUPPLIER_C, "C", 2d, 0d);
        StarSystemNode d = new StarSystemNode(SUPPLIER_D, "D", 3d, 0d);
        ArrayList<JumpConnection> jumps = new ArrayList<>(List.of(
                new JumpConnection(START_A, START_B),
                new JumpConnection(START_A, SUPPLIER_C),
                new JumpConnection(START_A, SUPPLIER_D),
                new JumpConnection(START_B, SUPPLIER_C),
                new JumpConnection(START_B, SUPPLIER_D)));
        if (includeSupplierCrossLink) {
            jumps.add(new JumpConnection(SUPPLIER_C, SUPPLIER_D));
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "shared-producer-bound-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(a, b, c, d))),
                jumps);
    }

    private record RouteKey(StarSystemId origin, StarSystemId destination) {
    }
}

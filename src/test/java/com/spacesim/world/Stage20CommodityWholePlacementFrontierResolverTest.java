package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CommodityWholePlacementFrontierResolverTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void maximumCapNecessaryConditionCanProveCompleteEmptyFrontierWithoutDfs() {
        var result = Stage20CommodityWholePlacementFrontierResolver.resolve(
                topology(),
                placement(false),
                supply(Map.of(
                        new SupplyKey(WATER, SUPPLIER_C), 10d,
                        new SupplyKey(WATER, SUPPLIER_D), 2d)),
                requirement(6d),
                Map.of(FACTION_A, 1, FACTION_B, 1),
                50,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals(Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION, result.version());
        assertEquals(FrontierStatus.COMPLETE, result.status());
        assertEquals(0, result.searchNodesVisited());
        assertTrue(result.options().isEmpty());
        assertEquals(Map.of(FACTION_A, 1, FACTION_B, 1), result.remoteFreighterBudgetByFaction());
    }

    @Test
    void possiblyFeasibleMaximumCapDelegatesToExactFrontierGenerator() {
        var result = Stage20CommodityWholePlacementFrontierResolver.resolve(
                topology(),
                placement(false),
                supply(Map.of(
                        new SupplyKey(WATER, START_A), 6d,
                        new SupplyKey(WATER, START_B), 6d)),
                requirement(6d),
                Map.of(FACTION_A, 1, FACTION_B, 1),
                50,
                (origin, destination, ships) -> Optional.empty());

        assertEquals(Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION, result.version());
        assertEquals(FrontierStatus.COMPLETE, result.status());
        assertEquals(1, result.options().size());
        assertEquals(Map.of(FACTION_A, 0, FACTION_B, 0),
                result.options().get(0).remoteFreightersByFaction());
        assertTrue(result.searchNodesVisited() > 0,
                "possible maximum-cap evidence must still delegate to exact route-prefix search");
    }

    @Test
    void inputOrderingDoesNotChangeResolvedEvidence() {
        LinkedHashMap<String, Integer> reversedBudgets = new LinkedHashMap<>();
        reversedBudgets.put(FACTION_B, 1);
        reversedBudgets.put(FACTION_A, 1);
        var supply = supply(Map.of(
                new SupplyKey(WATER, START_A), 6d,
                new SupplyKey(WATER, START_B), 6d));

        var canonical = Stage20CommodityWholePlacementFrontierResolver.resolve(
                topology(), placement(false), supply, requirement(6d),
                Map.of(FACTION_A, 1, FACTION_B, 1), 50,
                (origin, destination, ships) -> Optional.empty());
        var reversed = Stage20CommodityWholePlacementFrontierResolver.resolve(
                topology(), placement(true), supply, requirement(6d),
                reversedBudgets, 50,
                (origin, destination, ships) -> Optional.empty());

        assertEquals(canonical, reversed);
    }

    @Test
    void invalidSearchBudgetFailsBeforeAnyClassification() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20CommodityWholePlacementFrontierResolver.resolve(
                        topology(),
                        placement(false),
                        supply(Map.of(new SupplyKey(WATER, START_A), 6d,
                                new SupplyKey(WATER, START_B), 6d)),
                        requirement(6d),
                        Map.of(FACTION_A, 1, FACTION_B, 1),
                        0,
                        (origin, destination, ships) -> Optional.empty()));
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
                "test.water.frontier-resolver",
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
                "test.profile.frontier-resolver",
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
                "frontier-resolver-test",
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

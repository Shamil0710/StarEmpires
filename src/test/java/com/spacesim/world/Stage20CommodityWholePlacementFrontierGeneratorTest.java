package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityFrontier;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
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

class Stage20CommodityWholePlacementFrontierGeneratorTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void completeFrontierKeepsComplementaryAsymmetricShipVectors() {
        FrontierReport report = generateAsymmetric(2_000, placement(false), orderedBudgets(false));

        assertEquals(Stage20CommodityWholePlacementFrontierGenerator.CURRENT_VERSION, report.version());
        assertEquals(FrontierStatus.COMPLETE, report.status());
        assertEquals(WATER, report.commodityId());
        assertEquals(2, report.options().size());
        assertEquals(
                Set.of(
                        Map.of(FACTION_A, 1, FACTION_B, 2),
                        Map.of(FACTION_A, 2, FACTION_B, 1)),
                report.options().stream()
                        .map(value -> value.remoteFreightersByFaction())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(report.searchNodesVisited() > 0);
        assertTrue(report.options().stream().allMatch(value -> value.starts().size() == 2));
        assertTrue(report.options().stream().allMatch(value -> value.producerUsage().size() == 2));
    }

    @Test
    void generatedFrontierFeedsExactCrossCommodityCombiner() {
        FrontierReport water = generateAsymmetric(2_000, placement(false), orderedBudgets(false));
        CommodityFrontier ore = new CommodityFrontier(
                ORE,
                "test.ore.frontier.v1",
                FrontierStatus.COMPLETE,
                List.of(new CommodityOption(
                        "ore-a-one",
                        ORE,
                        Map.of(FACTION_A, 1, FACTION_B, 0))));

        CombinationReport combined = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(water.toCombinerFrontier(), ore),
                Map.of(FACTION_A, 2, FACTION_B, 2));

        assertEquals(Stage20CommodityFreightFrontierCombiner.Status.ACCEPTED, combined.status());
        assertEquals(Map.of(FACTION_A, 2, FACTION_B, 2), combined.remoteFreightersUsedByFaction());
        var selectedWater = combined.selectedOptions().stream()
                .filter(value -> value.commodityId().equals(WATER))
                .findFirst()
                .orElseThrow();
        assertEquals(Map.of(FACTION_A, 1, FACTION_B, 2), selectedWater.remoteFreightersByFaction());
    }

    @Test
    void boundedSearchRetainsFailClosedIncompleteStatus() {
        FrontierReport report = generateAsymmetric(1, placement(false), orderedBudgets(false));

        assertEquals(FrontierStatus.UNRESOLVED_SEARCH_BUDGET, report.status());
        assertEquals(1, report.searchNodesVisited());
        assertTrue(report.options().isEmpty());
        assertEquals(
                FrontierStatus.UNRESOLVED_SEARCH_BUDGET,
                report.toCombinerFrontier().status());
    }

    @Test
    void aggregateProducerScarcityProvesCompleteEmptyFrontierWithoutSearch() {
        FrontierReport report = Stage20CommodityWholePlacementFrontierGenerator.generate(
                topology(),
                placement(false),
                supply(WATER, Map.of(new SupplyKey(WATER, SUPPLIER_C), 6d)),
                requirement(WATER, 6d),
                Map.of(FACTION_A, 2, FACTION_B, 2),
                20,
                routes(Map.of(
                        new RouteKey(SUPPLIER_C, START_A), 6d,
                        new RouteKey(SUPPLIER_C, START_B), 6d)));

        assertEquals(FrontierStatus.COMPLETE, report.status());
        assertEquals(0, report.searchNodesVisited());
        assertTrue(report.options().isEmpty());
    }

    @Test
    void allLocalServiceProvesZeroShipFrontierImmediately() {
        FrontierReport report = Stage20CommodityWholePlacementFrontierGenerator.generate(
                topology(),
                placement(false),
                supply(WATER, Map.of(
                        new SupplyKey(WATER, START_A), 6d,
                        new SupplyKey(WATER, START_B), 6d)),
                requirement(WATER, 6d),
                Map.of(FACTION_A, 2, FACTION_B, 2),
                20,
                routes(Map.of()));

        assertEquals(FrontierStatus.COMPLETE, report.status());
        assertEquals(1, report.searchNodesVisited());
        assertEquals(1, report.options().size());
        assertEquals(
                Map.of(FACTION_A, 0, FACTION_B, 0),
                report.options().get(0).remoteFreightersByFaction());
        assertTrue(report.options().get(0).starts().stream()
                .flatMap(value -> value.demands().stream())
                .flatMap(value -> value.commitments().stream())
                .allMatch(value -> value.local() && value.allocatedFreighters() == 0));
    }

    @Test
    void assignmentAndBudgetMapOrderCannotChangeFrontier() {
        FrontierReport canonical = generateAsymmetric(
                2_000,
                placement(false),
                orderedBudgets(false));
        FrontierReport reversed = generateAsymmetric(
                2_000,
                placement(true),
                orderedBudgets(true));

        assertEquals(canonical.status(), reversed.status());
        assertEquals(canonical.searchNodesVisited(), reversed.searchNodesVisited());
        assertEquals(
                canonical.options().stream().map(value -> value.optionId()).toList(),
                reversed.options().stream().map(value -> value.optionId()).toList());
        assertEquals(
                canonical.options().stream().map(value -> value.remoteFreightersByFaction()).toList(),
                reversed.options().stream().map(value -> value.remoteFreightersByFaction()).toList());
    }

    @Test
    void budgetsMustCoverExactlyPlacedFactions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20CommodityWholePlacementFrontierGenerator.generate(
                        topology(),
                        placement(false),
                        asymmetricSupply(),
                        requirement(WATER, 6d),
                        Map.of(FACTION_A, 2),
                        20,
                        asymmetricRoutes()));
    }

    @Test
    void routePathMustRemainOnExplicitNeighborTopology() {
        var invalidRoutes = (Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator) (origin, destination, ships) -> {
            if (origin.equals(SUPPLIER_C) && destination.equals(START_A)) {
                return Optional.of(new RouteAssessment(
                        List.of(origin, SUPPLIER_D, destination),
                        100d,
                        6d * ships));
            }
            return asymmetricRoutes().assess(origin, destination, ships);
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20CommodityWholePlacementFrontierGenerator.generate(
                        topologyWithoutSupplierCrossLink(),
                        placement(false),
                        asymmetricSupply(),
                        requirement(WATER, 6d),
                        Map.of(FACTION_A, 2, FACTION_B, 2),
                        100,
                        invalidRoutes));
    }

    private static FrontierReport generateAsymmetric(
            int searchBudget,
            PlacementResult placement,
            Map<String, Integer> budgets) {
        return Stage20CommodityWholePlacementFrontierGenerator.generate(
                topology(),
                placement,
                asymmetricSupply(),
                requirement(WATER, 6d),
                budgets,
                searchBudget,
                asymmetricRoutes());
    }

    private static SupplyThroughputReport asymmetricSupply() {
        return supply(WATER, Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));
    }

    private static Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator asymmetricRoutes() {
        return routes(Map.of(
                new RouteKey(SUPPLIER_C, START_A), 6d,
                new RouteKey(SUPPLIER_D, START_A), 3d,
                new RouteKey(SUPPLIER_C, START_B), 6d,
                new RouteKey(SUPPLIER_D, START_B), 3d));
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

    private static SupplyThroughputReport supply(
            String commodityId,
            Map<SupplyKey, Double> capacities) {
        return new SupplyThroughputReport(
                "test." + commodityId + ".frontier",
                capacities,
                Set.of(),
                List.of());
    }

    private static Map<String, Integer> orderedBudgets(boolean reverse) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (reverse) {
            result.put(FACTION_B, 2);
            result.put(FACTION_A, 2);
        } else {
            result.put(FACTION_A, 2);
            result.put(FACTION_B, 2);
        }
        return result;
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
                "test.profile.frontier",
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
                "commodity-frontier-generator-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(a, b, c, d))),
                jumps);
    }

    private record RouteKey(StarSystemId origin, StarSystemId destination) {
    }
}

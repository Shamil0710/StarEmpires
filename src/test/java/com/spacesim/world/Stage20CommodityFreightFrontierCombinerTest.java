package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityFrontier;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FailureReason;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage20CommodityFreightFrontierCombinerTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final String ALPHA = "faction.alpha";
    private static final String BETA = "faction.beta";
    private static final Map<String, Integer> BUDGET_13 = Map.of(ALPHA, 13, BETA, 13);

    @Test
    void combinesComplementaryCommodityOptionsUnderOneFiniteFleetPerStart() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        complete(WATER,
                                option("water-a-heavy", WATER, 8, 2),
                                option("water-balanced", WATER, 3, 7)),
                        complete(ORE,
                                option("ore-balanced", ORE, 7, 3),
                                option("ore-b-heavy", ORE, 5, 6))),
                Map.of(ALPHA, 10, BETA, 10));

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(Optional.empty(), report.failureReason());
        assertEquals(Map.of(ALPHA, 10, BETA, 10), report.remoteFreightersUsedByFaction());
        assertEquals(List.of("ore-balanced", "water-balanced"),
                report.selectedOptions().stream().map(value -> value.optionId()).toList());
    }

    @Test
    void completeFrontiersWithoutSharedFleetCombinationAreProvedInfeasible() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        complete(WATER, option("water", WATER, 8, 8)),
                        complete(ORE, option("ore", ORE, 6, 6))),
                BUDGET_13);

        assertEquals(Status.INFEASIBLE, report.status());
        assertEquals(Optional.of(FailureReason.SHARED_FLEET_COMBINATION_INFEASIBLE), report.failureReason());
    }

    @Test
    void incompleteFrontierCannotBecomeFalsePhysicalInfeasibility() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        unresolved(WATER, option("water-known", WATER, 10, 10)),
                        complete(ORE, option("ore", ORE, 4, 4))),
                BUDGET_13);

        assertEquals(Status.UNRESOLVED_FRONTIER, report.status());
        assertEquals(Optional.of(FailureReason.FRONTIER_INCOMPLETE), report.failureReason());
    }

    @Test
    void incompleteFrontierCanStillAcceptAConcreteKnownCombination() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        unresolved(WATER, option("water-known", WATER, 8, 7)),
                        complete(ORE, option("ore", ORE, 5, 6))),
                BUDGET_13);

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(Map.of(ALPHA, 13, BETA, 13), report.remoteFreightersUsedByFaction());
    }

    @Test
    void completeEmptyCommodityFrontierProvesCommodityInfeasibilityEvenWithOtherUnknowns() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        new CommodityFrontier(WATER, "test.frontier.v1", FrontierStatus.COMPLETE, List.of()),
                        new CommodityFrontier(ORE, "test.frontier.v1", FrontierStatus.UNRESOLVED_SEARCH_BUDGET, List.of())),
                BUDGET_13);

        assertEquals(Status.INFEASIBLE, report.status());
        assertEquals(Optional.of(FailureReason.COMMODITY_INFEASIBLE), report.failureReason());
    }

    @Test
    void dominancePruningKeepsTheLowerShipOptionAndDeterministicEqualVectorTie() {
        CombinationReport report = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(
                        complete(WATER,
                                option("z-dominated", WATER, 6, 6),
                                option("b-equal", WATER, 5, 5),
                                option("a-equal", WATER, 5, 5)),
                        complete(ORE, option("ore", ORE, 1, 1))),
                BUDGET_13);

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(List.of("a-equal", "ore"),
                report.selectedOptions().stream().map(value -> value.optionId()).sorted().toList());
        assertEquals(Map.of(ALPHA, 6, BETA, 6), report.remoteFreightersUsedByFaction());
    }

    @Test
    void frontierAndMapInsertionOrderCannotChangeExactCombination() {
        LinkedHashMap<String, Integer> firstBudgets = new LinkedHashMap<>();
        firstBudgets.put(BETA, 13);
        firstBudgets.put(ALPHA, 13);
        LinkedHashMap<String, Integer> secondBudgets = new LinkedHashMap<>();
        secondBudgets.put(ALPHA, 13);
        secondBudgets.put(BETA, 13);

        CommodityFrontier waterFirst = complete(WATER,
                optionWithMap("water", WATER, Map.of(BETA, 7, ALPHA, 8)));
        CommodityFrontier oreSecond = complete(ORE,
                optionWithMap("ore", ORE, Map.of(ALPHA, 5, BETA, 6)));

        CombinationReport first = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(waterFirst, oreSecond), firstBudgets);
        CombinationReport second = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(oreSecond, waterFirst), secondBudgets);

        assertEquals(first, second);
    }

    @Test
    void everyOptionMustCoverExactlyThePlacedStarts() {
        CommodityFrontier invalid = complete(WATER,
                optionWithMap("water", WATER, Map.of(ALPHA, 5)));

        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityFreightFrontierCombiner.combine(List.of(invalid), BUDGET_13));
    }

    @Test
    void negativeShipCountsAndDuplicateCommodityFrontiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new CommodityOption("bad", WATER, Map.of(ALPHA, -1, BETA, 0)));

        CommodityFrontier water = complete(WATER, option("water", WATER, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityFreightFrontierCombiner.combine(List.of(water, water), BUDGET_13));
    }

    private static CommodityFrontier complete(String commodityId, CommodityOption... options) {
        return new CommodityFrontier(
                commodityId,
                "test.frontier.v1",
                FrontierStatus.COMPLETE,
                List.of(options));
    }

    private static CommodityFrontier unresolved(String commodityId, CommodityOption... options) {
        return new CommodityFrontier(
                commodityId,
                "test.frontier.v1",
                FrontierStatus.UNRESOLVED_SEARCH_BUDGET,
                List.of(options));
    }

    private static CommodityOption option(
            String optionId,
            String commodityId,
            int alphaShips,
            int betaShips) {
        return optionWithMap(optionId, commodityId, Map.of(ALPHA, alphaShips, BETA, betaShips));
    }

    private static CommodityOption optionWithMap(
            String optionId,
            String commodityId,
            Map<String, Integer> ships) {
        return new CommodityOption(optionId, commodityId, ships);
    }
}

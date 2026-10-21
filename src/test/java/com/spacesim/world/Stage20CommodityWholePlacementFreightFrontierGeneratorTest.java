package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityWholePlacementFreightFrontierGenerator.EvaluationStatus;
import com.spacesim.world.Stage20CommodityWholePlacementFreightFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20CommodityWholePlacementFreightFrontierGenerator.PhysicalEvaluation;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage20CommodityWholePlacementFreightFrontierGeneratorTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ALPHA = "faction.alpha";
    private static final String BETA = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId PRODUCER = new StarSystemId(3L);
    private static final CommodityRequirement REQUIREMENT = new CommodityRequirement(WATER, 1_000d, 5d);

    @Test
    void exhaustiveBudgetVectorSweepRecoversEveryNondominatedUsageVector() {
        FrontierReport report = Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                placement(START_B, START_A),
                REQUIREMENT,
                3,
                100,
                (requirement, budget, nodeBudget) -> {
                    int alpha = budget.get(ALPHA);
                    int beta = budget.get(BETA);
                    if (alpha >= 1 && beta >= 3) {
                        return accepted(budget, 1, 3, 4);
                    }
                    if (alpha >= 2 && beta >= 2) {
                        return accepted(budget, 2, 2, 5);
                    }
                    if (alpha >= 3 && beta >= 1) {
                        return accepted(budget, 3, 1, 6);
                    }
                    return new PhysicalEvaluation(EvaluationStatus.INFEASIBLE, 1, List.of(), List.of());
                });

        assertEquals(9, report.budgetVectorsEvaluated());
        assertEquals(6, report.acceptedVectorCount());
        assertEquals(3, report.infeasibleVectorCount());
        assertEquals(0, report.unresolvedVectorCount());
        assertEquals(FrontierStatus.COMPLETE, report.combinableFrontier().status());
        assertEquals(List.of(
                        Map.of(ALPHA, 1, BETA, 3),
                        Map.of(ALPHA, 2, BETA, 2),
                        Map.of(ALPHA, 3, BETA, 1)),
                report.plans().stream()
                        .map(value -> value.option().remoteFreightersByFaction())
                        .toList());
    }

    @Test
    void unresolvedBudgetVectorKeepsFrontierUnresolvedButRetainsConcreteOptions() {
        FrontierReport report = Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                placement(START_A, START_B),
                REQUIREMENT,
                2,
                50,
                (requirement, budget, nodeBudget) -> {
                    if (budget.get(ALPHA) == 1 && budget.get(BETA) == 2) {
                        return new PhysicalEvaluation(
                                EvaluationStatus.UNRESOLVED_SEARCH_BUDGET,
                                nodeBudget,
                                List.of(),
                                List.of());
                    }
                    if (budget.get(ALPHA) >= 2 && budget.get(BETA) >= 2) {
                        return accepted(budget, 1, 1, 7);
                    }
                    return new PhysicalEvaluation(EvaluationStatus.INFEASIBLE, 2, List.of(), List.of());
                });

        assertEquals(4, report.budgetVectorsEvaluated());
        assertEquals(1, report.acceptedVectorCount());
        assertEquals(2, report.infeasibleVectorCount());
        assertEquals(1, report.unresolvedVectorCount());
        assertEquals(FrontierStatus.UNRESOLVED_SEARCH_BUDGET, report.combinableFrontier().status());
        assertEquals(1, report.plans().size());
        assertEquals(Map.of(ALPHA, 1, BETA, 1), report.plans().get(0).option().remoteFreightersByFaction());
    }

    @Test
    void dominatedPhysicalUsageVectorsAreRemovedFromCombinableFrontier() {
        FrontierReport report = Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                placement(START_A, START_B),
                REQUIREMENT,
                3,
                20,
                (requirement, budget, nodeBudget) -> {
                    if (budget.get(ALPHA) >= 1 && budget.get(BETA) >= 1) {
                        int alphaUsed = budget.get(ALPHA) >= 2 ? 2 : 1;
                        int betaUsed = budget.get(BETA) >= 2 ? 2 : 1;
                        return accepted(budget, alphaUsed, betaUsed, 3);
                    }
                    return new PhysicalEvaluation(EvaluationStatus.INFEASIBLE, 1, List.of(), List.of());
                });

        assertEquals(FrontierStatus.COMPLETE, report.combinableFrontier().status());
        assertEquals(1, report.plans().size());
        assertEquals(Map.of(ALPHA, 1, BETA, 1), report.plans().get(0).option().remoteFreightersByFaction());
    }

    @Test
    void localOnlyZeroUsageIsDiscoverableWithoutInventingZeroBudgetSemantics() {
        FrontierReport report = Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                placement(START_A, START_B),
                REQUIREMENT,
                1,
                10,
                (requirement, budget, nodeBudget) -> acceptedLocal(budget, 1));

        assertEquals(1, report.budgetVectorsEvaluated());
        assertEquals(Map.of(ALPHA, 0, BETA, 0), report.plans().get(0).option().remoteFreightersByFaction());
        assertEquals(Map.of(ALPHA, 1, BETA, 1), report.plans().get(0).evaluatedBudgetByFaction());
    }

    @Test
    void acceptedPhysicalResultMustExposeExactEvaluatedPerStartBudgets() {
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                        placement(START_A, START_B),
                        REQUIREMENT,
                        1,
                        10,
                        (requirement, budget, nodeBudget) -> accepted(
                                Map.of(ALPHA, 2, BETA, 1),
                                1,
                                1,
                                1)));
    }

    @Test
    void singleCommodityEvaluationCannotLeakAnotherCommodityProducerUsage() {
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                        placement(START_A, START_B),
                        REQUIREMENT,
                        1,
                        10,
                        (requirement, budget, nodeBudget) -> new PhysicalEvaluation(
                                EvaluationStatus.ACCEPTED,
                                1,
                                accepted(budget, 1, 1, 1).starts(),
                                List.of(new ProducerUsage(
                                        new SupplyKey("commodity.other", PRODUCER),
                                        20d,
                                        10d)))));
    }

    @Test
    void rejectedPlacementAndInvalidBudgetsAreRejectedBeforePlannerInvocation() {
        PlacementResult rejected = new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile",
                PlacementStatus.REJECTED_SEED,
                List.of(),
                0,
                Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                        rejected, REQUIREMENT, 1, 10, (requirement, budget, nodeBudget) -> null));
        assertThrows(IllegalArgumentException.class, () ->
                Stage20CommodityWholePlacementFreightFrontierGenerator.generate(
                        placement(START_A, START_B), REQUIREMENT, 0, 10,
                        (requirement, budget, nodeBudget) -> null));
    }

    private static PhysicalEvaluation accepted(
            Map<String, Integer> budget,
            int alphaUsed,
            int betaUsed,
            int nodes) {
        return new PhysicalEvaluation(
                EvaluationStatus.ACCEPTED,
                nodes,
                List.of(
                        remoteStart(ALPHA, START_A, budget.get(ALPHA), alphaUsed),
                        remoteStart(BETA, START_B, budget.get(BETA), betaUsed)),
                List.of(new ProducerUsage(new SupplyKey(WATER, PRODUCER), 20d, 10d)));
    }

    private static PhysicalEvaluation acceptedLocal(Map<String, Integer> budget, int nodes) {
        return new PhysicalEvaluation(
                EvaluationStatus.ACCEPTED,
                nodes,
                List.of(
                        localStart(ALPHA, START_A, budget.get(ALPHA)),
                        localStart(BETA, START_B, budget.get(BETA))),
                List.of(
                        new ProducerUsage(new SupplyKey(WATER, START_A), 5d, 5d),
                        new ProducerUsage(new SupplyKey(WATER, START_B), 5d, 5d)));
    }

    private static StartPlan remoteStart(
            String faction,
            StarSystemId start,
            int budget,
            int used) {
        SupplierCommitment commitment = new SupplierCommitment(
                WATER,
                PRODUCER,
                false,
                used,
                5d,
                Optional.of(new RouteAssessment(List.of(PRODUCER, start), 100d, 5d)));
        DemandPlan demand = new DemandPlan(WATER, 5d, 5d, used, List.of(commitment));
        return new StartPlan(faction, start, budget, used, List.of(demand));
    }

    private static StartPlan localStart(String faction, StarSystemId start, int budget) {
        SupplierCommitment commitment = new SupplierCommitment(
                WATER,
                start,
                true,
                0,
                5d,
                Optional.empty());
        DemandPlan demand = new DemandPlan(WATER, 5d, 5d, 0, List.of(commitment));
        return new StartPlan(faction, start, budget, 0, List.of(demand));
    }

    private static PlacementResult placement(StarSystemId first, StarSystemId second) {
        ArrayList<Assignment> assignments = new ArrayList<>();
        assignments.add(new Assignment(BETA, second, 0d));
        assignments.add(new Assignment(ALPHA, first, 0d));
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile",
                PlacementStatus.ACCEPTED,
                assignments,
                assignments.size(),
                Optional.empty());
    }
}

package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionStockResiliencePolicyReviewerTest {
    @Test
    void raisesOnlyByBoundedStepAndPreservesProductionPolicy() {
        FactionProductionPolicyState production = new FactionProductionPolicyState(
                "station.arsenal", "recipe.weapons_assembly");
        FactionStockProductionPolicyState previous = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 20)),
                List.of(production));
        FactionResiliencePlan resilience = resiliencePlan(List.of(
                decision("item.energy", 50),
                decision("item.food", 1)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10));

        assertEquals(1, plan.increasedItemCount());
        assertEquals(0, plan.blockedDecreaseItemCount());
        assertEquals(30, stockFloor(plan.candidatePolicy(), "item.energy"));
        assertTrue(plan.candidatePolicy().stockPolicies().stream()
                        .noneMatch(policy -> policy.itemContentId().equals("item.food")),
                "A one-unit delta inside the deadband must not create a stock policy");
        assertEquals(List.of(production), plan.candidatePolicy().productionPolicies());
    }

    @Test
    void lowerRecommendationIsReportedButNeverAutoAppliedWithoutProvenance() {
        FactionStockProductionPolicyState previous = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 40)),
                List.of());
        FactionResiliencePlan resilience = resiliencePlan(List.of(decision("item.energy", 10)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10));

        assertEquals(0, plan.increasedItemCount());
        assertEquals(1, plan.blockedDecreaseItemCount());
        assertEquals(previous, plan.candidatePolicy(),
                "Reviewer must not erase a possibly intentional base floor without policy provenance");
    }

    @Test
    void upwardDeltaInsideDeadbandIsStable() {
        FactionStockProductionPolicyState previous = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 20)),
                List.of());
        FactionResiliencePlan resilience = resiliencePlan(List.of(decision("item.energy", 22)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10));

        assertEquals(previous, plan.candidatePolicy());
        assertEquals(0, plan.increasedItemCount());
    }

    private static int stockFloor(FactionStockProductionPolicyState policy, String itemContentId) {
        return policy.stockPolicies().stream()
                .filter(stock -> stock.itemContentId().equals(itemContentId))
                .findFirst()
                .orElseThrow()
                .targetStockFloor();
    }

    private static FactionResiliencePlan resiliencePlan(List<FactionResilienceItemDecision> decisions) {
        return new FactionResiliencePlan(
                "faction.neutral",
                100L,
                80,
                1,
                decisions);
    }

    private static FactionResilienceItemDecision decision(String itemContentId, int targetFloor) {
        return new FactionResilienceItemDecision(
                itemContentId,
                100L,
                7_000,
                targetFloor,
                100_000L,
                true,
                6_000,
                targetFloor,
                true,
                targetFloor > 0,
                true);
    }
}

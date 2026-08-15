package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionStockResiliencePolicyReviewerTest {
    @Test
    void raisesOnlyAutomaticOverlayByBoundedStepAndHonorsDeadband() {
        List<FactionStockPolicyState> previous = List.of(
                new FactionStockPolicyState("item.energy", 20));
        FactionResiliencePlan resilience = resiliencePlan(List.of(
                decision("item.energy", 50),
                decision("item.food", 1)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10, 5));

        assertEquals(1, plan.increasedItemCount());
        assertEquals(0, plan.decreasedItemCount());
        assertEquals(30, stockFloor(plan.candidateOverlay(), "item.energy"));
        assertTrue(plan.candidateOverlay().stream()
                        .noneMatch(policy -> policy.itemContentId().equals("item.food")),
                "A one-unit delta inside the deadband must not create an automatic overlay floor");
    }

    @Test
    void lowerRecommendationDecreasesOnlyByBoundedStep() {
        List<FactionStockPolicyState> previous = List.of(
                new FactionStockPolicyState("item.energy", 40));
        FactionResiliencePlan resilience = resiliencePlan(List.of(decision("item.energy", 10)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10, 5));

        assertEquals(0, plan.increasedItemCount());
        assertEquals(1, plan.decreasedItemCount());
        assertEquals(35, stockFloor(plan.candidateOverlay(), "item.energy"));
    }

    @Test
    void disappearedRiskReleasesOverlayToZeroAcrossBoundedReviews() {
        FactionStockResilienceReviewProfile profile = new FactionStockResilienceReviewProfile(2, 10, 5);
        FactionResiliencePlan noRisk = resiliencePlan(List.of());

        FactionStockResiliencePolicyReviewer.Plan first = FactionStockResiliencePolicyReviewer.plan(
                List.of(new FactionStockPolicyState("item.energy", 8)),
                noRisk,
                profile);
        assertEquals(1, first.decreasedItemCount());
        assertEquals(3, stockFloor(first.candidateOverlay(), "item.energy"));

        FactionStockResiliencePolicyReviewer.Plan second = FactionStockResiliencePolicyReviewer.plan(
                first.candidateOverlay(),
                noRisk,
                profile);
        assertEquals(1, second.decreasedItemCount());
        assertTrue(second.candidateOverlay().isEmpty(),
                "Exact zero target must fully release the automatic overlay instead of leaving a deadband tail");
    }

    @Test
    void protectedBaseDemandReleasesRedundantOverlayByBoundedStep() {
        FactionStockResilienceReviewProfile profile = new FactionStockResilienceReviewProfile(2, 10, 5);
        FactionResiliencePlan resilience = resiliencePlan(List.of(decision("item.energy", 10)));
        List<FactionStockPolicyState> protectedDemand = List.of(
                new FactionStockPolicyState("item.energy", 10));

        FactionStockResiliencePolicyReviewer.Plan first = FactionStockResiliencePolicyReviewer.plan(
                List.of(new FactionStockPolicyState("item.energy", 8)),
                protectedDemand,
                resilience,
                profile);
        assertEquals(0, first.increasedItemCount());
        assertEquals(1, first.decreasedItemCount());
        assertEquals(3, stockFloor(first.candidateOverlay(), "item.energy"));

        FactionStockResiliencePolicyReviewer.Plan second = FactionStockResiliencePolicyReviewer.plan(
                first.candidateOverlay(),
                protectedDemand,
                resilience,
                profile);
        assertEquals(1, second.decreasedItemCount());
        assertTrue(second.candidateOverlay().isEmpty(),
                "Base/non-resilience demand that already covers the recommendation must make overlay redundant");
    }

    @Test
    void nonzeroDeltaInsideDeadbandIsStable() {
        List<FactionStockPolicyState> previous = List.of(
                new FactionStockPolicyState("item.energy", 20));
        FactionResiliencePlan resilience = resiliencePlan(List.of(decision("item.energy", 22)));

        FactionStockResiliencePolicyReviewer.Plan plan = FactionStockResiliencePolicyReviewer.plan(
                previous,
                resilience,
                new FactionStockResilienceReviewProfile(2, 10, 5));

        assertEquals(previous, plan.candidateOverlay());
        assertEquals(0, plan.increasedItemCount());
        assertEquals(0, plan.decreasedItemCount());
    }

    private static int stockFloor(List<FactionStockPolicyState> policies, String itemContentId) {
        return policies.stream()
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

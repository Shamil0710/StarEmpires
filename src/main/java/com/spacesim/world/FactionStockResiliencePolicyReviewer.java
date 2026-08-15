package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-17F.6 read-only planner and claimed-window author for the automatic resilience stock overlay.
 *
 * <p>The reviewer reuses the physical Stage-17F.5 {@link FactionResiliencePlanner} signal, but it
 * never rewrites the operator/player/AI-authored base stock policy. Instead it adjusts the dedicated
 * persistent {@link FactionStrategicGoalState.GoalType#RESILIENCE} demand contribution by bounded
 * steps inside the shared policy-review cadence.</p>
 *
 * <p>Authoring the overlay never moves cargo, money or production output and does not mutate physical
 * market targets. The ordinary explicit strategic-policy apply remains responsible for materializing
 * the new effective demand through the reversible configured-baseline boundary.</p>
 */
public final class FactionStockResiliencePolicyReviewer {
    private FactionStockResiliencePolicyReviewer() {
        throw new AssertionError("Utility class");
    }

    /**
     * Builds one read-only resilience-overlay plan from the current world snapshot.
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction ID
     * @param profile bounded adjustment profile
     * @return immutable plan; world state is unchanged
     */
    public static Plan plan(
            WorldSimulation world,
            String factionContentId,
            FactionStockResilienceReviewProfile profile) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = requireFactionId(factionContentId);
        List<FactionStockPolicyState> previousOverlay = checkedWorld.findFactionResilienceDemandFloors(factionId);
        FactionResiliencePlan resilience = FactionResiliencePlanner.analyze(checkedWorld, factionId);
        return plan(previousOverlay, resilience, profile);
    }

    /**
     * Pure value-layer planning boundary used by deterministic acceptance tests and the world adapter.
     *
     * @param previousOverlay current automatic resilience demand floors
     * @param resilience current read-only Stage-17F.5 resilience recommendation
     * @param profile bounded adjustment profile
     * @return immutable bounded candidate plan
     */
    static Plan plan(
            List<FactionStockPolicyState> previousOverlay,
            FactionResiliencePlan resilience,
            FactionStockResilienceReviewProfile profile) {
        List<FactionStockPolicyState> checkedPrevious = List.copyOf(Objects.requireNonNull(
                previousOverlay, "Previous resilience overlay not set"));
        FactionResiliencePlan checkedResilience = Objects.requireNonNull(
                resilience, "Faction resilience plan not set");
        FactionStockResilienceReviewProfile checkedProfile = Objects.requireNonNull(
                profile, "Stock resilience review profile not set");

        Map<String, Integer> currentFloors = new TreeMap<>();
        for (FactionStockPolicyState stock : checkedPrevious) {
            FactionStockPolicyState value = Objects.requireNonNull(stock, "Resilience overlay floor not set");
            currentFloors.merge(value.itemContentId(), value.targetStockFloor(), Math::max);
        }
        Map<String, Integer> targetFloors = new TreeMap<>();
        for (FactionResilienceItemDecision item : checkedResilience.items()) {
            targetFloors.merge(
                    item.itemContentId(),
                    item.recommendedTargetFloorPerMarketUnits(),
                    Math::max);
        }

        TreeSet<String> items = new TreeSet<>(currentFloors.keySet());
        items.addAll(targetFloors.keySet());
        List<FactionStockPolicyState> candidate = new ArrayList<>();
        int increasedItems = 0;
        int decreasedItems = 0;
        for (String itemContentId : items) {
            int current = currentFloors.getOrDefault(itemContentId, 0);
            int target = targetFloors.getOrDefault(itemContentId, 0);
            int next = current;
            if (target > current) {
                long delta = (long) target - current;
                if (delta > checkedProfile.deadbandUnits()) {
                    next = boundedIncrease(current, target, checkedProfile.maxIncreaseUnitsPerReview());
                    increasedItems++;
                }
            } else if (target < current) {
                long delta = (long) current - target;
                if (target == 0 || delta > checkedProfile.deadbandUnits()) {
                    next = boundedDecrease(current, target, checkedProfile.maxDecreaseUnitsPerReview());
                    decreasedItems++;
                }
            }
            if (next > 0) {
                candidate.add(new FactionStockPolicyState(itemContentId, next));
            }
        }

        return new Plan(
                checkedResilience.observationTick(),
                checkedPrevious,
                List.copyOf(candidate),
                increasedItems,
                decreasedItems);
    }

    /**
     * Applies a previously built overlay plan inside an already claimed common review window.
     *
     * <p>The stale-overlay check prevents a plan from overwriting a manual or other strategic update
     * that happened after planning. This method authors the persistent resilience contribution only;
     * it intentionally does not call {@link WorldSimulation#applyFactionStrategicPolicy(String)}.</p>
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction ID
     * @param plan read-only plan built for the same faction
     * @return immutable claimed-window result
     */
    public static Result applyClaimed(
            WorldSimulation world,
            String factionContentId,
            Plan plan) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = requireFactionId(factionContentId);
        Plan checkedPlan = Objects.requireNonNull(plan, "Stock resilience plan not set");
        List<FactionStockPolicyState> current = checkedWorld.findFactionResilienceDemandFloors(factionId);
        if (!current.equals(checkedPlan.previousOverlay())) {
            throw new IllegalStateException("Stock/resilience overlay plan is stale for faction: " + factionId);
        }
        if (checkedPlan.candidateOverlay().equals(current)) {
            return new Result(
                    true,
                    false,
                    checkedPlan.increasedItemCount(),
                    checkedPlan.decreasedItemCount(),
                    current,
                    current);
        }
        List<FactionStockPolicyState> installed = checkedWorld.updateFactionResilienceDemandFloors(
                factionId, checkedPlan.candidateOverlay());
        return new Result(
                true,
                true,
                checkedPlan.increasedItemCount(),
                checkedPlan.decreasedItemCount(),
                current,
                installed);
    }

    /**
     * Builds an unclaimed result for a cadence window that was not available.
     *
     * @param plan previously computed read-only plan
     * @return result reporting no overlay mutation
     */
    public static Result unclaimed(Plan plan) {
        Plan checked = Objects.requireNonNull(plan, "Stock resilience plan not set");
        return new Result(
                false,
                false,
                checked.increasedItemCount(),
                checked.decreasedItemCount(),
                checked.previousOverlay(),
                checked.previousOverlay());
    }

    private static int boundedIncrease(int current, int target, int maxStep) {
        long next = Math.min((long) target, (long) current + maxStep);
        return Math.toIntExact(next);
    }

    private static int boundedDecrease(int current, int target, int maxStep) {
        long next = Math.max((long) target, (long) current - maxStep);
        return Math.toIntExact(next);
    }

    private static String requireFactionId(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        return factionId;
    }

    /**
     * Read-only resilience-overlay plan captured before the common cadence claim.
     *
     * @param observationTick authoritative tick used by Stage-17F.5 resilience diagnostics
     * @param previousOverlay automatic resilience demand observed during planning
     * @param candidateOverlay bounded candidate automatic resilience demand
     * @param increasedItemCount number of item floors proposed upward by one bounded step
     * @param decreasedItemCount number of item floors proposed downward by one bounded step
     */
    public record Plan(
            long observationTick,
            List<FactionStockPolicyState> previousOverlay,
            List<FactionStockPolicyState> candidateOverlay,
            int increasedItemCount,
            int decreasedItemCount) {

        /**
         * Validates one immutable plan.
         *
         * @param observationTick authoritative observation tick
         * @param previousOverlay overlay observed before claim
         * @param candidateOverlay bounded candidate overlay
         * @param increasedItemCount proposed upward item adjustments
         * @param decreasedItemCount proposed downward item adjustments
         */
        public Plan {
            if (observationTick < 0L || increasedItemCount < 0 || decreasedItemCount < 0) {
                throw new IllegalArgumentException("Stock resilience plan counters/tick cannot be negative");
            }
            previousOverlay = List.copyOf(Objects.requireNonNull(
                    previousOverlay, "Previous resilience overlay not set"));
            candidateOverlay = List.copyOf(Objects.requireNonNull(
                    candidateOverlay, "Candidate resilience overlay not set"));
        }
    }

    /**
     * Explainable result of one stock/resilience review attempt.
     *
     * @param reviewClaimed whether the shared review window was claimed
     * @param policyChanged whether persistent resilience overlay changed
     * @param increasedItemCount number of bounded upward recommendations in the plan
     * @param decreasedItemCount number of bounded downward recommendations in the plan
     * @param previousOverlay overlay before the call
     * @param resultingOverlay overlay after the call
     */
    public record Result(
            boolean reviewClaimed,
            boolean policyChanged,
            int increasedItemCount,
            int decreasedItemCount,
            List<FactionStockPolicyState> previousOverlay,
            List<FactionStockPolicyState> resultingOverlay) {

        /**
         * Validates one immutable result.
         *
         * @param reviewClaimed whether shared cadence was claimed
         * @param policyChanged whether overlay changed
         * @param increasedItemCount number of upward item recommendations
         * @param decreasedItemCount number of downward item recommendations
         * @param previousOverlay overlay before review
         * @param resultingOverlay overlay after review
         */
        public Result {
            if (increasedItemCount < 0 || decreasedItemCount < 0) {
                throw new IllegalArgumentException("Stock resilience result counters cannot be negative");
            }
            previousOverlay = List.copyOf(Objects.requireNonNull(
                    previousOverlay, "Previous resilience overlay not set"));
            resultingOverlay = List.copyOf(Objects.requireNonNull(
                    resultingOverlay, "Resulting resilience overlay not set"));
            if (policyChanged && !reviewClaimed) {
                throw new IllegalArgumentException("Resilience overlay cannot change without a claimed review");
            }
            if (policyChanged == previousOverlay.equals(resultingOverlay)) {
                throw new IllegalArgumentException("Stock resilience change flag is inconsistent with overlay values");
            }
        }
    }
}

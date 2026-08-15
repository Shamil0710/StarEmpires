package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-17F.6 read-only planner and claimed-window author for resilience-driven stock policy.
 *
 * <p>The reviewer reuses the physical Stage-17F.5 {@link FactionResiliencePlanner} signal and the
 * common Stage-17F.4 stock-policy authoring boundary. It may raise a stock floor by one bounded step
 * after a shared review claim, but never moves cargo, money or production output.</p>
 *
 * <p>Automatic downward release is deliberately deferred. The current market executor stores only
 * one mutable target-stock value and cannot yet distinguish configured baseline demand from an old
 * policy contribution. A lower recommendation is therefore reported as deferred rather than
 * pretending to remove physical demand that the executor cannot safely attribute.</p>
 */
public final class FactionStockResiliencePolicyReviewer {
    private FactionStockResiliencePolicyReviewer() {
        throw new AssertionError("Utility class");
    }

    /**
     * Builds one read-only stock/resilience plan from the current world snapshot.
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
        FactionStockResilienceReviewProfile checkedProfile = Objects.requireNonNull(
                profile, "Stock resilience review profile not set");
        FactionStockProductionPolicyState previous = checkedWorld.findFactionStockProductionPolicy(factionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Faction has no stock/production policy state: " + factionId));
        FactionResiliencePlan resilience = FactionResiliencePlanner.analyze(checkedWorld, factionId);

        Map<String, Integer> stockFloors = new TreeMap<>();
        for (FactionStockPolicyState stock : previous.stockPolicies()) {
            stockFloors.put(stock.itemContentId(), stock.targetStockFloor());
        }

        int increasedItems = 0;
        int deferredReleaseItems = 0;
        for (FactionResilienceItemDecision item : resilience.items()) {
            int currentFloor = stockFloors.getOrDefault(item.itemContentId(), 0);
            int targetFloor = item.recommendedTargetFloorPerMarketUnits();
            if (targetFloor > currentFloor) {
                long delta = (long) targetFloor - currentFloor;
                if (delta <= checkedProfile.deadbandUnits()) {
                    continue;
                }
                int nextFloor = boundedIncrease(
                        currentFloor,
                        targetFloor,
                        checkedProfile.maxIncreaseUnitsPerReview());
                stockFloors.put(item.itemContentId(), nextFloor);
                increasedItems++;
            } else if (currentFloor > targetFloor
                    && (long) currentFloor - targetFloor > checkedProfile.deadbandUnits()) {
                deferredReleaseItems++;
            }
        }

        List<FactionStockPolicyState> candidateStocks = new ArrayList<>(stockFloors.size());
        for (Map.Entry<String, Integer> stock : stockFloors.entrySet()) {
            if (stock.getValue() > 0) {
                candidateStocks.add(new FactionStockPolicyState(stock.getKey(), stock.getValue()));
            }
        }
        FactionStockProductionPolicyState candidate = new FactionStockProductionPolicyState(
                candidateStocks,
                previous.productionPolicies());
        return new Plan(
                resilience.observationTick(),
                previous,
                candidate,
                increasedItems,
                deferredReleaseItems);
    }

    /**
     * Applies a previously built stock plan inside an already claimed common review window.
     *
     * <p>The stale-policy check prevents a plan from overwriting a manual or other strategic update
     * that happened after planning. This method authors policy only; it intentionally does not call
     * {@link WorldSimulation#applyFactionStrategicPolicy(String)}.</p>
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
        FactionStockProductionPolicyState current = checkedWorld.findFactionStockProductionPolicy(factionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Faction has no stock/production policy state: " + factionId));
        if (!current.equals(checkedPlan.previousPolicy())) {
            throw new IllegalStateException("Stock/resilience plan is stale for faction: " + factionId);
        }
        if (checkedPlan.candidatePolicy().equals(current)) {
            return new Result(
                    true,
                    false,
                    checkedPlan.increasedItemCount(),
                    checkedPlan.deferredReleaseItemCount(),
                    current,
                    current);
        }
        FactionStockProductionPolicyState installed = checkedWorld.updateFactionStockProductionPolicy(
                factionId, checkedPlan.candidatePolicy());
        return new Result(
                true,
                true,
                checkedPlan.increasedItemCount(),
                checkedPlan.deferredReleaseItemCount(),
                current,
                installed);
    }

    /**
     * Builds an unclaimed result for a cadence window that was not available.
     *
     * @param plan previously computed read-only plan
     * @return result reporting no policy mutation
     */
    public static Result unclaimed(Plan plan) {
        Plan checked = Objects.requireNonNull(plan, "Stock resilience plan not set");
        return new Result(
                false,
                false,
                checked.increasedItemCount(),
                checked.deferredReleaseItemCount(),
                checked.previousPolicy(),
                checked.previousPolicy());
    }

    private static int boundedIncrease(int current, int target, int maxStep) {
        long next = Math.min((long) target, (long) current + maxStep);
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
     * Read-only stock/resilience plan captured before the common cadence claim.
     *
     * @param observationTick authoritative tick used by Stage-17F.5 resilience diagnostics
     * @param previousPolicy stock/production policy observed during planning
     * @param candidatePolicy bounded candidate; production choices are preserved
     * @param increasedItemCount number of item floors proposed upward by one bounded step
     * @param deferredReleaseItemCount number of lower recommendations deliberately not auto-applied
     */
    public record Plan(
            long observationTick,
            FactionStockProductionPolicyState previousPolicy,
            FactionStockProductionPolicyState candidatePolicy,
            int increasedItemCount,
            int deferredReleaseItemCount) {

        /**
         * Validates one immutable plan.
         *
         * @param observationTick authoritative observation tick
         * @param previousPolicy policy observed before claim
         * @param candidatePolicy bounded candidate policy
         * @param increasedItemCount proposed upward item adjustments
         * @param deferredReleaseItemCount deliberately deferred downward adjustments
         */
        public Plan {
            if (observationTick < 0L || increasedItemCount < 0 || deferredReleaseItemCount < 0) {
                throw new IllegalArgumentException("Stock resilience plan counters/tick cannot be negative");
            }
            Objects.requireNonNull(previousPolicy, "Previous stock policy not set");
            Objects.requireNonNull(candidatePolicy, "Candidate stock policy not set");
            if (!previousPolicy.productionPolicies().equals(candidatePolicy.productionPolicies())) {
                throw new IllegalArgumentException("Stock resilience review cannot change production policy");
            }
        }
    }

    /**
     * Explainable result of one stock/resilience review attempt.
     *
     * @param reviewClaimed whether the shared review window was claimed
     * @param policyChanged whether persistent stock policy changed
     * @param increasedItemCount number of bounded upward recommendations in the plan
     * @param deferredReleaseItemCount lower recommendations intentionally awaiting provenance support
     * @param previousPolicy policy before the call
     * @param resultingPolicy policy after the call
     */
    public record Result(
            boolean reviewClaimed,
            boolean policyChanged,
            int increasedItemCount,
            int deferredReleaseItemCount,
            FactionStockProductionPolicyState previousPolicy,
            FactionStockProductionPolicyState resultingPolicy) {

        /**
         * Validates one immutable result.
         *
         * @param reviewClaimed whether shared cadence was claimed
         * @param policyChanged whether policy changed
         * @param increasedItemCount number of upward item recommendations
         * @param deferredReleaseItemCount number of deferred release recommendations
         * @param previousPolicy policy before review
         * @param resultingPolicy policy after review
         */
        public Result {
            if (increasedItemCount < 0 || deferredReleaseItemCount < 0) {
                throw new IllegalArgumentException("Stock resilience result counters cannot be negative");
            }
            Objects.requireNonNull(previousPolicy, "Previous stock policy not set");
            Objects.requireNonNull(resultingPolicy, "Resulting stock policy not set");
            if (policyChanged && !reviewClaimed) {
                throw new IllegalArgumentException("Stock policy cannot change without a claimed review");
            }
            if (policyChanged == previousPolicy.equals(resultingPolicy)) {
                throw new IllegalArgumentException("Stock resilience change flag is inconsistent with policy values");
            }
        }
    }
}

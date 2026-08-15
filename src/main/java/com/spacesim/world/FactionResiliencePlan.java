package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only deterministic resilience plan derived from doctrine and current structural dependence.
 *
 * <p>The plan is not an economic effect. It can recommend larger ordinary stock floors and identify
 * supplier/local-production/corridor risks. Applying its stock recommendation still uses the same
 * Stage-17F.4 policy command and ordinary market/logistics systems.</p>
 *
 * @param factionContentId stable faction ID
 * @param observationTick authoritative observation tick
 * @param economicResiliencePriority doctrine axis in range 0..100
 * @param ownedMarketStations completed owned markets used to translate aggregate risk into a per-market floor
 * @param items canonical item decisions
 */
public record FactionResiliencePlan(
        String factionContentId,
        long observationTick,
        int economicResiliencePriority,
        int ownedMarketStations,
        List<FactionResilienceItemDecision> items) {

    /**
     * Validates and canonicalizes one immutable resilience plan.
     *
     * @param factionContentId stable faction ID
     * @param observationTick authoritative observation tick
     * @param economicResiliencePriority doctrine resilience priority in range 0..100
     * @param ownedMarketStations completed owned markets used for per-market recommendations
     * @param items item-level resilience decisions
     */
    public FactionResiliencePlan {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        if (observationTick < 0L) {
            throw new IllegalArgumentException("Observation tick cannot be negative");
        }
        if (economicResiliencePriority < 0 || economicResiliencePriority > 100) {
            throw new IllegalArgumentException("Economic resilience priority must be in [0,100]");
        }
        if (ownedMarketStations < 0) {
            throw new IllegalArgumentException("Owned market count cannot be negative");
        }
        List<FactionResilienceItemDecision> canonical = new ArrayList<>(
                Objects.requireNonNull(items, "Resilience item decisions not set"));
        canonical.sort(null);
        for (int index = 0; index < canonical.size(); index++) {
            FactionResilienceItemDecision decision = Objects.requireNonNull(
                    canonical.get(index), "Resilience item decision not set");
            if (index > 0 && canonical.get(index - 1).itemContentId().equals(decision.itemContentId())) {
                throw new IllegalArgumentException("Duplicate resilience item decision: " + decision.itemContentId());
            }
        }
        items = List.copyOf(canonical);
    }

    /**
     * Materializes only the stock-floor part of this plan into the common Stage-17F.4 policy value.
     *
     * <p>Existing production choices are preserved. Existing stock floors are never reduced. The returned
     * value is still only policy; callers must use the ordinary update/apply boundary explicitly.</p>
     *
     * @param current current persistent stock/production policy
     * @return merged canonical policy with recommended stock floors
     */
    public FactionStockProductionPolicyState mergeRecommendedStockFloors(
            FactionStockProductionPolicyState current) {
        FactionStockProductionPolicyState checked = Objects.requireNonNull(current, "Current policy not set");
        List<FactionStockPolicyState> stocks = new ArrayList<>(checked.stockPolicies());
        for (FactionResilienceItemDecision item : items) {
            if (item.recommendedTargetFloorPerMarketUnits() <= 0) {
                continue;
            }
            int existingIndex = -1;
            int existingFloor = 0;
            for (int index = 0; index < stocks.size(); index++) {
                FactionStockPolicyState stock = stocks.get(index);
                if (stock.itemContentId().equals(item.itemContentId())) {
                    existingIndex = index;
                    existingFloor = stock.targetStockFloor();
                    break;
                }
            }
            int desired = Math.max(existingFloor, item.recommendedTargetFloorPerMarketUnits());
            if (desired == existingFloor) {
                continue;
            }
            FactionStockPolicyState replacement = new FactionStockPolicyState(item.itemContentId(), desired);
            if (existingIndex >= 0) {
                stocks.set(existingIndex, replacement);
            } else {
                stocks.add(replacement);
            }
        }
        return new FactionStockProductionPolicyState(stocks, checked.productionPolicies());
    }
}

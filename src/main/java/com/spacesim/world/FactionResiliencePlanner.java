package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts current structural economic exposure into explainable resilience recommendations.
 *
 * <p>The planner is intentionally read-only. It observes the existing Stage-17E dependence diagnostics,
 * current doctrine and real owned-market count. It does not alter routes, create suppliers, produce goods
 * or grant resources. Recommended stock floors can be fed back through the common Stage-17F.4 command,
 * after which ordinary markets, traders and production must physically satisfy the new demand.</p>
 */
public final class FactionResiliencePlanner {
    private static final int BASIS_POINTS = 10_000;
    private static final int LOCAL_PRODUCTION_PRIORITY_THRESHOLD = 60;
    private static final int ROUTE_REDUNDANCY_PRIORITY_THRESHOLD = 50;

    private FactionResiliencePlanner() {
        throw new AssertionError("Utility class");
    }

    /**
     * Measures one live faction and returns deterministic item-level resilience recommendations.
     *
     * @param world authoritative world runtime
     * @param factionContentId authored or world-defined stable faction ID
     * @return immutable read-only plan
     */
    public static FactionResiliencePlan analyze(
            WorldSimulation world,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId).orElseThrow(
                () -> new IllegalArgumentException("Faction has no strategic state: " + factionId));
        FactionFiscalPositionDiagnostics fiscal = FactionFiscalPositionAnalyzer.analyze(checkedWorld, factionId);
        int priority = strategy.doctrine().economicResiliencePriority();
        int preferredMaximumPartnerShare = BASIS_POINTS - priority * 50;

        Map<String, MutableItem> byItem = new HashMap<>();
        long observationTick = checkedWorld.getAuthoritativeWorldTick();
        for (FactionDiplomacyState partner : checkedWorld.getFactionDiplomacyStates()) {
            if (partner.factionContentId().equals(factionId)) {
                continue;
            }
            FactionEconomicDependenceDiagnostics dependence = checkedWorld.analyzeEconomicDependence(
                    factionId, partner.factionContentId());
            if (dependence.observationTick() != observationTick) {
                throw new IllegalStateException("Economic dependence diagnostics use inconsistent observation ticks");
            }
            for (FactionItemDependenceDiagnostic item : dependence.items()) {
                MutableItem aggregate = byItem.computeIfAbsent(
                        item.itemContentId(), ignored -> new MutableItem());
                aggregate.sourceRequiredStockUnits = Math.max(
                        aggregate.sourceRequiredStockUnits, item.sourceRequiredStockUnits());
                aggregate.worstPartnerSupplyShareBasisPoints = Math.max(
                        aggregate.worstPartnerSupplyShareBasisPoints, item.partnerSupplyShareBasisPoints());
                aggregate.worstUncoveredUnitsAfterPartnerLoss = Math.max(
                        aggregate.worstUncoveredUnitsAfterPartnerLoss, item.uncoveredUnitsAfterPartnerLoss());
                aggregate.worstReplacementPremiumMilliCredits = Math.max(
                        aggregate.worstReplacementPremiumMilliCredits, item.estimatedReplacementPremiumMilliCredits());
                aggregate.uniqueCorridorExposure |= item.uniquePartnerShortestRoute();
            }
        }

        List<FactionResilienceItemDecision> decisions = new ArrayList<>(byItem.size());
        for (Map.Entry<String, MutableItem> entry : byItem.entrySet()) {
            MutableItem item = entry.getValue();
            long protectedRiskUnits = percentageCeil(
                    item.worstUncoveredUnitsAfterPartnerLoss, priority);
            long desiredAggregateUnits = safeAdd(item.sourceRequiredStockUnits, protectedRiskUnits);
            int perMarketFloor = toPerMarketFloor(
                    desiredAggregateUnits, fiscal.ownedMarketStations());
            boolean diversify = item.worstPartnerSupplyShareBasisPoints > preferredMaximumPartnerShare;
            boolean localProduction = priority >= LOCAL_PRODUCTION_PRIORITY_THRESHOLD
                    && item.worstUncoveredUnitsAfterPartnerLoss > 0L;
            boolean redundantRoute = priority >= ROUTE_REDUNDANCY_PRIORITY_THRESHOLD
                    && item.uniqueCorridorExposure;
            decisions.add(new FactionResilienceItemDecision(
                    entry.getKey(),
                    item.sourceRequiredStockUnits,
                    item.worstPartnerSupplyShareBasisPoints,
                    item.worstUncoveredUnitsAfterPartnerLoss,
                    item.worstReplacementPremiumMilliCredits,
                    item.uniqueCorridorExposure,
                    preferredMaximumPartnerShare,
                    perMarketFloor,
                    diversify,
                    localProduction,
                    redundantRoute));
        }
        return new FactionResiliencePlan(
                factionId,
                observationTick,
                priority,
                fiscal.ownedMarketStations(),
                decisions);
    }

    private static long percentageCeil(long value, int percent) {
        if (value <= 0L || percent <= 0) {
            return 0L;
        }
        long whole = value / 100L;
        long remainder = value % 100L;
        long scaledWhole = Math.multiplyExact(whole, (long) percent);
        long scaledRemainder = (remainder * percent + 99L) / 100L;
        return Math.addExact(scaledWhole, scaledRemainder);
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Resilience stock recommendation overflow", exception);
        }
    }

    private static int toPerMarketFloor(long aggregateUnits, int marketCount) {
        if (aggregateUnits <= 0L || marketCount <= 0) {
            return 0;
        }
        long floor = aggregateUnits / marketCount;
        if (aggregateUnits % marketCount != 0L) {
            floor++;
        }
        if (floor > Integer.MAX_VALUE) {
            throw new IllegalStateException("Resilience stock floor exceeds runtime inventory range");
        }
        return (int) floor;
    }

    private static final class MutableItem {
        private long sourceRequiredStockUnits;
        private int worstPartnerSupplyShareBasisPoints;
        private long worstUncoveredUnitsAfterPartnerLoss;
        private long worstReplacementPremiumMilliCredits;
        private boolean uniqueCorridorExposure;
    }
}

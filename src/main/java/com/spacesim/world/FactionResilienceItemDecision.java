package com.spacesim.world;

import java.util.Objects;

/**
 * Explainable resilience decision for one commodity, derived only from observed physical/economic state.
 *
 * @param itemContentId stable commodity ID
 * @param sourceRequiredStockUnits current aggregate source stock requirement
 * @param worstPartnerSupplyShareBasisPoints highest observed single-partner share of accessible foreign surplus
 * @param worstUncoveredUnitsAfterPartnerLoss largest current requirement left uncovered after one partner is removed
 * @param worstReplacementPremiumMilliCredits largest measured current replacement premium
 * @param uniqueCorridorExposure whether at least one important partner supply path has a unique shortest corridor
 * @param preferredMaximumPartnerShareBasisPoints doctrine-derived concentration threshold
 * @param recommendedTargetFloorPerMarketUnits per-market strategic stock floor recommendation
 * @param diversifySuppliersRecommended whether concentration exceeds the preferred threshold
 * @param localProductionRecommended whether observed partner loss leaves uncovered units and resilience priority is high
 * @param routeRedundancyRecommended whether unique-corridor exposure is material to the current doctrine
 */
public record FactionResilienceItemDecision(
        String itemContentId,
        long sourceRequiredStockUnits,
        int worstPartnerSupplyShareBasisPoints,
        long worstUncoveredUnitsAfterPartnerLoss,
        long worstReplacementPremiumMilliCredits,
        boolean uniqueCorridorExposure,
        int preferredMaximumPartnerShareBasisPoints,
        int recommendedTargetFloorPerMarketUnits,
        boolean diversifySuppliersRecommended,
        boolean localProductionRecommended,
        boolean routeRedundancyRecommended)
        implements Comparable<FactionResilienceItemDecision> {

    /** Validates one immutable item-level resilience decision. */
    public FactionResilienceItemDecision {
        itemContentId = Objects.requireNonNull(itemContentId, "Item content ID not set").strip();
        if (itemContentId.isEmpty()) {
            throw new IllegalArgumentException("Item content ID cannot be blank");
        }
        if (sourceRequiredStockUnits < 0L
                || worstUncoveredUnitsAfterPartnerLoss < 0L
                || worstReplacementPremiumMilliCredits < 0L
                || recommendedTargetFloorPerMarketUnits < 0) {
            throw new IllegalArgumentException("Resilience decision values cannot be negative");
        }
        requireBasisPoints(worstPartnerSupplyShareBasisPoints, "worstPartnerSupplyShareBasisPoints");
        requireBasisPoints(preferredMaximumPartnerShareBasisPoints, "preferredMaximumPartnerShareBasisPoints");
    }

    @Override
    public int compareTo(FactionResilienceItemDecision other) {
        return itemContentId.compareTo(Objects.requireNonNull(other, "Decision not set").itemContentId);
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000");
        }
    }
}

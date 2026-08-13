package com.spacesim.world;

/**
 * Explicit deterministic policy for Stage-11A expansion opportunity discovery and scoring.
 *
 * @param maxJumpHops maximum path horizon from existing controlled territory
 * @param maxCandidates maximum ranked opportunities returned per faction
 * @param resourceWeight weight of normalized remaining mineable resources
 * @param demandWeight weight of normalized live market unmet demand
 * @param marketNetworkWeight weight of normalized existing market count
 * @param proximityWeight weight of inverse authoritative jump time
 * @param constructionCostWeight weight of inverse anchor construction funding
 * @param threatPenaltyWeight subtraction weight of normalized hostile-neighbor pressure
 * @param foreignControlPenaltyBasisPoints multiplicative penalty when another faction controls target
 */
public record ExpansionOpportunityPolicy(
        int maxJumpHops,
        int maxCandidates,
        int resourceWeight,
        int demandWeight,
        int marketNetworkWeight,
        int proximityWeight,
        int constructionCostWeight,
        int threatPenaltyWeight,
        int foreignControlPenaltyBasisPoints) {

    /** Balanced initial policy for the current regional simulation. */
    public static final ExpansionOpportunityPolicy DEFAULT = new ExpansionOpportunityPolicy(
            3, 16, 30, 25, 15, 15, 15, 20, 3_000);

    /**
     * Validates bounded search and non-negative scoring weights.
     *
     * @param maxJumpHops maximum path horizon
     * @param maxCandidates maximum returned candidates
     * @param resourceWeight resource benefit weight
     * @param demandWeight demand benefit weight
     * @param marketNetworkWeight market-network benefit weight
     * @param proximityWeight proximity benefit weight
     * @param constructionCostWeight construction-cost benefit weight
     * @param threatPenaltyWeight hostile-pressure penalty weight
     * @param foreignControlPenaltyBasisPoints foreign-control penalty in basis points
     */
    public ExpansionOpportunityPolicy {
        if (maxJumpHops <= 0 || maxCandidates <= 0) {
            throw new IllegalArgumentException("Expansion search bounds must be positive");
        }
        if (resourceWeight < 0 || demandWeight < 0 || marketNetworkWeight < 0
                || proximityWeight < 0 || constructionCostWeight < 0 || threatPenaltyWeight < 0) {
            throw new IllegalArgumentException("Expansion scoring weights cannot be negative");
        }
        if (resourceWeight + demandWeight + marketNetworkWeight + proximityWeight + constructionCostWeight <= 0) {
            throw new IllegalArgumentException("Expansion policy requires a positive benefit weight");
        }
        if (foreignControlPenaltyBasisPoints < 0 || foreignControlPenaltyBasisPoints > 10_000) {
            throw new IllegalArgumentException("Foreign-control penalty must be in range 0..10000 bps");
        }
    }

    /** @return sum used to normalize positive weighted signals */
    public int benefitWeightSum() {
        return resourceWeight + demandWeight + marketNetworkWeight + proximityWeight + constructionCostWeight;
    }
}

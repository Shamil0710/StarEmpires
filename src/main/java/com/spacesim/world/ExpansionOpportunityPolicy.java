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
 * @param routineMaxJumpHops maximum distance treated as routine expansion without an exceptional-value gate
 * @param distantAdvantagePerExtraHopBasisPoints required utility advantage for every hop beyond routine range
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
        int foreignControlPenaltyBasisPoints,
        int routineMaxJumpHops,
        int distantAdvantagePerExtraHopBasisPoints) {

    /**
     * Stage-22 cadence-reviewed default: ordinary growth prefers the one-hop frontier and every
     * additional hop must beat the best routine candidate by another 30%.
     */
    public static final ExpansionOpportunityPolicy DEFAULT = new ExpansionOpportunityPolicy(
            3, 16, 25, 20, 10, 30, 15, 20, 3_000, 1, 3_000);

    /**
     * Backwards-compatible constructor for explicit legacy/custom policies.
     *
     * <p>Callers that author only the original Stage-11 scoring fields retain the previous behavior:
     * the entire requested search horizon is routine and no additional distant-opportunity gate is
     * imposed. New production policies should use the full constructor when anti-sprawl behavior is
     * desired.</p>
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
    public ExpansionOpportunityPolicy(
            int maxJumpHops,
            int maxCandidates,
            int resourceWeight,
            int demandWeight,
            int marketNetworkWeight,
            int proximityWeight,
            int constructionCostWeight,
            int threatPenaltyWeight,
            int foreignControlPenaltyBasisPoints) {
        this(
                maxJumpHops,
                maxCandidates,
                resourceWeight,
                demandWeight,
                marketNetworkWeight,
                proximityWeight,
                constructionCostWeight,
                threatPenaltyWeight,
                foreignControlPenaltyBasisPoints,
                maxJumpHops,
                0);
    }

    /**
     * Validates bounded search, non-negative scoring weights and the explicit anti-sprawl gate.
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
        if (routineMaxJumpHops <= 0 || routineMaxJumpHops > maxJumpHops) {
            throw new IllegalArgumentException("Routine expansion horizon must be within maxJumpHops");
        }
        if (distantAdvantagePerExtraHopBasisPoints < 0 || distantAdvantagePerExtraHopBasisPoints > 10_000) {
            throw new IllegalArgumentException("Distant expansion advantage must be in range 0..10000 bps");
        }
    }

    /** @return sum used to normalize positive weighted signals */
    public int benefitWeightSum() {
        return resourceWeight + demandWeight + marketNetworkWeight + proximityWeight + constructionCostWeight;
    }
}

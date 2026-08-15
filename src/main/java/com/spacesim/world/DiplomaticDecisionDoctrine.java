package com.spacesim.world;

/**
 * Immutable weights/thresholds for the common treaty proposal utility model.
 *
 * <p>This is a decision-policy contract, not a combat/economic performance bonus. Stage 17F may
 * persist faction-specific doctrine profiles against the same fields; Stage 21 may tune authored
 * values without changing evaluator mechanics.</p>
 *
 * @param economicBenefitWeight weight [0,100] for expected net economic benefit
 * @param dependencyAversionWeight weight [0,100] for critical external-dependency risk
 * @param securityWeight weight [0,100] for security value or exposure
 * @param sovereigntyAversionWeight weight [0,100] for sovereignty/jurisdiction cost
 * @param trustWeight weight [0,100] for directed trust
 * @param credibilityWeight weight [0,100] for directed credibility assessment
 * @param fiscalCostWeight weight [0,100] for expected treasury/fiscal cost
 * @param milliCreditsPerEconomicPoint positive money normalization scale
 * @param informationDecayTicks positive observed-information freshness horizon
 * @param minimumDecisionConfidenceBasisPoints minimum confidence [0,10000] required for acceptance
 * @param acceptUtilityThreshold utility threshold at/above which a sufficiently informed AI accepts
 * @param rejectUtilityThreshold utility threshold at/below which an AI rejects
 */
public record DiplomaticDecisionDoctrine(
        int economicBenefitWeight,
        int dependencyAversionWeight,
        int securityWeight,
        int sovereigntyAversionWeight,
        int trustWeight,
        int credibilityWeight,
        int fiscalCostWeight,
        long milliCreditsPerEconomicPoint,
        long informationDecayTicks,
        int minimumDecisionConfidenceBasisPoints,
        int acceptUtilityThreshold,
        int rejectUtilityThreshold) {

    /**
     * Validates bounded deterministic doctrine parameters.
     *
     * @param economicBenefitWeight weight [0,100] for expected net economic benefit
     * @param dependencyAversionWeight weight [0,100] for critical external-dependency risk
     * @param securityWeight weight [0,100] for security value or exposure
     * @param sovereigntyAversionWeight weight [0,100] for sovereignty/jurisdiction cost
     * @param trustWeight weight [0,100] for directed trust
     * @param credibilityWeight weight [0,100] for directed credibility assessment
     * @param fiscalCostWeight weight [0,100] for expected treasury/fiscal cost
     * @param milliCreditsPerEconomicPoint positive money normalization scale
     * @param informationDecayTicks positive observed-information freshness horizon
     * @param minimumDecisionConfidenceBasisPoints minimum confidence [0,10000] required for acceptance
     * @param acceptUtilityThreshold acceptance utility threshold
     * @param rejectUtilityThreshold rejection utility threshold
     */
    public DiplomaticDecisionDoctrine {
        requireWeight(economicBenefitWeight, "Economic-benefit weight");
        requireWeight(dependencyAversionWeight, "Dependency-aversion weight");
        requireWeight(securityWeight, "Security weight");
        requireWeight(sovereigntyAversionWeight, "Sovereignty-aversion weight");
        requireWeight(trustWeight, "Trust weight");
        requireWeight(credibilityWeight, "Credibility weight");
        requireWeight(fiscalCostWeight, "Fiscal-cost weight");
        if (milliCreditsPerEconomicPoint <= 0L) {
            throw new IllegalArgumentException("Economic money normalization scale must be positive");
        }
        if (informationDecayTicks <= 0L || informationDecayTicks > Long.MAX_VALUE / 10_000L) {
            throw new IllegalArgumentException("Information decay horizon is outside deterministic bounds");
        }
        if (minimumDecisionConfidenceBasisPoints < 0 || minimumDecisionConfidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("Minimum decision confidence must be in [0,10000]");
        }
        if (rejectUtilityThreshold >= acceptUtilityThreshold) {
            throw new IllegalArgumentException("Reject threshold must be lower than accept threshold");
        }
    }

    private static void requireWeight(int value, String label) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(label + " must be in [0,100]");
        }
    }
}

package com.spacesim.world;

/**
 * Observed/estimated inputs consumed by the common treaty proposal utility model.
 *
 * <p>The evaluator deliberately does not fetch omniscient economic truth. Stage 17E.7 can provide
 * richer dependency diagnostics and Stage 19 can provide delayed intelligence through this same
 * boundary.</p>
 *
 * @param expectedNetEconomicBenefitMilliCredits signed expected net benefit over the caller's evaluation horizon
 * @param criticalDependencyRiskScore bounded dependency risk [0,100]
 * @param securityValueScore signed security value/exposure [-100,100]
 * @param sovereigntyCostScore bounded sovereignty/jurisdiction cost [0,100]
 * @param expectedFiscalCostMilliCredits non-negative expected fiscal/treasury cost
 * @param observationTick authoritative tick when these diagnostics were observed/estimated
 * @param confidenceBasisPoints diagnostic confidence [0,10000]
 */
public record DiplomaticTreatyEvaluationInputs(
        long expectedNetEconomicBenefitMilliCredits,
        int criticalDependencyRiskScore,
        int securityValueScore,
        int sovereigntyCostScore,
        long expectedFiscalCostMilliCredits,
        long observationTick,
        int confidenceBasisPoints) {

    /**
     * Validates bounded observed inputs.
     *
     * @param expectedNetEconomicBenefitMilliCredits signed expected net economic benefit
     * @param criticalDependencyRiskScore bounded dependency risk [0,100]
     * @param securityValueScore signed security value/exposure [-100,100]
     * @param sovereigntyCostScore bounded sovereignty/jurisdiction cost [0,100]
     * @param expectedFiscalCostMilliCredits non-negative expected fiscal/treasury cost
     * @param observationTick non-negative observation tick
     * @param confidenceBasisPoints diagnostic confidence [0,10000]
     */
    public DiplomaticTreatyEvaluationInputs {
        if (criticalDependencyRiskScore < 0 || criticalDependencyRiskScore > 100) {
            throw new IllegalArgumentException("Dependency risk score must be in [0,100]");
        }
        if (securityValueScore < -100 || securityValueScore > 100) {
            throw new IllegalArgumentException("Security value score must be in [-100,100]");
        }
        if (sovereigntyCostScore < 0 || sovereigntyCostScore > 100) {
            throw new IllegalArgumentException("Sovereignty cost score must be in [0,100]");
        }
        if (expectedFiscalCostMilliCredits < 0L) {
            throw new IllegalArgumentException("Expected fiscal cost cannot be negative");
        }
        if (observationTick < 0L) {
            throw new IllegalArgumentException("Observation tick cannot be negative");
        }
        if (confidenceBasisPoints < 0 || confidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("Diagnostic confidence must be in [0,10000]");
        }
    }
}

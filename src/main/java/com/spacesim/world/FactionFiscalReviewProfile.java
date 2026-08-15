package com.spacesim.world;

/**
 * Explicit bounded targets for one Stage-17F.6 fiscal anti-oscillation controller.
 *
 * <p>The profile is not money and does not execute fiscal transfers. It only describes how quickly
 * the common fiscal policy may move between a normal posture and a liquidity-stress posture after a
 * due strategic review.</p>
 *
 * @param liquidityStressEnterBasisPoints shortfall ratio that starts stress adjustment
 * @param liquidityStressExitBasisPoints shortfall ratio at/below which normal adjustment resumes
 * @param normalStationTaxTargetBasisPoints normal own-station tax target
 * @param stressStationTaxTargetBasisPoints own-station tax target under liquidity stress
 * @param maxStationTaxStepBasisPoints maximum tax-rate change per claimed review
 * @param normalLiquiditySupportCapMilliCredits normal treasury-support authorization target
 * @param stressLiquiditySupportCapMilliCredits stress treasury-support authorization target
 * @param maxLiquiditySupportCapStepMilliCredits maximum support-cap change per claimed review
 */
public record FactionFiscalReviewProfile(
        int liquidityStressEnterBasisPoints,
        int liquidityStressExitBasisPoints,
        int normalStationTaxTargetBasisPoints,
        int stressStationTaxTargetBasisPoints,
        int maxStationTaxStepBasisPoints,
        long normalLiquiditySupportCapMilliCredits,
        long stressLiquiditySupportCapMilliCredits,
        long maxLiquiditySupportCapStepMilliCredits) {

    /**
     * Validates one explicit fiscal review profile.
     *
     * @param liquidityStressEnterBasisPoints stress-entry threshold
     * @param liquidityStressExitBasisPoints stress-exit threshold
     * @param normalStationTaxTargetBasisPoints normal station-tax target
     * @param stressStationTaxTargetBasisPoints stress station-tax target
     * @param maxStationTaxStepBasisPoints maximum tax step per review
     * @param normalLiquiditySupportCapMilliCredits normal support-cap target
     * @param stressLiquiditySupportCapMilliCredits stress support-cap target
     * @param maxLiquiditySupportCapStepMilliCredits maximum support-cap step per review
     */
    public FactionFiscalReviewProfile {
        requireBasisPoints(liquidityStressEnterBasisPoints, "Liquidity stress enter threshold");
        requireBasisPoints(liquidityStressExitBasisPoints, "Liquidity stress exit threshold");
        requireBasisPoints(normalStationTaxTargetBasisPoints, "Normal station tax target");
        requireBasisPoints(stressStationTaxTargetBasisPoints, "Stress station tax target");
        if (liquidityStressExitBasisPoints > liquidityStressEnterBasisPoints) {
            throw new IllegalArgumentException("Liquidity stress exit threshold cannot exceed enter threshold");
        }
        if (stressStationTaxTargetBasisPoints > normalStationTaxTargetBasisPoints) {
            throw new IllegalArgumentException("Stress station tax target cannot exceed normal target");
        }
        if (maxStationTaxStepBasisPoints <= 0 || maxStationTaxStepBasisPoints > 10_000) {
            throw new IllegalArgumentException("Maximum station tax step must be in 1..10000 bps");
        }
        if (normalLiquiditySupportCapMilliCredits < 0L
                || stressLiquiditySupportCapMilliCredits < normalLiquiditySupportCapMilliCredits
                || maxLiquiditySupportCapStepMilliCredits <= 0L) {
            throw new IllegalArgumentException("Liquidity-support targets/step are inconsistent");
        }
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in 0..10000 bps");
        }
    }
}

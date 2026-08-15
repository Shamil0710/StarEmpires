package com.spacesim.world;

/**
 * Unified persistent fiscal-policy contract shared by player and AI factions.
 *
 * <p>The policy authorizes or redirects ordinary conserved money flows; it never creates money,
 * cargo, production output or legal rights. Tax/levy values operate on the existing station to
 * treasury transfer path, while reserve/budget values only constrain treasury spending.</p>
 *
 * @param stationTaxBasisPoints own-station fiscal levy in basis points [0,10000]
 * @param foreignTerritoryLevyBasisPoints foreign-station territorial levy in basis points [0,10000]
 * @param treasuryReserveFloorMilliCredits protected treasury balance unavailable to ordinary policy spending
 * @param stationLiquidityReserveMilliCredits target minimum wallet balance for owned market stations
 * @param maxLiquiditySupportPerDecisionMilliCredits maximum treasury to station support in one decision
 * @param maxConstructionInvestmentPerDecisionMilliCredits maximum treasury construction funding in one decision
 */
public record FactionFiscalPolicyState(
        int stationTaxBasisPoints,
        int foreignTerritoryLevyBasisPoints,
        long treasuryReserveFloorMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits) {

    /** Validates the bounded fiscal policy without applying any economic mutation. */
    public FactionFiscalPolicyState {
        requireBasisPoints(stationTaxBasisPoints, "Station tax");
        requireBasisPoints(foreignTerritoryLevyBasisPoints, "Foreign territory levy");
        if (treasuryReserveFloorMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Fiscal policy money limits cannot be negative");
        }
    }

    /**
     * Returns the amount currently available for ordinary policy spending after the protected reserve.
     *
     * @param treasuryBalanceMilliCredits non-negative current treasury balance
     * @return spendable amount, never negative
     */
    public long spendableTreasuryMilliCredits(long treasuryBalanceMilliCredits) {
        if (treasuryBalanceMilliCredits < 0L) {
            throw new IllegalArgumentException("Treasury balance cannot be negative");
        }
        return Math.max(0L, treasuryBalanceMilliCredits - treasuryReserveFloorMilliCredits);
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in [0,10000] basis points");
        }
    }
}

package com.spacesim.world;

/**
 * Read-only measured fiscal position of one faction at a specific authoritative world state.
 *
 * <p>Every money value is derived from existing treasury, station and construction wallets. The
 * diagnostics neither creates a parallel budget nor applies an abstract performance modifier.</p>
 *
 * @param factionContentId stable faction identity
 * @param policy current common fiscal policy
 * @param treasuryBalanceMilliCredits current real faction treasury balance
 * @param spendableTreasuryMilliCredits treasury balance above the protected reserve floor
 * @param ownedMarketStations completed owned market stations included in liquidity diagnostics
 * @param stationsBelowLiquidityReserve owned markets currently below their configured liquidity target
 * @param ownedMarketLiquidityMilliCredits total current wallet balance of completed owned markets
 * @param liquidityReserveTargetMilliCredits summed configured target across completed owned markets
 * @param liquidityShortfallMilliCredits summed real wallet shortfall to the configured station target
 * @param activeConstructionProjects active faction-treasury construction projects
 * @param activeConstructionWalletMilliCredits real money currently held by those project wallets
 */
public record FactionFiscalPositionDiagnostics(
        String factionContentId,
        FactionFiscalPolicyState policy,
        long treasuryBalanceMilliCredits,
        long spendableTreasuryMilliCredits,
        int ownedMarketStations,
        int stationsBelowLiquidityReserve,
        long ownedMarketLiquidityMilliCredits,
        long liquidityReserveTargetMilliCredits,
        long liquidityShortfallMilliCredits,
        int activeConstructionProjects,
        long activeConstructionWalletMilliCredits) {

    /**
     * Validates immutable read-only diagnostics.
     *
     * @param factionContentId stable faction identity
     * @param policy current common fiscal policy
     * @param treasuryBalanceMilliCredits current real treasury balance
     * @param spendableTreasuryMilliCredits treasury balance above protected reserve
     * @param ownedMarketStations number of completed owned markets
     * @param stationsBelowLiquidityReserve number of owned markets below target liquidity
     * @param ownedMarketLiquidityMilliCredits total completed-owned-market wallet balance
     * @param liquidityReserveTargetMilliCredits summed station liquidity target
     * @param liquidityShortfallMilliCredits summed station liquidity shortfall
     * @param activeConstructionProjects active faction-treasury construction projects
     * @param activeConstructionWalletMilliCredits money held by active project wallets
     */
    public FactionFiscalPositionDiagnostics {
        if (factionContentId == null || factionContentId.isBlank()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Fiscal policy cannot be null");
        }
        if (treasuryBalanceMilliCredits < 0L
                || spendableTreasuryMilliCredits < 0L
                || ownedMarketStations < 0
                || stationsBelowLiquidityReserve < 0
                || ownedMarketLiquidityMilliCredits < 0L
                || liquidityReserveTargetMilliCredits < 0L
                || liquidityShortfallMilliCredits < 0L
                || activeConstructionProjects < 0
                || activeConstructionWalletMilliCredits < 0L) {
            throw new IllegalArgumentException("Fiscal diagnostics cannot contain negative values");
        }
        if (spendableTreasuryMilliCredits > treasuryBalanceMilliCredits) {
            throw new IllegalArgumentException("Spendable treasury cannot exceed treasury balance");
        }
        if (stationsBelowLiquidityReserve > ownedMarketStations) {
            throw new IllegalArgumentException("Liquidity-short station count exceeds owned markets");
        }
    }
}

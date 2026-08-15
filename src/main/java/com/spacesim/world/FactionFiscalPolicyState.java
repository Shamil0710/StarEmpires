package com.spacesim.world;

import java.util.Objects;

/**
 * Shared Stage-17F.2 player/AI fiscal-policy command and read model.
 *
 * <p>The record combines policy values persisted in the strategic, diplomacy and economic
 * aggregates without introducing another treasury. Tax/levy/customs rates authorize ordinary
 * real transfers; reserve and budget values limit spending from the existing treasury wallet.</p>
 *
 * @param factionContentId authored or world-defined stable faction ID
 * @param ownStationTaxBasisPoints own-station fiscal levy, 0..10000
 * @param territorialForeignStationLevyBasisPoints foreign-station levy inside controlled territory, 0..10000
 * @param customsTariffBasisPoints transaction/customs tariff before treaty exemptions, 0..10000
 * @param treasuryReserveFloorMilliCredits protected treasury floor for discretionary spending
 * @param stationLiquidityReserveMilliCredits desired station operating-liquidity floor
 * @param maxLiquiditySupportPerDecisionMilliCredits maximum treasury subsidy per decision
 * @param maxConstructionInvestmentPerDecisionMilliCredits maximum treasury construction funding per decision
 */
public record FactionFiscalPolicyState(
        String factionContentId,
        int ownStationTaxBasisPoints,
        int territorialForeignStationLevyBasisPoints,
        int customsTariffBasisPoints,
        long treasuryReserveFloorMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits) {

    /**
     * Validates bounded rates and non-negative spending authorizations.
     *
     * @param factionContentId authored or world-defined stable faction ID
     * @param ownStationTaxBasisPoints own-station fiscal levy, 0..10000
     * @param territorialForeignStationLevyBasisPoints foreign-station territorial levy, 0..10000
     * @param customsTariffBasisPoints transaction/customs tariff, 0..10000
     * @param treasuryReserveFloorMilliCredits protected treasury floor for discretionary spending
     * @param stationLiquidityReserveMilliCredits desired station operating-liquidity floor
     * @param maxLiquiditySupportPerDecisionMilliCredits maximum treasury subsidy per decision
     * @param maxConstructionInvestmentPerDecisionMilliCredits maximum construction funding per decision
     */
    public FactionFiscalPolicyState {
        factionContentId = Objects.requireNonNull(factionContentId, "Fiscal policy faction ID not set").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Fiscal policy faction ID cannot be blank");
        }
        requireBasisPoints(ownStationTaxBasisPoints, "Own-station tax");
        requireBasisPoints(territorialForeignStationLevyBasisPoints, "Territorial foreign-station levy");
        requireBasisPoints(customsTariffBasisPoints, "Customs tariff");
        if (treasuryReserveFloorMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Fiscal spending policy cannot be negative");
        }
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000 bps");
        }
    }
}

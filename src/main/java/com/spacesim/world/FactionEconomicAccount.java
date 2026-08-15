package com.spacesim.world;

import com.spacesim.components.WalletComponent;

import java.util.Objects;

/** Runtime mutable treasury/governance account for one persistent {@link FactionEconomicState}. */
final class FactionEconomicAccount {
    private final String factionContentId;
    private final WalletComponent treasury;
    private long stationLiquidityReserveMilliCredits;
    private long maxLiquiditySupportPerDecisionMilliCredits;
    private long treasuryReserveFloorMilliCredits;
    private long maxConstructionInvestmentPerDecisionMilliCredits;
    private FactionPolicyReviewState policyReviewState;

    FactionEconomicAccount(FactionEconomicState state) {
        FactionEconomicState checked = Objects.requireNonNull(state, "FactionEconomicState не задан");
        factionContentId = checked.factionContentId();
        treasury = new WalletComponent(checked.treasuryMilliCredits());
        stationLiquidityReserveMilliCredits = checked.stationLiquidityReserveMilliCredits();
        maxLiquiditySupportPerDecisionMilliCredits = checked.maxLiquiditySupportPerDecisionMilliCredits();
        treasuryReserveFloorMilliCredits = checked.treasuryReserveFloorMilliCredits();
        maxConstructionInvestmentPerDecisionMilliCredits =
                checked.maxConstructionInvestmentPerDecisionMilliCredits();
        policyReviewState = checked.policyReviewState();
    }

    String factionContentId() {
        return factionContentId;
    }

    WalletComponent treasury() {
        return treasury;
    }

    long stationLiquidityReserveMilliCredits() {
        return stationLiquidityReserveMilliCredits;
    }

    long maxLiquiditySupportPerDecisionMilliCredits() {
        return maxLiquiditySupportPerDecisionMilliCredits;
    }

    long treasuryReserveFloorMilliCredits() {
        return treasuryReserveFloorMilliCredits;
    }

    long maxConstructionInvestmentPerDecisionMilliCredits() {
        return maxConstructionInvestmentPerDecisionMilliCredits;
    }

    FactionPolicyReviewState policyReviewState() {
        return policyReviewState;
    }

    void updatePolicyReviewState(FactionPolicyReviewState state) {
        policyReviewState = Objects.requireNonNull(state, "Faction policy review state not set");
    }

    long spendableTreasuryMilliCredits() {
        return Math.max(0L, treasury.getBalanceMilliCredits() - treasuryReserveFloorMilliCredits);
    }

    FactionFiscalPolicyState fiscalPolicy(int stationTaxBasisPoints, int foreignTerritoryLevyBasisPoints) {
        return new FactionFiscalPolicyState(
                stationTaxBasisPoints,
                foreignTerritoryLevyBasisPoints,
                treasuryReserveFloorMilliCredits,
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits);
    }

    void updateFiscalPolicy(FactionFiscalPolicyState policy) {
        FactionFiscalPolicyState checked = Objects.requireNonNull(policy, "Faction fiscal policy not set");
        treasuryReserveFloorMilliCredits = checked.treasuryReserveFloorMilliCredits();
        stationLiquidityReserveMilliCredits = checked.stationLiquidityReserveMilliCredits();
        maxLiquiditySupportPerDecisionMilliCredits = checked.maxLiquiditySupportPerDecisionMilliCredits();
        maxConstructionInvestmentPerDecisionMilliCredits =
                checked.maxConstructionInvestmentPerDecisionMilliCredits();
    }

    FactionEconomicState snapshot() {
        return new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits,
                policyReviewState);
    }
}

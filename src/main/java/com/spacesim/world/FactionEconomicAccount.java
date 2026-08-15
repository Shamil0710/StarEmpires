package com.spacesim.world;

import com.spacesim.components.WalletComponent;

import java.util.Objects;

/** Runtime mutable treasury and fiscal authorization state for one persistent faction economy. */
final class FactionEconomicAccount {
    private final String factionContentId;
    private final WalletComponent treasury;
    private long stationLiquidityReserveMilliCredits;
    private long maxLiquiditySupportPerDecisionMilliCredits;
    private long treasuryReserveFloorMilliCredits;
    private long maxConstructionInvestmentPerDecisionMilliCredits;

    FactionEconomicAccount(FactionEconomicState state) {
        FactionEconomicState checked = Objects.requireNonNull(state, "FactionEconomicState не задан");
        factionContentId = checked.factionContentId();
        treasury = new WalletComponent(checked.treasuryMilliCredits());
        applyPolicy(checked);
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

    long discretionaryTreasuryMilliCredits() {
        long balance = treasury.getBalanceMilliCredits();
        return balance <= treasuryReserveFloorMilliCredits ? 0L : balance - treasuryReserveFloorMilliCredits;
    }

    long constructionInvestmentAuthorizationMilliCredits() {
        return Math.min(discretionaryTreasuryMilliCredits(), maxConstructionInvestmentPerDecisionMilliCredits);
    }

    void updatePolicy(
            long stationLiquidityReserveMilliCredits,
            long maxLiquiditySupportPerDecisionMilliCredits,
            long treasuryReserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
        FactionEconomicState checked = new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits);
        applyPolicy(checked);
    }

    FactionEconomicState snapshot() {
        return new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits);
    }

    private void applyPolicy(FactionEconomicState state) {
        stationLiquidityReserveMilliCredits = state.stationLiquidityReserveMilliCredits();
        maxLiquiditySupportPerDecisionMilliCredits = state.maxLiquiditySupportPerDecisionMilliCredits();
        treasuryReserveFloorMilliCredits = state.treasuryReserveFloorMilliCredits();
        maxConstructionInvestmentPerDecisionMilliCredits = state.maxConstructionInvestmentPerDecisionMilliCredits();
    }
}

package com.spacesim.world;

import com.spacesim.components.WalletComponent;

import java.util.Objects;

/** Runtime mutable treasury одного persistent {@link FactionEconomicState}. */
final class FactionEconomicAccount {
    private final String factionContentId;
    private final WalletComponent treasury;
    private final long stationLiquidityReserveMilliCredits;
    private final long maxLiquiditySupportPerDecisionMilliCredits;

    FactionEconomicAccount(FactionEconomicState state) {
        FactionEconomicState checked = Objects.requireNonNull(state, "FactionEconomicState не задан");
        factionContentId = checked.factionContentId();
        treasury = new WalletComponent(checked.treasuryMilliCredits());
        stationLiquidityReserveMilliCredits = checked.stationLiquidityReserveMilliCredits();
        maxLiquiditySupportPerDecisionMilliCredits = checked.maxLiquiditySupportPerDecisionMilliCredits();
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

    FactionEconomicState snapshot() {
        return new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits);
    }
}

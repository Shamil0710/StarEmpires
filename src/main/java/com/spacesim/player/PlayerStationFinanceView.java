package com.spacesim.player;

import java.util.Objects;

/**
 * Read-only finance snapshot for one physically docked player-owned station.
 *
 * @param stationRef persistent owned station reference
 * @param playerWalletMilliCredits current personal wallet balance
 * @param stationWalletMilliCredits current station operating wallet balance
 */
public record PlayerStationFinanceView(
        OwnedStationRef stationRef,
        long playerWalletMilliCredits,
        long stationWalletMilliCredits) {

    /**
     * Validates a station finance snapshot.
     *
     * @param stationRef persistent owned station reference
     * @param playerWalletMilliCredits non-negative personal balance
     * @param stationWalletMilliCredits non-negative station balance
     */
    public PlayerStationFinanceView {
        Objects.requireNonNull(stationRef, "Owned station reference not set");
        if (playerWalletMilliCredits < 0L || stationWalletMilliCredits < 0L) {
            throw new IllegalArgumentException("Station finance balances cannot be negative");
        }
    }
}

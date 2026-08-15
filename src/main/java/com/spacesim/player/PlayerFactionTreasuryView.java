package com.spacesim.player;

import java.util.Objects;

/**
 * Read-only Stage-17C view of the explicit personal-wallet / faction-treasury boundary.
 *
 * @param factionContentId stable faction identity
 * @param personalWalletMilliCredits player's personal/company wallet balance
 * @param factionTreasuryMilliCredits authoritative Stage-8 faction treasury balance
 */
public record PlayerFactionTreasuryView(
        String factionContentId,
        long personalWalletMilliCredits,
        long factionTreasuryMilliCredits) {
    /**
     * Validates the finance snapshot.
     *
     * @param factionContentId stable faction identity
     * @param personalWalletMilliCredits non-negative personal balance
     * @param factionTreasuryMilliCredits non-negative treasury balance
     */
    public PlayerFactionTreasuryView {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction ID not set").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction ID cannot be blank");
        }
        if (personalWalletMilliCredits < 0L || factionTreasuryMilliCredits < 0L) {
            throw new IllegalArgumentException("Faction treasury view balances cannot be negative");
        }
    }
}

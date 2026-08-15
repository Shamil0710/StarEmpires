package com.spacesim.player;

import com.spacesim.components.WalletComponent;

import java.util.Objects;
import java.util.Optional;

/**
 * Stage-17C boundary between the player's personal/company wallet and the ordinary Stage-8 faction treasury.
 *
 * <p>Capitalization is an explicit one-way money transfer. It never merges balances automatically,
 * grants starting capital, touches station operating wallets or creates passive income. Treasury
 * withdrawals are intentionally not exposed by this first slice because spending public faction
 * money belongs to explicit governance/budget policy rather than an unrestricted personal withdrawal.</p>
 */
public final class PlayerFactionTreasuryService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";
    private static final String CAPITALIZATION_REASON = "player-faction-capitalization";

    private final PlayerRuntime runtime;

    /**
     * Creates the faction-treasury adapter.
     *
     * @param runtime current playable runtime
     */
    public PlayerFactionTreasuryService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Returns the current personal/treasury balances for an affiliated faction with economic state.
     *
     * @return immutable finance view, or empty while the player is independent or the faction has no treasury
     */
    public Optional<PlayerFactionTreasuryView> view() {
        PlayerState player = runtime.player();
        if (!player.affiliated()) {
            return Optional.empty();
        }
        Long treasury = runtime.world().findFactionTreasuryBalance(player.factionContentId()).orElse(null);
        if (treasury == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerFactionTreasuryView(
                player.factionContentId(),
                player.walletMilliCredits(),
                treasury));
    }

    /**
     * Explicitly capitalizes the player's current faction from the personal/company wallet.
     *
     * <p>The player state is prepared first, then the ordinary wallet-to-wallet transfer is executed
     * by {@code WorldSimulation}. If the treasury transfer is rejected, the previous PlayerState is
     * restored. The world boundary itself rolls back a treasury mutation if ledger recording fails,
     * so a successful call always preserves total money and records one {@code MONEY_TRANSFER}.</p>
     *
     * @param amountMilliCredits strictly positive capitalization amount
     * @return {@code true} only when the full amount moved; {@code false} for an independent player,
     *         insufficient personal balance or treasury credit overflow
     * @throws IllegalArgumentException if amount is not positive
     */
    public boolean capitalize(long amountMilliCredits) {
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Faction capitalization amount must be positive");
        }
        PlayerState previous = runtime.player();
        if (!previous.affiliated()) {
            return false;
        }

        WalletComponent personalWallet = new WalletComponent(previous.walletMilliCredits());
        if (!personalWallet.canDebit(amountMilliCredits)) {
            return false;
        }
        long resultingPersonalWallet = Math.subtractExact(
                previous.walletMilliCredits(), amountMilliCredits);
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                previous,
                resultingPersonalWallet,
                previous.ownedFleetIds(),
                previous.activeFleetId());

        runtime.replacePlayerState(candidate);
        try {
            if (!runtime.world().transferToFactionTreasury(
                    previous.factionContentId(),
                    personalWallet,
                    PLAYER_LEDGER_NAME,
                    amountMilliCredits,
                    CAPITALIZATION_REASON)) {
                runtime.replacePlayerState(previous);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            runtime.replacePlayerState(previous);
            throw exception;
        }
    }
}

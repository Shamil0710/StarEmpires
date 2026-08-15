package com.spacesim.player;

import com.spacesim.components.WalletComponent;

import java.util.Objects;
import java.util.Optional;

/**
 * Live Stage-17C adapter between {@link PlayerState#walletMilliCredits()} and the ordinary
 * Stage-8 faction treasury owned by {@link com.spacesim.world.WorldSimulation}.
 *
 * <p>The adapter exposes only explicit capitalization. It does not merge personal and public money,
 * does not touch station operating wallets and does not provide an unrestricted treasury withdrawal.
 * A successful capitalization produces one world-ledger {@code MONEY_TRANSFER}; no money source or
 * sink is involved.</p>
 */
public final class PlayerFactionTreasuryRuntimeService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";
    private static final String CAPITALIZATION_REASON = "player-faction-capitalization";

    private final PlayerRuntime runtime;

    /**
     * Creates a live faction-treasury adapter.
     *
     * @param runtime current playable runtime
     */
    public PlayerFactionTreasuryRuntimeService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Returns the current personal/treasury balances through the already proven persistent view.
     *
     * @return finance view, or empty while the player is independent or its faction has no economy
     */
    public Optional<PlayerFactionTreasuryView> view() {
        return PlayerFactionTreasuryService.view(runtime.snapshot());
    }

    /**
     * Moves an explicit amount of existing personal/company money into the affiliated faction treasury.
     *
     * <p>The candidate PlayerState is installed before the world transfer so a rejected or throwing
     * treasury operation can restore the previous player state. The world transfer independently
     * rolls back its two wallets if ledger recording throws. Therefore a failed call cannot leave a
     * partial personal/treasury mutation.</p>
     *
     * @param amountMilliCredits strictly positive full-transfer amount
     * @return true when the full amount moved and one ledger transfer was recorded; false when the
     *         player is independent, personal funds are insufficient or treasury capacity rejects it
     * @throws IllegalArgumentException if amount is not positive or the affiliated world faction has
     *         no ordinary economic account
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
            boolean transferred = runtime.world().transferToFactionTreasury(
                    previous.factionContentId(),
                    personalWallet,
                    PLAYER_LEDGER_NAME,
                    amountMilliCredits,
                    CAPITALIZATION_REASON);
            if (!transferred) {
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

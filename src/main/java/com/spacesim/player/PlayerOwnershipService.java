package com.spacesim.player;

import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.world.FleetId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-12B atomic ownership transfer service for existing physical world fleets.
 *
 * <p>Ownership is represented only by PlayerState FleetIds and is deliberately independent from a
 * fleet's faction/legal components. Purchase and sale transfer existing money between the player
 * wallet and an explicit persistent counterparty wallet; no entity is spawned or duplicated.</p>
 */
public final class PlayerOwnershipService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";

    private final PlayerRuntime runtime;

    /**
     * Creates an ownership service for one playable runtime.
     *
     * @param runtime current player/world runtime
     */
    public PlayerOwnershipService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Purchases ownership of an already existing world fleet.
     *
     * @param fleetId stable fleet to acquire
     * @param sellerWallet persistent seller/counterparty wallet
     * @param priceMilliCredits strictly positive purchase price
     * @param ledger economic ledger that records the money transfer
     * @param sellerName non-blank diagnostic seller name
     * @return true only when money and ownership transfer atomically
     */
    public boolean purchaseFleet(
            FleetId fleetId,
            WalletComponent sellerWallet,
            long priceMilliCredits,
            EconomicLedger ledger,
            String sellerName) {
        FleetId checkedFleet = Objects.requireNonNull(fleetId, "FleetId not set");
        WalletComponent seller = Objects.requireNonNull(sellerWallet, "Seller wallet not set");
        EconomicLedger checkedLedger = Objects.requireNonNull(ledger, "EconomicLedger not set");
        String checkedSeller = normalizedName(sellerName, "Seller name");
        PlayerState current = runtime.player();
        if (priceMilliCredits <= 0L
                || current.ownedFleetIds().contains(checkedFleet)
                || runtime.world().findFleet(checkedFleet).isEmpty()) {
            return false;
        }

        WalletComponent playerWallet = new WalletComponent(current.walletMilliCredits());
        if (!playerWallet.canDebit(priceMilliCredits) || !seller.canCredit(priceMilliCredits)) {
            return false;
        }
        List<FleetId> owned = new ArrayList<>(current.ownedFleetIds());
        owned.add(checkedFleet);
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                current,
                current.walletMilliCredits() - priceMilliCredits,
                owned,
                current.activeFleetId() == null ? checkedFleet : current.activeFleetId());

        if (!playerWallet.transferTo(seller, priceMilliCredits)) {
            return false;
        }
        try {
            runtime.replacePlayerState(candidate);
            checkedLedger.recordMoneyTransfer(
                    PLAYER_LEDGER_NAME,
                    checkedSeller,
                    priceMilliCredits,
                    "player-fleet-purchase");
            return true;
        } catch (RuntimeException exception) {
            rollbackPlayerPayment(current, playerWallet, seller, priceMilliCredits, exception);
            throw exception;
        }
    }

    /**
     * Sells ownership of an existing player-owned fleet without deleting the physical fleet.
     *
     * @param fleetId stable fleet to sell
     * @param buyerWallet persistent buyer/counterparty wallet
     * @param priceMilliCredits strictly positive sale price
     * @param ledger economic ledger that records the money transfer
     * @param buyerName non-blank diagnostic buyer name
     * @return true only when money and ownership transfer atomically
     */
    public boolean sellFleet(
            FleetId fleetId,
            WalletComponent buyerWallet,
            long priceMilliCredits,
            EconomicLedger ledger,
            String buyerName) {
        FleetId checkedFleet = Objects.requireNonNull(fleetId, "FleetId not set");
        WalletComponent buyer = Objects.requireNonNull(buyerWallet, "Buyer wallet not set");
        EconomicLedger checkedLedger = Objects.requireNonNull(ledger, "EconomicLedger not set");
        String checkedBuyer = normalizedName(buyerName, "Buyer name");
        PlayerState current = runtime.player();
        if (priceMilliCredits <= 0L
                || !current.ownedFleetIds().contains(checkedFleet)
                || runtime.world().findFleet(checkedFleet).isEmpty()) {
            return false;
        }

        long resultingWallet;
        try {
            resultingWallet = Math.addExact(current.walletMilliCredits(), priceMilliCredits);
        } catch (ArithmeticException exception) {
            return false;
        }
        WalletComponent playerWallet = new WalletComponent(current.walletMilliCredits());
        if (!buyer.canDebit(priceMilliCredits) || !playerWallet.canCredit(priceMilliCredits)) {
            return false;
        }
        List<FleetId> owned = new ArrayList<>(current.ownedFleetIds());
        owned.remove(checkedFleet);
        FleetId active = current.activeFleetId();
        if (checkedFleet.equals(active)) {
            active = owned.isEmpty() ? null : owned.get(0);
        }
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                current,
                resultingWallet,
                owned,
                active);

        if (!buyer.transferTo(playerWallet, priceMilliCredits)) {
            return false;
        }
        try {
            runtime.replacePlayerState(candidate);
            checkedLedger.recordMoneyTransfer(
                    checkedBuyer,
                    PLAYER_LEDGER_NAME,
                    priceMilliCredits,
                    "player-fleet-sale");
            return true;
        } catch (RuntimeException exception) {
            rollbackBuyerPayment(current, playerWallet, buyer, priceMilliCredits, exception);
            throw exception;
        }
    }

    private void rollbackPlayerPayment(
            PlayerState previous,
            WalletComponent playerWallet,
            WalletComponent seller,
            long amount,
            RuntimeException cause) {
        runtime.replacePlayerState(previous);
        if (!seller.transferTo(playerWallet, amount)) {
            cause.addSuppressed(new IllegalStateException("Fleet purchase rollback could not restore money"));
        }
    }

    private void rollbackBuyerPayment(
            PlayerState previous,
            WalletComponent playerWallet,
            WalletComponent buyer,
            long amount,
            RuntimeException cause) {
        runtime.replacePlayerState(previous);
        if (!playerWallet.transferTo(buyer, amount)) {
            cause.addSuppressed(new IllegalStateException("Fleet sale rollback could not restore money"));
        }
    }

    private static String normalizedName(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}

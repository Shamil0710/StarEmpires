package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.simulation.SimulationSession;

import java.util.Objects;
import java.util.Optional;

/**
 * Physical Stage-16 finance boundary for completed player-owned stations.
 *
 * <p>The first station-finance baseline deliberately requires the active player ship to be
 * physically docked at the owned station. This prevents an implicit remote-banking mechanic before
 * a future communications/company-account system explicitly introduces one. Money always moves via
 * real wallet transfer and {@link EconomicLedger} entries; ownership alone never creates passive
 * personal income.</p>
 */
public final class PlayerStationFinanceService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";

    private final PlayerRuntime runtime;

    /**
     * Creates the station-finance adapter.
     *
     * @param runtime current playable runtime
     */
    public PlayerStationFinanceService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Returns finance state only when the active ship is docked at a live player-owned station.
     *
     * @return current finance snapshot or empty
     */
    public Optional<PlayerStationFinanceView> view() {
        Context context = resolveDockedOwnedStation();
        if (context == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerStationFinanceView(
                context.stationRef(),
                context.player().walletMilliCredits(),
                context.stationWallet().getBalanceMilliCredits()));
    }

    /**
     * Deposits personal money into the currently docked owned station operating wallet.
     *
     * @param amountMilliCredits positive amount to transfer
     * @return true only when the full transfer and player-state update complete atomically
     */
    public boolean deposit(long amountMilliCredits) {
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Station deposit amount must be positive");
        }
        Context context = resolveDockedOwnedStation();
        if (context == null) {
            return false;
        }
        PlayerState previous = context.player();
        WalletComponent playerWallet = new WalletComponent(previous.walletMilliCredits());
        if (!playerWallet.canDebit(amountMilliCredits)
                || !context.stationWallet().canCredit(amountMilliCredits)) {
            return false;
        }
        long resultingPlayerWallet = Math.subtractExact(
                previous.walletMilliCredits(), amountMilliCredits);
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                previous,
                resultingPlayerWallet,
                previous.ownedFleetIds(),
                previous.activeFleetId());

        if (!playerWallet.transferTo(context.stationWallet(), amountMilliCredits)) {
            return false;
        }
        try {
            runtime.replacePlayerState(candidate);
            context.session().getLedger().recordMoneyTransfer(
                    PLAYER_LEDGER_NAME,
                    stationLedgerName(context.stationRef()),
                    amountMilliCredits,
                    "player-station-deposit");
            return true;
        } catch (RuntimeException exception) {
            rollbackDeposit(previous, playerWallet, context.stationWallet(), amountMilliCredits, exception);
            throw exception;
        }
    }

    /**
     * Withdraws money from the currently docked owned station into the personal wallet.
     *
     * @param amountMilliCredits positive amount to transfer
     * @return true only when station liquidity and personal wallet capacity allow the full transfer
     */
    public boolean withdraw(long amountMilliCredits) {
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Station withdrawal amount must be positive");
        }
        Context context = resolveDockedOwnedStation();
        if (context == null) {
            return false;
        }
        PlayerState previous = context.player();
        WalletComponent playerWallet = new WalletComponent(previous.walletMilliCredits());
        if (!context.stationWallet().canDebit(amountMilliCredits)
                || !playerWallet.canCredit(amountMilliCredits)) {
            return false;
        }
        final long resultingPlayerWallet;
        try {
            resultingPlayerWallet = Math.addExact(previous.walletMilliCredits(), amountMilliCredits);
        } catch (ArithmeticException exception) {
            return false;
        }
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                previous,
                resultingPlayerWallet,
                previous.ownedFleetIds(),
                previous.activeFleetId());

        if (!context.stationWallet().transferTo(playerWallet, amountMilliCredits)) {
            return false;
        }
        try {
            runtime.replacePlayerState(candidate);
            context.session().getLedger().recordMoneyTransfer(
                    stationLedgerName(context.stationRef()),
                    PLAYER_LEDGER_NAME,
                    amountMilliCredits,
                    "player-station-withdraw");
            return true;
        } catch (RuntimeException exception) {
            rollbackWithdraw(previous, playerWallet, context.stationWallet(), amountMilliCredits, exception);
            throw exception;
        }
    }

    private Context resolveDockedOwnedStation() {
        PlayerState player = runtime.player();
        DiscoveredObjectRef docked = player.dockedAt();
        if (docked == null) {
            return null;
        }
        OwnedStationRef stationRef = new OwnedStationRef(docked.systemId(), docked.entityId());
        if (!player.ownedStations().contains(stationRef)) {
            return null;
        }
        SimulationSession session = runtime.world().findSession(stationRef.systemId()).orElse(null);
        Entity station = session == null ? null : session.getEntityRegistry().find(stationRef.stationEntityId());
        IdentityComponent identity = station == null ? null : station.getComponent(IdentityComponent.class);
        WalletComponent wallet = station == null ? null : station.getComponent(WalletComponent.class);
        if (identity == null || identity.kind != IdentityComponent.Kind.STATION || wallet == null) {
            return null;
        }
        return new Context(player, stationRef, session, wallet);
    }

    private void rollbackDeposit(
            PlayerState previous,
            WalletComponent playerWallet,
            WalletComponent stationWallet,
            long amount,
            RuntimeException cause) {
        runtime.replacePlayerState(previous);
        if (!stationWallet.transferTo(playerWallet, amount)) {
            cause.addSuppressed(new IllegalStateException("Station deposit rollback could not restore money"));
        }
    }

    private void rollbackWithdraw(
            PlayerState previous,
            WalletComponent playerWallet,
            WalletComponent stationWallet,
            long amount,
            RuntimeException cause) {
        runtime.replacePlayerState(previous);
        if (!playerWallet.transferTo(stationWallet, amount)) {
            cause.addSuppressed(new IllegalStateException("Station withdrawal rollback could not restore money"));
        }
    }

    private static String stationLedgerName(OwnedStationRef stationRef) {
        return "station:" + stationRef.systemId().value() + ":" + stationRef.stationEntityId().value();
    }

    private record Context(
            PlayerState player,
            OwnedStationRef stationRef,
            SimulationSession session,
            WalletComponent stationWallet) {
    }
}

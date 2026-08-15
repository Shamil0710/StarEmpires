package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.WalletComponent;

import java.util.Objects;

/**
 * Optional settlement policy for ordinary {@link TradeController} transactions.
 *
 * <p>The policy may quote an additional conserved customs payment but cannot mutate wallets,
 * inventories or the economic ledger itself. The controller remains the single atomic transaction
 * boundary. Legacy/local isolated simulations use {@link #none()} and therefore retain zero
 * transaction duty.</p>
 */
@FunctionalInterface
public interface TradeTransactionPolicy {
    /** Direction of the commodity transaction relative to the station. */
    enum Direction {
        /** Participant buys cargo from the station. */
        BUY_FROM_STATION,
        /** Participant sells cargo to the station. */
        SELL_TO_STATION
    }

    /**
     * Immutable extra settlement quoted before any mutation.
     *
     * @param amountMilliCredits non-negative customs amount
     * @param collectorWallet receiving authoritative wallet, required when amount is positive
     * @param collectorLedgerName non-empty ledger destination when amount is positive
     * @param reason non-empty ledger reason when amount is positive
     */
    record Charge(
            long amountMilliCredits,
            WalletComponent collectorWallet,
            String collectorLedgerName,
            String reason) {
        /** Validates one non-mutating settlement quote. */
        public Charge {
            if (amountMilliCredits < 0L) {
                throw new IllegalArgumentException("Trade policy charge cannot be negative");
            }
            if (amountMilliCredits == 0L) {
                collectorWallet = null;
                collectorLedgerName = "";
                reason = "";
            } else {
                collectorWallet = Objects.requireNonNull(collectorWallet, "Trade charge collector wallet not set");
                collectorLedgerName = Objects.requireNonNull(
                        collectorLedgerName, "Trade charge collector ledger name not set").strip();
                reason = Objects.requireNonNull(reason, "Trade charge reason not set").strip();
                if (collectorLedgerName.isEmpty() || reason.isEmpty()) {
                    throw new IllegalArgumentException("Positive trade charge requires ledger labels");
                }
            }
        }

        /** @return canonical no-charge quote */
        public static Charge none() {
            return new Charge(0L, null, "", "");
        }
    }

    /**
     * Quotes additional settlement for one otherwise-valid trade request.
     *
     * @param station market station
     * @param participant buyer/seller entity
     * @param direction commodity direction
     * @param tradeValueMilliCredits ordinary gross commodity value
     * @return immutable non-mutating charge
     */
    Charge quote(
            Entity station,
            Entity participant,
            Direction direction,
            long tradeValueMilliCredits);

    /** @return stateless policy with no transaction charges */
    static TradeTransactionPolicy none() {
        return (station, participant, direction, tradeValue) -> Charge.none();
    }
}

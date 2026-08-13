package com.spacesim.world;

/** Explicit fate of wallet money owned by a destroyed entity. */
public enum MoneyDestructionFate {
    /** Wallet balance is explicitly destroyed and recorded as a money sink. */
    SINK,
    /** Wallet balance is transferred to the destroyed entity owner's faction treasury. */
    TRANSFER_TO_FACTION_TREASURY
}

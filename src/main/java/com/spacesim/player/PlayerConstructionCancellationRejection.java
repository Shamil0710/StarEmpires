package com.spacesim.player;

/** Stable authoritative rejection reasons for Stage-16 player construction cancellation. */
public enum PlayerConstructionCancellationRejection {
    /** Cancellation is currently allowed. */
    NONE,
    /** The referenced project is not owned by the player. */
    NOT_OWNED,
    /** The project is already terminal. */
    TERMINAL,
    /** Voluntary cancellation during physical assembly is not supported before salvage-by-progress exists. */
    BUILDING,
    /** At least one required material unit is already physically present at the site. */
    MATERIALS_DELIVERED,
    /** The live construction site or its required wallet/inventory is unavailable. */
    SITE_UNAVAILABLE,
    /** Refunding the full site wallet would overflow the personal player wallet. */
    PLAYER_WALLET_CAPACITY
}

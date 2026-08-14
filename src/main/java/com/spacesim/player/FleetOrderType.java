package com.spacesim.player;

/**
 * Persistent player fleet-order categories.
 *
 * <p>The enum describes durable intent only. Execution must go through ordinary movement,
 * jump, economy, mining, construction and combat boundaries rather than mutating world state
 * directly.</p>
 */
public enum FleetOrderType {
    /** Remain at the current physical location and brake to rest. */
    HOLD,
    /** Move the physical fleet to a system-local coordinate. */
    MOVE,
    /** Repeatedly move real cargo between two explicit markets using ordinary trade. */
    TRADE,
    /** Extract a real finite asteroid resource and optionally deliver it to a market. */
    MINE,
    /** Protect and remain near another physical FleetId. */
    ESCORT,
    /** Repeatedly traverse a deterministic ordered list of systems. */
    PATROL,
    /** Follow another physical FleetId without providing an escort-risk contribution. */
    FOLLOW,
    /** Acquire real required cargo from a known supplier and physically deliver it to an owned construction site. */
    SUPPLY_PROJECT
}

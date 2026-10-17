package com.spacesim.persistence;

/**
 * A freighter cannot start its remaining route because no safe finite-propellant plan exists from
 * the reaction mass it carries and the stock it can reach. The freighter waits where it is; this is
 * an ordinary operating outcome, not a broken world.
 */
public final class FreightPropellantUnavailableException extends IllegalStateException {
    /** @param message exact reason, required delta-v and projected reaction mass */
    public FreightPropellantUnavailableException(String message) {
        super(message);
    }
}

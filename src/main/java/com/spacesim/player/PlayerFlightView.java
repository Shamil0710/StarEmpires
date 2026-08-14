package com.spacesim.player;

/**
 * Read-only Stage-14E physical flight diagnostics for the active player ship.
 *
 * @param speed current physical speed in world units per simulation second
 * @param speedCap assisted hull speed cap
 * @param dryMass current hull/structure dry mass
 * @param cargoMass current mass contributed by real cargo inventory
 * @param totalMass current total translational mass
 * @param acceleration maximum current acceleration at this load
 * @param brakingAcceleration maximum current braking acceleration at this load
 * @param estimatedStopSeconds estimated time to stop from current speed under full counter-thrust
 * @param estimatedStopDistance estimated straight-line braking distance under full counter-thrust
 */
public record PlayerFlightView(
        float speed,
        float speedCap,
        float dryMass,
        float cargoMass,
        float totalMass,
        float acceleration,
        float brakingAcceleration,
        float estimatedStopSeconds,
        float estimatedStopDistance) {
}

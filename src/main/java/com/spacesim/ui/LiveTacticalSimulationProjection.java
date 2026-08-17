package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalSimulationSession;

import java.util.Objects;

/**
 * Pure read-only projection from one live tactical simulation instant into prototype tactical glyphs.
 *
 * <p>The adapter never advances the simulation and never creates combat outcomes. It projects the
 * authoritative ship damage/shield state and the currently existing physical projectile bodies.</p>
 */
public final class LiveTacticalSimulationProjection {
    private static final double ATTACKER_X_M = 260d;
    private static final double TARGET_X_M = 1_690d;
    private static final double CENTER_Y_M = 700d;

    /** Creates a stateless live tactical projection adapter. */
    public LiveTacticalSimulationProjection() {
    }

    /**
     * Converts the supplied authoritative live state into one immutable presentation snapshot.
     *
     * @param state authoritative read snapshot from the live simulation session
     * @return immutable tactical visual snapshot
     */
    public TacticalPrototypeVisualSnapshot project(LiveTacticalSimulationSession.Snapshot state) {
        LiveTacticalSimulationSession.Snapshot checked = Objects.requireNonNull(state, "state");
        Stage175ITacticalVisualProjection projection = new Stage175ITacticalVisualProjection()
                .addShip(
                        LiveTacticalSimulationSession.ATTACKER_ENTITY_ID,
                        checked.attackerHull(),
                        checked.attackerDamage(),
                        ATTACKER_X_M,
                        CENTER_Y_M,
                        0d,
                        0d,
                        null,
                        null)
                .addShip(
                        LiveTacticalSimulationSession.TARGET_ENTITY_ID,
                        checked.targetHull(),
                        checked.targetDamage(),
                        TARGET_X_M,
                        CENTER_Y_M,
                        Math.PI,
                        0d,
                        checked.targetShieldDefinition(),
                        checked.targetShieldState());
        for (var projectile : checked.projectiles()) {
            projection.addKinetic(projectile);
        }
        if (checked.recentImpact() != null) {
            projection.addImpact(
                    178_000L + Math.max(0L, checked.recentImpactTick()),
                    TARGET_X_M,
                    CENTER_Y_M,
                    checked.recentImpact());
        }
        return projection.snapshot();
    }
}

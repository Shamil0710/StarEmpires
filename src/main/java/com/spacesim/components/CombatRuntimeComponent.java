package com.spacesim.components;

import com.badlogic.ashley.core.Component;

import java.util.Objects;

/**
 * Transient weapon runtime state derived from a persistent ship archetype.
 *
 * <p>The equipped weapon ID is data-driven and stable, while cooldown is intentionally local
 * runtime state in the Stage-13 vertical slice. Hull and shields remain authoritative persistent
 * values in {@link CombatComponent}. A later persistence hardening pass may persist cooldown if
 * save/load inside sub-second weapon cycles proves materially important.</p>
 */
public final class CombatRuntimeComponent implements Component {
    /** Stable content ID of the currently equipped weapon. */
    public String weaponId;
    /** Remaining cooldown in simulation seconds, clamped to zero. */
    public float cooldownRemaining;

    /** Creates an unconfigured runtime component for Ashley/component restoration helpers. */
    public CombatRuntimeComponent() {
    }

    /**
     * Creates a configured runtime weapon state.
     *
     * @param weaponId stable data-driven weapon ID
     */
    public CombatRuntimeComponent(String weaponId) {
        this.weaponId = Objects.requireNonNull(weaponId, "Combat weaponId not set");
    }

    /**
     * Advances cooldown using authoritative simulation delta.
     *
     * @param deltaSeconds finite non-negative simulation delta
     */
    public void advanceCooldown(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Combat cooldown delta must be finite and non-negative");
        }
        cooldownRemaining = Math.max(0f, cooldownRemaining - deltaSeconds);
    }

    /** @return true when the weapon may attempt another shot */
    public boolean ready() {
        return Float.isFinite(cooldownRemaining) && cooldownRemaining <= 0f;
    }
}

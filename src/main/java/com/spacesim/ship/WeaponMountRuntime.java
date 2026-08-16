package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic Stage-17.5E launcher-cycle runtime shared by player and AI weapon commands.
 *
 * <p>The runtime owns only time-based launcher readiness. It does not duplicate ammunition
 * quantities, fire-control state, energy or heat; those remain in their existing authoritative
 * systems.</p>
 */
public final class WeaponMountRuntime {
    private static final double EPSILON = 1e-9d;

    /**
     * Persistent-ready cooldown state by fitted weapon mount.
     *
     * @param cooldownSecondsByMount remaining physical cycle time by mount
     */
    public record RuntimeState(Map<String, Double> cooldownSecondsByMount) {
        /**
         * Validates and freezes deterministic cooldown state.
         *
         * @param cooldownSecondsByMount remaining physical cycle time by mount
         */
        public RuntimeState {
            Objects.requireNonNull(cooldownSecondsByMount, "cooldownSecondsByMount");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : cooldownSecondsByMount.entrySet()) {
                requireNonBlank(entry.getKey(), "mountId");
                Double value = Objects.requireNonNull(entry.getValue(), "cooldownSeconds");
                requireNonNegativeFinite(value, "cooldownSeconds");
                copy.put(entry.getKey(), canonicalZero(value));
            }
            cooldownSecondsByMount = Collections.unmodifiableMap(copy);
        }

        /** @return empty cooldown state */
        public static RuntimeState empty() {
            return new RuntimeState(Map.of());
        }
    }

    /**
     * Returns whether the requested launcher mount has completed its physical cycle.
     *
     * @param state current cooldown state
     * @param mountId fitted weapon mount ID
     * @return {@code true} when a new shot may begin its cycle
     */
    public boolean ready(RuntimeState state, String mountId) {
        RuntimeState checked = Objects.requireNonNull(state, "state");
        requireNonBlank(mountId, "mountId");
        return checked.cooldownSecondsByMount().getOrDefault(mountId, 0d) <= EPSILON;
    }

    /**
     * Commits one accepted shot and starts the authored launcher cycle.
     *
     * @param state current cooldown state
     * @param mountId fitted weapon mount ID
     * @param launcher physical launcher definition
     * @return next cooldown state
     */
    public RuntimeState commitShot(RuntimeState state, String mountId, Launcher launcher) {
        RuntimeState checked = Objects.requireNonNull(state, "state");
        requireNonBlank(mountId, "mountId");
        Launcher checkedLauncher = Objects.requireNonNull(launcher, "launcher");
        if (!ready(checked, mountId)) {
            throw new IllegalStateException("weapon mount is still cycling: " + mountId);
        }
        TreeMap<String, Double> next = new TreeMap<>(checked.cooldownSecondsByMount());
        next.put(mountId, checkedLauncher.cycleTimeSeconds());
        return new RuntimeState(next);
    }

    /**
     * Advances all launcher cooldowns by one deterministic simulation interval.
     *
     * @param state current cooldown state
     * @param deltaSeconds positive simulation interval
     * @return next cooldown state
     */
    public RuntimeState advance(RuntimeState state, double deltaSeconds) {
        RuntimeState checked = Objects.requireNonNull(state, "state");
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        TreeMap<String, Double> next = new TreeMap<>();
        for (Map.Entry<String, Double> entry : checked.cooldownSecondsByMount().entrySet()) {
            next.put(entry.getKey(), canonicalZero(Math.max(0d, entry.getValue() - deltaSeconds)));
        }
        return new RuntimeState(next);
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) <= EPSILON ? 0d : value;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

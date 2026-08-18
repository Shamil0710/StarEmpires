package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;

import java.util.Objects;

/**
 * Central immutable Stage-19J presentation palette for tactical sides.
 *
 * <p>Palette values affect rendering only and never participate in combat, sensing, targeting or
 * persistence. ALPHA and BETA intentionally use both distinct color families and separate non-color
 * renderer cues so side identity does not depend on hue perception alone.</p>
 */
public final class TacticalSidePalette {
    /** Immutable normalized RGBA presentation color. */
    public record Rgba(float r, float g, float b, float a) {
        /**
         * Validates one normalized immutable color.
         *
         * @param r red channel in [0,1]
         * @param g green channel in [0,1]
         * @param b blue channel in [0,1]
         * @param a alpha channel in [0,1]
         */
        public Rgba {
            requireUnit(r, "r");
            requireUnit(g, "g");
            requireUnit(b, "b");
            requireUnit(a, "a");
        }
    }

    private static final Rgba ALPHA_FILL = new Rgba(0.16f, 0.50f, 0.68f, 1f);
    private static final Rgba ALPHA_OUTLINE = new Rgba(0.52f, 0.94f, 1.00f, 1f);
    private static final Rgba BETA_FILL = new Rgba(0.70f, 0.27f, 0.13f, 1f);
    private static final Rgba BETA_OUTLINE = new Rgba(1.00f, 0.66f, 0.28f, 1f);
    private static final Rgba NEUTRAL_FILL = new Rgba(0.58f, 0.66f, 0.74f, 1f);
    private static final Rgba NEUTRAL_OUTLINE = new Rgba(0.84f, 0.90f, 0.96f, 1f);

    private TacticalSidePalette() {
    }

    /**
     * Returns the darker readable hull fill for a projected side.
     *
     * @param side presentation-side identity
     * @return immutable fill color
     */
    public static Rgba fill(TacticalSide side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case ALPHA -> ALPHA_FILL;
            case BETA -> BETA_FILL;
            case NEUTRAL -> NEUTRAL_FILL;
        };
    }

    /**
     * Returns the bright high-contrast outline for a projected side.
     *
     * @param side presentation-side identity
     * @return immutable outline color
     */
    public static Rgba outline(TacticalSide side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case ALPHA -> ALPHA_OUTLINE;
            case BETA -> BETA_OUTLINE;
            case NEUTRAL -> NEUTRAL_OUTLINE;
        };
    }

    private static void requireUnit(float value, String label) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }
}

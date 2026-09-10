package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.Objects;

/** Package-local screen-space metrics shared by selection hit testing and selection overlays. */
final class TacticalShipMarkerMetrics {
    static final float MIN_SHIP_LENGTH_PX = 18f;
    static final float MIN_SHIP_WIDTH_PX = 11f;
    private static final float HIT_PADDING_PX = 5f;

    private TacticalShipMarkerMetrics() {
        throw new AssertionError("utility class");
    }

    static Bounds bounds(WorldMapLayout layout, ShipGlyph ship) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(ship, "ship");
        float baseLength = Math.max(MIN_SHIP_LENGTH_PX, screenLength(layout, ship.lengthM()));
        float baseWidth = Math.max(MIN_SHIP_WIDTH_PX, screenLength(layout, ship.widthM()));
        float length = baseLength;
        float width = baseWidth;
        float halfLength = length * 0.68f + HIT_PADDING_PX;
        float halfWidth = width * roleEnvelopeScale(ship.role()) * 0.5f + HIT_PADDING_PX;
        return new Bounds(halfLength, halfWidth);
    }

    private static float screenLength(WorldMapLayout layout, double worldLengthM) {
        if (!Double.isFinite(worldLengthM) || worldLengthM <= 0d) {
            return 0f;
        }
        return (float) (worldLengthM * layout.getScale());
    }

    private static float roleEnvelopeScale(ShipVisualRole role) {
        return switch (role) {
            case MISSILE -> 1.20f;
            case DEFENSIVE_EW -> 1.48f;
            case BEAM -> 1.08f;
            case KINETIC, BALANCED, UNCLASSIFIED -> 1f;
        };
    }

    record Bounds(float halfLengthPx, float halfWidthPx) {
        Bounds {
            if (!Float.isFinite(halfLengthPx) || halfLengthPx <= 0f
                    || !Float.isFinite(halfWidthPx) || halfWidthPx <= 0f) {
                throw new IllegalArgumentException("marker bounds must be finite and positive");
            }
        }

        float radiusPx() {
            return (float) Math.hypot(halfLengthPx, halfWidthPx);
        }
    }
}

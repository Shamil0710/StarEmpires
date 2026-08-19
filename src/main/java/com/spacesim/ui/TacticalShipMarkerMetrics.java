package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
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
        float length = baseLength * roleLengthScale(ship.role());
        float width = baseWidth * roleWidthScale(ship.role());
        float halfLength = length * 0.68f + HIT_PADDING_PX;
        float halfWidth = width * roleEnvelopeScale(ship.role()) * 0.5f + HIT_PADDING_PX;
        return new Bounds(halfLength, halfWidth);
    }

    private static float screenLength(WorldMapLayout layout, double worldLengthM) {
        if (!Double.isFinite(worldLengthM) || worldLengthM <= 0d) {
            return 0f;
        }
        Vector2 origin = new Vector2();
        Vector2 end = new Vector2();
        if (!layout.worldToScreen(0f, 0f, origin)
                || !layout.worldToScreen((float) worldLengthM, 0f, end)) {
            return 0f;
        }
        return Math.abs(end.x - origin.x);
    }

    private static float roleLengthScale(ShipVisualRole role) {
        return switch (role) {
            case KINETIC -> 1.18f;
            case MISSILE -> 0.96f;
            case BEAM -> 1.22f;
            case DEFENSIVE_EW -> 0.84f;
            case BALANCED, UNCLASSIFIED -> 1f;
        };
    }

    private static float roleWidthScale(ShipVisualRole role) {
        return switch (role) {
            case KINETIC -> 0.72f;
            case MISSILE -> 1.22f;
            case BEAM -> 0.74f;
            case DEFENSIVE_EW -> 1.34f;
            case BALANCED, UNCLASSIFIED -> 1f;
        };
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

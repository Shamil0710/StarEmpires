package com.spacesim.ship;

import java.util.OptionalDouble;

/**
 * Shared deterministic two-dimensional swept collision geometry for exact local tactical bodies.
 *
 * <p>The helper contains geometry only. It does not know factions, damage, weapons, rendering or AI.
 * Callers transform moving-body/moving-target motion into a relative segment and ask for the first
 * intersection against the target's axis-aligned hull-local footprint.</p>
 */
public final class TacticalCollisionGeometry {
    private static final double EPSILON = 1e-12d;

    private TacticalCollisionGeometry() {
        throw new AssertionError("TacticalCollisionGeometry does not create instances");
    }

    /**
     * Returns the first normalized time where a relative-motion segment enters an axis-aligned box.
     *
     * <p>The supplied segment is expressed in target-relative coordinates. Therefore a moving
     * projectile and moving target are handled by subtracting target start/end positions from the
     * corresponding projectile start/end positions before calling this method. A return value of
     * {@code 0} means the body already starts inside the footprint; {@code 1} means first contact at
     * the end of the interval.</p>
     *
     * @param relativeStartX body x relative to target at interval start
     * @param relativeStartY body y relative to target at interval start
     * @param relativeEndX body x relative to target at interval end
     * @param relativeEndY body y relative to target at interval end
     * @param halfLengthM positive target half-length along the world/local x axis
     * @param halfWidthM positive target half-width along the world/local y axis
     * @return first hit fraction in {@code [0,1]}, or empty when the swept segment misses
     */
    public static OptionalDouble firstSegmentAabbHitFraction(
            double relativeStartX,
            double relativeStartY,
            double relativeEndX,
            double relativeEndY,
            double halfLengthM,
            double halfWidthM) {
        requireFinite(relativeStartX, "relativeStartX");
        requireFinite(relativeStartY, "relativeStartY");
        requireFinite(relativeEndX, "relativeEndX");
        requireFinite(relativeEndY, "relativeEndY");
        requirePositiveFinite(halfLengthM, "halfLengthM");
        requirePositiveFinite(halfWidthM, "halfWidthM");

        double dx = relativeEndX - relativeStartX;
        double dy = relativeEndY - relativeStartY;
        double tEnter = 0d;
        double tExit = 1d;

        double[] xInterval = axisInterval(relativeStartX, dx, halfLengthM);
        if (xInterval == null) {
            return OptionalDouble.empty();
        }
        tEnter = Math.max(tEnter, xInterval[0]);
        tExit = Math.min(tExit, xInterval[1]);
        if (tEnter - tExit > EPSILON) {
            return OptionalDouble.empty();
        }

        double[] yInterval = axisInterval(relativeStartY, dy, halfWidthM);
        if (yInterval == null) {
            return OptionalDouble.empty();
        }
        tEnter = Math.max(tEnter, yInterval[0]);
        tExit = Math.min(tExit, yInterval[1]);
        if (tEnter - tExit > EPSILON || tExit < -EPSILON || tEnter > 1d + EPSILON) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.max(0d, Math.min(1d, tEnter)));
    }

    private static double[] axisInterval(double start, double delta, double halfExtent) {
        if (Math.abs(delta) <= EPSILON) {
            return Math.abs(start) <= halfExtent + EPSILON
                    ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
                    : null;
        }
        double first = (-halfExtent - start) / delta;
        double second = (halfExtent - start) / delta;
        return first <= second ? new double[]{first, second} : new double[]{second, first};
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}

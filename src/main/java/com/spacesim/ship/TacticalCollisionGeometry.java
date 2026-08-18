package com.spacesim.ship;

import java.util.OptionalDouble;

/**
 * Shared deterministic two-dimensional swept collision geometry for exact local tactical bodies.
 *
 * <p>The helper contains geometry only. It does not know factions, damage, weapons, rendering or AI.
 * Callers transform moving-body/moving-target motion into a relative segment and ask for the first
 * intersection against either a target hull footprint or a physical body radius.</p>
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

    /**
     * Returns first contact between two moving physical bodies represented by circumscribed radii.
     *
     * <p>Callers supply the first body's start/end position relative to the second body's start/end
     * position. The method therefore solves a swept segment against the origin-centered circle whose
     * radius is the sum of both bodies' positive physical radii. It contains no interception chance
     * and cannot delete or modify either body.</p>
     *
     * @param relativeStartX first-body x relative to second body at interval start
     * @param relativeStartY first-body y relative to second body at interval start
     * @param relativeEndX first-body x relative to second body at interval end
     * @param relativeEndY first-body y relative to second body at interval end
     * @param combinedRadiusM positive sum of physical body radii
     * @return first contact fraction in {@code [0,1]}, or empty when swept bodies miss
     */
    public static OptionalDouble firstSegmentCircleHitFraction(
            double relativeStartX,
            double relativeStartY,
            double relativeEndX,
            double relativeEndY,
            double combinedRadiusM) {
        requireFinite(relativeStartX, "relativeStartX");
        requireFinite(relativeStartY, "relativeStartY");
        requireFinite(relativeEndX, "relativeEndX");
        requireFinite(relativeEndY, "relativeEndY");
        requirePositiveFinite(combinedRadiusM, "combinedRadiusM");

        double radiusSquared = combinedRadiusM * combinedRadiusM;
        double startDistanceSquared = relativeStartX * relativeStartX + relativeStartY * relativeStartY;
        if (startDistanceSquared <= radiusSquared + EPSILON) {
            return OptionalDouble.of(0d);
        }

        double dx = relativeEndX - relativeStartX;
        double dy = relativeEndY - relativeStartY;
        double a = dx * dx + dy * dy;
        if (a <= EPSILON) {
            return OptionalDouble.empty();
        }
        double b = 2d * (relativeStartX * dx + relativeStartY * dy);
        double c = startDistanceSquared - radiusSquared;
        double discriminant = b * b - 4d * a * c;
        if (discriminant < -EPSILON) {
            return OptionalDouble.empty();
        }
        double root = Math.sqrt(Math.max(0d, discriminant));
        double first = (-b - root) / (2d * a);
        double second = (-b + root) / (2d * a);
        double best = Double.POSITIVE_INFINITY;
        if (first >= -EPSILON && first <= 1d + EPSILON) {
            best = Math.max(0d, Math.min(1d, first));
        }
        if (second >= -EPSILON && second <= 1d + EPSILON) {
            double candidate = Math.max(0d, Math.min(1d, second));
            if (candidate < best) {
                best = candidate;
            }
        }
        return Double.isFinite(best) ? OptionalDouble.of(best) : OptionalDouble.empty();
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

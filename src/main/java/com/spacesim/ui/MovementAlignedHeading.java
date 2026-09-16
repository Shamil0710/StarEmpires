package com.spacesim.ui;

/** Presentation-only heading resolver that points a ship's +X/nose axis along its velocity. */
final class MovementAlignedHeading {
    private static final double MIN_SPEED_MPS = 1.0e-6d;
    private static final double MIN_SPEED_SQUARED = MIN_SPEED_MPS * MIN_SPEED_MPS;

    private MovementAlignedHeading() {
        throw new AssertionError("No instances");
    }

    static double radians(double velocityXMps, double velocityYMps) {
        if (!Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)) {
            return 0d;
        }
        double speedSquared = velocityXMps * velocityXMps + velocityYMps * velocityYMps;
        if (!Double.isFinite(speedSquared) || speedSquared <= MIN_SPEED_SQUARED) {
            return 0d;
        }
        return Math.atan2(velocityYMps, velocityXMps);
    }

    static float degrees(double headingRad) {
        if (!Double.isFinite(headingRad)) {
            return 0f;
        }
        return (float) Math.toDegrees(headingRad);
    }
}

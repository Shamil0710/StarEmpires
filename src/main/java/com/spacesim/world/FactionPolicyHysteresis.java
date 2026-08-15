package com.spacesim.world;

/** Utility primitives for deterministic Stage-17F.6 policy deadbands and bounded adjustments. */
public final class FactionPolicyHysteresis {
    private FactionPolicyHysteresis() {
        throw new AssertionError("Utility class");
    }

    /**
     * Applies Schmitt-trigger hysteresis to one binary policy preference.
     *
     * <p>An inactive policy activates only at or above {@code enterThreshold}. An active policy
     * remains active until the signal falls to or below {@code exitThreshold}. Values inside the
     * deadband preserve the current decision.</p>
     *
     * @param active current policy state
     * @param signal measured non-negative signal
     * @param enterThreshold activation threshold
     * @param exitThreshold deactivation threshold, not greater than enter threshold
     * @return next stable state
     */
    public static boolean binaryDecision(
            boolean active,
            long signal,
            long enterThreshold,
            long exitThreshold) {
        if (signal < 0L || enterThreshold < 0L || exitThreshold < 0L) {
            throw new IllegalArgumentException("Policy hysteresis signal/thresholds cannot be negative");
        }
        if (exitThreshold > enterThreshold) {
            throw new IllegalArgumentException("Exit threshold cannot exceed enter threshold");
        }
        if (active) {
            return signal > exitThreshold;
        }
        return signal >= enterThreshold;
    }

    /**
     * Moves a basis-point policy toward a target by at most one bounded review step.
     *
     * @param currentBasisPoints current policy value in 0..10000
     * @param targetBasisPoints desired policy value in 0..10000
     * @param maxStepBasisPoints positive maximum adjustment per claimed review
     * @return bounded next value
     */
    public static int boundedBasisPointStep(
            int currentBasisPoints,
            int targetBasisPoints,
            int maxStepBasisPoints) {
        requireBasisPoints(currentBasisPoints, "Current policy");
        requireBasisPoints(targetBasisPoints, "Target policy");
        if (maxStepBasisPoints <= 0 || maxStepBasisPoints > 10_000) {
            throw new IllegalArgumentException("Maximum policy step must be in range 1..10000 bps");
        }
        if (currentBasisPoints == targetBasisPoints) {
            return currentBasisPoints;
        }
        if (currentBasisPoints < targetBasisPoints) {
            return Math.min(targetBasisPoints, currentBasisPoints + maxStepBasisPoints);
        }
        return Math.max(targetBasisPoints, currentBasisPoints - maxStepBasisPoints);
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000 bps");
        }
    }
}

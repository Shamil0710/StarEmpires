package com.spacesim.ship;

/**
 * Compact Stage-17.5D uncertainty state for a two-dimensional target track.
 *
 * <p>The initial implementation stores scalar position variance plus bearing/range variances rather
 * than pretending to have a full Kalman covariance matrix. The boundary is explicit so a later
 * matrix implementation can replace the representation without introducing scalar accuracy bonuses.</p>
 *
 * @param positionVarianceM2 Cartesian position variance when position is known, otherwise {@code null}
 * @param bearingVarianceRad2 bearing variance
 * @param rangeVarianceM2 range variance when range is known, otherwise {@code null}
 */
@SuppressWarnings("doclint:missing")
public record TrackCovariance(
        Double positionVarianceM2,
        double bearingVarianceRad2,
        Double rangeVarianceM2) {

    /** Validates explicit known/unknown covariance channels. */
    public TrackCovariance {
        requirePositive(bearingVarianceRad2, "bearingVarianceRad2");
        if (positionVarianceM2 != null) {
            requirePositive(positionVarianceM2, "positionVarianceM2");
        }
        if (rangeVarianceM2 != null) {
            requirePositive(rangeVarianceM2, "rangeVarianceM2");
        }
    }

    /** @return whether Cartesian position uncertainty is available */
    public boolean hasPositionCovariance() {
        return positionVarianceM2 != null;
    }

    /** @return whether range uncertainty is available */
    public boolean hasRangeCovariance() {
        return rangeVarianceM2 != null;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}

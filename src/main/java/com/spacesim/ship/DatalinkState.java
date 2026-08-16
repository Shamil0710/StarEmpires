package com.spacesim.ship;

/**
 * Stage-17.5D datalink delivery constraints for shared sensor measurements.
 *
 * <p>Datalink does not improve a track by aura. It changes which measurements have arrived, their
 * freshness and the covariance penalty accumulated before fusion.</p>
 *
 * @param latencySeconds deterministic end-to-end delivery latency
 * @param maxMeasurementAgeSeconds measurements older than this are not fused
 * @param transportVarianceM2PerSecond additional position-process variance accumulated per second of transport age
 */
public record DatalinkState(
        double latencySeconds,
        double maxMeasurementAgeSeconds,
        double transportVarianceM2PerSecond) {

    /**
     * Validates finite non-negative transport parameters.
     *
     * @param latencySeconds deterministic end-to-end delivery latency
     * @param maxMeasurementAgeSeconds measurements older than this are not fused
     * @param transportVarianceM2PerSecond additional position-process variance accumulated per second of transport age
     */
    public DatalinkState {
        requireNonNegative(latencySeconds, "latencySeconds");
        if (!Double.isFinite(maxMeasurementAgeSeconds) || maxMeasurementAgeSeconds <= 0d) {
            throw new IllegalArgumentException("maxMeasurementAgeSeconds must be finite and positive");
        }
        requireNonNegative(transportVarianceM2PerSecond, "transportVarianceM2PerSecond");
    }

    /** @return zero-latency local fusion policy */
    public static DatalinkState local() {
        return new DatalinkState(0d, 120d, 0d);
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

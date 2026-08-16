package com.spacesim.ship;

import com.spacesim.ship.SignatureState.Channel;

import java.util.Objects;

/**
 * One deterministic sensor measurement before track fusion.
 *
 * <p>A bearing-only measurement deliberately has {@code rangeM == null} and
 * {@code rangeVarianceM2 == null}; callers must not infer exact range merely because the contact was
 * detected or classified.</p>
 *
 * @param observerId stable observer identity value
 * @param targetId stable target identity value
 * @param channel signature channel
 * @param timestampSeconds authoritative measurement time
 * @param observerXM observer x position in meters
 * @param observerYM observer y position in meters
 * @param bearingRad measured bearing
 * @param rangeM measured range or {@code null} for bearing-only measurement
 * @param bearingVarianceRad2 one-sigma bearing variance
 * @param rangeVarianceM2 range variance or {@code null} when range is unknown
 * @param receivedSignalPowerW received target signal power
 * @param effectiveInterferencePowerW post-ECCM interference power
 * @param snr resulting signal-to-noise-plus-interference ratio
 * @param evidenceState strongest information-state evidence supplied by this measurement alone
 */
public record SensorMeasurement(
        long observerId,
        long targetId,
        Channel channel,
        double timestampSeconds,
        double observerXM,
        double observerYM,
        double bearingRad,
        Double rangeM,
        double bearingVarianceRad2,
        Double rangeVarianceM2,
        double receivedSignalPowerW,
        double effectiveInterferencePowerW,
        double snr,
        TrackState.InformationState evidenceState) {

    /**
     * Validates identity, geometry and covariance semantics.
     *
     * @param observerId stable observer identity value
     * @param targetId stable target identity value
     * @param channel signature channel
     * @param timestampSeconds authoritative measurement time
     * @param observerXM observer x position in meters
     * @param observerYM observer y position in meters
     * @param bearingRad measured bearing
     * @param rangeM measured range or {@code null} for bearing-only measurement
     * @param bearingVarianceRad2 one-sigma bearing variance
     * @param rangeVarianceM2 range variance or {@code null} when range is unknown
     * @param receivedSignalPowerW received target signal power
     * @param effectiveInterferencePowerW post-ECCM interference power
     * @param snr resulting signal-to-noise-plus-interference ratio
     * @param evidenceState strongest information-state evidence supplied by this measurement alone
     */
    public SensorMeasurement {
        if (observerId <= 0L || targetId <= 0L) {
            throw new IllegalArgumentException("observerId and targetId must be positive");
        }
        Objects.requireNonNull(channel, "measurement channel");
        Objects.requireNonNull(evidenceState, "measurement evidenceState");
        requireFinite(timestampSeconds, "timestampSeconds");
        requireFinite(observerXM, "observerXM");
        requireFinite(observerYM, "observerYM");
        requireFinite(bearingRad, "bearingRad");
        requirePositiveFinite(bearingVarianceRad2, "bearingVarianceRad2");
        requireNonNegativeFinite(receivedSignalPowerW, "receivedSignalPowerW");
        requireNonNegativeFinite(effectiveInterferencePowerW, "effectiveInterferencePowerW");
        requireNonNegativeFinite(snr, "snr");
        if ((rangeM == null) != (rangeVarianceM2 == null)) {
            throw new IllegalArgumentException("range and range variance must either both be present or both be absent");
        }
        if (rangeM != null) {
            requirePositiveFinite(rangeM, "rangeM");
            requirePositiveFinite(rangeVarianceM2, "rangeVarianceM2");
        }
    }

    /** @return true only when this measurement carries direct range information */
    public boolean hasRange() {
        return rangeM != null;
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

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

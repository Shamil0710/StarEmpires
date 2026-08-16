package com.spacesim.ship;

/**
 * Mutable-authoritative inputs that affect one sensor's current physical/processing capability.
 *
 * <p>Stage 17.5F damage will later derive aperture/processing degradation from subsystem damage.
 * Stage 17.5D consumes the physical fractions directly and never applies a generic accuracy debuff.</p>
 *
 * @param enabled whether the sensor is operating
 * @param eccmEnabled whether explicit ECCM processing is enabled
 * @param apertureFraction surviving/available aperture fraction in [0,1]
 * @param processingFraction available processing fraction in (0,1]
 */
@SuppressWarnings("doclint:missing")
public record SensorRuntimeState(
        boolean enabled,
        boolean eccmEnabled,
        double apertureFraction,
        double processingFraction) {

    /** Validates physical capability fractions. */
    public SensorRuntimeState {
        if (!Double.isFinite(apertureFraction) || apertureFraction < 0d || apertureFraction > 1d) {
            throw new IllegalArgumentException("apertureFraction must be finite in [0,1]");
        }
        if (!Double.isFinite(processingFraction) || processingFraction <= 0d || processingFraction > 1d) {
            throw new IllegalArgumentException("processingFraction must be finite in (0,1]");
        }
    }

    /** @return fully healthy enabled sensor with ECCM disabled */
    public static SensorRuntimeState nominal() {
        return new SensorRuntimeState(true, false, 1d, 1d);
    }
}

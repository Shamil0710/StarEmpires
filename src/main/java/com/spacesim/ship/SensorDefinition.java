package com.spacesim.ship;

import com.spacesim.ship.SignatureState.Channel;

import java.util.Objects;

/**
 * Immutable physical sensor definition for Stage 17.5D.
 *
 * <p>There is no authored hard detection range. Received signal is derived from source/reflection,
 * propagation geometry, aperture, receiver noise and interference. Thresholds are information-state
 * SNR requirements, not range walls.</p>
 *
 * @param id stable sensor definition ID
 * @param mode sensing mode
 * @param channel observed signature channel
 * @param apertureAreaM2 effective collecting aperture
 * @param receiverNoisePowerW receiver noise in the modeled observation band/dwell
 * @param detectionSnr minimum SNR for DETECTED evidence
 * @param classificationSnr minimum SNR for CLASSIFIED evidence
 * @param trackSnr minimum SNR for a range-capable measurement to support TRACKED evidence
 * @param fireControlSnr minimum SNR for a range-capable measurement to support FIRE_CONTROL evidence
 * @param bearingSigmaFloorRad best-case one-sigma bearing error floor
 * @param rangeSigmaFraction best-case fractional one-sigma range error for ranging modes
 * @param activeTransmitPowerW emitted radar power when active mode is enabled
 * @param transmitGainLinear directional transmit gain for active radar
 * @param eccmProcessingGainLinear interference rejection gain while ECCM is enabled
 * @param eccmPowerDemandW explicit additional electrical demand while ECCM is enabled
 */
public record SensorDefinition(
        String id,
        Mode mode,
        Channel channel,
        double apertureAreaM2,
        double receiverNoisePowerW,
        double detectionSnr,
        double classificationSnr,
        double trackSnr,
        double fireControlSnr,
        double bearingSigmaFloorRad,
        double rangeSigmaFraction,
        double activeTransmitPowerW,
        double transmitGainLinear,
        double eccmProcessingGainLinear,
        double eccmPowerDemandW) {

    /** Sensor measurement mode. */
    public enum Mode {
        /** Passive thermal-band observation. */ PASSIVE_THERMAL,
        /** Passive observation of engine-plume radiation. */ PASSIVE_PLUME,
        /** Passive reflected-light observation. */ PASSIVE_OPTICAL,
        /** Passive detection of active RF emissions. */ PASSIVE_RADIO,
        /** Active monostatic radar: emits, obtains bearing and range. */ ACTIVE_RADAR
    }

    /**
     * Validates monotonic thresholds and finite physical parameters.
     *
     * @param id stable sensor definition ID
     * @param mode sensing mode
     * @param channel observed signature channel
     * @param apertureAreaM2 effective collecting aperture
     * @param receiverNoisePowerW receiver noise in the modeled observation band/dwell
     * @param detectionSnr minimum SNR for DETECTED evidence
     * @param classificationSnr minimum SNR for CLASSIFIED evidence
     * @param trackSnr minimum SNR for a range-capable measurement to support TRACKED evidence
     * @param fireControlSnr minimum SNR for a range-capable measurement to support FIRE_CONTROL evidence
     * @param bearingSigmaFloorRad best-case one-sigma bearing error floor
     * @param rangeSigmaFraction best-case fractional one-sigma range error for ranging modes
     * @param activeTransmitPowerW emitted radar power when active mode is enabled
     * @param transmitGainLinear directional transmit gain for active radar
     * @param eccmProcessingGainLinear interference rejection gain while ECCM is enabled
     * @param eccmPowerDemandW explicit additional electrical demand while ECCM is enabled
     */
    public SensorDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("sensor id must be non-blank");
        }
        Objects.requireNonNull(mode, "sensor mode");
        Objects.requireNonNull(channel, "signature channel");
        requirePositive(apertureAreaM2, "apertureAreaM2");
        requirePositive(receiverNoisePowerW, "receiverNoisePowerW");
        requirePositive(detectionSnr, "detectionSnr");
        if (!Double.isFinite(classificationSnr) || classificationSnr < detectionSnr) {
            throw new IllegalArgumentException("classificationSnr must be finite and >= detectionSnr");
        }
        if (!Double.isFinite(trackSnr) || trackSnr < classificationSnr) {
            throw new IllegalArgumentException("trackSnr must be finite and >= classificationSnr");
        }
        if (!Double.isFinite(fireControlSnr) || fireControlSnr < trackSnr) {
            throw new IllegalArgumentException("fireControlSnr must be finite and >= trackSnr");
        }
        requirePositive(bearingSigmaFloorRad, "bearingSigmaFloorRad");
        requirePositive(rangeSigmaFraction, "rangeSigmaFraction");
        requireNonNegative(activeTransmitPowerW, "activeTransmitPowerW");
        requirePositive(transmitGainLinear, "transmitGainLinear");
        requirePositive(eccmProcessingGainLinear, "eccmProcessingGainLinear");
        requireNonNegative(eccmPowerDemandW, "eccmPowerDemandW");
        if (mode == Mode.ACTIVE_RADAR && activeTransmitPowerW <= 0d) {
            throw new IllegalArgumentException("ACTIVE_RADAR requires positive activeTransmitPowerW");
        }
        if (mode != Mode.ACTIVE_RADAR && activeTransmitPowerW != 0d) {
            throw new IllegalArgumentException("passive sensor modes must not author activeTransmitPowerW");
        }
        if (mode == Mode.ACTIVE_RADAR && channel != Channel.RADAR) {
            throw new IllegalArgumentException("ACTIVE_RADAR must observe RADAR channel");
        }
    }

    /** @return whether successful measurements carry direct range information */
    public boolean providesRange() {
        return mode == Mode.ACTIVE_RADAR;
    }

    /** @return emitted active-radar power when enabled */
    public double emittedPowerW() {
        return mode == Mode.ACTIVE_RADAR ? activeTransmitPowerW : 0d;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

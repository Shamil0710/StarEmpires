package com.spacesim.ship;

import java.util.List;
import java.util.Objects;

/**
 * Physical Stage-17.5D electronic-warfare state visible to the sensor measurement model.
 *
 * <p>Noise jammers contribute received interference through propagation geometry. Deception sources
 * are explicit hypotheses rather than a scalar accuracy penalty or random decoy chance.</p>
 *
 * @param noiseJammers active noise/interference emitters
 * @param deceptionSources explicit false-measurement hypotheses
 */
public record ElectronicWarfareState(
        List<NoiseJammer> noiseJammers,
        List<DeceptionSource> deceptionSources) {

    /**
     * Validates immutable non-null EW lists.
     *
     * @param noiseJammers active noise/interference emitters
     * @param deceptionSources explicit false-measurement hypotheses
     */
    public ElectronicWarfareState {
        noiseJammers = List.copyOf(Objects.requireNonNull(noiseJammers, "noiseJammers"));
        deceptionSources = List.copyOf(Objects.requireNonNull(deceptionSources, "deceptionSources"));
        if (noiseJammers.stream().anyMatch(Objects::isNull)
                || deceptionSources.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("EW lists must not contain null entries");
        }
    }

    /** @return empty EW environment */
    public static ElectronicWarfareState empty() {
        return new ElectronicWarfareState(List.of(), List.of());
    }

    /**
     * One active noise jammer.
     *
     * @param emitterId stable emitter identity value
     * @param xM emitter x position in meters
     * @param yM emitter y position in meters
     * @param radiatedPowerW radiated jammer power in the modeled band
     * @param gainLinear directional/effective gain toward the victim receiver
     * @param waveformOverlapFraction fraction [0,1] overlapping the sensor's current waveform/band
     */
    public record NoiseJammer(
            long emitterId,
            double xM,
            double yM,
            double radiatedPowerW,
            double gainLinear,
            double waveformOverlapFraction) {

        /**
         * Validates physical emitter parameters.
         *
         * @param emitterId stable emitter identity value
         * @param xM emitter x position in meters
         * @param yM emitter y position in meters
         * @param radiatedPowerW radiated jammer power in the modeled band
         * @param gainLinear directional/effective gain toward the victim receiver
         * @param waveformOverlapFraction fraction [0,1] overlapping the sensor's current waveform/band
         */
        public NoiseJammer {
            if (emitterId <= 0L) {
                throw new IllegalArgumentException("emitterId must be positive");
            }
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireNonNegative(radiatedPowerW, "radiatedPowerW");
            requirePositive(gainLinear, "gainLinear");
            if (!Double.isFinite(waveformOverlapFraction)
                    || waveformOverlapFraction < 0d || waveformOverlapFraction > 1d) {
                throw new IllegalArgumentException("waveformOverlapFraction must be finite in [0,1]");
            }
        }
    }

    /**
     * Explicit deceptive measurement hypothesis.
     *
     * @param emitterId stable emitter identity value
     * @param hypothesisId stable hypothesis label
     * @param apparentBearingBiasRad deterministic apparent bearing bias
     * @param apparentRangeBiasM deterministic apparent range bias; ignored for bearing-only sensors
     * @param receivedEquivalentPowerW equivalent received deceptive signal power before receiver noise
     */
    public record DeceptionSource(
            long emitterId,
            String hypothesisId,
            double apparentBearingBiasRad,
            double apparentRangeBiasM,
            double receivedEquivalentPowerW) {

        /**
         * Validates explicit hypothesis parameters.
         *
         * @param emitterId stable emitter identity value
         * @param hypothesisId stable hypothesis label
         * @param apparentBearingBiasRad deterministic apparent bearing bias
         * @param apparentRangeBiasM deterministic apparent range bias; ignored for bearing-only sensors
         * @param receivedEquivalentPowerW equivalent received deceptive signal power before receiver noise
         */
        public DeceptionSource {
            if (emitterId <= 0L) {
                throw new IllegalArgumentException("emitterId must be positive");
            }
            if (hypothesisId == null || hypothesisId.isBlank()) {
                throw new IllegalArgumentException("hypothesisId must be non-blank");
            }
            requireFinite(apparentBearingBiasRad, "apparentBearingBiasRad");
            requireFinite(apparentRangeBiasM, "apparentRangeBiasM");
            requireNonNegative(receivedEquivalentPowerW, "receivedEquivalentPowerW");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
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

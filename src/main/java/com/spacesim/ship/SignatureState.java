package com.spacesim.ship;

/**
 * Channelized physical signature state used by Stage 17.5D sensing.
 *
 * <p>This is deliberately not a scalar stealth score. Each channel has its own physical source or
 * reflection term and is propagated by the observing sensor model.</p>
 *
 * @param thermalRadiantPowerW thermal-band radiant power presented to the observer model
 * @param enginePlumeRadiantPowerW engine-plume radiant power in the observed band
 * @param radarCrossSectionM2 radar cross section for the current frequency/aspect/configuration sample
 * @param reflectedOpticalPowerW reflected optical power presented to the observer model
 * @param activeRadioEmissionPowerW active radar/communications emission power visible to passive RF sensors
 * @param jammerEmissionPowerW deliberate EW emission power visible to passive RF sensors
 */
@SuppressWarnings("doclint:missing")
public record SignatureState(
        double thermalRadiantPowerW,
        double enginePlumeRadiantPowerW,
        double radarCrossSectionM2,
        double reflectedOpticalPowerW,
        double activeRadioEmissionPowerW,
        double jammerEmissionPowerW) {

    /** Signature channels required by the Stage-17.5D contract plus passive observation of active RF. */
    public enum Channel {
        /** Passive thermal radiation. */ THERMAL,
        /** Hot/high-energy propulsion plume. */ ENGINE_PLUME,
        /** Radar reflection or active radio emission. */ RADAR,
        /** Reflected visible/near-visible optical energy. */ REFLECTED_OPTICAL
    }

    /** Validates finite non-negative channel values. */
    public SignatureState {
        requireNonNegativeFinite(thermalRadiantPowerW, "thermalRadiantPowerW");
        requireNonNegativeFinite(enginePlumeRadiantPowerW, "enginePlumeRadiantPowerW");
        requireNonNegativeFinite(radarCrossSectionM2, "radarCrossSectionM2");
        requireNonNegativeFinite(reflectedOpticalPowerW, "reflectedOpticalPowerW");
        requireNonNegativeFinite(activeRadioEmissionPowerW, "activeRadioEmissionPowerW");
        requireNonNegativeFinite(jammerEmissionPowerW, "jammerEmissionPowerW");
    }

    /** @return zero-emission/reflection state */
    public static SignatureState zero() {
        return new SignatureState(0d, 0d, 0d, 0d, 0d, 0d);
    }

    /**
     * Returns a copy with additional active RF emissions produced by operating sensors/EW.
     *
     * @param activeRadarPowerW additional radar emission
     * @param jammerPowerW additional jammer emission
     * @return updated signature state
     */
    public SignatureState withActiveRadioEmissions(double activeRadarPowerW, double jammerPowerW) {
        requireNonNegativeFinite(activeRadarPowerW, "activeRadarPowerW");
        requireNonNegativeFinite(jammerPowerW, "jammerPowerW");
        return new SignatureState(
                thermalRadiantPowerW,
                enginePlumeRadiantPowerW,
                radarCrossSectionM2,
                reflectedOpticalPowerW,
                activeRadioEmissionPowerW + activeRadarPowerW,
                jammerEmissionPowerW + jammerPowerW);
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

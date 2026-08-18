package com.spacesim.ship;

import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;

import java.util.Objects;
import java.util.Optional;

/**
 * Damage-aware Stage-19I projection from the common fitted engineering state to physical EW emitters.
 *
 * <p>The existing {@code jammer_w} signature contribution is already the authored radiated jammer
 * source power in the modeled band. This adapter does not create an EW score or range. For the current
 * content-provisional omnidirectional noise-jammer case that source is represented with unit effective
 * gain and full waveform overlap; propagation and receiver effects remain owned by
 * {@link ShipSensorRuntime}.</p>
 */
public final class ShipElectronicWarfareEngineeringAdapter {
    private final ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();

    /**
     * Projects one fitted ship into an active isotropic noise-jammer emitter when radiated power survives.
     *
     * @param emitterId stable physical ship identity
     * @param xM current emitter x coordinate
     * @param yM current emitter y coordinate
     * @param derivedState current damage-aware derived ship state
     * @return physical jammer emitter or empty when no jammer output survives
     */
    public Optional<NoiseJammer> deriveNoiseJammer(
            long emitterId,
            double xM,
            double yM,
            DerivedShipState derivedState) {
        if (emitterId <= 0L) {
            throw new IllegalArgumentException("emitterId must be positive");
        }
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        SignatureState signature = sensorAdapter.derive(
                Objects.requireNonNull(derivedState, "derivedState")).staticSignature();
        double radiatedPowerW = signature.jammerEmissionPowerW();
        if (radiatedPowerW <= 0d) {
            return Optional.empty();
        }
        return Optional.of(new NoiseJammer(
                emitterId,
                xM,
                yM,
                radiatedPowerW,
                1d,
                1d));
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}

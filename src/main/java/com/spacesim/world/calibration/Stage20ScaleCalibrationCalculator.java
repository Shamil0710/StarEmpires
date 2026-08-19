package com.spacesim.world.calibration;

import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.Objects;

/**
 * Converts authoritative ship-engineering output into Stage-20 spatial calibration measurements.
 *
 * <p>This class deliberately does not recalculate ship mass, thrust, mass flow or rocket-equation
 * delta-v. Those values remain owned by the Stage-17.5 engineering pipeline and arrive through
 * {@link DerivedShipState}. The only equations here describe a calibration manoeuvre: all available
 * reaction mass is split between maximum-thrust acceleration and braking so that both legs spend the
 * same delta-v and the ship finishes at rest.</p>
 */
public final class Stage20ScaleCalibrationCalculator {
    private Stage20ScaleCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives one deterministic local-propulsion calibration envelope.
     *
     * <p>For a constant-thrust drive with constant mass flow, equal acceleration/braking delta-v is
     * obtained by changing thrust direction at the geometric-mean mass
     * {@code sqrt(wetMass * dryAfterReactionMass)}. This preserves the already-authoritative total
     * delta-v while accounting for the changing ship mass during both burn legs.</p>
     *
     * @param sourceFitId stable engineering fit ID used to produce {@code state}
     * @param loadCaseId stable Stage-20 calibration load-case ID
     * @param state authoritative derived ship state
     * @return immutable propulsion envelope in SI units
     */
    public static RepresentativeShipPropulsionEnvelope derive(
            String sourceFitId,
            String loadCaseId,
            DerivedShipState state) {
        requireNonBlank(sourceFitId, "sourceFitId");
        requireNonBlank(loadCaseId, "loadCaseId");
        DerivedShipState checked = Objects.requireNonNull(state, "state");
        if (!checked.validation().isValid()) {
            throw new IllegalArgumentException("calibration requires a valid authoritative ship state");
        }

        double wetMassKg = requirePositiveFinite(checked.totalMassKg(), "totalMassKg");
        double reactionMassKg = requirePositiveFinite(checked.reactionMassKg(), "reactionMassKg");
        double thrustN = requirePositiveFinite(checked.availableThrustN(), "availableThrustN");
        double massFlowKgPerS = requirePositiveFinite(checked.massFlowKgPerS(), "massFlowKgPerS");
        double initialAccelerationMps2 = requirePositiveFinite(checked.accelerationMps2(), "accelerationMps2");
        double exhaustVelocityMps = requirePositiveFinite(
                checked.effectiveExhaustVelocityMps(), "effectiveExhaustVelocityMps");
        double deltaVMps = requirePositiveFinite(checked.deltaVMps(), "deltaVMps");

        double dryMassAfterReactionKg = wetMassKg - reactionMassKg;
        if (!(dryMassAfterReactionKg > 0d) || !Double.isFinite(dryMassAfterReactionKg)) {
            throw new IllegalArgumentException("reaction mass must be smaller than total ship mass");
        }

        double reactionMassFraction = reactionMassKg / wetMassKg;
        double terminalAccelerationMps2 = thrustN / dryMassAfterReactionKg;
        double midpointMassKg = Math.sqrt(wetMassKg * dryMassAfterReactionKg);
        double accelerationBurnDurationS = (wetMassKg - midpointMassKg) / massFlowKgPerS;
        double brakingBurnDurationS = (midpointMassKg - dryMassAfterReactionKg) / massFlowKgPerS;
        double fullBurnDurationS = reactionMassKg / massFlowKgPerS;
        double symmetricPeakSpeedMps = deltaVMps / 2d;

        double accelerationDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (wetMassKg - midpointMassKg
                + midpointMassKg * Math.log(midpointMassKg / wetMassKg));
        double brakingDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (midpointMassKg * Math.log(midpointMassKg / dryMassAfterReactionKg)
                - midpointMassKg + dryMassAfterReactionKg);
        double characteristicRestToRestDistanceM = accelerationDistanceM + brakingDistanceM;

        return new RepresentativeShipPropulsionEnvelope(
                sourceFitId,
                loadCaseId,
                wetMassKg,
                dryMassAfterReactionKg,
                reactionMassKg,
                reactionMassFraction,
                thrustN,
                massFlowKgPerS,
                initialAccelerationMps2,
                terminalAccelerationMps2,
                exhaustVelocityMps,
                deltaVMps,
                fullBurnDurationS,
                symmetricPeakSpeedMps,
                accelerationBurnDurationS,
                brakingBurnDurationS,
                accelerationDistanceM,
                brakingDistanceM,
                characteristicRestToRestDistanceM);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static double requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
        return value;
    }
}

package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.Objects;

/**
 * Derives deterministic rest-to-rest route timing from a Stage-20 propulsion envelope.
 *
 * <p>Short routes consume only the reaction mass required for an accelerate/flip/brake manoeuvre.
 * Longer routes use the full symmetric burn represented by the propulsion envelope and coast at the
 * resulting peak speed. The calculation preserves constant thrust, constant mass flow and changing
 * vehicle mass instead of falling back to a constant-acceleration map approximation.</p>
 */
public final class Stage20RouteCalibrationCalculator {
    private static final int PARTIAL_BURN_SOLVER_ITERATIONS = 160;

    private Stage20RouteCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /** Travel regimes emitted by the Stage-20 route calibration layer. */
    public enum TravelRegime {
        /** Route is closed by acceleration and braking without a coast segment. */
        ACCEL_BRAKE,
        /** Full symmetric burn is followed by a coast segment before braking. */
        ACCEL_COAST_BRAKE
    }

    /**
     * Derives one route sample in SI units.
     *
     * @param representativeId stable Stage-20 representative ID
     * @param propulsion representative propulsion envelope
     * @param distanceM rest-to-rest route distance
     * @return deterministic route sample
     */
    public static RouteTravelSample derive(
            String representativeId,
            RepresentativeShipPropulsionEnvelope propulsion,
            double distanceM) {
        requireNonBlank(representativeId, "representativeId");
        RepresentativeShipPropulsionEnvelope checked = Objects.requireNonNull(propulsion, "propulsion");
        requirePositiveFinite(distanceM, "distanceM");

        if (distanceM >= checked.characteristicRestToRestDistanceM()) {
            double coastDistanceM = distanceM - checked.characteristicRestToRestDistanceM();
            double coastTimeS = coastDistanceM / checked.symmetricPeakSpeedMps();
            return new RouteTravelSample(
                    representativeId,
                    distanceM,
                    coastDistanceM > 0d ? TravelRegime.ACCEL_COAST_BRAKE : TravelRegime.ACCEL_BRAKE,
                    checked.fullBurnDurationS() + coastTimeS,
                    coastTimeS,
                    checked.deltaVMps(),
                    checked.reactionMassKg(),
                    1d,
                    checked.symmetricPeakSpeedMps(),
                    checked.accelerationBurnDurationS(),
                    checked.brakingBurnDurationS(),
                    checked.accelerationDistanceM(),
                    checked.brakingDistanceM());
        }

        BurnGeometry partial = solvePartialBurn(checked, distanceM);
        double reactionMassConsumedKg = checked.wetMassKg() - partial.finalMassKg();
        return new RouteTravelSample(
                representativeId,
                distanceM,
                TravelRegime.ACCEL_BRAKE,
                partial.accelerationBurnDurationS() + partial.brakingBurnDurationS(),
                0d,
                partial.deltaVMps(),
                reactionMassConsumedKg,
                reactionMassConsumedKg / checked.reactionMassKg(),
                partial.peakSpeedMps(),
                partial.accelerationBurnDurationS(),
                partial.brakingBurnDurationS(),
                partial.accelerationDistanceM(),
                partial.brakingDistanceM());
    }

    private static BurnGeometry solvePartialBurn(
            RepresentativeShipPropulsionEnvelope propulsion,
            double targetDistanceM) {
        double lowFinalMassKg = propulsion.dryMassAfterReactionKg();
        double highFinalMassKg = propulsion.wetMassKg();
        for (int iteration = 0; iteration < PARTIAL_BURN_SOLVER_ITERATIONS; iteration++) {
            double candidateFinalMassKg = 0.5d * (lowFinalMassKg + highFinalMassKg);
            BurnGeometry candidate = geometryForFinalMass(propulsion, candidateFinalMassKg);
            if (candidate.totalDistanceM() > targetDistanceM) {
                lowFinalMassKg = candidateFinalMassKg;
            } else {
                highFinalMassKg = candidateFinalMassKg;
            }
        }
        return geometryForFinalMass(propulsion, 0.5d * (lowFinalMassKg + highFinalMassKg));
    }

    private static BurnGeometry geometryForFinalMass(
            RepresentativeShipPropulsionEnvelope propulsion,
            double finalMassKg) {
        double wetMassKg = propulsion.wetMassKg();
        double massFlowKgPerS = propulsion.massFlowKgPerS();
        double exhaustVelocityMps = propulsion.effectiveExhaustVelocityMps();
        double midpointMassKg = Math.sqrt(wetMassKg * finalMassKg);
        double deltaVMps = exhaustVelocityMps * Math.log(wetMassKg / finalMassKg);
        double peakSpeedMps = deltaVMps / 2d;
        double accelerationBurnDurationS = (wetMassKg - midpointMassKg) / massFlowKgPerS;
        double brakingBurnDurationS = (midpointMassKg - finalMassKg) / massFlowKgPerS;
        double accelerationDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (wetMassKg - midpointMassKg
                + midpointMassKg * Math.log(midpointMassKg / wetMassKg));
        double brakingDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (midpointMassKg * Math.log(midpointMassKg / finalMassKg)
                - midpointMassKg + finalMassKg);
        return new BurnGeometry(
                finalMassKg,
                deltaVMps,
                peakSpeedMps,
                accelerationBurnDurationS,
                brakingBurnDurationS,
                accelerationDistanceM,
                brakingDistanceM);
    }

    /**
     * One deterministic rest-to-rest travel measurement.
     *
     * @param representativeId stable representative ID
     * @param distanceM route distance
     * @param regime acceleration/coast/braking regime
     * @param totalTravelTimeS total rest-to-rest travel time
     * @param coastTimeS coast portion of travel time
     * @param requiredDeltaVMps delta-v consumed by the route manoeuvre
     * @param reactionMassConsumedKg reaction mass consumed by the route manoeuvre
     * @param reactionMassFractionConsumed fraction of the represented full reaction-mass load consumed
     * @param peakSpeedMps peak route speed
     * @param accelerationBurnDurationS acceleration burn duration
     * @param brakingBurnDurationS braking burn duration
     * @param accelerationDistanceM acceleration-leg distance
     * @param brakingDistanceM braking-leg distance
     */
    public record RouteTravelSample(
            String representativeId,
            double distanceM,
            TravelRegime regime,
            double totalTravelTimeS,
            double coastTimeS,
            double requiredDeltaVMps,
            double reactionMassConsumedKg,
            double reactionMassFractionConsumed,
            double peakSpeedMps,
            double accelerationBurnDurationS,
            double brakingBurnDurationS,
            double accelerationDistanceM,
            double brakingDistanceM) {
    }

    private record BurnGeometry(
            double finalMassKg,
            double deltaVMps,
            double peakSpeedMps,
            double accelerationBurnDurationS,
            double brakingBurnDurationS,
            double accelerationDistanceM,
            double brakingDistanceM) {
        double totalDistanceM() {
            return accelerationDistanceM + brakingDistanceM;
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}

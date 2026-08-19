package com.spacesim.world.calibration;

import java.util.List;
import java.util.Objects;

/**
 * Machine-readable Stage-20A.5 spatial calibration output for production weapon and defense runtimes.
 *
 * <p>All distances in this profile are deterministic probe geometries. They are evidence for later
 * world-generation scale decisions, not authoritative weapon ranges or generated-system boundaries.
 * Missing physical closure is recorded explicitly instead of being replaced by balance constants.</p>
 *
 * @param version stable calibration profile version
 * @param kineticSamples production fire-control/kinetic-body geometry samples
 * @param beamSamples production beam geometry/dwell samples
 * @param guidedSamples production guided-navigation samples
 * @param defenseSamples production layered-defense assignment samples
 * @param unresolvedConstraints physical closures not yet represented by production runtime
 */
public record Stage20WeaponSpatialCalibrationProfile(
        String version,
        List<KineticSample> kineticSamples,
        List<BeamSample> beamSamples,
        List<GuidedSample> guidedSamples,
        List<DefenseSample> defenseSamples,
        List<String> unresolvedConstraints) {
    /** Current Stage-20A.5 weapon/defense spatial calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.weapon-pd-spatial.v1";

    /** Creates one immutable validated profile. */
    public Stage20WeaponSpatialCalibrationProfile {
        requireNonBlank(version, "version");
        kineticSamples = immutable(kineticSamples, "kineticSamples");
        beamSamples = immutable(beamSamples, "beamSamples");
        guidedSamples = immutable(guidedSamples, "guidedSamples");
        defenseSamples = immutable(defenseSamples, "defenseSamples");
        unresolvedConstraints = immutableStrings(unresolvedConstraints, "unresolvedConstraints");
    }

    /**
     * One kinetic fire-control sensitivity probe.
     *
     * @param rangeM current target range
     * @param targetLateralVelocityMps controlled lateral-velocity probe input
     * @param velocitySigmaMps controlled one-sigma target-velocity uncertainty input
     * @param maneuverAccelerationMps2 controlled bounded target-maneuver input
     * @param allowed whether production fire control formed a physical intercept
     * @param timeOfFlightSeconds production constant-velocity intercept time
     * @param oneSigmaAimUncertaintyM propagated production uncertainty envelope
     * @param maneuverEnvelopeRadiusM production bounded maneuver displacement envelope
     * @param projectileKineticEnergyJ fitted projectile launch-frame kinetic energy
     * @param source production seam that owns the measurement
     */
    public record KineticSample(
            double rangeM,
            double targetLateralVelocityMps,
            double velocitySigmaMps,
            double maneuverAccelerationMps2,
            boolean allowed,
            double timeOfFlightSeconds,
            double oneSigmaAimUncertaintyM,
            double maneuverEnvelopeRadiusM,
            double projectileKineticEnergyJ,
            String source) {
        public KineticSample {
            requirePositiveFinite(rangeM, "rangeM");
            requireFinite(targetLateralVelocityMps, "targetLateralVelocityMps");
            requireNonNegativeFinite(velocitySigmaMps, "velocitySigmaMps");
            requireNonNegativeFinite(maneuverAccelerationMps2, "maneuverAccelerationMps2");
            requireNonNegativeFinite(timeOfFlightSeconds, "timeOfFlightSeconds");
            requireNonNegativeFinite(oneSigmaAimUncertaintyM, "oneSigmaAimUncertaintyM");
            requireNonNegativeFinite(maneuverEnvelopeRadiusM, "maneuverEnvelopeRadiusM");
            requirePositiveFinite(projectileKineticEnergyJ, "projectileKineticEnergyJ");
            requireNonBlank(source, "source");
            if (allowed && timeOfFlightSeconds <= 0d) {
                throw new IllegalArgumentException("allowed kinetic sample requires positive time of flight");
            }
        }
    }

    /** One production beam spot/dwell probe at a finite geometry sample. */
    public record BeamSample(
            double rangeM,
            boolean allowed,
            double dwellSeconds,
            double effectiveSpotRadiusM,
            double meanIrradianceWPerM2,
            double deliveredBeamEnergyJ,
            String source) {
        public BeamSample {
            requirePositiveFinite(rangeM, "rangeM");
            requirePositiveFinite(dwellSeconds, "dwellSeconds");
            requireNonNegativeFinite(effectiveSpotRadiusM, "effectiveSpotRadiusM");
            requireNonNegativeFinite(meanIrradianceWPerM2, "meanIrradianceWPerM2");
            requireNonNegativeFinite(deliveredBeamEnergyJ, "deliveredBeamEnergyJ");
            requireNonBlank(source, "source");
        }
    }

    /** One production lead-pursuit/propulsion probe for an authored guided body. */
    public record GuidedSample(
            String ammunitionId,
            double rangeM,
            double targetLateralVelocityMps,
            boolean guidanceAllowed,
            double predictedInterceptSeconds,
            double initialRemainingDeltaVMps,
            double terminalReserveMps,
            double commandedBurnSeconds,
            double propellantConsumedKg,
            String source) {
        public GuidedSample {
            requireNonBlank(ammunitionId, "ammunitionId");
            requirePositiveFinite(rangeM, "rangeM");
            requireFinite(targetLateralVelocityMps, "targetLateralVelocityMps");
            requireNonNegativeFinite(predictedInterceptSeconds, "predictedInterceptSeconds");
            requireNonNegativeFinite(initialRemainingDeltaVMps, "initialRemainingDeltaVMps");
            requireNonNegativeFinite(terminalReserveMps, "terminalReserveMps");
            requireNonNegativeFinite(commandedBurnSeconds, "commandedBurnSeconds");
            requireNonNegativeFinite(propellantConsumedKg, "propellantConsumedKg");
            requireNonBlank(source, "source");
            if (guidanceAllowed && commandedBurnSeconds <= 0d) {
                throw new IllegalArgumentException("allowed guided sample requires positive burn");
            }
        }
    }

    /** One production layered-defense assignment probe. */
    public record DefenseSample(
            double threatStartRangeM,
            double threatClosingSpeedMps,
            double safeMinimumInterceptDistanceM,
            boolean assigned,
            double predictedImpactSeconds,
            double plannedInterceptSeconds,
            double interceptDistanceFromProtectedCenterM,
            String source) {
        public DefenseSample {
            requirePositiveFinite(threatStartRangeM, "threatStartRangeM");
            requirePositiveFinite(threatClosingSpeedMps, "threatClosingSpeedMps");
            requireNonNegativeFinite(safeMinimumInterceptDistanceM, "safeMinimumInterceptDistanceM");
            requireNonNegativeFinite(predictedImpactSeconds, "predictedImpactSeconds");
            requireNonNegativeFinite(plannedInterceptSeconds, "plannedInterceptSeconds");
            requireNonNegativeFinite(interceptDistanceFromProtectedCenterM, "interceptDistanceFromProtectedCenterM");
            requireNonBlank(source, "source");
            if (assigned && (predictedImpactSeconds <= 0d || plannedInterceptSeconds <= 0d)) {
                throw new IllegalArgumentException("assigned defense sample requires positive timing");
            }
        }
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        return List.copyOf(values);
    }

    private static List<String> immutableStrings(List<String> values, String field) {
        List<String> copy = immutable(values, field);
        for (String value : copy) {
            requireNonBlank(value, field + " entry");
        }
        return copy;
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

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}

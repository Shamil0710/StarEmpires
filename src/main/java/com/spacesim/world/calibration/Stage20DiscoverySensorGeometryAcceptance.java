package com.spacesim.world.calibration;

import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.RepresentativeGroup;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.ThrustPolicy;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetClass;

import java.util.Objects;

/**
 * Stage-20G acceptance of a measurable physical detection-to-fire-control interval.
 *
 * <p>The acceptance derives every value from existing production/calibration authority. First
 * detection is the farthest physical channel threshold for the accepted bright capital reference;
 * classification/track/fire-control come from its production active-radar sample. Worst-case
 * closing speed is the maximum accepted military max-thrust local-route peak speed. The required
 * useful duration is the existing tracked-state freshness horizon, not a new fog/map constant.</p>
 */
public final class Stage20DiscoverySensorGeometryAcceptance {
    /** Stable Stage-20G physical sensor/world coupling acceptance version. */
    public static final String CURRENT_VERSION = "stage20g.sensor-consistent-visibility.v1";

    private Stage20DiscoverySensorGeometryAcceptance() {
        throw new AssertionError("No instances");
    }

    /** Final geometry acceptance status. */
    public enum Status {
        /** Physical geometry retains at least one existing track-freshness horizon before fire control. */ ACCEPTED,
        /** Current calibrated scale collapses the intermediate information phase too far. */ REJECTED_PROFILE
    }

    /**
     * Machine-readable representative bright-capital visibility result.
     *
     * @param version Stage-20G acceptance version
     * @param targetClass accepted representative target
     * @param targetCoverageVersion exact target-coverage profile
     * @param trackPolicyVersion exact sensor/track policy profile
     * @param routeCalibrationVersion exact physical route/speed profile
     * @param firstDetectionMaxDistanceM farthest physical first-detection threshold
     * @param activeClassificationMaxDistanceM active-radar classification threshold
     * @param activeTrackedMaxDistanceM active-radar tracked threshold
     * @param activeFireControlMaxDistanceM active-radar fire-control threshold
     * @param maximumRepresentativeClosingSpeedMps worst accepted military local-route peak speed
     * @param intermediateDurationSeconds first-detection to fire-control closing duration
     * @param minimumMeaningfulDurationSeconds existing tracked-state freshness horizon
     * @param status final physical scale decision
     */
    public record GeometryReport(
            String version,
            TargetClass targetClass,
            String targetCoverageVersion,
            String trackPolicyVersion,
            String routeCalibrationVersion,
            double firstDetectionMaxDistanceM,
            double activeClassificationMaxDistanceM,
            double activeTrackedMaxDistanceM,
            double activeFireControlMaxDistanceM,
            double maximumRepresentativeClosingSpeedMps,
            double intermediateDurationSeconds,
            double minimumMeaningfulDurationSeconds,
            Status status) {
        /**
         * Validates physical nesting, exact derivation and the reported decision.
         *
         * @param version acceptance version
         * @param targetClass representative target
         * @param targetCoverageVersion target coverage version
         * @param trackPolicyVersion track policy version
         * @param routeCalibrationVersion route calibration version
         * @param firstDetectionMaxDistanceM first-detection distance
         * @param activeClassificationMaxDistanceM classification distance
         * @param activeTrackedMaxDistanceM tracked distance
         * @param activeFireControlMaxDistanceM fire-control distance
         * @param maximumRepresentativeClosingSpeedMps worst representative closing speed
         * @param intermediateDurationSeconds derived intermediate duration
         * @param minimumMeaningfulDurationSeconds required existing freshness horizon
         * @param status final decision
         */
        public GeometryReport {
            requireText(version, "version");
            Objects.requireNonNull(targetClass, "targetClass");
            requireText(targetCoverageVersion, "targetCoverageVersion");
            requireText(trackPolicyVersion, "trackPolicyVersion");
            requireText(routeCalibrationVersion, "routeCalibrationVersion");
            requirePositive(firstDetectionMaxDistanceM, "firstDetectionMaxDistanceM");
            requirePositive(activeClassificationMaxDistanceM, "activeClassificationMaxDistanceM");
            requirePositive(activeTrackedMaxDistanceM, "activeTrackedMaxDistanceM");
            requirePositive(activeFireControlMaxDistanceM, "activeFireControlMaxDistanceM");
            requirePositive(maximumRepresentativeClosingSpeedMps, "maximumRepresentativeClosingSpeedMps");
            requirePositive(intermediateDurationSeconds, "intermediateDurationSeconds");
            requirePositive(minimumMeaningfulDurationSeconds, "minimumMeaningfulDurationSeconds");
            Objects.requireNonNull(status, "status");
            if (firstDetectionMaxDistanceM < activeClassificationMaxDistanceM
                    || activeClassificationMaxDistanceM < activeTrackedMaxDistanceM
                    || activeTrackedMaxDistanceM < activeFireControlMaxDistanceM) {
                throw new IllegalArgumentException("sensor information-state distances must remain nested");
            }
            double expectedDuration = (firstDetectionMaxDistanceM - activeFireControlMaxDistanceM)
                    / maximumRepresentativeClosingSpeedMps;
            double tolerance = Math.max(1.0e-9d, expectedDuration * 1.0e-12d);
            if (Math.abs(expectedDuration - intermediateDurationSeconds) > tolerance) {
                throw new IllegalArgumentException("intermediate duration must derive from physical distance/speed");
            }
            Status expected = intermediateDurationSeconds >= minimumMeaningfulDurationSeconds
                    ? Status.ACCEPTED
                    : Status.REJECTED_PROFILE;
            if (status != expected) {
                throw new IllegalArgumentException("geometry status differs from the calibrated duration decision");
            }
        }

        /** @return whether current generated/sensor scale preserves a meaningful intermediate phase */
        public boolean accepted() {
            return status == Status.ACCEPTED;
        }
    }

    /**
     * Derives current Stage-20G acceptance for the accepted bright battleship reference.
     *
     * @return deterministic machine-readable sensor/world coupling result
     */
    public static GeometryReport deriveCurrent() {
        Stage20SensorTargetClassCoverageProfile targets =
                Stage20SensorTargetClassCoverageProfile.deriveCurrent();
        Stage20SensorCalibrationProfile sensor = Stage20SensorCalibrationProfile.deriveCurrent();
        Stage20LocalRouteSemanticCalibrationProfile routes =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        if (!targets.closesStage20BEntryCoverage() || !routes.closesStage20BEntryCoverage()) {
            throw new IllegalStateException("Stage-20G requires closed Stage-20A sensor and route profiles");
        }

        TargetClass targetClass = TargetClass.BATTLESHIP;
        var passive = targets.sample(targetClass, Mode.PASSIVE_THERMAL).thresholds();
        var active = targets.sample(targetClass, Mode.ACTIVE_RADAR).thresholds();
        double firstDetection = Math.max(
                passive.detectedMaxDistanceM().orElseThrow(),
                active.detectedMaxDistanceM().orElseThrow());
        double classification = active.classifiedMaxDistanceM().orElseThrow();
        double tracked = active.trackedMaxDistanceM().orElseThrow();
        double fireControl = active.fireControlMaxDistanceM().orElseThrow();
        double maximumClosingSpeed = routes.samples().stream()
                .filter(value -> value.representativeGroup() == RepresentativeGroup.MILITARY)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.MAX_THRUST_RESPONSE)
                .mapToDouble(Stage20LocalRouteSemanticCalibrationProfile.SemanticRouteSample::peakSpeedMps)
                .max()
                .orElseThrow();
        double duration = (firstDetection - fireControl) / maximumClosingSpeed;
        double minimumDuration = sensor.trackPolicy().trackedMaxAgeSeconds();
        Status status = duration >= minimumDuration ? Status.ACCEPTED : Status.REJECTED_PROFILE;
        return new GeometryReport(
                CURRENT_VERSION,
                targetClass,
                targets.version(),
                sensor.version(),
                routes.version(),
                firstDetection,
                classification,
                tracked,
                fireControl,
                maximumClosingSpeed,
                duration,
                minimumDuration,
                status);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}

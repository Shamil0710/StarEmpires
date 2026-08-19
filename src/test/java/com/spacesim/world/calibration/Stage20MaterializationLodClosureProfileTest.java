package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RelevanceInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MaterializationLodClosureProfileTest {
    @Test
    void closureSupersedesButDoesNotRewriteHistoricalUnresolvedProfile() {
        Stage20MaterializationLodCalibrationProfile historical =
                Stage20MaterializationLodCalibrationCalculator.calibrate();
        Stage20MaterializationLodClosureProfile closure =
                Stage20MaterializationLodClosureProfile.deriveCurrent();

        assertEquals(Stage20MaterializationLodCalibrationProfile.CURRENT_VERSION, historical.version());
        assertEquals(2, historical.currentDistanceBandClosures().size());
        assertTrue(historical.currentDistanceBandClosures().stream()
                .allMatch(value -> value.authority() == DistanceBandAuthority.UNRESOLVED
                        && value.activationDistanceM().isEmpty()));

        assertEquals(Stage20MaterializationLodClosureProfile.CURRENT_VERSION, closure.version());
        assertEquals(historical.version(), closure.historicalProfileVersion());
        assertEquals(2, closure.distanceBands().size());
        assertEquals(
                Set.of(RepresentationLevel.ACTIVE_LOCAL, RepresentationLevel.TACTICAL),
                closure.distanceBands().stream().map(value -> value.level()).collect(Collectors.toSet()));
        assertTrue(closure.distanceBands().stream().allMatch(value ->
                value.authority() == DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT
                        && value.activationDistanceM().isPresent()));
        assertTrue(closure.closesStage20BEntryCoverage());
    }

    @Test
    void activeLocalAndTacticalBandsAreDerivedFromAcceptedPhysicalSources() {
        Stage20MaterializationLodClosureProfile closure =
                Stage20MaterializationLodClosureProfile.deriveCurrent();
        Stage20MajorInfrastructureExtentCalibrationProfile infrastructure =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        Stage20StationPhysicalGeometryProfile stationPhysical =
                Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        double expectedStationOperational = stationPhysical.placementEnvelopes().stream()
                .mapToDouble(StationPlacementEnvelope::operationalRadiusM)
                .max()
                .orElseThrow();
        double expectedDefensive = stationDefensive.stations().stream()
                .mapToDouble(Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry::defensiveExclusionReferenceM)
                .max()
                .orElseThrow();
        double expectedTactical = Math.max(expectedStationOperational, expectedDefensive);

        assertEquals(
                infrastructure.maximumMajorInfrastructureExtentM(),
                closure.activationDistanceM(RepresentationLevel.ACTIVE_LOCAL),
                0d);
        assertEquals(
                expectedTactical,
                closure.activationDistanceM(RepresentationLevel.TACTICAL),
                0d);
        assertEquals(1_000_000_000d, closure.activationDistanceM(RepresentationLevel.ACTIVE_LOCAL), 0d);
        assertTrue(closure.activationDistanceM(RepresentationLevel.ACTIVE_LOCAL)
                >= closure.activationDistanceM(RepresentationLevel.TACTICAL));
        assertEquals(0d, closure.wakeLatencySimulationSeconds(), 0d);
    }

    @Test
    void farSensorDetectionDoesNotBecomeAUniversalTacticalRadius() {
        Stage20MaterializationLodClosureProfile closure =
                Stage20MaterializationLodClosureProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        double maximumPassiveDetection = stationDefensive.stations().stream()
                .mapToDouble(Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry::passiveDetectionWarningM)
                .max()
                .orElseThrow();
        assertTrue(maximumPassiveDetection > closure.activationDistanceM(RepresentationLevel.TACTICAL));
        assertTrue(closure.distanceBands().stream()
                .filter(value -> value.level() == RepresentationLevel.TACTICAL)
                .findFirst().orElseThrow()
                .provenance().contains("not_sensor_detection_wall"));
    }

    @Test
    void distanceAndRenderingCannotSuppressAuthoritativeRelevance() {
        Stage20MaterializationLodClosureProfile closure =
                Stage20MaterializationLodClosureProfile.deriveCurrent();

        assertTrue(closure.authoritativeStateRetained());
        assertFalse(closure.distanceCanSuppressDirectRelevance());
        assertFalse(closure.renderBoundary());
        assertFalse(closure.worldBoundary());

        assertEquals(
                RepresentationLevel.TACTICAL,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(true, false, false, false)));
        assertEquals(
                RepresentationLevel.ACTIVE_LOCAL,
                Stage20MaterializationLodCalibrationCalculator.requiredRepresentation(
                        new RelevanceInput(false, true, false, false)));
        Stage20MaterializationLodCalibrationProfile.RenderCullingDecision culled =
                Stage20MaterializationLodCalibrationCalculator.decideRendering(
                        new RelevanceInput(true, false, false, false),
                        false);
        assertEquals(RepresentationLevel.TACTICAL, culled.representationLevel());
        assertFalse(culled.rendered());
        assertTrue(culled.authoritativeStateRetained());
    }
}

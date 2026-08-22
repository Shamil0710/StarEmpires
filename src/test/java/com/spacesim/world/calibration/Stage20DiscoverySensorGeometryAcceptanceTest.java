package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20DiscoverySensorGeometryAcceptance.GeometryReport;
import com.spacesim.world.calibration.Stage20DiscoverySensorGeometryAcceptance.Status;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20DiscoverySensorGeometryAcceptanceTest {
    @Test
    void brightCapitalContactRetainsMeaningfulPhysicalInformationPhase() {
        GeometryReport first = Stage20DiscoverySensorGeometryAcceptance.deriveCurrent();
        GeometryReport second = Stage20DiscoverySensorGeometryAcceptance.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20DiscoverySensorGeometryAcceptance.CURRENT_VERSION, first.version());
        assertEquals(TargetClass.BATTLESHIP, first.targetClass());
        assertEquals(Status.ACCEPTED, first.status());
        assertTrue(first.accepted());
        assertTrue(first.firstDetectionMaxDistanceM() > first.activeClassificationMaxDistanceM());
        assertTrue(first.activeClassificationMaxDistanceM() >= first.activeTrackedMaxDistanceM());
        assertTrue(first.activeTrackedMaxDistanceM() >= first.activeFireControlMaxDistanceM());
        assertTrue(first.intermediateDurationSeconds() >= first.minimumMeaningfulDurationSeconds());
        assertEquals(
                (first.firstDetectionMaxDistanceM() - first.activeFireControlMaxDistanceM())
                        / first.maximumRepresentativeClosingSpeedMps(),
                first.intermediateDurationSeconds(),
                Math.max(1.0e-9d, first.intermediateDurationSeconds() * 1.0e-12d));
    }

    @Test
    void reportRejectsScreenScaleOrHandAuthoredDurationSubstitution() {
        GeometryReport accepted = Stage20DiscoverySensorGeometryAcceptance.deriveCurrent();

        assertThrows(IllegalArgumentException.class, () -> new GeometryReport(
                accepted.version(),
                accepted.targetClass(),
                accepted.targetCoverageVersion(),
                accepted.trackPolicyVersion(),
                accepted.routeCalibrationVersion(),
                accepted.firstDetectionMaxDistanceM(),
                accepted.activeClassificationMaxDistanceM(),
                accepted.activeTrackedMaxDistanceM(),
                accepted.activeFireControlMaxDistanceM(),
                accepted.maximumRepresentativeClosingSpeedMps(),
                accepted.intermediateDurationSeconds() * 0.5d,
                accepted.minimumMeaningfulDurationSeconds(),
                Status.ACCEPTED));
    }
}

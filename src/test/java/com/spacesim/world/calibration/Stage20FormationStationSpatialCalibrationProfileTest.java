package com.spacesim.world.calibration;

import com.spacesim.ship.TacticalFormationPlanner;
import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.FormationProbeSample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.ShipyardBerthSample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationGeometrySample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementGeometryInput;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FormationStationSpatialCalibrationProfileTest {
    @Test
    void currentCalibrationIsDeterministicAndKeepsStationGeometryGapsVisible() {
        Stage20FormationStationSpatialCalibrationProfile first =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        Stage20FormationStationSpatialCalibrationProfile second =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();

        assertEquals(first, second);
        assertEquals(Stage20FormationStationSpatialCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(3, first.formationSamples().size());
        assertEquals(8, first.stationGeometrySamples().size());
        assertEquals(1, first.shipyardBerthSamples().size());
        assertFalse(first.unresolvedConstraints().isEmpty());
        assertTrue(first.stationGeometrySamples().stream().noneMatch(StationGeometrySample::placementReady));
        assertTrue(first.stationGeometrySamples().stream().allMatch(value -> value.footprintLengthM().isEmpty()));
        assertTrue(first.stationGeometrySamples().stream().allMatch(value -> value.footprintWidthM().isEmpty()));
        assertTrue(first.stationGeometrySamples().stream().allMatch(value -> !value.unresolvedReasons().isEmpty()));
    }

    @Test
    void stage19ProbeSpansRemainAuthoredEvidenceRatherThanOneGlobalSpacing() {
        List<FormationProbeSample> samples = Stage20FormationStationSpatialCalibrationCalculator.calibrate()
                .formationSamples().stream()
                .sorted(Comparator.comparing(FormationProbeSample::probeId))
                .toList();

        FormationProbeSample compact4 = sample(samples, "stage19.compact_4v4");
        FormationProbeSample dispersed4 = sample(samples, "stage19.dispersed_4v4");
        FormationProbeSample compact16 = sample(samples, "stage19.compact_16_ship_side");

        assertEquals(360d, compact4.lineSpanM());
        assertEquals(720d, dispersed4.lineSpanM());
        assertEquals(1_500d, compact16.lineSpanM());
        assertTrue(dispersed4.lineSpanM() > compact4.lineSpanM());
        assertTrue(compact16.lineSpanM() > compact4.lineSpanM());
        assertTrue(samples.stream().allMatch(value -> value.source().contains("stage19i_l_tactical_formation")));
    }

    @Test
    void formationRecoveryEvidenceRespondsToPhysicalAccelerationAndSlotGeometry() {
        TacticalFormationPlanner.Objective objective =
                new TacticalFormationPlanner.Objective(FormationMode.COMPACT, 0d, 120d, 5d, 80d);
        FormationProbeSample fast = Stage20FormationStationSpatialCalibrationCalculator.deriveFormationProbe(
                "probe.fast", objective, 4, 2d, "test.explicit_physics");
        FormationProbeSample slow = Stage20FormationStationSpatialCalibrationCalculator.deriveFormationProbe(
                "probe.slow", objective, 4, 0.5d, "test.explicit_physics");
        FormationProbeSample widerRoster = Stage20FormationStationSpatialCalibrationCalculator.deriveFormationProbe(
                "probe.wider", objective, 8, 2d, "test.explicit_physics");

        assertEquals(75d, fast.recoveryDistanceToToleranceM());
        assertTrue(slow.idealRestToToleranceRecoveryTimeS() > fast.idealRestToToleranceRecoveryTimeS());
        assertEquals(fast.idealRestToToleranceRecoveryTimeS() * 2d, slow.idealRestToToleranceRecoveryTimeS(), 1e-9d);
        assertTrue(widerRoster.lineSpanM() > fast.lineSpanM());
    }

    @Test
    void productionShipyardBerthIsPhysicalEvidenceButNotPromotedToStationFootprint() {
        Stage20FormationStationSpatialCalibrationProfile profile =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        ShipyardBerthSample berth = profile.shipyardBerthSamples().get(0);

        assertEquals("yard.orbital_escort_v1", berth.yardId());
        assertEquals(300d, berth.berthLengthM());
        assertEquals(120d, berth.berthWidthM());
        assertEquals(70d, berth.berthHeightM());
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("berth_envelope_is_not_station_footprint")));
    }

    @Test
    void explicitPhysicalStationGeometryChangesPlacementEvidenceWithoutCapacityFallback() {
        StationPlacementGeometryInput baseline = new StationPlacementGeometryInput(
                "station.infrastructure.test_explicit",
                "accepted.explicit.geometry.v1",
                200d,
                100d,
                50d,
                30d);
        StationPlacementGeometryInput largerFootprint = new StationPlacementGeometryInput(
                "station.infrastructure.test_explicit",
                "accepted.explicit.geometry.v2",
                400d,
                100d,
                50d,
                30d);
        StationPlacementGeometryInput largerTrafficClearance = new StationPlacementGeometryInput(
                "station.infrastructure.test_explicit",
                "accepted.explicit.geometry.v3",
                200d,
                100d,
                50d,
                90d);

        StationPlacementEnvelope base =
                Stage20FormationStationSpatialCalibrationCalculator.deriveStationPlacementEnvelope(baseline);
        StationPlacementEnvelope footprint =
                Stage20FormationStationSpatialCalibrationCalculator.deriveStationPlacementEnvelope(largerFootprint);
        StationPlacementEnvelope traffic =
                Stage20FormationStationSpatialCalibrationCalculator.deriveStationPlacementEnvelope(largerTrafficClearance);

        assertTrue(footprint.sameClassMinimumCenterSeparationM() > base.sameClassMinimumCenterSeparationM());
        assertTrue(traffic.sameClassMinimumCenterSeparationM() > base.sameClassMinimumCenterSeparationM());
        assertEquals(50d, base.operationalClearanceM());
        assertEquals(90d, traffic.operationalClearanceM());
    }

    private static FormationProbeSample sample(List<FormationProbeSample> samples, String id) {
        return samples.stream()
                .filter(value -> id.equals(value.probeId()))
                .findFirst()
                .orElseThrow();
    }
}

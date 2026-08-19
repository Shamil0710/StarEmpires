package com.spacesim.world.calibration;

import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.ArrivalSpatialAuthority;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.DerivedStandOffEnvelope;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.StandOffGeometryInput;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20JumpArrivalSpatialCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndDoesNotPromoteLegacyViewportAnchor() {
        Stage20JumpArrivalSpatialCalibrationProfile first =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Stage20JumpArrivalSpatialCalibrationProfile second =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();

        assertEquals(first, second);
        assertEquals(Stage20JumpArrivalSpatialCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals("NEIGHBOR_EDGE_ONLY", first.runtimeArrivalPolicy().topologySemantics());
        assertTrue(first.runtimeArrivalPolicy().explicitCoordinatesPreserved());
        assertTrue(first.runtimeArrivalPolicy().legacyZeroPairResolved());
        assertEquals(LocalSystemCoordinates.ARRIVAL_X, first.runtimeArrivalPolicy().legacyArrivalX());
        assertEquals(LocalSystemCoordinates.ARRIVAL_Y, first.runtimeArrivalPolicy().legacyArrivalY());
        assertEquals(
                ArrivalSpatialAuthority.LEGACY_BOUNDED_VIEWPORT_COMPATIBILITY,
                first.runtimeArrivalPolicy().legacyAnchorAuthority());
        assertEquals(0d, first.runtimeArrivalPolicy().arrivalVelocityMps());
        assertTrue(first.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("legacy_viewport_arrival_anchor")));
    }

    @Test
    void currentRuntimeZeroSpeedProducesZeroPostJumpBrakingForEveryCurrentRepresentative() {
        Stage20JumpArrivalSpatialCalibrationProfile profile =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();

        assertEquals(scale.representativeShips().size(), profile.representativeArrivalSamples().size());
        assertEquals(
                scale.representativeShips().stream()
                        .map(Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope::representativeId)
                        .collect(Collectors.toSet()),
                profile.representativeArrivalSamples().stream()
                        .map(Stage20JumpArrivalSpatialCalibrationProfile.RepresentativeArrivalSample::representativeId)
                        .collect(Collectors.toSet()));
        assertEquals(9, profile.representativeArrivalSamples().size());
        assertTrue(profile.representativeArrivalSamples().stream()
                .allMatch(value -> value.arrivalSpeedMps() == 0d));
        assertTrue(profile.representativeArrivalSamples().stream()
                .allMatch(value -> value.brakingDistanceM() == 0d));
        assertTrue(profile.representativeArrivalSamples().stream()
                .allMatch(value -> value.accelerationMps2() > 0d));
    }

    @Test
    void allEightStationStandOffsAreDerivedFromAcceptedPhysicalAndExclusionReferences() {
        Stage20JumpArrivalSpatialCalibrationProfile profile =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Stage20StationPhysicalGeometryProfile physical = Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile defensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        assertEquals(8, profile.stationStandOffSamples().size());
        assertTrue(profile.stationStandOffSamples().stream()
                .allMatch(value -> value.authority() == ArrivalSpatialAuthority.PROVISIONAL_STAGE20_DESIGN_REFERENCE));
        assertTrue(profile.stationStandOffSamples().stream().allMatch(value -> value.centerStandOffM().isPresent()));
        assertTrue(profile.stationStandOffSamples().stream().allMatch(value -> value.unresolvedReasons().isEmpty()));
        assertTrue(profile.stationStandOffSamples().stream()
                .allMatch(value -> value.provenance().contains(Stage20StationPhysicalGeometryProfile.CURRENT_VERSION)));
        assertTrue(profile.stationStandOffSamples().stream()
                .allMatch(value -> value.provenance().contains(Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION)));

        for (var sample : profile.stationStandOffSamples()) {
            double operationalRadius = physical.placementEnvelope(sample.stationArchetypeId()).operationalRadiusM();
            double exclusion = defensive.station(sample.stationArchetypeId()).defensiveExclusionReferenceM();
            assertEquals(Math.max(operationalRadius, exclusion), sample.centerStandOffM().orElseThrow(), 1e-9d);
        }
    }

    @Test
    void tacticalProbeRangesRemainSeparateFromDerivedStationStandOffs() {
        Stage20JumpArrivalSpatialCalibrationProfile profile =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();

        assertEquals(10_000_000d, profile.tacticalResponseEvidence().maxDirectFireProbeRangeM());
        assertEquals(30_000_000d, profile.tacticalResponseEvidence().maxBeamProbeRangeM());
        assertEquals(1_000_000d, profile.tacticalResponseEvidence().maxGuidedProbeRangeM());
        assertTrue(profile.tacticalResponseEvidence().maxAssignedDefenseInterceptDistanceM() > 0d);
        assertEquals(
                ArrivalSpatialAuthority.PROVISIONAL_CALIBRATION_PROBE,
                profile.tacticalResponseEvidence().authority());
        assertTrue(profile.stationStandOffSamples().stream()
                .allMatch(value -> !value.provenance().contains("Stage20WeaponSpatialCalibrationProfile")));
        assertFalse(profile.stationStandOffSamples().stream()
                .anyMatch(value -> value.centerStandOffM().orElseThrow()
                        == profile.tacticalResponseEvidence().maxDirectFireProbeRangeM()));
    }

    @Test
    void explicitPhysicalInputsChangeDerivedStandOffWithoutUniversalJumpRadius() {
        StandOffGeometryInput baseline = new StandOffGeometryInput(
                "station.explicit",
                "accepted.explicit.geometry.v1",
                1_000d,
                500d,
                2_000d,
                100d,
                2d);
        StandOffGeometryInput fasterArrival = new StandOffGeometryInput(
                "station.explicit",
                "accepted.explicit.geometry.v2",
                1_000d,
                500d,
                2_000d,
                200d,
                2d);
        StandOffGeometryInput strongerDefense = new StandOffGeometryInput(
                "station.explicit",
                "accepted.explicit.geometry.v3",
                1_000d,
                500d,
                20_000d,
                100d,
                2d);

        DerivedStandOffEnvelope base = Stage20JumpArrivalSpatialCalibrationCalculator.deriveStandOff(baseline);
        DerivedStandOffEnvelope fast = Stage20JumpArrivalSpatialCalibrationCalculator.deriveStandOff(fasterArrival);
        DerivedStandOffEnvelope defense = Stage20JumpArrivalSpatialCalibrationCalculator.deriveStandOff(strongerDefense);

        assertEquals(2_500d, base.brakingDistanceM());
        assertEquals(3_500d, base.requiredCenterStandOffM());
        assertTrue(fast.requiredCenterStandOffM() > base.requiredCenterStandOffM());
        assertEquals(20_000d, defense.requiredCenterStandOffM());
    }

    @Test
    void brakingEvidenceRespondsToPhysicalAcceleration() {
        double slowBraking = Stage20JumpArrivalSpatialCalibrationCalculator.brakingDistance(1_000d, 1d);
        double fastBraking = Stage20JumpArrivalSpatialCalibrationCalculator.brakingDistance(1_000d, 4d);

        assertEquals(500_000d, slowBraking);
        assertEquals(125_000d, fastBraking);
        assertTrue(slowBraking > fastBraking);
    }
}

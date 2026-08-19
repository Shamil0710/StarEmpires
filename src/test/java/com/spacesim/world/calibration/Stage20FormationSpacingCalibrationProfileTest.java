package com.spacesim.world.calibration;

import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.world.calibration.Stage20FormationSpacingCalibrationProfile.BandId;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FormationSpacingCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndClosesFormationSpacingWithoutNewGeometry() {
        Stage20FormationSpacingCalibrationProfile first = Stage20FormationSpacingCalibrationProfile.deriveCurrent();
        Stage20FormationSpacingCalibrationProfile second = Stage20FormationSpacingCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20FormationSpacingCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertEquals(Stage20FormationStationSpatialCalibrationProfile.CURRENT_VERSION, first.sourceProfileVersion());
        assertEquals(Stage20FormationSpacingCalibrationProfile.SOURCE_DOCUMENT, first.sourceDocument());
        assertTrue(first.stage22ReviewRequired());
        assertTrue(first.closesStage20BEntryCoverage());
    }

    @Test
    void compactBandIsDerivedOnlyFromExistingFourAndSixteenShipSamples() {
        Stage20FormationSpacingCalibrationProfile profile = Stage20FormationSpacingCalibrationProfile.deriveCurrent();
        var compact = profile.band(BandId.COMPACT_ACCEPTANCE);

        assertEquals(FormationMode.COMPACT, compact.mode());
        assertEquals(4, compact.minimumShipCount());
        assertEquals(16, compact.maximumShipCount());
        assertEquals(100d, compact.minimumSpacingM());
        assertEquals(120d, compact.maximumSpacingM());
        assertEquals(List.of("stage19.compact_16_ship_side", "stage19.compact_4v4"), compact.sourceProbeIds());
    }

    @Test
    void dispersedBandPreservesTheSingleAcceptedStage19GeometryInsteadOfInterpolating() {
        Stage20FormationSpacingCalibrationProfile profile = Stage20FormationSpacingCalibrationProfile.deriveCurrent();
        var dispersed = profile.band(BandId.DISPERSED_ACCEPTANCE);

        assertEquals(FormationMode.DISPERSED, dispersed.mode());
        assertEquals(4, dispersed.minimumShipCount());
        assertEquals(4, dispersed.maximumShipCount());
        assertEquals(240d, dispersed.minimumSpacingM());
        assertEquals(240d, dispersed.maximumSpacingM());
        assertEquals(List.of("stage19.dispersed_4v4"), dispersed.sourceProbeIds());
    }

    @Test
    void everyBandRetainsPhysicalRecoveryEvidenceFromProductionEscortAcceleration() {
        Stage20FormationSpacingCalibrationProfile profile = Stage20FormationSpacingCalibrationProfile.deriveCurrent();

        assertTrue(profile.bands().stream().allMatch(value -> value.minimumIdealRecoveryTimeS() > 0d));
        assertTrue(profile.bands().stream().allMatch(value -> value.maximumIdealRecoveryTimeS() > 0d));
        assertTrue(profile.sourceSamples().stream().allMatch(value -> value.accelerationMps2() > 0d));
        assertTrue(profile.sourceSamples().stream().allMatch(value ->
                value.source().contains(Stage20FormationSpacingCalibrationProfile.SOURCE_DOCUMENT)));
    }

    @Test
    void provisionalBalanceAuthorityRemainsVisibleForLaterReview() {
        Stage20FormationSpacingCalibrationProfile profile = Stage20FormationSpacingCalibrationProfile.deriveCurrent();

        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, profile.authority());
        assertTrue(profile.stage22ReviewRequired());
        assertEquals(3, profile.sourceSamples().size());
        assertEquals(2, profile.bands().size());
    }
}

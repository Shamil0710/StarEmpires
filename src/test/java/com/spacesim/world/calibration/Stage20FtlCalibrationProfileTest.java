package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.ReferenceDriveCompatibility;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.JumpTopologyMode;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FtlCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicNeighborEdgeOnlyAndPreservesDualProvenance() {
        Stage20FtlCalibrationProfile first = Stage20FtlCalibrationProfile.deriveCurrent();
        Stage20FtlCalibrationProfile second = Stage20FtlCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20FtlCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(JumpTopologyMode.NEIGHBOR_EDGE_ONLY, first.reference().topologyMode());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.reference().status());
        assertEquals(5, first.samples().size());

        Map<String, JumpEdgeCalibrationSample> byId = first.samples().stream()
                .collect(Collectors.toMap(JumpEdgeCalibrationSample::representativeId, Function.identity()));
        JumpEdgeCalibrationSample escort = byId.get("ESCORT_DESTROYER");
        JumpEdgeCalibrationSample corvette = byId.get("TORPEDO_CORVETTE");

        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, escort.shipAuthority());
        assertEquals("fit.escort_destroyer_schema_v1", escort.shipProvenanceId());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, escort.ftlAuthority());
        assertTrue(escort.ftlProvenanceId().startsWith("ship_mathematics_v1_0_design_baseline:"));

        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, corvette.shipAuthority());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, corvette.ftlAuthority());
    }

    @Test
    void compatibleRepresentativesPayMassSensitiveEnergySpoolTransitAndCooldown() {
        Map<String, JumpEdgeCalibrationSample> byId = Stage20FtlCalibrationProfile.deriveCurrent().samples().stream()
                .collect(Collectors.toMap(JumpEdgeCalibrationSample::representativeId, Function.identity()));
        JumpEdgeCalibrationSample corvette = byId.get("TORPEDO_CORVETTE");
        JumpEdgeCalibrationSample escort = byId.get("ESCORT_DESTROYER");

        assertEquals(ReferenceDriveCompatibility.COMPATIBLE, corvette.compatibility());
        assertEquals(ReferenceDriveCompatibility.COMPATIBLE, escort.compatibility());
        assertEquals(2_140_000d, corvette.translatedMassKg(), 0d);
        assertEquals(21_320_000d, escort.translatedMassKg(), 0d);

        assertEquals(53_500_000_000d, corvette.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(13.375d, corvette.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(133.375d, corvette.readyAgainCadenceS().orElseThrow(), 1e-12d);

        assertEquals(533_000_000_000d, escort.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(133.25d, escort.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(253.25d, escort.readyAgainCadenceS().orElseThrow(), 1e-12d);
        assertEquals(30d, escort.referenceEdgeTransitTimeS(), 0d);
        assertEquals(90d, escort.cooldownS(), 0d);
        assertTrue(escort.spoolTimeS().orElseThrow() > corvette.spoolTimeS().orElseThrow());
    }

    @Test
    void overmassRepresentativesRemainExplicitlyIncompatibleWithoutOutOfDomainExtrapolation() {
        Map<String, JumpEdgeCalibrationSample> byId = Stage20FtlCalibrationProfile.deriveCurrent().samples().stream()
                .collect(Collectors.toMap(JumpEdgeCalibrationSample::representativeId, Function.identity()));

        for (String id : new String[]{"BATTLESHIP", "BULK_FREIGHTER_LOADED", "FLEET_TANKER_LOADED"}) {
            JumpEdgeCalibrationSample sample = byId.get(id);
            assertEquals(ReferenceDriveCompatibility.EXCEEDS_TRANSLATED_MASS_LIMIT, sample.compatibility());
            assertTrue(sample.translatedMassToLimitRatio() > 1d);
            assertFalse(sample.requiredTranslationEnergyJ().isPresent());
            assertFalse(sample.spoolTimeS().isPresent());
            assertFalse(sample.readyAgainCadenceS().isPresent());
        }
    }

    @Test
    void sampleValidationRejectsHiddenFtlExtrapolation() {
        assertThrows(IllegalArgumentException.class, () -> new JumpEdgeCalibrationSample(
                "TEST",
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                "ship.test",
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                "ftl.test",
                200d,
                100d,
                2d,
                ReferenceDriveCompatibility.EXCEEDS_TRANSLATED_MASS_LIMIT,
                OptionalDouble.of(2000d),
                OptionalDouble.empty(),
                30d,
                90d,
                OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new JumpEdgeCalibrationSample(
                "TEST",
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                "ship.test",
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                "ftl.test",
                50d,
                100d,
                0.5d,
                ReferenceDriveCompatibility.COMPATIBLE,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                30d,
                90d,
                OptionalDouble.empty()));
    }
}

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
    void currentProfileIsDeterministicNeighborEdgeOnlyAndCoversNineRepresentatives() {
        Stage20FtlCalibrationProfile first = Stage20FtlCalibrationProfile.deriveCurrent();
        Stage20FtlCalibrationProfile second = Stage20FtlCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals("stage20a.ftl-edge-cadence.v2", first.version());
        assertEquals(JumpTopologyMode.NEIGHBOR_EDGE_ONLY, first.reference().topologyMode());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.reference().status());
        assertEquals(9, first.samples().size());

        Map<String, JumpEdgeCalibrationSample> byId = first.samples().stream()
                .collect(Collectors.toMap(JumpEdgeCalibrationSample::representativeId, Function.identity()));
        JumpEdgeCalibrationSample escort = byId.get("ESCORT_DESTROYER");
        JumpEdgeCalibrationSample corvette = byId.get("TORPEDO_CORVETTE");
        JumpEdgeCalibrationSample early = byId.get("EARLY_CIVILIAN_FREIGHTER");
        JumpEdgeCalibrationSample miner = byId.get("MINING_SHIP");

        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, escort.shipAuthority());
        assertEquals("fit.escort_destroyer_schema_v1", escort.shipProvenanceId());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, escort.ftlAuthority());
        assertTrue(escort.ftlProvenanceId().startsWith("ship_mathematics_v1_0_design_baseline:"));

        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, corvette.shipAuthority());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, corvette.ftlAuthority());
        assertTrue(early.shipProvenanceId().contains("stage20a_representative_propulsion_v2.md"));
        assertTrue(miner.shipProvenanceId().contains("stage20a_representative_propulsion_v2.md"));
    }

    @Test
    void compatibleRepresentativesPayMassSensitiveEnergySpoolTransitAndCooldown() {
        Map<String, JumpEdgeCalibrationSample> byId = byId();
        JumpEdgeCalibrationSample corvette = byId.get("TORPEDO_CORVETTE");
        JumpEdgeCalibrationSample escort = byId.get("ESCORT_DESTROYER");
        JumpEdgeCalibrationSample early = byId.get("EARLY_CIVILIAN_FREIGHTER");
        JumpEdgeCalibrationSample miner = byId.get("MINING_SHIP");
        JumpEdgeCalibrationSample cruiser = byId.get("CRUISER");

        for (JumpEdgeCalibrationSample sample : new JumpEdgeCalibrationSample[]{
                corvette, escort, early, miner, cruiser}) {
            assertEquals(ReferenceDriveCompatibility.COMPATIBLE, sample.compatibility());
            assertTrue(sample.translatedMassToLimitRatio() <= 1d);
            assertTrue(sample.requiredTranslationEnergyJ().isPresent());
            assertTrue(sample.spoolTimeS().isPresent());
            assertTrue(sample.readyAgainCadenceS().isPresent());
        }

        assertEquals(2_140_000d, corvette.translatedMassKg(), 0d);
        assertEquals(21_320_000d, escort.translatedMassKg(), 0d);
        assertEquals(28_000_000d, early.translatedMassKg(), 0d);
        assertEquals(56_000_000d, miner.translatedMassKg(), 0d);
        assertEquals(70_279_000d, cruiser.translatedMassKg(), 0d);

        assertEquals(53_500_000_000d, corvette.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(13.375d, corvette.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(133.375d, corvette.readyAgainCadenceS().orElseThrow(), 1e-12d);

        assertEquals(533_000_000_000d, escort.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(133.25d, escort.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(253.25d, escort.readyAgainCadenceS().orElseThrow(), 1e-12d);

        assertEquals(700_000_000_000d, early.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(175d, early.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(295d, early.readyAgainCadenceS().orElseThrow(), 1e-12d);

        assertEquals(1_400_000_000_000d, miner.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(350d, miner.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(470d, miner.readyAgainCadenceS().orElseThrow(), 1e-12d);

        assertEquals(1_756_975_000_000d, cruiser.requiredTranslationEnergyJ().orElseThrow(), 0d);
        assertEquals(439.24375d, cruiser.spoolTimeS().orElseThrow(), 1e-12d);
        assertEquals(559.24375d, cruiser.readyAgainCadenceS().orElseThrow(), 1e-12d);
        assertEquals(30d, cruiser.referenceEdgeTransitTimeS(), 0d);
        assertEquals(90d, cruiser.cooldownS(), 0d);
    }

    @Test
    void heavyRepresentativesRemainExplicitlyIncompatibleWithoutOutOfDomainExtrapolation() {
        Map<String, JumpEdgeCalibrationSample> byId = byId();

        for (String id : new String[]{
                "BATTLESHIP",
                "BULK_FREIGHTER_LOADED",
                "FLEET_TANKER_LOADED",
                "CARRIER_AVIATION_GROUP"}) {
            JumpEdgeCalibrationSample sample = byId.get(id);
            assertEquals(ReferenceDriveCompatibility.EXCEEDS_TRANSLATED_MASS_LIMIT, sample.compatibility());
            assertTrue(sample.translatedMassToLimitRatio() > 1d);
            assertFalse(sample.requiredTranslationEnergyJ().isPresent());
            assertFalse(sample.spoolTimeS().isPresent());
            assertFalse(sample.readyAgainCadenceS().isPresent());
        }
        assertEquals(508_143_000d, byId.get("CARRIER_AVIATION_GROUP").translatedMassKg(), 0d);
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

    private static Map<String, JumpEdgeCalibrationSample> byId() {
        return Stage20FtlCalibrationProfile.deriveCurrent().samples().stream()
                .collect(Collectors.toMap(JumpEdgeCalibrationSample::representativeId, Function.identity()));
    }
}

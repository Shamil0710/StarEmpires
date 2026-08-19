package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FtlCalibrationReference.CalibrationGap;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.JumpTopologyMode;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FtlCalibrationReferenceLoaderTest {
    @Test
    void packagedReferenceClosesAcceptedV10JumpPhysicsAndKeepsGapsExplicit() {
        Stage20FtlCalibrationReference reference = Stage20FtlCalibrationReferenceLoader.loadDefault();

        assertEquals(1, reference.schemaVersion());
        assertEquals("stage20a.ftl-jump-reference.v1", reference.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, reference.status());
        assertEquals("ship_mathematics_v1_0_design_baseline", reference.sourceBaselineId());
        assertEquals(JumpTopologyMode.NEIGHBOR_EDGE_ONLY, reference.topologyMode());
        assertTrue(reference.stage22ReviewRequired());

        assertEquals(100_000_000d, reference.referenceDrive().maxTranslatedMassKg(), 0d);
        assertEquals(25_000d, reference.referenceDrive().translationEnergyPerKgJ(), 0d);
        assertEquals(5_000_000_000d, reference.referenceDrive().chargePowerW(), 0d);
        assertEquals(0.80d, reference.referenceDrive().chargeEfficiency(), 0d);
        assertEquals(90d, reference.referenceDrive().cooldownS(), 0d);

        assertEquals(21_927_000d, reference.referenceClosure().translatedMassKg(), 0d);
        assertEquals(548_175_000_000d, reference.referenceClosure().requiredTranslationEnergyJ(), 0d);
        assertEquals(137.04375d, reference.referenceClosure().spoolTimeS(), 1e-12d);
        assertEquals(30d, reference.referenceClosure().exampleEdgeTransitTimeS(), 0d);

        assertTrue(reference.unresolvedGaps().contains(CalibrationGap.PRODUCTION_FTL_MODULE_NOT_AUTHORED));
        assertTrue(reference.unresolvedGaps().contains(
                CalibrationGap.EDGE_TRANSIT_DISTRIBUTION_NOT_YET_WORLD_AUTHORED));
        assertTrue(reference.unresolvedGaps().contains(
                CalibrationGap.DRIVE_HEAT_COEFFICIENT_NOT_NUMERIC_IN_V1_BASELINE));
        assertThrows(UnsupportedOperationException.class,
                () -> reference.unresolvedGaps().add(CalibrationGap.PRODUCTION_FTL_MODULE_NOT_AUTHORED));
    }

    @Test
    void parserRejectsSchemaDriftBrokenEnergyAndBrokenSpoolClosure() {
        String valid = """
                {
                  "schemaVersion": 1,
                  "version": "test.ftl.v1",
                  "status": "PROVISIONAL_ACCEPTED_REFERENCE",
                  "sourceBaselineId": "baseline.test",
                  "sourceEvidence": "evidence.test",
                  "stage22ReviewRequired": true,
                  "topologyMode": "NEIGHBOR_EDGE_ONLY",
                  "referenceDrive": {
                    "id": "drive.test",
                    "maxTranslatedMassKg": 1000.0,
                    "translationEnergyPerKgJ": 10.0,
                    "chargePowerW": 100.0,
                    "chargeEfficiency": 0.5,
                    "cooldownS": 4.0
                  },
                  "referenceClosure": {
                    "translatedMassKg": 500.0,
                    "requiredTranslationEnergyJ": 5000.0,
                    "spoolTimeS": 100.0,
                    "exampleEdgeTransitTimeS": 3.0
                  },
                  "unresolvedGaps": ["PRODUCTION_FTL_MODULE_NOT_AUTHORED"]
                }
                """;
        Stage20FtlCalibrationReference parsed = Stage20FtlCalibrationReferenceLoader.parse(valid);
        assertEquals(5000d, parsed.referenceClosure().requiredTranslationEnergyJ(), 0d);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20FtlCalibrationReferenceLoader.parse(valid.replace(
                        "\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20FtlCalibrationReferenceLoader.parse(valid.replace(
                        "\"requiredTranslationEnergyJ\": 5000.0",
                        "\"requiredTranslationEnergyJ\": 5001.0")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20FtlCalibrationReferenceLoader.parse(valid.replace(
                        "\"spoolTimeS\": 100.0", "\"spoolTimeS\": 101.0")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20FtlCalibrationReferenceLoader.parse(valid.replace(
                        "\"chargeEfficiency\": 0.5", "\"chargeEfficiency\": 1.5")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20FtlCalibrationReferenceLoader.parse(valid.replace(
                        "NEIGHBOR_EDGE_ONLY", "DIRECT_ANY_SYSTEM")));
        assertThrows(IllegalArgumentException.class, () -> Stage20FtlCalibrationReferenceLoader.parse(" "));
        assertThrows(NullPointerException.class, () -> Stage20FtlCalibrationReferenceLoader.parse(null));
    }
}

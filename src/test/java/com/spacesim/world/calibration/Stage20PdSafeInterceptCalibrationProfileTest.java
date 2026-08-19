package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20PdSafeInterceptReferenceCatalog.DebrisRiskSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20PdSafeInterceptCalibrationProfileTest {
    @Test
    void packagedEvidenceRetainsTheFullV07SensitivityMatrixAndBenchmarkStatus() {
        Stage20PdSafeInterceptReferenceCatalog catalog = Stage20PdSafeInterceptReferenceCatalogLoader.loadDefault();

        assertEquals(1, catalog.schemaVersion());
        assertEquals(Stage20PdSafeInterceptCalibrationProfile.CURRENT_VERSION, catalog.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, catalog.status());
        assertTrue(catalog.stage22ReviewRequired());
        assertEquals("authoring-benchmark-only", catalog.sourceBenchmarkStatus());
        assertEquals("M_ANTI_SHIP_MISSILE", catalog.sourceThreat());
        assertEquals(1_944_000_000_000d, catalog.sourceThreatKineticEnergyJ(), 0d);
        assertEquals("REFERENCE_BATTLESHIP_NOSE_ON", catalog.projectedTarget());
        assertEquals(0.02d, catalog.maxProjectedHitFraction(), 0d);
        assertEquals(12, catalog.samples().size());
        assertEquals(List.of(50d, 200d, 500d), catalog.samples().stream()
                .map(DebrisRiskSample::lateralSigmaMps)
                .distinct()
                .toList());
    }

    @Test
    void conservativePolicySelectsFirstPassingAuthoredStandOff() {
        Stage20PdSafeInterceptCalibrationProfile profile =
                Stage20PdSafeInterceptCalibrationProfile.deriveCurrent();

        assertTrue(profile.closesStage20BEntryCoverage());
        assertEquals(50d, profile.conservativeLateralSigmaMps(), 0d);
        assertEquals(0.02d, profile.maxProjectedHitFraction(), 0d);
        assertEquals(100_000d, profile.selectedMinimumInterceptDistanceM(), 0d);
        assertEquals(0.01908598986265913d, profile.selectedProjectedHitFraction(), 0d);
        assertEquals(37_103_164_293.009346d, profile.selectedIntersectingEnergyJ(), 0d);
        assertEquals(50_000d, profile.closestRejectedStandOffM(), 0d);
        assertEquals(0.07401645401990709d, profile.closestRejectedProjectedHitFraction(), 0d);
        assertTrue(profile.closestRejectedProjectedHitFraction() > profile.maxProjectedHitFraction());
        assertTrue(profile.selectedProjectedHitFraction() <= profile.maxProjectedHitFraction());
    }

    @Test
    void policyDoesNotClaimZeroResidualRiskOrProductionPhysicalLaw() {
        Stage20PdSafeInterceptCalibrationProfile profile =
                Stage20PdSafeInterceptCalibrationProfile.deriveCurrent();

        assertFalse(profile.residualRiskZero());
        assertFalse(profile.physicalLaw());
        assertTrue(profile.schedulerInputReady());
        assertTrue(profile.selectedIntersectingEnergyJ() > 0d);
        assertTrue(profile.stage22ReviewRequired());
        assertEquals("authoring-benchmark-only", profile.sourceBenchmarkStatus());
    }

    @Test
    void allCloserConservativeRowsFailTheCurrentRiskPolicy() {
        Stage20PdSafeInterceptReferenceCatalog catalog = Stage20PdSafeInterceptReferenceCatalogLoader.loadDefault();
        Stage20PdSafeInterceptCalibrationProfile profile =
                Stage20PdSafeInterceptCalibrationProfile.deriveCurrent();

        List<DebrisRiskSample> closer = catalog.samples().stream()
                .filter(value -> Double.compare(value.lateralSigmaMps(), profile.conservativeLateralSigmaMps()) == 0)
                .filter(value -> value.standOffM() < profile.selectedMinimumInterceptDistanceM())
                .sorted(Comparator.comparingDouble(DebrisRiskSample::standOffM))
                .toList();
        assertEquals(3, closer.size());
        assertTrue(closer.stream().allMatch(value -> value.shipHitFraction() > profile.maxProjectedHitFraction()));
    }

    @Test
    void parserRejectsDuplicateSensitivityCoordinates() {
        String duplicate = """
                {
                  "schemaVersion":1,
                  "version":"test",
                  "status":"PROVISIONAL_ACCEPTED_REFERENCE",
                  "stage22ReviewRequired":true,
                  "sourceBenchmark":"docs/benchmarks/protection_debris_reference_v0_7.json",
                  "sourceBenchmarkStatus":"authoring-benchmark-only",
                  "sourceThreat":"M_ANTI_SHIP_MISSILE",
                  "sourceThreatKineticEnergyJ":1944000000000.0,
                  "projectedTarget":"REFERENCE_BATTLESHIP_NOSE_ON",
                  "policyEvidence":"test",
                  "maxProjectedHitFraction":0.02,
                  "samples":[
                    {"lateralSigmaMps":50,"standOffM":100000,"shipHitFraction":0.01,"intersectingEnergyJ":1},
                    {"lateralSigmaMps":50,"standOffM":100000,"shipHitFraction":0.01,"intersectingEnergyJ":1}
                  ]
                }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> Stage20PdSafeInterceptReferenceCatalogLoader.parse(duplicate));
    }
}

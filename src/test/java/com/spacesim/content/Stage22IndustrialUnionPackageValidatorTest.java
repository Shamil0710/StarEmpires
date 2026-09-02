package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22IndustrialUnionPackageValidatorTest {
    @Test
    void defaultPackageClosesCrossAuthorityReferencesAndFormalBenefitCaps() {
        var report = Stage22IndustrialUnionPackageValidator.validateDefault();

        assertEquals(64, report.packageFingerprint().length());
        assertEquals(64, report.productionFingerprint().length());
        assertEquals(64, report.engineeringFingerprint().length());
        assertEquals(64, report.manufacturingFingerprint().length());
        assertEquals(64, report.shipyardFingerprint().length());
        assertEquals(64, report.stationFingerprint().length());
        assertEquals(64, report.characterFingerprint().length());
        assertEquals(7, report.recurringNpcCount());
        assertEquals(11, report.missionCount());
        assertEquals(9, report.familyMetrics().size());
        assertTrue(report.maximumBuildTimeReduction() <= 0.10d + 1e-12d);
        assertTrue(report.maximumThroughputImprovement() <= 0.15d + 1e-12d);
        report.familyMetrics().forEach((roleId, metrics) -> {
            assertTrue(metrics.remainingOperationalMassKg() >= 0d, roleId);
            assertTrue(metrics.continuousPowerMarginW() >= 0d, roleId);
            assertTrue(metrics.continuousThermalMarginW() >= 0d, roleId);
            assertTrue(metrics.staffedCrewBurden() <= metrics.authoredLifeSupportCapacity(), roleId);
        });
    }
}

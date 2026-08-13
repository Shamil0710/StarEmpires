package com.spacesim.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage9ERecoveryTest {
    private static final long SEED = 0x9E5EEDL;

    @Test
    void physicalRecoveryScenarioCompletes() {
        Stage9ERecoveryReport report = Stage9ERecoveryRunner.run(SEED);

        assertTrue(report.successful(), report::toString);
        assertTrue(report.peakSteelUnmetDemand() > report.baselineSteelUnmetDemand(), report::toString);
        assertTrue(report.peakWeaponsUnmetDemand() > 0L, report::toString);
        assertTrue(report.peakSteelPressureBasisPoints() > 10_000, report::toString);
        assertTrue(report.deliveredSteelUnits() >= 180, report::toString);
        assertTrue(report.deliveredEnergyUnits() >= 120, report::toString);
        assertEquals(report.expectedFinalMoneyMilliCredits(), report.finalMoneyMilliCredits(), report::toString);
        assertTrue(report.moneyConserved(), report::toString);
        assertTrue(report.resourcesConserved(), report::toString);
        assertEquals(1, report.foundriesBeforeShock(), report::toString);
        assertEquals(0, report.foundriesImmediatelyAfterShock(), report::toString);
        assertTrue(report.foundriesAfterRecovery() >= 1, report::toString);
    }
}

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
        assertTrue(report.detectionTick() > report.shockTick(), report::toString);
        assertTrue(report.decisionTick() >= report.detectionTick(), report::toString);
        assertTrue(report.firstMaterialDeliveryTick() >= report.decisionTick(), report::toString);
        assertTrue(report.materialsFulfilledTick() >= report.firstMaterialDeliveryTick(), report::toString);
        assertTrue(report.buildStartedTick() >= report.materialsFulfilledTick(), report::toString);
        assertTrue(report.replacementCompletedTick() >= report.buildStartedTick(), report::toString);
        assertTrue(report.recoveryTick() >= report.replacementCompletedTick(), report::toString);
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

    @Test
    void recoveryReportIsDeterministicForSameSeed() {
        Stage9ERecoveryReport first = Stage9ERecoveryRunner.run(SEED);
        Stage9ERecoveryReport second = Stage9ERecoveryRunner.run(SEED);

        assertEquals(first, second);
    }
}

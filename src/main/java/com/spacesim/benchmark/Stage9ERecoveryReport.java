package com.spacesim.benchmark;

record Stage9ERecoveryReport(
        long rootSeed,
        long warmupTicks,
        long shockTick,
        long detectionTick,
        long decisionTick,
        long firstMaterialDeliveryTick,
        long materialsFulfilledTick,
        long buildStartedTick,
        long replacementCompletedTick,
        long recoveryTick,
        long baselineSteelUnmetDemand,
        long peakSteelUnmetDemand,
        long peakWeaponsUnmetDemand,
        int peakSteelPressureBasisPoints,
        long projectFundingMilliCredits,
        int deliveredSteelUnits,
        int deliveredEnergyUnits,
        long initialMoneyMilliCredits,
        long finalMoneyMilliCredits,
        long expectedFinalMoneyMilliCredits,
        boolean moneyConserved,
        boolean resourcesConserved,
        int foundriesBeforeShock,
        int foundriesImmediatelyAfterShock,
        int foundriesAfterRecovery,
        long replacementProjectId) {

    boolean successful() {
        return detectionTick >= shockTick
                && decisionTick >= detectionTick
                && firstMaterialDeliveryTick >= decisionTick
                && materialsFulfilledTick >= firstMaterialDeliveryTick
                && buildStartedTick >= materialsFulfilledTick
                && replacementCompletedTick >= buildStartedTick
                && recoveryTick >= replacementCompletedTick
                && peakSteelUnmetDemand > baselineSteelUnmetDemand
                && projectFundingMilliCredits > 0L
                && deliveredSteelUnits > 0
                && deliveredEnergyUnits > 0
                && moneyConserved
                && resourcesConserved
                && foundriesBeforeShock == 1
                && foundriesImmediatelyAfterShock == 0
                && foundriesAfterRecovery >= 1
                && replacementProjectId > 0L;
    }
}

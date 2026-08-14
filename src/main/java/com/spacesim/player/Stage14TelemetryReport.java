package com.spacesim.player;

/**
 * Immutable Stage-14D first-hour progression diagnostics.
 *
 * @param elapsedSeconds sampled simulation seconds
 * @param initialWalletMilliCredits player wallet at observation start
 * @param finalWalletMilliCredits player wallet at observation end
 * @param netWalletChangeMilliCredits final minus initial wallet
 * @param creditsPerHour normalized net wallet rate over sampled time
 * @param tradeProfitMilliCredits net wallet contribution explicitly attributed to ordinary trade
 * @param miningProfitMilliCredits net wallet contribution explicitly attributed to mined-cargo sale
 * @param shipPurchaseCostMilliCredits real player spending on ship progression
 * @param travelSeconds time sampled while the active ship was physically moving or in jump transit
 * @param miningSeconds time sampled while manual extraction was requested
 * @param combatSeconds time sampled while fire was requested against a live target
 * @param idleSeconds remaining sampled time
 * @param averageCargoUtilization average active-ship cargo fill ratio in {@code [0,1]}
 * @param peakCargoUtilization highest sampled cargo fill ratio in {@code [0,1]}
 * @param ownedFleetLosses number of previously owned FleetIds physically lost during observation
 * @param damageTaken total decrease in active-owned shield/hull effective durability while observed
 * @param secondsToFirstShipProgression simulation seconds until owned FleetId count first increased
 * @param firstProgressionObserved whether a real ownership progression event occurred
 */
public record Stage14TelemetryReport(
        double elapsedSeconds,
        long initialWalletMilliCredits,
        long finalWalletMilliCredits,
        long netWalletChangeMilliCredits,
        double creditsPerHour,
        long tradeProfitMilliCredits,
        long miningProfitMilliCredits,
        long shipPurchaseCostMilliCredits,
        double travelSeconds,
        double miningSeconds,
        double combatSeconds,
        double idleSeconds,
        double averageCargoUtilization,
        double peakCargoUtilization,
        int ownedFleetLosses,
        double damageTaken,
        double secondsToFirstShipProgression,
        boolean firstProgressionObserved) {
    /**
     * Validates bounded/non-negative telemetry dimensions while permitting a negative wallet result.
     *
     * @param elapsedSeconds sampled simulation seconds
     * @param initialWalletMilliCredits starting wallet
     * @param finalWalletMilliCredits ending wallet
     * @param netWalletChangeMilliCredits ending minus starting wallet
     * @param creditsPerHour normalized wallet change
     * @param tradeProfitMilliCredits trade contribution
     * @param miningProfitMilliCredits mined-cargo sale contribution
     * @param shipPurchaseCostMilliCredits ship progression spending
     * @param travelSeconds physical travel time
     * @param miningSeconds extraction-request time
     * @param combatSeconds fire-request time
     * @param idleSeconds remaining time
     * @param averageCargoUtilization average cargo ratio
     * @param peakCargoUtilization peak cargo ratio
     * @param ownedFleetLosses owned physical losses
     * @param damageTaken observed durability loss
     * @param secondsToFirstShipProgression time to first ownership increase
     * @param firstProgressionObserved whether progression occurred
     */
    public Stage14TelemetryReport {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0d
                || !Double.isFinite(creditsPerHour)
                || !nonNegativeFinite(travelSeconds)
                || !nonNegativeFinite(miningSeconds)
                || !nonNegativeFinite(combatSeconds)
                || !nonNegativeFinite(idleSeconds)
                || !boundedRatio(averageCargoUtilization)
                || !boundedRatio(peakCargoUtilization)
                || peakCargoUtilization + 1e-9 < averageCargoUtilization
                || ownedFleetLosses < 0
                || !nonNegativeFinite(damageTaken)
                || !nonNegativeFinite(secondsToFirstShipProgression)
                || shipPurchaseCostMilliCredits < 0L) {
            throw new IllegalArgumentException("Invalid Stage-14 telemetry report");
        }
    }

    private static boolean nonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0d;
    }

    private static boolean boundedRatio(double value) {
        return Double.isFinite(value) && value >= 0d && value <= 1d;
    }
}

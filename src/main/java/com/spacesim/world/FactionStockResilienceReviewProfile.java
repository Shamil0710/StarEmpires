package com.spacesim.world;

/**
 * Stage-17F.6 bounded adjustment profile for resilience-driven strategic stock floors.
 *
 * <p>The profile contains no economic value or hidden resilience score. It only limits how quickly
 * an already measured Stage-17F.5 target may raise persistent stock policy after a common strategic
 * review window is claimed. Automatic downward release is deliberately excluded until market target
 * provenance can distinguish configured baseline demand from previously applied policy demand.</p>
 *
 * @param deadbandUnits positive target delta held without policy mutation
 * @param maxIncreaseUnitsPerReview maximum upward floor step for one item in one review
 */
public record FactionStockResilienceReviewProfile(
        int deadbandUnits,
        int maxIncreaseUnitsPerReview) {

    /**
     * Validates one bounded stock-review profile.
     *
     * @param deadbandUnits positive target delta held without mutation
     * @param maxIncreaseUnitsPerReview maximum upward step
     */
    public FactionStockResilienceReviewProfile {
        if (deadbandUnits < 0) {
            throw new IllegalArgumentException("Stock-floor deadband cannot be negative");
        }
        if (maxIncreaseUnitsPerReview <= 0) {
            throw new IllegalArgumentException("Stock-floor review increase step must be positive");
        }
    }

    /**
     * Conservative default suitable for the first Stage-17F.6 stock/resilience integration.
     *
     * @return bounded default profile
     */
    public static FactionStockResilienceReviewProfile conservativeDefault() {
        return new FactionStockResilienceReviewProfile(2, 10);
    }
}

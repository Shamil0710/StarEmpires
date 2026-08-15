package com.spacesim.world;

/**
 * Stage-17F.6 bounded adjustment profile for resilience-driven strategic stock demand.
 *
 * <p>The profile contains no economic value or hidden resilience score. It only limits how quickly
 * an already measured Stage-17F.5 target may change the dedicated automatic resilience overlay after
 * a common strategic review window is claimed. Base operator/player/AI stock policy is not modified.</p>
 *
 * @param deadbandUnits absolute target delta held without overlay mutation
 * @param maxIncreaseUnitsPerReview maximum upward overlay step for one item in one review
 * @param maxDecreaseUnitsPerReview maximum downward overlay step for one item in one review
 */
public record FactionStockResilienceReviewProfile(
        int deadbandUnits,
        int maxIncreaseUnitsPerReview,
        int maxDecreaseUnitsPerReview) {

    /**
     * Validates one bounded stock-review profile.
     *
     * @param deadbandUnits absolute target delta held without mutation
     * @param maxIncreaseUnitsPerReview maximum upward step
     * @param maxDecreaseUnitsPerReview maximum downward step
     */
    public FactionStockResilienceReviewProfile {
        if (deadbandUnits < 0) {
            throw new IllegalArgumentException("Stock-floor deadband cannot be negative");
        }
        if (maxIncreaseUnitsPerReview <= 0 || maxDecreaseUnitsPerReview <= 0) {
            throw new IllegalArgumentException("Stock-floor review steps must be positive");
        }
    }

    /**
     * Conservative default for the Stage-17F.6 resilience overlay.
     *
     * @return bounded default profile
     */
    public static FactionStockResilienceReviewProfile conservativeDefault() {
        return new FactionStockResilienceReviewProfile(2, 10, 5);
    }
}

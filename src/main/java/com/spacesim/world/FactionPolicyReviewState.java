package com.spacesim.world;

/**
 * Minimal persistent anti-oscillation watermark for one faction's strategic policy review lifecycle.
 *
 * <p>The state deliberately stores only the last authoritative world tick at which the common policy
 * review window was claimed. Individual policy values remain the memory of their own prior decisions;
 * no parallel hidden utility score is persisted.</p>
 *
 * @param lastPolicyReviewTick last claimed authoritative review tick, or {@code -1} before the first review
 */
public record FactionPolicyReviewState(long lastPolicyReviewTick) {
    /** Canonical state for a faction that has never performed an automatic policy review. */
    public static final FactionPolicyReviewState INITIAL = new FactionPolicyReviewState(-1L);

    /**
     * Validates the optional authoritative tick watermark.
     *
     * @param lastPolicyReviewTick last claimed review tick or {@code -1}
     */
    public FactionPolicyReviewState {
        if (lastPolicyReviewTick < -1L) {
            throw new IllegalArgumentException("Policy review tick must be -1 or non-negative");
        }
    }

    /** @return whether the faction has already claimed at least one policy review window */
    public boolean reviewed() {
        return lastPolicyReviewTick >= 0L;
    }
}

package com.spacesim.world;

import java.util.Objects;

/**
 * Deterministic authoritative-tick cadence for faction policy reviews.
 *
 * <p>The phase offset staggers the first review across factions without wall-clock time or randomness.
 * After the first claimed review, the persisted last-review tick becomes authoritative and the next
 * review is due only after a complete interval.</p>
 *
 * @param intervalTicks positive minimum ticks between claimed reviews
 * @param firstReviewOffsetTicks deterministic first-review offset in {@code [0, intervalTicks)}
 */
public record FactionPolicyReviewCadence(long intervalTicks, long firstReviewOffsetTicks) {
    /** Default policy-review interval: one thousand authoritative world ticks. */
    public static final long DEFAULT_INTERVAL_TICKS = 1_000L;

    /**
     * Validates one bounded deterministic cadence.
     *
     * @param intervalTicks positive minimum ticks between reviews
     * @param firstReviewOffsetTicks first-review offset
     */
    public FactionPolicyReviewCadence {
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException("Policy review interval must be positive");
        }
        if (firstReviewOffsetTicks < 0L || firstReviewOffsetTicks >= intervalTicks) {
            throw new IllegalArgumentException("Policy review offset must be inside the cadence interval");
        }
    }

    /**
     * Creates a stable staggered cadence from the faction stable ID.
     *
     * @param factionContentId stable faction content ID
     * @return deterministic cadence using {@link #DEFAULT_INTERVAL_TICKS}
     */
    public static FactionPolicyReviewCadence defaultForFaction(String factionContentId) {
        return staggered(DEFAULT_INTERVAL_TICKS, factionContentId);
    }

    /**
     * Creates a stable staggered cadence without relying on JVM-randomized hash state.
     *
     * @param intervalTicks positive minimum interval
     * @param factionContentId stable faction content ID
     * @return deterministic cadence
     */
    public static FactionPolicyReviewCadence staggered(long intervalTicks, String factionContentId) {
        String id = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException("Policy review interval must be positive");
        }
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < id.length(); index++) {
            hash ^= id.charAt(index);
            hash *= 0x100000001b3L;
        }
        long offset = Long.remainderUnsigned(hash, intervalTicks);
        return new FactionPolicyReviewCadence(intervalTicks, offset);
    }

    /**
     * Returns whether a faction may claim a review at the supplied authoritative tick.
     *
     * @param state persistent review watermark
     * @param currentTick authoritative world tick
     * @return {@code true} only after the first offset or a complete post-review interval
     */
    public boolean isDue(FactionPolicyReviewState state, long currentTick) {
        FactionPolicyReviewState checked = Objects.requireNonNull(state, "Policy review state not set");
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
        if (!checked.reviewed()) {
            return currentTick >= firstReviewOffsetTicks;
        }
        long last = checked.lastPolicyReviewTick();
        return currentTick >= last && currentTick - last >= intervalTicks;
    }

    /**
     * Claims the current review window after validating cadence eligibility.
     *
     * @param state previous persistent watermark
     * @param currentTick authoritative world tick
     * @return new persistent watermark
     */
    public FactionPolicyReviewState claim(FactionPolicyReviewState state, long currentTick) {
        if (!isDue(state, currentTick)) {
            throw new IllegalStateException("Faction policy review is not due at tick " + currentTick);
        }
        return new FactionPolicyReviewState(currentTick);
    }
}

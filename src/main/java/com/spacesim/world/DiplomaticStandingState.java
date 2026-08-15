package com.spacesim.world;

import java.util.Objects;

/**
 * Directed trust/credibility assessment held by one faction about another.
 *
 * @param targetFactionContentId stable target faction ID
 * @param trust bounded trust in range [-100, 100]
 * @param credibility bounded credibility in range [0, 100]
 * @param lastUpdatedTick authoritative tick of the last standing change
 */
public record DiplomaticStandingState(
        String targetFactionContentId,
        int trust,
        int credibility,
        long lastUpdatedTick) implements Comparable<DiplomaticStandingState> {

    /** Neutral credibility used when no explicit directed assessment exists. */
    public static final int NEUTRAL_CREDIBILITY = 50;

    /** Validates and normalizes one directed diplomatic assessment. */
    public DiplomaticStandingState {
        targetFactionContentId = requireId(targetFactionContentId, "Target faction content ID");
        if (trust < -100 || trust > 100) {
            throw new IllegalArgumentException("Diplomatic trust must be in [-100, 100]");
        }
        if (credibility < 0 || credibility > 100) {
            throw new IllegalArgumentException("Diplomatic credibility must be in [0, 100]");
        }
        if (lastUpdatedTick < 0L) {
            throw new IllegalArgumentException("Diplomatic standing tick cannot be negative");
        }
    }

    @Override
    public int compareTo(DiplomaticStandingState other) {
        return targetFactionContentId.compareTo(
                Objects.requireNonNull(other, "DiplomaticStandingState not set").targetFactionContentId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}

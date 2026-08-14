package com.spacesim.player;

import com.spacesim.constants.Constants;

import java.util.Objects;

/**
 * Persistent player reputation toward one content-defined faction.
 *
 * @param factionContentId stable faction content ID
 * @param value reputation points in the same range used by runtime ReputationComponent
 */
public record PlayerReputationState(
        String factionContentId,
        float value) implements Comparable<PlayerReputationState> {

    /**
     * Validates and normalizes one persistent reputation entry.
     *
     * @param factionContentId stable faction content ID
     * @param value finite reputation value within global bounds
     */
    public PlayerReputationState {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be empty");
        }
        if (!Float.isFinite(value)
                || value < Constants.MIN_REPUTATION
                || value > Constants.MAX_REPUTATION) {
            throw new IllegalArgumentException("Player reputation is outside supported bounds");
        }
    }

    @Override
    public int compareTo(PlayerReputationState other) {
        return factionContentId.compareTo(other.factionContentId);
    }
}

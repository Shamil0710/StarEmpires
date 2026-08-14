package com.spacesim.player;

import com.spacesim.world.WorldState;

import java.util.Objects;

/**
 * Playable save envelope combining the independent world with optional player state.
 *
 * <p>The underlying WorldState remains unaware of the player. A {@code null} player is valid only
 * as a migration result for pre-Stage-12 saves before a playable actor has been initialized.</p>
 *
 * @param schemaVersion playable-layer schema version
 * @param worldState complete authoritative world snapshot
 * @param playerState persistent player state, or {@code null} for migrated legacy worlds
 */
public record PlayableWorldState(
        int schemaVersion,
        WorldState worldState,
        PlayerState playerState) {
    /** Current playable-layer persistent schema with persistent docking state. */
    public static final int CURRENT_VERSION = 2;
    /** Stage-12A schema before persistent docking state. */
    public static final int LEGACY_STAGE12A_VERSION = 1;

    /**
     * Validates one playable save snapshot.
     *
     * @param schemaVersion playable-layer schema version
     * @param worldState authoritative world state
     * @param playerState player state or {@code null} for legacy migration
     */
    public PlayableWorldState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported playable-world schema: " + schemaVersion);
        }
        Objects.requireNonNull(worldState, "WorldState not set");
    }

    /** @return whether the playable actor has already been initialized */
    public boolean hasPlayer() {
        return playerState != null;
    }
}

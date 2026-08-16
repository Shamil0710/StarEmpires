package com.spacesim.world;

import com.spacesim.constants.Constants;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persistent identity metadata for a faction defined by world state rather than authored content.
 *
 * <p>Authored factions remain definitions in the immutable content catalog. This record covers
 * dynamic political actors such as the player's Stage-17 faction as well as deterministic
 * world-bootstrap actors used by large manual-test worlds. Both reserve one dense runtime faction
 * slot without changing the content fingerprint.</p>
 *
 * @param stableFactionId stable world faction ID using canonical {@code faction.*} syntax
 * @param runtimeFactionId bounded dense runtime ID used at the local ECS boundary
 * @param displayName public non-empty faction name
 * @param origin origin of the world-defined faction identity
 */
public record WorldFactionIdentityState(
        String stableFactionId,
        int runtimeFactionId,
        String displayName,
        Origin origin)
        implements Comparable<WorldFactionIdentityState> {
    private static final Pattern STABLE_ID = Pattern.compile(
            "faction\\.[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;

    /** Supported origins for world-defined faction identities. */
    public enum Origin {
        /** Faction founded explicitly by the playable actor. */
        PLAYER_CREATED,
        /** Deterministic NPC/political actor materialized as part of an authored world bootstrap. */
        WORLD_BOOTSTRAP
    }

    /**
     * Validates and normalizes world-defined faction identity metadata.
     *
     * @param stableFactionId stable world faction ID
     * @param runtimeFactionId dense runtime slot in the Stage-17 bounded capacity
     * @param displayName public display name
     * @param origin identity origin
     */
    public WorldFactionIdentityState {
        stableFactionId = normalizeStableId(stableFactionId);
        if (runtimeFactionId < 0 || runtimeFactionId >= Constants.FACTION_RUNTIME_CAPACITY) {
            throw new IllegalArgumentException("Runtime faction ID is outside bounded capacity");
        }
        displayName = Objects.requireNonNull(displayName, "Faction display name not set").strip();
        if (displayName.isEmpty() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Faction display name length is outside supported bounds");
        }
        origin = Objects.requireNonNull(origin, "Faction identity origin not set");
    }

    @Override
    public int compareTo(WorldFactionIdentityState other) {
        WorldFactionIdentityState checked = Objects.requireNonNull(other, "Faction identity not set");
        int idOrder = stableFactionId.compareTo(checked.stableFactionId);
        return idOrder != 0 ? idOrder : Integer.compare(runtimeFactionId, checked.runtimeFactionId);
    }

    /**
     * Normalizes and validates one stable faction ID.
     *
     * @param value requested ID
     * @return stripped canonical ID
     */
    public static String normalizeStableId(String value) {
        String id = Objects.requireNonNull(value, "Faction stable ID not set").strip();
        if (!STABLE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Faction stable ID must use lower-case faction.* syntax");
        }
        return id;
    }
}

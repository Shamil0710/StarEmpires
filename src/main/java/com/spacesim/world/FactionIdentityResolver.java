package com.spacesim.world;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves authored and world-defined factions through one stable-ID/runtime-ID boundary.
 *
 * <p>Authored factions keep the runtime IDs declared by the immutable {@link ContentCatalog}.
 * Dynamic factions reserve explicit world-persistent slots represented by
 * {@link WorldFactionIdentityState}. Allocation always chooses the lowest free slot, making the
 * result deterministic for the same authored catalog, persisted identities and slot capacity.</p>
 *
 * <p>This resolver does not mutate local ECS arrays. Stage-17 persistence/capacity migration makes
 * {@link Constants#MAX_FACTIONS} available to reputation and market-access storage before dynamic
 * runtime IDs are materialized into {@code FactionComponent}.</p>
 */
public final class FactionIdentityResolver {
    /** Default bounded faction capacity shared with local ECS hot-path arrays. */
    public static final int DEFAULT_RUNTIME_SLOT_CAPACITY = Constants.MAX_FACTIONS;

    private final ContentCatalog content;
    private final int runtimeSlotCapacity;
    private final List<WorldFactionIdentityState> dynamicIdentities;
    private final Map<String, Integer> runtimeIdByStableId;
    private final Map<Integer, String> stableIdByRuntimeId;
    private final Map<String, String> displayNameByStableId;

    /**
     * Builds and validates one immutable unified faction directory.
     *
     * @param content immutable authored content catalog
     * @param dynamicIdentities persistent world-defined faction identities
     * @param runtimeSlotCapacity bounded dense runtime slot capacity
     */
    public FactionIdentityResolver(
            ContentCatalog content,
            List<WorldFactionIdentityState> dynamicIdentities,
            int runtimeSlotCapacity) {
        this.content = Objects.requireNonNull(content, "Content catalog not set");
        Objects.requireNonNull(dynamicIdentities, "Dynamic faction identities not set");
        if (runtimeSlotCapacity <= 0) {
            throw new IllegalArgumentException("Runtime faction slot capacity must be positive");
        }
        this.runtimeSlotCapacity = runtimeSlotCapacity;

        Map<String, Integer> byStable = new HashMap<>();
        Map<Integer, String> byRuntime = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            requireRuntimeSlot(faction.runtimeId(), runtimeSlotCapacity);
            if (byStable.putIfAbsent(faction.id(), faction.runtimeId()) != null) {
                throw new IllegalArgumentException("Duplicate authored faction stable ID: " + faction.id());
            }
            if (byRuntime.putIfAbsent(faction.runtimeId(), faction.id()) != null) {
                throw new IllegalArgumentException("Duplicate authored faction runtime ID: " + faction.runtimeId());
            }
            names.put(faction.id(), faction.displayName());
        }

        List<WorldFactionIdentityState> sorted = new ArrayList<>(dynamicIdentities.size());
        Set<String> dynamicStableIds = new HashSet<>();
        Set<Integer> dynamicRuntimeIds = new HashSet<>();
        for (WorldFactionIdentityState identity : dynamicIdentities) {
            WorldFactionIdentityState value = Objects.requireNonNull(identity, "Dynamic faction identity not set");
            requireRuntimeSlot(value.runtimeFactionId(), runtimeSlotCapacity);
            if (!dynamicStableIds.add(value.stableFactionId())) {
                throw new IllegalArgumentException("Duplicate dynamic faction stable ID: " + value.stableFactionId());
            }
            if (!dynamicRuntimeIds.add(value.runtimeFactionId())) {
                throw new IllegalArgumentException("Duplicate dynamic faction runtime ID: " + value.runtimeFactionId());
            }
            if (byStable.containsKey(value.stableFactionId())) {
                throw new IllegalArgumentException("Dynamic faction stable ID collides with authored faction: "
                        + value.stableFactionId());
            }
            if (byRuntime.containsKey(value.runtimeFactionId())) {
                throw new IllegalArgumentException("Dynamic faction runtime ID collides with authored faction: "
                        + value.runtimeFactionId());
            }
            byStable.put(value.stableFactionId(), value.runtimeFactionId());
            byRuntime.put(value.runtimeFactionId(), value.stableFactionId());
            names.put(value.stableFactionId(), value.displayName());
            sorted.add(value);
        }
        sorted.sort(WorldFactionIdentityState::compareTo);
        this.dynamicIdentities = List.copyOf(sorted);
        this.runtimeIdByStableId = Map.copyOf(byStable);
        this.stableIdByRuntimeId = Map.copyOf(byRuntime);
        this.displayNameByStableId = Map.copyOf(names);
    }

    /**
     * Builds the default Stage-17 resolver capacity.
     *
     * @param content authored catalog
     * @param dynamicIdentities persistent dynamic identities
     * @return immutable resolver using {@link #DEFAULT_RUNTIME_SLOT_CAPACITY}
     */
    public static FactionIdentityResolver createDefault(
            ContentCatalog content,
            List<WorldFactionIdentityState> dynamicIdentities) {
        return new FactionIdentityResolver(content, dynamicIdentities, DEFAULT_RUNTIME_SLOT_CAPACITY);
    }

    /** @return bounded runtime slot capacity represented by this directory */
    public int runtimeSlotCapacity() {
        return runtimeSlotCapacity;
    }

    /** @return canonical immutable dynamic identity list */
    public List<WorldFactionIdentityState> dynamicIdentities() {
        return dynamicIdentities;
    }

    /**
     * Resolves a stable authored/world faction ID to its dense runtime slot.
     *
     * @param stableFactionId stable faction ID or {@code null}
     * @return runtime slot or empty
     */
    public Optional<Integer> runtimeId(String stableFactionId) {
        return Optional.ofNullable(stableFactionId == null ? null : runtimeIdByStableId.get(stableFactionId));
    }

    /**
     * Resolves one dense runtime slot back to its stable faction ID.
     *
     * @param runtimeFactionId runtime slot
     * @return stable faction ID or empty
     */
    public Optional<String> stableId(int runtimeFactionId) {
        return Optional.ofNullable(stableIdByRuntimeId.get(runtimeFactionId));
    }

    /**
     * Returns the public authored/world faction name.
     *
     * @param stableFactionId stable faction ID or {@code null}
     * @return display name or empty
     */
    public Optional<String> displayName(String stableFactionId) {
        return Optional.ofNullable(stableFactionId == null ? null : displayNameByStableId.get(stableFactionId));
    }

    /**
     * Checks whether any authored or dynamic faction owns the stable ID.
     *
     * @param stableFactionId stable ID or {@code null}
     * @return true when the directory contains the faction
     */
    public boolean containsStableId(String stableFactionId) {
        return stableFactionId != null && runtimeIdByStableId.containsKey(stableFactionId);
    }

    /**
     * Creates metadata for a new player-created faction using the lowest free runtime slot.
     *
     * <p>The resolver itself remains immutable. The caller persists the returned identity and then
     * builds a new resolver from the updated world state.</p>
     *
     * @param stableFactionId requested new stable ID
     * @param displayName public faction name
     * @return validated dynamic identity with deterministic runtime slot
     * @throws IllegalArgumentException if the ID already exists
     * @throws IllegalStateException if all bounded runtime slots are occupied
     */
    public WorldFactionIdentityState allocatePlayerCreated(
            String stableFactionId,
            String displayName) {
        String id = WorldFactionIdentityState.normalizeStableId(stableFactionId);
        if (containsStableId(id)) {
            throw new IllegalArgumentException("Faction stable ID already exists: " + id);
        }
        for (int runtimeId = 0; runtimeId < runtimeSlotCapacity; runtimeId++) {
            if (!stableIdByRuntimeId.containsKey(runtimeId)) {
                return new WorldFactionIdentityState(
                        id,
                        runtimeId,
                        displayName,
                        WorldFactionIdentityState.Origin.PLAYER_CREATED);
            }
        }
        throw new IllegalStateException("No free runtime faction slots remain");
    }

    private static void requireRuntimeSlot(int runtimeId, int capacity) {
        if (runtimeId < 0 || runtimeId >= capacity) {
            throw new IllegalArgumentException("Faction runtime ID is outside bounded capacity: " + runtimeId);
        }
    }
}

package com.spacesim.persistence;

import com.spacesim.world.FactionLivingActorState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21A generated-world checkpoint composition.
 *
 * <p>The accepted Stage-20 runtime payload remains embedded and unmodified. Stage 21A adds only
 * autonomous-faction lifecycle metadata on top, preserving the existing world/economy/freight
 * authorities and their independent persistence schemas.</p>
 *
 * @param schemaVersion Stage-21A composition schema
 * @param runtimeVersion exact Stage-21A runtime composition contract
 * @param stage20Runtime exact underlying generated-world runtime checkpoint
 * @param livingActors autonomous faction lifecycle rows in canonical stable-ID order
 */
public record Stage21AGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage20GeneratedWorldRuntimePersistentState stage20Runtime,
        List<FactionLivingActorState> livingActors) {
    /** Current Stage-21A checkpoint composition schema. */
    public static final int CURRENT_VERSION = 1;
    /** Stable Stage-21A runtime composition contract. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21a.generated-world-living-actors.v1";

    /**
     * Validates cross-layer faction identities and canonicalizes lifecycle rows.
     *
     * @param schemaVersion Stage-21A composition schema
     * @param runtimeVersion exact Stage-21A runtime composition contract
     * @param stage20Runtime exact underlying generated-world runtime checkpoint
     * @param livingActors autonomous faction lifecycle rows in canonical stable-ID order
     */
    public Stage21AGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-21A runtime checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "Stage-21A runtime version");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-21A runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage20Runtime, "Stage-20 runtime checkpoint not set");
        ArrayList<FactionLivingActorState> actors = new ArrayList<>(
                Objects.requireNonNull(livingActors, "Living actor states not set"));
        if (actors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Living actor states cannot contain null");
        }
        actors.sort(Comparator.naturalOrder());
        HashSet<String> actorIds = new HashSet<>();
        for (FactionLivingActorState actor : actors) {
            if (!actorIds.add(actor.factionContentId())) {
                throw new IllegalArgumentException(
                        "Duplicate Stage-21A actor state: " + actor.factionContentId());
            }
        }

        Set<String> savedFactionIds = stage20Runtime.worldState().factions().stream()
                .map(faction -> faction.factionContentId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String actorId : actorIds) {
            if (!savedFactionIds.contains(actorId)) {
                throw new IllegalArgumentException(
                        "Stage-21A actor is absent from saved world faction authority: " + actorId);
            }
        }
        livingActors = List.copyOf(actors);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}

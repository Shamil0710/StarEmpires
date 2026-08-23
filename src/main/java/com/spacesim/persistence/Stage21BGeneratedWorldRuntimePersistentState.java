package com.spacesim.persistence;

import com.spacesim.world.FactionStrategicIntentState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Atomic Stage-21B generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21A payload remains embedded and unmodified. Stage 21B adds only
 * persistent strategic intent metadata on top, preserving world/economy/freight/diplomacy/fleet
 * authorities and their existing persistence schemas.</p>
 *
 * @param schemaVersion Stage-21B composition schema
 * @param runtimeVersion exact Stage-21B runtime composition contract
 * @param stage21ARuntime exact underlying Stage-21A generated-world checkpoint
 * @param strategicIntents persistent strategic intent rows in canonical faction-ID order
 */
public record Stage21BGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21AGeneratedWorldRuntimePersistentState stage21ARuntime,
        List<FactionStrategicIntentState> strategicIntents) {
    /** Current Stage-21B checkpoint composition schema. */
    public static final int CURRENT_VERSION = 5;
    /** Stable Stage-21B runtime composition contract. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21b.generated-world-strategic-intent.v5";

    /**
     * Validates one-to-one living-actor/intent ownership and canonicalizes intent rows.
     *
     * @param schemaVersion Stage-21B composition schema
     * @param runtimeVersion exact Stage-21B runtime composition contract
     * @param stage21ARuntime exact underlying Stage-21A generated-world checkpoint
     * @param strategicIntents persistent strategic intent rows in canonical faction-ID order
     */
    public Stage21BGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-21B runtime checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "Stage-21B runtime version");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21B runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21ARuntime, "Stage-21A runtime checkpoint not set");
        ArrayList<FactionStrategicIntentState> intents = new ArrayList<>(
                Objects.requireNonNull(strategicIntents, "Strategic intent states not set"));
        if (intents.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Strategic intent states cannot contain null");
        }
        intents.sort(Comparator.naturalOrder());
        HashSet<String> intentIds = new HashSet<>();
        for (FactionStrategicIntentState intent : intents) {
            if (!intentIds.add(intent.factionContentId())) {
                throw new IllegalArgumentException(
                        "Duplicate Stage-21B strategic intent state: " + intent.factionContentId());
            }
        }
        Map<String, Long> actorReviewCounts = stage21ARuntime.livingActors().stream()
                .collect(Collectors.toUnmodifiableMap(
                        actor -> actor.factionContentId(),
                        actor -> actor.completedReviewCount()));
        Set<String> actorIds = actorReviewCounts.keySet();
        if (!actorIds.equals(intentIds)) {
            throw new IllegalArgumentException(
                    "Stage-21B strategic intent identities must match Stage-21A living actors exactly");
        }
        for (FactionStrategicIntentState intent : intents) {
            long actorReviewCount = actorReviewCounts.get(intent.factionContentId());
            if (intent.lastActorReviewCount() > actorReviewCount) {
                throw new IllegalArgumentException(
                        "Stage-21B intent consumed a future Stage-21A actor review: "
                                + intent.factionContentId());
            }
        }
        strategicIntents = List.copyOf(intents);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}

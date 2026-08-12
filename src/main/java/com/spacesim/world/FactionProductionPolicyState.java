package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent production policy для одного station archetype.
 *
 * @param stationArchetypeContentId stable station archetype ID
 * @param recipeContentId stable recipe content ID, который должен быть активным
 */
public record FactionProductionPolicyState(
        String stationArchetypeContentId,
        String recipeContentId) implements Comparable<FactionProductionPolicyState> {

    /**
     * Нормализует оба stable content ID.
     *
     * @param stationArchetypeContentId stable station archetype ID
     * @param recipeContentId stable recipe ID
     */
    public FactionProductionPolicyState {
        stationArchetypeContentId = requireId(stationArchetypeContentId, "Station archetype ID");
        recipeContentId = requireId(recipeContentId, "Recipe content ID");
    }

    /** @param other другая policy @return deterministic ordering по archetype, затем recipe */
    @Override
    public int compareTo(FactionProductionPolicyState other) {
        Objects.requireNonNull(other, "FactionProductionPolicyState не задан");
        int archetype = stationArchetypeContentId.compareTo(other.stationArchetypeContentId);
        return archetype != 0 ? archetype : recipeContentId.compareTo(other.recipeContentId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " не задан").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " не может быть пустым");
        }
        return normalized;
    }
}

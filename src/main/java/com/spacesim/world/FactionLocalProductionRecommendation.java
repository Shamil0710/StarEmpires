package com.spacesim.world;

import java.util.Objects;

/**
 * Explainable recommendation to restore/use an already owned physical production capability.
 *
 * <p>The recommendation never grants a recipe capability to an arbitrary station. The referenced
 * recipe is the canonical default recipe of a station archetype that the faction currently owns as
 * a live {@code ProductionComponent} entity.</p>
 *
 * @param itemContentId critical output item that motivates the recommendation
 * @param stationArchetypeContentId already owned production station archetype
 * @param recipeContentId canonical recipe declared by that station archetype
 * @param outputUnitsPerCycle physical output units of the critical item per recipe cycle
 * @param durationSeconds physical recipe cycle duration
 */
public record FactionLocalProductionRecommendation(
        String itemContentId,
        String stationArchetypeContentId,
        String recipeContentId,
        int outputUnitsPerCycle,
        float durationSeconds)
        implements Comparable<FactionLocalProductionRecommendation> {

    /**
     * Validates one immutable existing-capacity recommendation.
     *
     * @param itemContentId critical output item content ID
     * @param stationArchetypeContentId owned station archetype content ID
     * @param recipeContentId canonical recipe content ID
     * @param outputUnitsPerCycle positive output units per cycle
     * @param durationSeconds positive finite recipe duration
     */
    public FactionLocalProductionRecommendation {
        itemContentId = requireId(itemContentId, "Item content ID");
        stationArchetypeContentId = requireId(stationArchetypeContentId, "Station archetype content ID");
        recipeContentId = requireId(recipeContentId, "Recipe content ID");
        if (outputUnitsPerCycle <= 0) {
            throw new IllegalArgumentException("Output units per cycle must be positive");
        }
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) {
            throw new IllegalArgumentException("Recipe duration must be finite and positive");
        }
    }

    /**
     * Orders recommendations by critical item, then archetype and recipe.
     *
     * @param other other recommendation
     * @return deterministic comparison result
     */
    @Override
    public int compareTo(FactionLocalProductionRecommendation other) {
        Objects.requireNonNull(other, "Local production recommendation not set");
        int item = itemContentId.compareTo(other.itemContentId);
        if (item != 0) {
            return item;
        }
        int archetype = stationArchetypeContentId.compareTo(other.stationArchetypeContentId);
        return archetype != 0 ? archetype : recipeContentId.compareTo(other.recipeContentId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}

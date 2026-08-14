package com.spacesim.player;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only authoritative construction option exposed to player UI and command layers.
 *
 * @param archetypeContentId stable station archetype content ID
 * @param displayName localized/display station name
 * @param minimumFundingMilliCredits minimum real site liquidity in milli-credits
 * @param requiredMaterials immutable canonical material bill by stable item content ID
 * @param materialWorkUnits normalized construction-handling work
 * @param estimatedBuildSeconds authoritative duration estimate for a newly created project
 */
public record PlayerConstructionArchetypeView(
        String archetypeContentId,
        String displayName,
        long minimumFundingMilliCredits,
        Map<String, Integer> requiredMaterials,
        double materialWorkUnits,
        double estimatedBuildSeconds) {

    /**
     * Validates one construction option snapshot.
     *
     * @param archetypeContentId stable station archetype content ID
     * @param displayName display station name
     * @param minimumFundingMilliCredits minimum site liquidity
     * @param requiredMaterials material bill
     * @param materialWorkUnits normalized material work
     * @param estimatedBuildSeconds calculated build time
     */
    public PlayerConstructionArchetypeView {
        archetypeContentId = requireText(archetypeContentId, "Construction archetype ID");
        displayName = requireText(displayName, "Construction display name");
        if (minimumFundingMilliCredits <= 0L) {
            throw new IllegalArgumentException("Construction minimum funding must be positive");
        }
        TreeMap<String, Integer> canonicalMaterials = new TreeMap<>(Objects.requireNonNull(
                requiredMaterials, "Construction materials not set"));
        if (canonicalMaterials.isEmpty()) {
            throw new IllegalArgumentException("Construction material bill cannot be empty");
        }
        for (Map.Entry<String, Integer> entry : canonicalMaterials.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Construction material bill contains invalid entry");
            }
        }
        requiredMaterials = Collections.unmodifiableMap(canonicalMaterials);
        if (!Double.isFinite(materialWorkUnits) || materialWorkUnits <= 0d
                || !Double.isFinite(estimatedBuildSeconds) || estimatedBuildSeconds <= 0d) {
            throw new IllegalArgumentException("Construction estimate must be positive and finite");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}

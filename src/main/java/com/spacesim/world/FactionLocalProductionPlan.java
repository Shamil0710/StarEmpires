package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only Stage-17F.5 plan for resilience-driven use of already owned production capacity.
 *
 * <p>Recommendations may only target capabilities proven by live owned production entities and their
 * canonical station-archetype recipes. Missing capability remains an explicit capacity gap; this plan
 * never constructs a station, creates output or invents a compatible recipe.</p>
 *
 * @param factionContentId stable faction ID
 * @param observationTick authoritative world tick used for the plan
 * @param recommendations deterministic existing-capacity production recommendations
 * @param capacityGapItemContentIds critical items with no currently owned canonical production capacity
 */
public record FactionLocalProductionPlan(
        String factionContentId,
        long observationTick,
        List<FactionLocalProductionRecommendation> recommendations,
        List<String> capacityGapItemContentIds) {

    /**
     * Validates and canonicalizes one local-production resilience plan.
     *
     * @param factionContentId stable faction ID
     * @param observationTick authoritative observation tick
     * @param recommendations existing-capacity recommendations
     * @param capacityGapItemContentIds critical items lacking owned production capacity
     */
    public FactionLocalProductionPlan {
        factionContentId = requireId(factionContentId, "Faction content ID");
        if (observationTick < 0L) {
            throw new IllegalArgumentException("Observation tick cannot be negative");
        }
        List<FactionLocalProductionRecommendation> canonicalRecommendations = new ArrayList<>(
                Objects.requireNonNull(recommendations, "Local production recommendations not set"));
        canonicalRecommendations.sort(null);
        TreeSet<String> recommendedItems = new TreeSet<>();
        for (FactionLocalProductionRecommendation recommendation : canonicalRecommendations) {
            FactionLocalProductionRecommendation checked = Objects.requireNonNull(
                    recommendation, "Local production recommendation not set");
            if (!recommendedItems.add(checked.itemContentId())) {
                throw new IllegalArgumentException(
                        "Duplicate local production recommendation item: " + checked.itemContentId());
            }
        }
        TreeSet<String> gaps = new TreeSet<>();
        for (String item : Objects.requireNonNull(capacityGapItemContentIds, "Capacity gaps not set")) {
            String checked = requireId(item, "Capacity gap item content ID");
            if (recommendedItems.contains(checked)) {
                throw new IllegalArgumentException("Item cannot be both recommended and a capacity gap: " + checked);
            }
            if (!gaps.add(checked)) {
                throw new IllegalArgumentException("Duplicate local production capacity gap: " + checked);
            }
        }
        recommendations = List.copyOf(canonicalRecommendations);
        capacityGapItemContentIds = List.copyOf(gaps);
    }

    /**
     * Merges existing-capacity recommendations into the common Stage-17F.4 authoring value.
     *
     * <p>Stock floors are preserved. A recommendation intentionally replaces an existing production
     * preference for the same archetype, but does not mutate physical production until the ordinary
     * strategic-policy apply boundary is invoked.</p>
     *
     * @param current current persistent stock/production policy
     * @return merged common policy value
     */
    public FactionStockProductionPolicyState mergeRecommendedProduction(
            FactionStockProductionPolicyState current) {
        FactionStockProductionPolicyState checked = Objects.requireNonNull(current, "Current policy not set");
        Map<String, FactionProductionPolicyState> productionByArchetype = new TreeMap<>();
        for (FactionProductionPolicyState policy : checked.productionPolicies()) {
            productionByArchetype.put(policy.stationArchetypeContentId(), policy);
        }
        for (FactionLocalProductionRecommendation recommendation : recommendations) {
            productionByArchetype.put(
                    recommendation.stationArchetypeContentId(),
                    new FactionProductionPolicyState(
                            recommendation.stationArchetypeContentId(),
                            recommendation.recipeContentId()));
        }
        return new FactionStockProductionPolicyState(
                checked.stockPolicies(),
                List.copyOf(productionByArchetype.values()));
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}

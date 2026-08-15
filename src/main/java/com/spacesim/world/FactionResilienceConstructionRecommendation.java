package com.spacesim.world;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Explainable recommendation to close one resilience production-capacity gap through ordinary construction.
 *
 * <p>This value is only a plan. It contains the real catalog funding, materials and build time of an
 * existing constructible station archetype; creating/funding the project remains a separate explicit
 * command and completion still requires physical material delivery plus build time.</p>
 *
 * @param factionContentId faction that would own and fund the project
 * @param observationTick authoritative world tick used to produce the recommendation
 * @param systemId controlled system containing the measured own-market deficit
 * @param itemContentId critical commodity whose domestic capacity is missing
 * @param ownedMarketDeficitUnits measured deficit across the faction's own markets in the target system
 * @param stationArchetypeContentId real constructible producer archetype
 * @param outputUnitsPerCycle physical output of the critical item per production cycle
 * @param fundingMilliCredits minimum real project funding
 * @param buildSeconds physical build duration after all construction materials arrive
 * @param materials required construction materials by stable item content ID
 * @param expectedUtilityScore shared Stage-9/17F deterministic producer-candidate score
 */
public record FactionResilienceConstructionRecommendation(
        String factionContentId,
        long observationTick,
        StarSystemId systemId,
        String itemContentId,
        long ownedMarketDeficitUnits,
        String stationArchetypeContentId,
        int outputUnitsPerCycle,
        long fundingMilliCredits,
        float buildSeconds,
        Map<String, Integer> materials,
        long expectedUtilityScore) {

    /**
     * Validates and canonicalizes one immutable construction recommendation.
     *
     * @param factionContentId stable owner faction ID
     * @param observationTick authoritative observation tick
     * @param systemId target controlled system
     * @param itemContentId critical output item ID
     * @param ownedMarketDeficitUnits positive physical own-market deficit
     * @param stationArchetypeContentId constructible producer archetype ID
     * @param outputUnitsPerCycle positive critical-item output per cycle
     * @param fundingMilliCredits positive project funding requirement
     * @param buildSeconds positive finite build duration
     * @param materials positive construction material requirements
     * @param expectedUtilityScore non-negative shared candidate score
     */
    public FactionResilienceConstructionRecommendation {
        factionContentId = requireId(factionContentId, "Faction content ID");
        Objects.requireNonNull(systemId, "Target system not set");
        itemContentId = requireId(itemContentId, "Item content ID");
        stationArchetypeContentId = requireId(stationArchetypeContentId, "Station archetype content ID");
        if (observationTick < 0L) {
            throw new IllegalArgumentException("Observation tick cannot be negative");
        }
        if (ownedMarketDeficitUnits <= 0L
                || outputUnitsPerCycle <= 0
                || fundingMilliCredits <= 0L
                || expectedUtilityScore < 0L) {
            throw new IllegalArgumentException("Construction recommendation quantities are invalid");
        }
        if (!Float.isFinite(buildSeconds) || buildSeconds <= 0f) {
            throw new IllegalArgumentException("Construction build time must be finite and positive");
        }
        TreeMap<String, Integer> canonicalMaterials = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(materials, "Materials not set").entrySet()) {
            String itemId = requireId(entry.getKey(), "Construction material item ID");
            Integer amount = Objects.requireNonNull(entry.getValue(), "Construction material amount not set");
            if (amount <= 0) {
                throw new IllegalArgumentException("Construction material amount must be positive");
            }
            canonicalMaterials.put(itemId, amount);
        }
        if (canonicalMaterials.isEmpty()) {
            throw new IllegalArgumentException("Construction recommendation requires materials");
        }
        materials = Collections.unmodifiableMap(canonicalMaterials);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}

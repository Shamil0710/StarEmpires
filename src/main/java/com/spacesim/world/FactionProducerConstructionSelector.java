package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared deterministic selector for physical station archetypes that can produce one commodity.
 *
 * <p>Stage-9 shortage recovery and Stage-17F.5 resilience construction use this same catalog rule so
 * they cannot silently disagree about which constructible producer is preferred. Selection only
 * describes a real data-driven station archetype; it never creates, funds or completes a project.</p>
 */
final class FactionProducerConstructionSelector {
    private FactionProducerConstructionSelector() {
        throw new AssertionError("Utility class");
    }

    /**
     * Finds the preferred constructible producer for one physical demand signal.
     *
     * <p>Faction-native archetypes remain preferred for continuity with the Stage-9 investment policy;
     * within the same native/non-native class, higher output-per-funded-credit utility wins with a
     * stable content-ID tie-break.</p>
     *
     * @param content authoritative semantic catalog
     * @param factionContentId faction requesting the producer
     * @param itemContentId required output item
     * @param demandUnits positive measured demand/shortfall used only for candidate utility ordering
     * @return best real constructible producer, or empty when the catalog has no producer
     */
    static Optional<Candidate> bestCandidate(
            ContentCatalog content,
            String factionContentId,
            String itemContentId,
            long demandUnits) {
        ContentCatalog checkedContent = Objects.requireNonNull(content, "ContentCatalog not set");
        String factionId = requireId(factionContentId, "Faction content ID");
        String itemId = requireId(itemContentId, "Item content ID");
        if (demandUnits <= 0L) {
            return Optional.empty();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (ContentCatalog.StationArchetypeDefinition station : checkedContent.getStationArchetypes()) {
            if (station.construction() == null || station.recipeId() == null) {
                continue;
            }
            ContentCatalog.RecipeDefinition recipe = checkedContent.findRecipe(station.recipeId());
            int output = recipe == null ? 0 : recipe.outputs().getOrDefault(itemId, 0);
            if (output <= 0) {
                continue;
            }
            long funding = Money.fromCredits(station.construction().fundingCredits());
            candidates.add(new Candidate(
                    station,
                    recipe,
                    output,
                    funding,
                    utilityScore(demandUnits, output, funding),
                    station.factionId().equals(factionId)));
        }
        Comparator<Candidate> utilityOrder = Comparator
                .comparingLong(Candidate::expectedUtilityScore).reversed();
        candidates.sort(Comparator
                .comparing(Candidate::nativeFaction).reversed()
                .thenComparing(utilityOrder)
                .thenComparing(candidate -> candidate.station().id()));
        return candidates.stream().findFirst();
    }

    private static long utilityScore(long demandUnits, int outputPerCycle, long fundingMilliCredits) {
        if (demandUnits <= 0L || outputPerCycle <= 0 || fundingMilliCredits <= 0L) {
            return 0L;
        }
        long numerator;
        try {
            numerator = Math.multiplyExact(
                    Math.multiplyExact(demandUnits, outputPerCycle),
                    1_000_000L);
        } catch (ArithmeticException exception) {
            numerator = Long.MAX_VALUE;
        }
        return numerator / fundingMilliCredits;
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }

    /**
     * One real constructible producer candidate.
     *
     * @param station station archetype with physical construction requirements
     * @param recipe canonical production recipe declared by the station archetype
     * @param outputUnitsPerCycle positive output of the requested item per cycle
     * @param fundingMilliCredits minimum real project funding
     * @param expectedUtilityScore deterministic demand/output/funding ordering score
     * @param nativeFaction whether the archetype's authored bootstrap faction matches the requester
     */
    record Candidate(
            ContentCatalog.StationArchetypeDefinition station,
            ContentCatalog.RecipeDefinition recipe,
            int outputUnitsPerCycle,
            long fundingMilliCredits,
            long expectedUtilityScore,
            boolean nativeFaction) {

        /**
         * Validates one selector result.
         *
         * @param station constructible station archetype
         * @param recipe canonical station recipe
         * @param outputUnitsPerCycle positive output units per cycle
         * @param fundingMilliCredits positive minimum project funding
         * @param expectedUtilityScore non-negative ordering score
         * @param nativeFaction whether the station is faction-native in authored content
         */
        Candidate {
            Objects.requireNonNull(station, "Station candidate not set");
            Objects.requireNonNull(recipe, "Recipe candidate not set");
            if (outputUnitsPerCycle <= 0 || fundingMilliCredits <= 0L || expectedUtilityScore < 0L) {
                throw new IllegalArgumentException("Producer construction candidate values are invalid");
            }
        }
    }
}

package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable explainable Stage-11A expansion candidate.
 *
 * @param factionContentId faction evaluating expansion
 * @param sourceSystemId nearest controlled source system
 * @param targetSystemId candidate target system
 * @param controllingFactionContentId current target controller, empty string when unclaimed
 * @param path authoritative Stage-10B-compatible jump path
 * @param anchorStationArchetypeContentId proposed constructible anchor archetype
 * @param constructionFundingMilliCredits physical treasury funding requirement of the anchor
 * @param remainingMineableUnits live remaining asteroid resources in the target system
 * @param unmetDemandUnits live aggregate market target-stock deficit in the target system
 * @param marketCount current physical markets in the target system
 * @param hostileNeighborPressure sum of negative diplomatic relations around the target
 * @param utilityScore normalized deterministic score; higher is preferred
 */
public record ExpansionOpportunity(
        String factionContentId,
        StarSystemId sourceSystemId,
        StarSystemId targetSystemId,
        String controllingFactionContentId,
        GalacticPath path,
        String anchorStationArchetypeContentId,
        long constructionFundingMilliCredits,
        long remainingMineableUnits,
        long unmetDemandUnits,
        int marketCount,
        int hostileNeighborPressure,
        long utilityScore) {

    /**
     * Validates one explainable expansion candidate.
     *
     * @param factionContentId evaluating faction
     * @param sourceSystemId controlled source
     * @param targetSystemId target system
     * @param controllingFactionContentId target controller or empty string
     * @param path authoritative path
     * @param anchorStationArchetypeContentId proposed anchor station archetype
     * @param constructionFundingMilliCredits positive funding requirement
     * @param remainingMineableUnits non-negative physical resources
     * @param unmetDemandUnits non-negative target-stock deficit
     * @param marketCount non-negative market count
     * @param hostileNeighborPressure non-negative diplomatic pressure
     * @param utilityScore non-negative normalized score
     */
    public ExpansionOpportunity {
        factionContentId = normalizedId(factionContentId, "Faction content ID");
        Objects.requireNonNull(sourceSystemId, "Source StarSystemId not set");
        Objects.requireNonNull(targetSystemId, "Target StarSystemId not set");
        controllingFactionContentId = controllingFactionContentId == null
                ? "" : controllingFactionContentId.strip();
        Objects.requireNonNull(path, "GalacticPath not set");
        anchorStationArchetypeContentId = normalizedId(anchorStationArchetypeContentId, "Anchor archetype ID");
        if (sourceSystemId.equals(targetSystemId) || path.jumpCount() <= 0) {
            throw new IllegalArgumentException("Expansion opportunity requires a remote target");
        }
        if (constructionFundingMilliCredits <= 0L || remainingMineableUnits < 0L || unmetDemandUnits < 0L
                || marketCount < 0 || hostileNeighborPressure < 0 || utilityScore < 0L) {
            throw new IllegalArgumentException("Expansion opportunity contains invalid negative/zero metrics");
        }
    }

    /** @return true when another faction already controls the target */
    public boolean foreignControlled() {
        return !controllingFactionContentId.isEmpty()
                && !controllingFactionContentId.equals(factionContentId);
    }

    private static String normalizedId(String value, String label) {
        String result = Objects.requireNonNull(value, label + " not set").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return result;
    }
}

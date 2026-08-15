package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only directed economic-dependence snapshot for {@code source → partner}.
 *
 * <p>Stage 17E.7 deliberately reports structural/current dependence rather than pretending to know
 * historical import/export shares that are not yet persisted. Aggregate percentages are weighted by
 * current stock gaps or exportable surplus. Exact rows preserve the underlying physical evidence for
 * AI diagnostics and later UI explanation.</p>
 *
 * @param sourceFactionContentId faction whose dependence is being measured
 * @param partnerFactionContentId potential supplier/market partner
 * @param observationTick authoritative world tick used for the snapshot
 * @param confidenceBasisPoints confidence of this authoritative observation, currently 10000
 * @param structuralImportDependenceBasisPoints weighted share of current external requirement coverable by the partner
 * @param structuralExportMarketDependenceBasisPoints weighted share of accessible foreign demand represented by the partner
 * @param estimatedCurrentAccessLossPremiumMilliCredits current-gap replacement price premium summed across commodities
 * @param currentUncoveredUnitsAfterAccessLoss physical current-gap units left uncovered after removing partner access
 * @param uniqueShortestCorridorCriticalItems number of externally required items whose best partner supply route is topologically unique
 * @param items canonical per-item evidence rows
 */
public record FactionEconomicDependenceDiagnostics(
        String sourceFactionContentId,
        String partnerFactionContentId,
        long observationTick,
        int confidenceBasisPoints,
        int structuralImportDependenceBasisPoints,
        int structuralExportMarketDependenceBasisPoints,
        long estimatedCurrentAccessLossPremiumMilliCredits,
        long currentUncoveredUnitsAfterAccessLoss,
        int uniqueShortestCorridorCriticalItems,
        List<FactionItemDependenceDiagnostic> items) {

    /**
     * Validates and canonicalizes one pair-level diagnostics snapshot.
     *
     * @param sourceFactionContentId faction whose dependence is measured
     * @param partnerFactionContentId supplier/market partner being evaluated
     * @param observationTick authoritative observation tick
     * @param confidenceBasisPoints observation confidence, 0..10000
     * @param structuralImportDependenceBasisPoints current structural import dependence, 0..10000
     * @param structuralExportMarketDependenceBasisPoints current structural export-market dependence, 0..10000
     * @param estimatedCurrentAccessLossPremiumMilliCredits current replacement price premium
     * @param currentUncoveredUnitsAfterAccessLoss units left uncovered after partner loss
     * @param uniqueShortestCorridorCriticalItems critical items exposed to a unique shortest corridor
     * @param items canonical per-item evidence rows
     */
    public FactionEconomicDependenceDiagnostics {
        sourceFactionContentId = requireId(sourceFactionContentId, "Source faction ID");
        partnerFactionContentId = requireId(partnerFactionContentId, "Partner faction ID");
        if (sourceFactionContentId.equals(partnerFactionContentId)) {
            throw new IllegalArgumentException("Economic dependence requires two different factions");
        }
        if (observationTick < 0L) {
            throw new IllegalArgumentException("Observation tick cannot be negative");
        }
        requireBasisPoints(confidenceBasisPoints, "confidenceBasisPoints");
        requireBasisPoints(structuralImportDependenceBasisPoints, "structuralImportDependenceBasisPoints");
        requireBasisPoints(structuralExportMarketDependenceBasisPoints, "structuralExportMarketDependenceBasisPoints");
        if (estimatedCurrentAccessLossPremiumMilliCredits < 0L || currentUncoveredUnitsAfterAccessLoss < 0L) {
            throw new IllegalArgumentException("Economic loss diagnostics cannot be negative");
        }
        if (uniqueShortestCorridorCriticalItems < 0) {
            throw new IllegalArgumentException("Unique corridor item count cannot be negative");
        }
        Objects.requireNonNull(items, "Item dependence diagnostics not set");
        List<FactionItemDependenceDiagnostic> sorted = new ArrayList<>(items.size());
        for (FactionItemDependenceDiagnostic item : items) {
            sorted.add(Objects.requireNonNull(item, "Item dependence diagnostic not set"));
        }
        sorted.sort(null);
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).itemContentId().equals(sorted.get(index).itemContentId())) {
                throw new IllegalArgumentException("Duplicate item dependence diagnostic: "
                        + sorted.get(index).itemContentId());
            }
        }
        items = List.copyOf(sorted);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000");
        }
    }
}

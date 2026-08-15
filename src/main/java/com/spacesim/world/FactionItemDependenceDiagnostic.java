package com.spacesim.world;

import java.util.Objects;

/**
 * Read-only current-snapshot structural dependence for one commodity in one directed faction pair.
 *
 * <p>These values are not historical trade shares. They describe what the authoritative physical
 * world can prove now: faction stock/targets, active production inputs, accessible market surplus,
 * current quoted prices and topology. A later intelligence layer may reduce confidence/freshness
 * without changing this contract.</p>
 *
 * @param itemContentId stable commodity content ID
 * @param sourceRequiredStockUnits current faction-wide required/target stock signal
 * @param sourceOnHandUnits physical stock at source-faction market stations
 * @param sourceProductionInputPerCycleUnits input units consumed by current active source production per cycle
 * @param bufferEnduranceCycles whole active-production cycles supportable by current stock, or -1 when no current consumption rate is observable
 * @param currentExternalRequirementUnits current stock gap relative to required stock
 * @param partnerPhysicalSurplusUnits physical partner-market stock above local target regardless of legal access
 * @param partnerAccessibleSurplusUnits partner surplus currently reachable through legal market access
 * @param alternativeAccessibleSurplusUnits accessible surplus at other foreign markets
 * @param partnerSupplyShareBasisPoints partner share of currently accessible foreign surplus, 0..10000
 * @param partnerCoverageBasisPoints share of current external requirement coverable by partner surplus, 0..10000
 * @param partnerBestUnitSellPriceMilliCredits best current partner quote for one unit, or -1 when unavailable
 * @param alternativeBestUnitSellPriceMilliCredits best current alternative quote for one unit, or -1 when unavailable
 * @param estimatedReplacementPremiumMilliCredits current-gap price premium if partner access is lost and an alternative exists
 * @param uncoveredUnitsAfterPartnerLoss current requirement left uncovered after removing partner supply
 * @param sourceExportableSurplusUnits source-market physical surplus above local targets
 * @param partnerAccessibleDemandUnits current partner-market deficit accessible to the source
 * @param otherAccessibleForeignDemandUnits current accessible demand at other foreign markets
 * @param partnerDemandShareBasisPoints partner share of currently accessible foreign market deficit, 0..10000
 * @param bestPartnerRouteHops shortest topology hop count from source economic footprint to a partner supplier, or -1
 * @param bestAlternativeRouteHops shortest topology hop count to an alternative supplier, or -1
 * @param uniquePartnerShortestRoute whether the best partner supplier has exactly one shortest topology route from the source footprint
 * @param uniquePartnerCorridorIntermediateSystems intermediate systems on that unique shortest route, otherwise zero
 */
public record FactionItemDependenceDiagnostic(
        String itemContentId,
        long sourceRequiredStockUnits,
        long sourceOnHandUnits,
        long sourceProductionInputPerCycleUnits,
        long bufferEnduranceCycles,
        long currentExternalRequirementUnits,
        long partnerPhysicalSurplusUnits,
        long partnerAccessibleSurplusUnits,
        long alternativeAccessibleSurplusUnits,
        int partnerSupplyShareBasisPoints,
        int partnerCoverageBasisPoints,
        long partnerBestUnitSellPriceMilliCredits,
        long alternativeBestUnitSellPriceMilliCredits,
        long estimatedReplacementPremiumMilliCredits,
        long uncoveredUnitsAfterPartnerLoss,
        long sourceExportableSurplusUnits,
        long partnerAccessibleDemandUnits,
        long otherAccessibleForeignDemandUnits,
        int partnerDemandShareBasisPoints,
        int bestPartnerRouteHops,
        int bestAlternativeRouteHops,
        boolean uniquePartnerShortestRoute,
        int uniquePartnerCorridorIntermediateSystems)
        implements Comparable<FactionItemDependenceDiagnostic> {

    /**
     * Validates one deterministic diagnostic row.
     *
     * @param itemContentId stable commodity content ID
     * @param sourceRequiredStockUnits current faction-wide required/target stock signal
     * @param sourceOnHandUnits physical stock at source-faction market stations
     * @param sourceProductionInputPerCycleUnits current active production input per cycle
     * @param bufferEnduranceCycles observable whole-cycle stock endurance, or -1
     * @param currentExternalRequirementUnits current required-stock gap
     * @param partnerPhysicalSurplusUnits partner physical market surplus
     * @param partnerAccessibleSurplusUnits legally accessible partner surplus
     * @param alternativeAccessibleSurplusUnits legally accessible non-partner foreign surplus
     * @param partnerSupplyShareBasisPoints partner share of accessible foreign surplus
     * @param partnerCoverageBasisPoints current requirement coverable by partner
     * @param partnerBestUnitSellPriceMilliCredits best accessible partner quote, or -1
     * @param alternativeBestUnitSellPriceMilliCredits best accessible alternative quote, or -1
     * @param estimatedReplacementPremiumMilliCredits current replacement price premium
     * @param uncoveredUnitsAfterPartnerLoss units still uncovered after partner loss
     * @param sourceExportableSurplusUnits source physical exportable surplus
     * @param partnerAccessibleDemandUnits accessible current partner demand
     * @param otherAccessibleForeignDemandUnits accessible current other foreign demand
     * @param partnerDemandShareBasisPoints partner share of accessible foreign demand
     * @param bestPartnerRouteHops best topology route to partner supply, or -1
     * @param bestAlternativeRouteHops best topology route to alternative supply, or -1
     * @param uniquePartnerShortestRoute whether the best partner route is unique
     * @param uniquePartnerCorridorIntermediateSystems intermediate systems on that unique route
     */
    public FactionItemDependenceDiagnostic {
        itemContentId = requireId(itemContentId);
        requireNonNegative(sourceRequiredStockUnits, "sourceRequiredStockUnits");
        requireNonNegative(sourceOnHandUnits, "sourceOnHandUnits");
        requireNonNegative(sourceProductionInputPerCycleUnits, "sourceProductionInputPerCycleUnits");
        if (bufferEnduranceCycles < -1L) {
            throw new IllegalArgumentException("bufferEnduranceCycles must be -1 or non-negative");
        }
        requireNonNegative(currentExternalRequirementUnits, "currentExternalRequirementUnits");
        requireNonNegative(partnerPhysicalSurplusUnits, "partnerPhysicalSurplusUnits");
        requireNonNegative(partnerAccessibleSurplusUnits, "partnerAccessibleSurplusUnits");
        requireNonNegative(alternativeAccessibleSurplusUnits, "alternativeAccessibleSurplusUnits");
        requireBasisPoints(partnerSupplyShareBasisPoints, "partnerSupplyShareBasisPoints");
        requireBasisPoints(partnerCoverageBasisPoints, "partnerCoverageBasisPoints");
        if (partnerBestUnitSellPriceMilliCredits < -1L || alternativeBestUnitSellPriceMilliCredits < -1L) {
            throw new IllegalArgumentException("Unit prices must be -1 or non-negative");
        }
        requireNonNegative(estimatedReplacementPremiumMilliCredits, "estimatedReplacementPremiumMilliCredits");
        requireNonNegative(uncoveredUnitsAfterPartnerLoss, "uncoveredUnitsAfterPartnerLoss");
        requireNonNegative(sourceExportableSurplusUnits, "sourceExportableSurplusUnits");
        requireNonNegative(partnerAccessibleDemandUnits, "partnerAccessibleDemandUnits");
        requireNonNegative(otherAccessibleForeignDemandUnits, "otherAccessibleForeignDemandUnits");
        requireBasisPoints(partnerDemandShareBasisPoints, "partnerDemandShareBasisPoints");
        if (bestPartnerRouteHops < -1 || bestAlternativeRouteHops < -1) {
            throw new IllegalArgumentException("Route hops must be -1 or non-negative");
        }
        if (uniquePartnerCorridorIntermediateSystems < 0) {
            throw new IllegalArgumentException("Unique partner corridor count cannot be negative");
        }
        if (!uniquePartnerShortestRoute && uniquePartnerCorridorIntermediateSystems != 0) {
            throw new IllegalArgumentException("Non-unique partner route cannot expose a unique corridor count");
        }
    }

    @Override
    public int compareTo(FactionItemDependenceDiagnostic other) {
        return itemContentId.compareTo(Objects.requireNonNull(other, "Diagnostic not set").itemContentId);
    }

    private static String requireId(String value) {
        String normalized = Objects.requireNonNull(value, "Item content ID not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Item content ID cannot be blank");
        }
        return normalized;
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000");
        }
    }
}

package com.spacesim.world.calibration;

/**
 * Versioned Stage-20E acceptance policy for ordinary procedural faction-start candidates.
 *
 * <p>The profile is a generation quality gate, not a faction bonus. It never changes production,
 * prices, reserves, route capacity or runtime doctrine. Thresholds only decide whether already
 * generated physical/economic state is acceptable as an ordinary procedural start and how far
 * independently selected starts must be separated in the ordinary jump graph.</p>
 *
 * <p>The v1 concentration thresholds are intentionally broad structural safety bounds rather than
 * final balance targets. They are retained as explicit versioned calibration and remain subject to
 * Stage-22 balance review. Monetary delivered-cost, inventory-buffer and ownership authorities are
 * not yet mandatory in the current v1 profile; diagnostics must still preserve their unresolved
 * state so a later profile can tighten those gates without inventing values.</p>
 *
 * @param version stable profile version
 * @param dominantImportDependencyFraction import share at which redundancy requirements tighten
 * @param maximumSupplierConcentrationHhi maximum supplier-capacity HHI for import-dominant essentials
 * @param maximumRouteConcentrationHhi maximum final-gateway HHI for import-dominant essentials
 * @param maximumCriticalGatewayDependencyFraction maximum largest final-gateway share
 * @param maximumAccessibleReserveConcentrationHhi maximum recoverable-source HHI for import-dominant essentials
 * @param minimumExternalSuppliersForAnyImport minimum external suppliers for any import-dependent essential
 * @param minimumExternalSuppliersForDominantImport minimum external suppliers for import-dominant essentials
 * @param minimumAlternativePathsForDominantImport minimum proven edge-disjoint path floor for dominant imports
 * @param minimumFactionStartHopSeparation minimum ordinary jump-edge separation between selected starts
 * @param maximumSearchNodes bounded deterministic placement-search budget
 * @param requireDeliveredCostAuthority whether missing delivered-cost authority blocks acceptance
 * @param requireBufferAuthority whether missing physical buffer authority blocks acceptance
 * @param requireOwnershipAuthority whether missing reserve ownership authority blocks acceptance
 * @param stage22ReviewRequired whether these generation-quality thresholds require later balance review
 */
public record Stage20FactionStartAcceptanceProfile(
        String version,
        double dominantImportDependencyFraction,
        double maximumSupplierConcentrationHhi,
        double maximumRouteConcentrationHhi,
        double maximumCriticalGatewayDependencyFraction,
        double maximumAccessibleReserveConcentrationHhi,
        int minimumExternalSuppliersForAnyImport,
        int minimumExternalSuppliersForDominantImport,
        int minimumAlternativePathsForDominantImport,
        int minimumFactionStartHopSeparation,
        int maximumSearchNodes,
        boolean requireDeliveredCostAuthority,
        boolean requireBufferAuthority,
        boolean requireOwnershipAuthority,
        boolean stage22ReviewRequired) {

    /** Current Stage-20E faction-start acceptance profile version. */
    public static final String CURRENT_VERSION = "stage20e.faction-start-acceptance.v1";

    /**
     * Validates one immutable generation acceptance profile.
     *
     * @param version stable profile version
     * @param dominantImportDependencyFraction import share at which redundancy requirements tighten
     * @param maximumSupplierConcentrationHhi maximum supplier-capacity HHI
     * @param maximumRouteConcentrationHhi maximum final-gateway HHI
     * @param maximumCriticalGatewayDependencyFraction maximum largest final-gateway share
     * @param maximumAccessibleReserveConcentrationHhi maximum recoverable-source HHI
     * @param minimumExternalSuppliersForAnyImport minimum suppliers for any import dependence
     * @param minimumExternalSuppliersForDominantImport minimum suppliers for dominant import dependence
     * @param minimumAlternativePathsForDominantImport minimum edge-disjoint path floor for dominant imports
     * @param minimumFactionStartHopSeparation minimum selected-start hop separation
     * @param maximumSearchNodes bounded placement-search budget
     * @param requireDeliveredCostAuthority whether delivered-cost authority is mandatory
     * @param requireBufferAuthority whether buffer authority is mandatory
     * @param requireOwnershipAuthority whether ownership authority is mandatory
     * @param stage22ReviewRequired whether later balance review is required
     */
    public Stage20FactionStartAcceptanceProfile {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        requireUnitFraction(dominantImportDependencyFraction, "dominantImportDependencyFraction");
        if (dominantImportDependencyFraction <= 0d) {
            throw new IllegalArgumentException("dominantImportDependencyFraction must be positive");
        }
        requireUnitFraction(maximumSupplierConcentrationHhi, "maximumSupplierConcentrationHhi");
        requireUnitFraction(maximumRouteConcentrationHhi, "maximumRouteConcentrationHhi");
        requireUnitFraction(maximumCriticalGatewayDependencyFraction, "maximumCriticalGatewayDependencyFraction");
        requireUnitFraction(maximumAccessibleReserveConcentrationHhi, "maximumAccessibleReserveConcentrationHhi");
        if (minimumExternalSuppliersForAnyImport < 1) {
            throw new IllegalArgumentException("minimumExternalSuppliersForAnyImport must be positive");
        }
        if (minimumExternalSuppliersForDominantImport < minimumExternalSuppliersForAnyImport) {
            throw new IllegalArgumentException("dominant-import supplier minimum cannot be weaker");
        }
        if (minimumAlternativePathsForDominantImport < 1 || minimumAlternativePathsForDominantImport > 2) {
            throw new IllegalArgumentException("minimumAlternativePathsForDominantImport must be in 1..2");
        }
        if (minimumFactionStartHopSeparation < 1) {
            throw new IllegalArgumentException("minimumFactionStartHopSeparation must be positive");
        }
        if (maximumSearchNodes < 1) {
            throw new IllegalArgumentException("maximumSearchNodes must be positive");
        }
    }

    /**
     * Returns the current conservative Stage-20E v1 ordinary-generation profile.
     *
     * <p>The profile rejects physical throughput deficits outright. For essentials that depend on
     * imports for at least half their sustained requirement, it also rejects strong supplier,
     * gateway or finite-reserve concentration and requires two external suppliers plus a proven
     * second edge-disjoint route. Optional monetary/buffer/ownership authorities remain diagnostic
     * until their upstream Stage-20 bootstrap authorities are closed.</p>
     *
     * @return current versioned acceptance profile
     */
    public static Stage20FactionStartAcceptanceProfile current() {
        return new Stage20FactionStartAcceptanceProfile(
                CURRENT_VERSION,
                0.50d,
                0.80d,
                0.80d,
                0.80d,
                0.80d,
                1,
                2,
                2,
                2,
                10_000,
                false,
                false,
                false,
                true);
    }

    private static void requireUnitFraction(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be finite and in [0,1]");
        }
    }
}

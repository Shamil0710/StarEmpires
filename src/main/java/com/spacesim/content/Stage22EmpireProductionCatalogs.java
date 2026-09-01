package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.ProductionManifestDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic M22.3 production-manifest and exact-fit visual binding projection.
 *
 * <p>All physical inputs are resolved from existing engineering, manufacturing, facility and shipyard
 * authorities. The projection owns no runtime production or visual state.</p>
 */
public final class Stage22EmpireProductionCatalogs {
    /** Single reviewed Empire construction/service yard for the M22.3 gold slice. */
    public static final String YARD_ID = "yard.empire_capital_service_v1";

    private Stage22EmpireProductionCatalogs() {
        throw new AssertionError("utility class");
    }

    /**
     * Builds one exact primary-fit manifest per required Empire ship family using the M22.2 manifest
     * format.
     *
     * @return deterministic nine-manifest catalog
     */
    public static Stage22CoreProductionManifestCatalog loadManifests() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        Stage18ShipyardCatalog shipyards = Stage22EmpireShipyardCatalogLoader.loadDefault();
        var yard = Objects.requireNonNull(shipyards.findYard(YARD_ID), "Empire production yard");
        List<ProductionManifestDefinition> manifests = new ArrayList<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            DemonstratorFitDefinition fit = requireFit(engineering, family.primaryFitId());
            manifests.add(new ProductionManifestDefinition(
                    family.productionManifestId(),
                    fit.id(),
                    fit.hullId(),
                    fit.installedModules().stream().map(value -> value.moduleId()).toList(),
                    YARD_ID,
                    yard.requiredSupportFacilityDefinitionIds().stream().sorted().toList(),
                    ContentMaturity.VALIDATED,
                    "M22.3 Empire primary fit uses ordinary Stage-17.5 engineering and Stage-18 manufacturing/shipyard paths."));
        }
        return new Stage22CoreProductionManifestCatalog(
                1, "stage22.empire_production_manifests.v1", manifests, List.of());
    }

    /**
     * Builds exact-fit visual bindings for primary and refit definitions. The Stage-22 assets are
     * engineering-approved silhouette references; final presentation replacement remains Stage 23.
     *
     * @return deterministic visual bindings for all 18 legal fits
     */
    public static List<VisualBindingDefinition> loadVisualBindings() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        List<VisualBindingDefinition> result = new ArrayList<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            String assetRef = assetRef(family.familyId());
            result.add(binding(
                    family.visualBindingId(), family.primaryFitId(), assetRef, engineering));
            result.add(binding(
                    family.visualBindingId() + ".refit", family.refitFitId(), assetRef, engineering));
        }
        result.sort(java.util.Comparator.comparing(VisualBindingDefinition::id));
        return List.copyOf(result);
    }

    private static VisualBindingDefinition binding(
            String id,
            String fitId,
            String assetRef,
            ShipEngineeringCatalog engineering) {
        requireFit(engineering, fitId);
        return new VisualBindingDefinition(
                id,
                fitId,
                AssetStatus.ENGINEERING_APPROVED,
                Stage22FitFingerprint.compute(engineering, fitId),
                assetRef,
                "docs/factions/empire_visual_bible.md");
    }

    private static String assetRef(String familyId) {
        String suffix = familyId.substring("ship_family.empire.".length());
        return "assets/ships/empire/" + suffix + "_silhouette.svg";
    }

    private static DemonstratorFitDefinition requireFit(ShipEngineeringCatalog engineering, String fitId) {
        DemonstratorFitDefinition fit = engineering.findDemonstratorFit(fitId);
        if (fit == null) {
            throw new IllegalArgumentException("Unknown Empire engineering fit: " + fitId);
        }
        return fit;
    }
}

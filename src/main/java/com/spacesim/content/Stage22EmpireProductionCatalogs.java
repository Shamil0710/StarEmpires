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
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic M22.3 production-manifest and exact-fit visual binding projection.
 *
 * <p>All physical inputs are resolved from existing engineering, manufacturing, facility and shipyard
 * authorities. The projection owns no runtime production or visual state. Production visual bindings
 * pin reviewed literal fit fingerprints so later engineering drift invalidates art instead of silently
 * refreshing its expected fingerprint.</p>
 */
public final class Stage22EmpireProductionCatalogs {
    /** Single reviewed Empire construction/service yard for the M22.3 gold slice. */
    public static final String YARD_ID = "yard.empire_capital_service_v1";

    private static final Map<String, String> FIT_FINGERPRINT_PINS = Map.ofEntries(
            Map.entry("fit.empire.battleship.line_v1", "f3965723e62befdbb5b8b32f356b90d916a1f92baa61095898fc457db97f62f3"),
            Map.entry("fit.empire.battleship.siege_refit_v1", "8f55e7cf383e7fd3c79042816d06f24e817d285cb22a9f4ec1a22bf71462deaf"),
            Map.entry("fit.empire.carrier.fleet_v1", "392f40f6c4f736a40703cb6c381b9c17e964c68beac825b54b4feed96efb8467"),
            Map.entry("fit.empire.carrier.support_refit_v1", "2dbe613a607f8093cb6a1028bca848f0ad6099ee1c7b3bc27cb6fb5a5c2d219f"),
            Map.entry("fit.empire.corvette.line_v1", "58c83819737c1e4dd0871f8681bbd525e64eebce3bbd86cb1806a8664db35c66"),
            Map.entry("fit.empire.corvette.escort_refit_v1", "9eda7db41c9ddb5930a50d5a72c2676e8f09b7d88f1522449a41c169a525e4fa"),
            Map.entry("fit.empire.cruiser.command_v1", "a32cc07aebe95f512407a26c88c723fb5e35aed4a0aa6ca1270add530deb9808"),
            Map.entry("fit.empire.cruiser.line_refit_v1", "c6ae1158854bce7d111e1ff043b4d1e3ebe3a3a4f77728bc77d0207716d7e580"),
            Map.entry("fit.empire.destroyer.screen_v1", "199a4e9eaffcddf9f60f0ad875a77d657724834c46b9671042a864a567a50b57"),
            Map.entry("fit.empire.destroyer.strike_defense_refit_v1", "cd579fd7285a07239269ced0578b680ef7962fcc5477e4394451a72c64740c3e"),
            Map.entry("fit.empire.frigate.patrol_v1", "83ca6649c76fadad6235eee4d3f671d46fadfb4474cc9f463b90f4aeffabfba9"),
            Map.entry("fit.empire.frigate.recon_refit_v1", "66fb845c6957909b860b9179500da3b9312fab41aab36a0fa49d4c76583280b2"),
            Map.entry("fit.empire.fleet_support.repair_v1", "4d2c2e3a5694ac6b31f28a7a55a0b3a79c6f409856488bc6e8d33c6b881a4e52"),
            Map.entry("fit.empire.fleet_support.salvage_refit_v1", "09343813c508077b142a6e1ed0291408cb4c77e5111a5b79a5a89e08d59ee22a"),
            Map.entry("fit.empire.freight.bulk_v1", "25f7beb0ad76971097d91563635a7b1bad25d81544054c7156e84e02c3353c10"),
            Map.entry("fit.empire.freight.secure_refit_v1", "1f7043d00b4f0c5c09c49ebfb69f3cca854b1ebd1284595bb3c364413218e09b"),
            Map.entry("fit.empire.tanker.fleet_v1", "87dba036218a43d9220adf797bf4b024c792f067521e346bea96c0050b4928ed"),
            Map.entry("fit.empire.tanker.armored_refit_v1", "f5416ef503b07cc202851f82aa659a012ad33295a2d59a9189b1cded51f41b15"));

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
     * Builds exact-fit production visual bindings for primary and refit definitions.
     *
     * @return deterministic visual bindings for all 18 legal fits
     */
    public static List<VisualBindingDefinition> loadVisualBindings() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        List<VisualBindingDefinition> result = new ArrayList<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            String assetRef = assetRef(family.familyId());
            result.add(binding(family.visualBindingId(), family.primaryFitId(), assetRef, engineering));
            result.add(binding(family.visualBindingId() + ".refit", family.refitFitId(), assetRef, engineering));
        }
        if (FIT_FINGERPRINT_PINS.size() != result.size()) {
            throw new IllegalStateException("Empire fit fingerprint pin set must cover all visual bindings");
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
        String pinnedFingerprint = FIT_FINGERPRINT_PINS.get(fitId);
        if (pinnedFingerprint == null) {
            throw new IllegalStateException("Missing reviewed Empire fit fingerprint pin: " + fitId);
        }
        return new VisualBindingDefinition(
                id,
                fitId,
                AssetStatus.PRODUCTION,
                pinnedFingerprint,
                assetRef,
                "docs/factions/empire_visual_bible.md");
    }

    private static String assetRef(String familyId) {
        String suffix = familyId.substring("ship_family.empire.".length());
        return "assets/ships/empire/production/" + suffix + "/" + suffix + "_base.png";
    }

    private static DemonstratorFitDefinition requireFit(ShipEngineeringCatalog engineering, String fitId) {
        DemonstratorFitDefinition fit = engineering.findDemonstratorFit(fitId);
        if (fit == null) {
            throw new IllegalArgumentException("Unknown Empire engineering fit: " + fitId);
        }
        return fit;
    }
}

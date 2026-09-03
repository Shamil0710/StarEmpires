package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;

import java.util.Objects;

/**
 * Immutable M22.5 compatibility-to-production bridge for civilian mining traffic.
 *
 * <p>The old {@code ship.basic_miner} runtime archetype is preserved for supported worlds. New/replacement
 * physical availability is satisfied by licensing the already-reviewed Industrial Union fleet-support
 * workshop hull as a multi-role industrial/mining asset. The bridge creates no new production runtime,
 * faction doctrine or extraction state; it proves that the licensed fit is physically manufacturable
 * through the existing Stage-18/22 chain and that the Stage-18 asteroid extraction method remains the
 * operating authority.</p>
 */
public final class Stage22CivilianMiningProductionPath {
    public static final String LEGACY_RUNTIME_ARCHETYPE = "ship.basic_miner";
    public static final String LICENSED_FIT_ID = "fit.industrial_union.fleet_support.repair_v1";
    public static final String PRODUCTION_MANIFEST_ID = "production_manifest.industrial_union.fleet_support_v1";
    public static final String EXTRACTION_METHOD_ID = "extraction.asteroid_excavation";
    public static final String SOURCE_PACKAGE_KEY = Stage22IndustrialUnionPackageCatalog.PACKAGE_KEY;
    public static final String OPERATOR_FACTION_ID = "faction.miners";

    private Stage22CivilianMiningProductionPath() {
        throw new AssertionError("utility class");
    }

    /**
     * Validates the complete compatibility/runtime → licensed physical production → extraction support path.
     *
     * @return deterministic evidence for the M22.5 mining role
     */
    public static ValidationReport validateDefault() {
        ContentCatalog legacy = ContentCatalogLoader.loadDefault();
        if (legacy.findShipArchetype(LEGACY_RUNTIME_ARCHETYPE) == null) {
            throw new IllegalStateException("Missing preserved mining runtime archetype: " + LEGACY_RUNTIME_ARCHETYPE);
        }

        Stage22IndustrialUnionPackageCatalog union = Stage22IndustrialUnionPackageLoader.loadDefault();
        Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition family = null;
        for (Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition candidate : union.shipFamilies()) {
            if (!candidate.roleId().equals("role.support.fleet_logistics_repair_salvage")) {
                continue;
            }
            if (family != null) {
                throw new IllegalStateException("Industrial Union has duplicate industrial-support family");
            }
            family = candidate;
        }
        if (family == null) {
            throw new IllegalStateException("Industrial Union lacks the reviewed industrial-support family");
        }
        if (!LICENSED_FIT_ID.equals(family.primaryFitId())
                || !PRODUCTION_MANIFEST_ID.equals(family.productionManifestId())) {
            throw new IllegalStateException("M22.5 mining license no longer matches the reviewed core family");
        }

        ShipEngineeringCatalog engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog.DemonstratorFitDefinition fit = Objects.requireNonNull(
                engineering.findDemonstratorFit(LICENSED_FIT_ID), "licensed mining fit");
        ShipEngineeringCatalog.HullDefinition hull = Objects.requireNonNull(
                engineering.findHull(fit.hullId()), "licensed mining hull");

        boolean hasMiningIndustrialModule = false;
        Stage18ManufacturingCatalog manufacturing = Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault();
        Stage18ShipyardCatalog shipyards = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        if (shipyards.findHullProfile(hull.id()) == null) {
            throw new IllegalStateException("Licensed mining hull lacks Stage-18 physical shipyard profile: " + hull.id());
        }
        for (ShipEngineeringCatalog.InstalledModuleDefinition installed : fit.installedModules()) {
            ShipEngineeringCatalog.ModuleDefinition module = Objects.requireNonNull(
                    engineering.findModule(installed.moduleId()), "licensed mining module");
            if (module.family() == ModuleFamily.MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE) {
                hasMiningIndustrialModule = true;
            }
            if (manufacturing.findProductBinding(module.id()) == null) {
                throw new IllegalStateException("Licensed mining fit module lacks manufacturing binding: " + module.id());
            }
            if (shipyards.findModuleProfile(module.id()) == null) {
                throw new IllegalStateException("Licensed mining fit module lacks shipyard service profile: " + module.id());
            }
        }
        if (!hasMiningIndustrialModule) {
            throw new IllegalStateException("Licensed mining fit lacks the common mining/industrial module family");
        }

        Stage22CoreProductionManifestCatalog manifests = Stage22IndustrialUnionProductionCatalogs.loadManifests();
        Stage22CoreProductionManifestCatalog.ProductionManifestDefinition manifest = Objects.requireNonNull(
                manifests.findManifest(PRODUCTION_MANIFEST_ID), "licensed mining production manifest");
        if (!manifest.fitId().equals(LICENSED_FIT_ID) || !manifest.hullId().equals(hull.id())) {
            throw new IllegalStateException("Licensed mining production manifest no longer resolves exact fit/hull");
        }

        Stage18ExtractionCatalog.ExtractionMethodDefinition extraction = Objects.requireNonNull(
                Stage18ExtractionCatalogLoader.loadDefault().findMethod(EXTRACTION_METHOD_ID),
                "asteroid extraction method");
        if (extraction.requiredCapabilityTags().isEmpty()) {
            throw new IllegalStateException("Asteroid extraction method lost its explicit capability contract");
        }

        return new ValidationReport(
                LEGACY_RUNTIME_ARCHETYPE,
                LICENSED_FIT_ID,
                hull.id(),
                PRODUCTION_MANIFEST_ID,
                EXTRACTION_METHOD_ID,
                true,
                true,
                true);
    }

    /** Deterministic evidence that the mining role has compatibility, production and support closure. */
    public record ValidationReport(
            String legacyRuntimeArchetype,
            String licensedFitId,
            String hullId,
            String productionManifestId,
            String extractionMethodId,
            boolean miningIndustrialModulePresent,
            boolean productionPathReady,
            boolean extractionSupportReady) {
        public ValidationReport {
            legacyRuntimeArchetype = Objects.requireNonNull(legacyRuntimeArchetype, "legacyRuntimeArchetype");
            licensedFitId = Objects.requireNonNull(licensedFitId, "licensedFitId");
            hullId = Objects.requireNonNull(hullId, "hullId");
            productionManifestId = Objects.requireNonNull(productionManifestId, "productionManifestId");
            extractionMethodId = Objects.requireNonNull(extractionMethodId, "extractionMethodId");
        }

        /** @return true when all M22.5 mining closure seams are present */
        public boolean ready() {
            return miningIndustrialModulePresent && productionPathReady && extractionSupportReady;
        }
    }
}

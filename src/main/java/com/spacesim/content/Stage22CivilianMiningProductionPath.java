package com.spacesim.content;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.economy.Stage18ExtractionRuntime;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalCargoStore;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable M22.5 compatibility-to-production bridge for civilian asteroid mining.
 *
 * <p>The legacy {@code ship.basic_miner} identity remains available for supported worlds and saves.
 * New/replacement availability uses a dedicated non-sovereign mining refit composed from the reviewed
 * Industrial Union support hull plus a civilian asteroid-excavation mission section. Manufacturing,
 * shipyard work and extraction still execute through the accepted Stage-18 authorities; this class
 * owns no inventory, construction progress, extraction reserve, treasury or faction doctrine.</p>
 */
public final class Stage22CivilianMiningProductionPath {
    /** Preserved compatibility-era mining archetype used by supported worlds and saves. */
    public static final String LEGACY_RUNTIME_ARCHETYPE = "ship.basic_miner";
    /** Physically authored M22.5 replacement fit for new civilian asteroid-mining availability. */
    public static final String LICENSED_FIT_ID = Stage22CivilianMiningEngineeringCatalogLoader.MINING_FIT_ID;
    /** Exact M22.5 production manifest for the civilian mining fit. */
    public static final String PRODUCTION_MANIFEST_ID = "production_manifest.civilian.miners.asteroid_excavator_v1";
    /** Existing Stage-18 asteroid extraction method used by civilian miners. */
    public static final String EXTRACTION_METHOD_ID = "extraction.asteroid_excavation";
    /** Exact Stage-18 capability required by the asteroid extraction method. */
    public static final String EXTRACTION_CAPABILITY_TAG = "capability.extraction.asteroid_excavation";
    /** Preserved minor-faction identity that operates the civilian mining role. */
    public static final String OPERATOR_FACTION_ID = "faction.miners";

    private Stage22CivilianMiningProductionPath() {
        throw new AssertionError("utility class");
    }

    /**
     * Validates compatibility, physical production/service closure and real Stage-18 extraction behavior.
     *
     * @return deterministic evidence for the complete M22.5 mining role path
     */
    public static ValidationReport validateDefault() {
        ContentCatalog legacy = ContentCatalogLoader.loadDefault();
        if (legacy.findShipArchetype(LEGACY_RUNTIME_ARCHETYPE) == null) {
            throw new IllegalStateException("Missing preserved mining runtime archetype: " + LEGACY_RUNTIME_ARCHETYPE);
        }

        ShipEngineeringCatalog engineering = Stage22CivilianMiningEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog.DemonstratorFitDefinition fit = Objects.requireNonNull(
                engineering.findDemonstratorFit(LICENSED_FIT_ID), "civilian mining fit");
        ShipEngineeringCatalog.HullDefinition hull = Objects.requireNonNull(
                engineering.findHull(fit.hullId()), "civilian mining hull");
        ShipEngineeringCatalog.ModuleDefinition miningModule = Objects.requireNonNull(
                engineering.findModule(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID),
                "civilian asteroid excavation module");

        long miningModuleCount = fit.installedModules().stream()
                .filter(value -> value.moduleId().equals(miningModule.id()))
                .count();
        if (miningModuleCount != 1L) {
            throw new IllegalStateException("Civilian mining fit must install exactly one asteroid excavation section");
        }

        Stage18ManufacturingCatalog manufacturing = Stage22AuthoredProductionBridge.withProductBindings(
                Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault(),
                List.of(new ProductBindingDefinition(
                        miningModule.id(), Stage22CommonManufacturingProfiles.INDUSTRIAL_SUPPORT)));

        Stage18ShipyardCatalog baseShipyards = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        ModuleServiceProfile workshopProfile = Objects.requireNonNull(
                baseShipyards.findModuleProfile(Stage22CivilianMiningEngineeringCatalogLoader.BASE_WORKSHOP_MODULE_ID),
                "reviewed workshop service profile");
        Stage18ShipyardCatalog shipyards = Stage22AuthoredProductionBridge.withShipyardProfiles(
                baseShipyards,
                List.of(),
                List.of(),
                List.of(new ModuleServiceProfile(
                        miningModule.id(),
                        workshopProfile.repairInputsAtFullLossKg(),
                        workshopProfile.maintenanceInputsKg())));

        if (shipyards.findHullProfile(hull.id()) == null) {
            throw new IllegalStateException("Civilian mining hull lacks Stage-18 physical shipyard profile: " + hull.id());
        }
        for (ShipEngineeringCatalog.InstalledModuleDefinition installed : fit.installedModules()) {
            ShipEngineeringCatalog.ModuleDefinition module = Objects.requireNonNull(
                    engineering.findModule(installed.moduleId()), "civilian mining module");
            if (manufacturing.findProductBinding(module.id()) == null) {
                throw new IllegalStateException("Civilian mining fit module lacks manufacturing binding: " + module.id());
            }
            if (shipyards.findModuleProfile(module.id()) == null) {
                throw new IllegalStateException("Civilian mining fit module lacks shipyard service profile: " + module.id());
            }
        }

        Stage18ShipyardCatalog.YardDefinition yard = Objects.requireNonNull(
                shipyards.findYard(Stage22IndustrialUnionProductionCatalogs.YARD_ID),
                "reviewed Union series yard");
        Stage22CoreProductionManifestCatalog manifests = new Stage22CoreProductionManifestCatalog(
                1,
                "stage22.civilian_mining_production.v1",
                List.of(new Stage22CoreProductionManifestCatalog.ProductionManifestDefinition(
                        PRODUCTION_MANIFEST_ID,
                        fit.id(),
                        hull.id(),
                        fit.installedModules().stream().map(ShipEngineeringCatalog.InstalledModuleDefinition::moduleId).toList(),
                        yard.id(),
                        yard.requiredSupportFacilityDefinitionIds().stream().sorted().toList(),
                        ContentMaturity.VALIDATED,
                        "M22.5 civilian mining refit uses ordinary Stage-17.5 engineering and Stage-18 manufacturing/shipyard authority.")),
                List.of());
        Stage22CoreProductionManifestCatalog.ProductionManifestDefinition manifest = Objects.requireNonNull(
                manifests.findManifest(PRODUCTION_MANIFEST_ID), "civilian mining production manifest");
        if (!manifest.fitId().equals(fit.id()) || !manifest.hullId().equals(hull.id())) {
            throw new IllegalStateException("Civilian mining production manifest does not resolve the exact fit/hull");
        }

        Stage18ExtractionCatalog.ExtractionMethodDefinition method = Objects.requireNonNull(
                Stage18ExtractionCatalogLoader.loadDefault().findMethod(EXTRACTION_METHOD_ID),
                "asteroid extraction method");
        if (!method.requiredCapabilityTags().equals(Set.of(EXTRACTION_CAPABILITY_TAG))) {
            throw new IllegalStateException("Asteroid extraction capability contract changed: " + method.requiredCapabilityTags());
        }

        ExtractionCapability miningCapability = extractionCapability(miningModule);
        if (!miningCapability.capabilityTags().containsAll(method.requiredCapabilityTags())) {
            throw new IllegalStateException("Civilian mining module lacks exact Stage-18 asteroid extraction capability");
        }
        double authoredMaxSourceKgPerSecond = positiveParameter(
                miningModule, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAX_SOURCE_KG_S);
        if (authoredMaxSourceKgPerSecond + 1e-9d < method.maxSourceKgPerSecond()) {
            throw new IllegalStateException("Civilian mining module cannot meet Stage-18 method throughput ceiling");
        }

        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ExtractionRuntime runtime = new Stage18ExtractionRuntime(ontology, Stage18ExtractionCatalogLoader.loadDefault());
        PhysicalSourceState source = metallicSource(100d);
        PhysicalCargoStore cargo = new PhysicalCargoStore(
                ontology, Map.of("storage.dry_bulk", 1_000d), Map.of());
        ExtractionResult result = runtime.extract(
                source,
                EXTRACTION_METHOD_ID,
                method.maxSourceKgPerSecond(),
                miningCapability,
                miningCapability.openInterval(1d),
                cargo);
        boolean runtimeExtractionReady = result.committed()
                && result.status() == Stage18ExtractionRuntime.Status.EXTRACTED;
        if (!runtimeExtractionReady) {
            throw new IllegalStateException("Civilian mining fit failed real Stage-18 extraction acceptance: " + result.status());
        }

        ShipEngineeringCatalog acceptedUnion = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog.ModuleDefinition repairWorkshop = Objects.requireNonNull(
                acceptedUnion.findModule(Stage22CivilianMiningEngineeringCatalogLoader.BASE_WORKSHOP_MODULE_ID),
                "accepted repair workshop");
        ExtractionCapability repairCapability = new ExtractionCapability(
                "capability.m22_5.negative.repair_workshop",
                Set.of(),
                positiveParameter(miningModule, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_AVAILABLE_POWER_W),
                positiveParameter(miningModule, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_WORK_RATE),
                positiveParameter(miningModule, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAINTENANCE_WORK_RATE));
        ExtractionResult repairAttempt = runtime.extract(
                metallicSource(100d),
                EXTRACTION_METHOD_ID,
                1d,
                repairCapability,
                repairCapability.openInterval(1d),
                new PhysicalCargoStore(ontology, Map.of("storage.dry_bulk", 1_000d), Map.of()));
        boolean repairFitRejectedForMining = repairAttempt.status() == Stage18ExtractionRuntime.Status.MISSING_CAPABILITY
                && repairWorkshop.capabilityParameters().keySet().stream().noneMatch(key -> key.startsWith("extraction_"));
        if (!repairFitRejectedForMining) {
            throw new IllegalStateException("Accepted repair workshop must not gain implicit asteroid-mining capability");
        }

        return new ValidationReport(
                LEGACY_RUNTIME_ARCHETYPE,
                LICENSED_FIT_ID,
                hull.id(),
                miningModule.id(),
                PRODUCTION_MANIFEST_ID,
                EXTRACTION_METHOD_ID,
                EXTRACTION_CAPABILITY_TAG,
                true,
                runtimeExtractionReady,
                repairFitRejectedForMining);
    }

    private static ExtractionCapability extractionCapability(ShipEngineeringCatalog.ModuleDefinition module) {
        return new ExtractionCapability(
                "capability.civilian.miners.asteroid_excavator_v1",
                Set.of(EXTRACTION_CAPABILITY_TAG),
                positiveParameter(module, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_AVAILABLE_POWER_W),
                positiveParameter(module, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_WORK_RATE),
                positiveParameter(module, Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAINTENANCE_WORK_RATE));
    }

    private static double positiveParameter(ShipEngineeringCatalog.ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalStateException("Civilian mining module lacks positive physical parameter: " + key);
        }
        return value;
    }

    private static PhysicalSourceState metallicSource(double massKg) {
        return new PhysicalSourceState(
                "source.m22_5.mining_validation",
                SourceKind.NATURAL_OCCURRENCE,
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                massKg,
                massKg,
                1d,
                1d,
                Set.of());
    }

    /** Deterministic evidence that the mining role has compatibility, production and runtime closure. */
    public record ValidationReport(
            String legacyRuntimeArchetype,
            String licensedFitId,
            String hullId,
            String miningModuleId,
            String productionManifestId,
            String extractionMethodId,
            String extractionCapabilityTag,
            boolean productionPathReady,
            boolean runtimeExtractionReady,
            boolean repairFitRejectedForMining) {
        /** Normalizes required identifiers for deterministic validation evidence. */
        public ValidationReport {
            legacyRuntimeArchetype = Objects.requireNonNull(legacyRuntimeArchetype, "legacyRuntimeArchetype");
            licensedFitId = Objects.requireNonNull(licensedFitId, "licensedFitId");
            hullId = Objects.requireNonNull(hullId, "hullId");
            miningModuleId = Objects.requireNonNull(miningModuleId, "miningModuleId");
            productionManifestId = Objects.requireNonNull(productionManifestId, "productionManifestId");
            extractionMethodId = Objects.requireNonNull(extractionMethodId, "extractionMethodId");
            extractionCapabilityTag = Objects.requireNonNull(extractionCapabilityTag, "extractionCapabilityTag");
        }

        /** @return true when physical production, real extraction and the negative repair control all hold */
        public boolean ready() {
            return productionPathReady && runtimeExtractionReady && repairFitRejectedForMining;
        }
    }
}

package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.ProductionManifestDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.content.ship.Stage22AuthoredShipyardIndustrialBridge;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionShipyardIndustrialCatalogLoader;
import com.spacesim.world.Stage21HNpcMissionState;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Cross-authority M22.4 validator for the Industrial Union production package.
 *
 * <p>The validator is diagnostic only. It proves that authored Union content composes the accepted
 * Stage-17.5 engineering, Stage-18 manufacturing/facility/shipyard/station and Stage-21H NPC/mission
 * authorities. It never creates inventory, ships, repair progress, treasury value or mission
 * outcomes, and it rejects stale or missing presentation bindings instead of silently substituting
 * fallback content.</p>
 */
public final class Stage22IndustrialUnionPackageValidator {
    private Stage22IndustrialUnionPackageValidator() {
        throw new AssertionError("utility class");
    }

    /**
     * Validates the complete built-in M22.4 Industrial Union package.
     *
     * @return deterministic validation report with per-role engineering metrics and balance evidence
     */
    public static ValidationReport validateDefault() {
        Stage22IndustrialUnionPackageCatalog union = Stage22IndustrialUnionPackageLoader.loadDefault();
        Stage22CoreContentSeamCatalog common = Stage22CoreContentSeamLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        Stage18ManufacturingCatalog manufacturing = Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault();
        Stage18ManufacturingProductRegistry registry = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog unionShipyards = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        Stage18ShipyardCatalog combinedShipyards = Stage22AuthoredProductionBridge.withShipyardProfiles(
                Stage18ShipyardCatalogLoader.loadDefault(),
                unionShipyards.getYards(),
                unionShipyards.getHullProfiles(),
                unionShipyards.getModuleProfiles());
        ShipyardIndustrialCatalog unionIndustrial = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog combinedIndustrial = Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                ShipyardIndustrialCatalogLoader.loadDefault(ShipEngineeringCatalogLoader.loadDefault()),
                unionIndustrial.getHullProfiles(),
                unionIndustrial.getModuleProfiles());
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage22CoreProductionManifestCatalog manifests = Stage22IndustrialUnionProductionCatalogs.loadManifests();
        List<VisualBindingDefinition> visuals = Stage22IndustrialUnionProductionCatalogs.loadVisualBindings();
        Stage22IndustrialUnionCharacterLineup.Catalog characters = Stage22IndustrialUnionCharacterLineup.loadDefault();
        Stage22IndustrialUnionIndustrialProgram.ValidationReport industrialProgram =
                Stage22IndustrialUnionIndustrialProgram.validateDefault();

        Map<String, VisualBindingDefinition> visualByFit = visuals.stream().collect(Collectors.toUnmodifiableMap(
                VisualBindingDefinition::fitId, value -> value));

        validateRoleCoverage(union, common);
        validateStations(union, stations);
        validateRecurringCharacters(union, characters);
        validateMissions(union);

        Stage18ShipyardCatalog.YardDefinition yard = require(
                combinedShipyards.findYard(Stage22IndustrialUnionProductionCatalogs.YARD_ID),
                "Industrial Union series yard");
        Set<String> yardCapabilities = new TreeSet<>();
        for (String facilityId : yard.requiredSupportFacilityDefinitionIds()) {
            var facility = require(facilities.findFacility(facilityId), "yard facility " + facilityId);
            yardCapabilities.addAll(facility.capabilityTags());
        }

        LinkedHashMap<String, FamilyMetrics> metrics = new LinkedHashMap<>();
        for (Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition family : union.shipFamilies()) {
            DemonstratorFitDefinition primary = require(
                    engineering.findDemonstratorFit(family.primaryFitId()), family.primaryFitId());
            DemonstratorFitDefinition refit = require(
                    engineering.findDemonstratorFit(family.refitFitId()), family.refitFitId());
            if (!primary.hullId().equals(refit.hullId())) {
                throw new IllegalArgumentException(
                        "Industrial Union primary/refit hull mismatch: " + family.familyId());
            }
            HullDefinition hull = require(engineering.findHull(primary.hullId()), primary.hullId());
            require(combinedShipyards.findHullProfile(hull.id()), "physical hull " + hull.id());
            require(combinedIndustrial.findHullProfile(hull.id()), "industrial hull " + hull.id());
            validateYardEnvelope(yard, hull);

            ProductionManifestDefinition manifest = require(
                    manifests.findManifest(family.productionManifestId()), family.productionManifestId());
            if (!manifest.fitId().equals(primary.id()) || !manifest.hullId().equals(hull.id())) {
                throw new IllegalArgumentException(
                        "Industrial Union production manifest fit/hull mismatch: " + manifest.id());
            }
            Set<String> exactPrimaryModules = primary.installedModules().stream()
                    .map(value -> value.moduleId()).collect(Collectors.toCollection(TreeSet::new));
            if (!exactPrimaryModules.equals(new TreeSet<>(manifest.componentIds()))) {
                throw new IllegalArgumentException(
                        "Industrial Union production manifest module mismatch: " + manifest.id());
            }
            if (!new TreeSet<>(yard.requiredSupportFacilityDefinitionIds())
                    .equals(new TreeSet<>(manifest.requiredFacilityIds()))) {
                throw new IllegalArgumentException(
                        "Industrial Union production manifest facility mismatch: " + manifest.id());
            }

            validateFitProducts(
                    primary, engineering, manufacturing, registry,
                    combinedShipyards, combinedIndustrial, yardCapabilities);
            validateFitProducts(
                    refit, engineering, manufacturing, registry,
                    combinedShipyards, combinedIndustrial, yardCapabilities);
            validateVisual(primary.id(), engineering, visualByFit);
            validateVisual(refit.id(), engineering, visualByFit);
            metrics.put(family.roleId(), metrics(engineering, hull, primary));
        }

        if (visuals.size() != Stage22IndustrialUnionPackageCatalog.REQUIRED_SHIP_FAMILIES * 2) {
            throw new IllegalArgumentException(
                    "Industrial Union visual bindings must cover every primary and refit fit");
        }
        if (industrialProgram.maximumBuildTimeReduction() > 0.10d + 1e-12d
                || industrialProgram.maximumThroughputImprovement() > 0.15d + 1e-12d) {
            throw new IllegalArgumentException("Industrial Union production benefit exceeds M22.4 formal caps");
        }

        return new ValidationReport(
                union.fingerprint(),
                manifests.fingerprint(),
                engineering.getFingerprint(),
                manufacturing.getFingerprint(),
                unionShipyards.getFingerprint(),
                stations.getFingerprint(),
                characters.fingerprint(),
                union.recurringNpcs().size(),
                union.missions().size(),
                industrialProgram.maximumBuildTimeReduction(),
                industrialProgram.maximumThroughputImprovement(),
                Map.copyOf(metrics));
    }

    private static void validateRoleCoverage(
            Stage22IndustrialUnionPackageCatalog union,
            Stage22CoreContentSeamCatalog common) {
        Set<String> packageRoles = union.shipFamilies().stream()
                .map(Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> commonRoles = common.roles().stream()
                .map(Stage22CoreContentSeamCatalog.RoleDefinition::id)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!packageRoles.equals(commonRoles)) {
            throw new IllegalArgumentException(
                    "Industrial Union package must cover the exact common nine-role taxonomy");
        }
    }

    private static void validateStations(
            Stage22IndustrialUnionPackageCatalog union,
            Stage18StationInfrastructureCatalog stations) {
        for (Stage22IndustrialUnionPackageCatalog.StationVariantDefinition variant : union.stations()) {
            var archetype = require(stations.findArchetype(variant.stage18ArchetypeId()), variant.stage18ArchetypeId());
            if (!archetype.installedFacilityDefinitionIds().containsAll(variant.requiredFacilityIds())) {
                throw new IllegalArgumentException(
                        "Industrial Union station claims facilities absent from Stage-18 archetype: " + variant.id());
            }
        }
    }

    private static void validateRecurringCharacters(
            Stage22IndustrialUnionPackageCatalog union,
            Stage22IndustrialUnionCharacterLineup.Catalog characters) {
        if (union.recurringNpcs().size() < 6) {
            throw new IllegalArgumentException("Industrial Union recurring NPC floor is not met");
        }
        for (Stage22IndustrialUnionPackageCatalog.RecurringNpcDefinition npc : union.recurringNpcs()) {
            if (characters.findOverlay(npc.characterOverlayId()) == null) {
                throw new IllegalArgumentException(
                        "Industrial Union NPC references missing character overlay: " + npc.id());
            }
        }
    }

    private static void validateMissions(Stage22IndustrialUnionPackageCatalog union) {
        if (union.missions().size() < 10 || union.storyChains().size() < 2) {
            throw new IllegalArgumentException("Industrial Union mission/story authored floor is not met");
        }
        for (Stage22IndustrialUnionPackageCatalog.MissionTemplateDefinition mission : union.missions()) {
            var issuer = require(union.findNpc(mission.issuerNpcId()), "mission issuer " + mission.issuerNpcId());
            if (!Stage21HNpcMissionState.canIssue(issuer.role(), mission.runtimeTemplate())) {
                throw new IllegalArgumentException(
                        "Industrial Union mission issuer/runtime template mismatch: " + mission.id());
            }
            Stage21HNpcMissionState.validateTemplateObjective(mission.runtimeTemplate(), mission.objectiveKind());
            if (Stage21HNpcMissionState.expectedAuthority(mission.objectiveKind()) != mission.authority()) {
                throw new IllegalArgumentException(
                        "Industrial Union mission objective authority mismatch: " + mission.id());
            }
        }
    }

    private static void validateFitProducts(
            DemonstratorFitDefinition fit,
            ShipEngineeringCatalog engineering,
            Stage18ManufacturingCatalog manufacturing,
            Stage18ManufacturingProductRegistry registry,
            Stage18ShipyardCatalog shipyards,
            ShipyardIndustrialCatalog industrial,
            Set<String> yardCapabilities) {
        for (var installed : fit.installedModules()) {
            ModuleDefinition module = require(engineering.findModule(installed.moduleId()), installed.moduleId());
            var product = require(registry.findProduct(module.id()), "manufactured product " + module.id());
            if (product.provenance() != Provenance.STAGE22_AUTHORED) {
                throw new IllegalArgumentException(
                        "Industrial Union module lacks Stage-22 authored provenance: " + module.id());
            }
            var binding = require(manufacturing.findProductBinding(module.id()), "manufacturing binding " + module.id());
            var profile = require(manufacturing.findProductProfile(binding.profileId()), binding.profileId());
            if (!yardCapabilities.containsAll(profile.requiredCapabilityTags())) {
                throw new IllegalArgumentException(
                        "Industrial Union yard facilities cannot manufacture " + module.id());
            }
            require(shipyards.findModuleProfile(module.id()), "physical module service profile " + module.id());
            require(industrial.findModuleProfile(module.id()), "industrial module profile " + module.id());
        }
    }

    private static void validateVisual(
            String fitId,
            ShipEngineeringCatalog engineering,
            Map<String, VisualBindingDefinition> visualByFit) {
        VisualBindingDefinition visual = require(visualByFit.get(fitId), "visual binding for " + fitId);
        String expected = Stage22FitFingerprint.compute(engineering, fitId);
        if (!expected.equals(visual.expectedFitFingerprint())) {
            throw new IllegalArgumentException("Stale Industrial Union visual fingerprint: " + visual.id());
        }
        if (visual.status() != Stage22ContentGovernanceCatalog.AssetStatus.PRODUCTION) {
            throw new IllegalArgumentException(
                    "Industrial Union exact-fit visual is not production-approved: " + visual.id());
        }
        URL asset = Stage22IndustrialUnionPackageValidator.class.getClassLoader().getResource(visual.assetRef());
        if (asset == null) {
            throw new IllegalArgumentException(
                    "Missing Industrial Union production visual resource: " + visual.assetRef());
        }
        String lower = visual.assetRef().toLowerCase(Locale.ROOT);
        if (!lower.contains("/production/") || !lower.endsWith("_base.png")) {
            throw new IllegalArgumentException(
                    "Industrial Union production visual must bind a production base PNG: " + visual.id());
        }
    }

    private static void validateYardEnvelope(Stage18ShipyardCatalog.YardDefinition yard, HullDefinition hull) {
        if (yard.maxServiceMassKg() + 1e-6d < hull.maxOperationalMassKg()) {
            throw new IllegalArgumentException(
                    "Industrial Union yard mass envelope cannot service hull: " + hull.id());
        }
        if (yard.berthDimensionsM().lengthM() + 1e-6d < hull.boundingDimensionsM().lengthM()
                || yard.berthDimensionsM().widthM() + 1e-6d < hull.boundingDimensionsM().widthM()
                || yard.berthDimensionsM().heightM() + 1e-6d < hull.boundingDimensionsM().heightM()) {
            throw new IllegalArgumentException(
                    "Industrial Union yard berth cannot fit hull: " + hull.id());
        }
    }

    private static FamilyMetrics metrics(
            ShipEngineeringCatalog engineering,
            HullDefinition hull,
            DemonstratorFitDefinition fit) {
        double moduleMass = 0d;
        double supply = 0d;
        double demand = 0d;
        double waste = 0d;
        double rejection = 0d;
        int moduleCrew = 0;
        for (var installed : fit.installedModules()) {
            ModuleDefinition module = require(engineering.findModule(installed.moduleId()), installed.moduleId());
            moduleMass += module.massKg();
            supply += module.continuousPowerSupplyW();
            demand += module.continuousPowerDemandW();
            waste += module.wasteHeatW();
            rejection += module.heatRejectionW();
            moduleCrew += module.crewRequirement();
        }
        double fittedDryMass = hull.bareHullMassKg() + moduleMass;
        if (fittedDryMass > hull.maxOperationalMassKg() + 1e-6d) {
            throw new IllegalArgumentException(
                    "Industrial Union primary fit exceeds hull operational mass: " + fit.id());
        }
        if (supply + 1e-6d < demand) {
            throw new IllegalArgumentException(
                    "Industrial Union primary fit has negative continuous power margin: " + fit.id());
        }
        if (rejection + 1e-6d < waste) {
            throw new IllegalArgumentException(
                    "Industrial Union primary fit has negative continuous thermal margin: " + fit.id());
        }
        int staffedCrew = Math.max(hull.crewBaseline(), moduleCrew);
        if (staffedCrew > hull.lifeSupportCapacity()) {
            throw new IllegalArgumentException(
                    "Industrial Union primary fit exceeds authored life-support capacity: " + fit.id());
        }
        return new FamilyMetrics(
                fit.id(), fittedDryMass, hull.maxOperationalMassKg() - fittedDryMass,
                supply - demand, rejection - waste, staffedCrew, hull.lifeSupportCapacity());
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required M22.4 reference: " + label);
        }
        return value;
    }

    /** Deterministic cross-authority M22.4 validation report. */
    public record ValidationReport(
            String packageFingerprint,
            String productionFingerprint,
            String engineeringFingerprint,
            String manufacturingFingerprint,
            String shipyardFingerprint,
            String stationFingerprint,
            String characterFingerprint,
            int recurringNpcCount,
            int missionCount,
            double maximumBuildTimeReduction,
            double maximumThroughputImprovement,
            Map<String, FamilyMetrics> familyMetrics) {
        /**
         * Freezes per-role metric diagnostics.
         *
         * @param packageFingerprint package semantic fingerprint
         * @param productionFingerprint production-manifest fingerprint
         * @param engineeringFingerprint engineering-catalog fingerprint
         * @param manufacturingFingerprint manufacturing-catalog fingerprint
         * @param shipyardFingerprint physical shipyard fingerprint
         * @param stationFingerprint station infrastructure fingerprint
         * @param characterFingerprint character lineup fingerprint
         * @param recurringNpcCount recurring named NPC count
         * @param missionCount standalone mission-template count
         * @param maximumBuildTimeReduction largest reviewed steady-series build-time reduction
         * @param maximumThroughputImprovement largest reviewed steady-series throughput improvement
         * @param familyMetrics immutable per-role engineering diagnostics
         */
        public ValidationReport {
            familyMetrics = Map.copyOf(familyMetrics);
        }
    }

    /** Diagnostic-only primary-fit burdens and margins. */
    public record FamilyMetrics(
            String fitId,
            double fittedDryMassKg,
            double remainingOperationalMassKg,
            double continuousPowerMarginW,
            double continuousThermalMarginW,
            int staffedCrewBurden,
            int authoredLifeSupportCapacity) { }
}

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
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22EmpireShipyardIndustrialCatalogLoader;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Cross-authority M22.3 validator for the Empire production package.
 *
 * <p>The validator is diagnostic only. It proves that authored Empire content composes existing
 * Stage-17.5 engineering, Stage-18 manufacturing/facility/shipyard/station and Stage-22 shared seams;
 * it never grants a ship, item, repair, outcome or faction bonus.</p>
 */
public final class Stage22EmpirePackageValidator {
    private Stage22EmpirePackageValidator() {
        throw new AssertionError("utility class");
    }

    /**
     * Validates the built-in M22.3 Empire package and returns deterministic engineering diagnostics.
     *
     * @return immutable validation report
     */
    public static ValidationReport validateDefault() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        Stage22CoreContentSeamCatalog common = Stage22CoreContentSeamLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        Stage18ManufacturingCatalog manufacturing = Stage22EmpireManufacturingCatalogLoader.loadDefault();
        Stage18ManufacturingProductRegistry registry = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog empireShipyards = Stage22EmpireShipyardCatalogLoader.loadDefault();
        Stage18ShipyardCatalog combinedShipyards = Stage22AuthoredProductionBridge.withShipyardProfiles(
                Stage18ShipyardCatalogLoader.loadDefault(),
                empireShipyards.getYards(),
                empireShipyards.getHullProfiles(),
                empireShipyards.getModuleProfiles());
        ShipyardIndustrialCatalog empireIndustrial = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog combinedIndustrial = Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                ShipyardIndustrialCatalogLoader.loadDefault(ShipEngineeringCatalogLoader.loadDefault()),
                empireIndustrial.getHullProfiles(),
                empireIndustrial.getModuleProfiles());
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage22CoreProductionManifestCatalog manifests = Stage22EmpireProductionCatalogs.loadManifests();
        List<VisualBindingDefinition> visuals = Stage22EmpireProductionCatalogs.loadVisualBindings();
        Map<String, VisualBindingDefinition> visualByFit = visuals.stream().collect(Collectors.toUnmodifiableMap(
                VisualBindingDefinition::fitId, value -> value));

        validateRoleCoverage(empire, common);
        validateStations(empire, stations);
        validateMissionIssuers(empire);

        var yard = require(combinedShipyards.findYard(Stage22EmpireProductionCatalogs.YARD_ID), "Empire yard");
        Set<String> yardCapabilities = new TreeSet<>();
        for (String facilityId : yard.requiredSupportFacilityDefinitionIds()) {
            var facility = require(facilities.findFacility(facilityId), "yard facility " + facilityId);
            yardCapabilities.addAll(facility.capabilityTags());
        }

        LinkedHashMap<String, FamilyMetrics> metrics = new LinkedHashMap<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            DemonstratorFitDefinition primary = require(
                    engineering.findDemonstratorFit(family.primaryFitId()), family.primaryFitId());
            DemonstratorFitDefinition refit = require(
                    engineering.findDemonstratorFit(family.refitFitId()), family.refitFitId());
            if (!primary.hullId().equals(refit.hullId())) {
                throw new IllegalArgumentException("Empire primary/refit hull mismatch: " + family.familyId());
            }
            HullDefinition hull = require(engineering.findHull(primary.hullId()), primary.hullId());
            require(combinedShipyards.findHullProfile(hull.id()), "physical hull " + hull.id());
            require(combinedIndustrial.findHullProfile(hull.id()), "industrial hull " + hull.id());
            validateYardEnvelope(yard, hull);

            ProductionManifestDefinition manifest = require(
                    manifests.findManifest(family.productionManifestId()), family.productionManifestId());
            if (!manifest.fitId().equals(primary.id()) || !manifest.hullId().equals(hull.id())) {
                throw new IllegalArgumentException("Empire production manifest fit/hull mismatch: " + manifest.id());
            }
            Set<String> exactPrimaryModules = primary.installedModules().stream()
                    .map(value -> value.moduleId()).collect(Collectors.toCollection(TreeSet::new));
            if (!exactPrimaryModules.equals(new TreeSet<>(manifest.componentIds()))) {
                throw new IllegalArgumentException("Empire production manifest module mismatch: " + manifest.id());
            }
            if (!new TreeSet<>(yard.requiredSupportFacilityDefinitionIds())
                    .equals(new TreeSet<>(manifest.requiredFacilityIds()))) {
                throw new IllegalArgumentException("Empire production manifest facility mismatch: " + manifest.id());
            }

            validateFitProducts(primary, engineering, manufacturing, registry, combinedShipyards,
                    combinedIndustrial, yardCapabilities);
            validateFitProducts(refit, engineering, manufacturing, registry, combinedShipyards,
                    combinedIndustrial, yardCapabilities);
            validateVisual(primary.id(), engineering, visualByFit);
            validateVisual(refit.id(), engineering, visualByFit);

            metrics.put(family.roleId(), metrics(engineering, hull, primary));
        }
        if (visuals.size() != Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES * 2) {
            throw new IllegalArgumentException("Empire visual bindings must cover primary and refit fits");
        }
        return new ValidationReport(
                empire.fingerprint(),
                manifests.fingerprint(),
                engineering.getFingerprint(),
                manufacturing.getFingerprint(),
                empireShipyards.getFingerprint(),
                stations.getFingerprint(),
                Map.copyOf(metrics));
    }

    private static void validateRoleCoverage(
            Stage22EmpirePackageCatalog empire,
            Stage22CoreContentSeamCatalog common) {
        Set<String> packageRoles = empire.shipFamilies().stream()
                .map(Stage22EmpirePackageCatalog.ShipFamilyDefinition::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> commonRoles = common.roles().stream()
                .map(Stage22CoreContentSeamCatalog.RoleDefinition::id)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!packageRoles.equals(commonRoles)) {
            throw new IllegalArgumentException("Empire package must cover the exact common nine-role taxonomy");
        }
    }

    private static void validateStations(
            Stage22EmpirePackageCatalog empire,
            Stage18StationInfrastructureCatalog stations) {
        for (Stage22EmpirePackageCatalog.StationVariantDefinition variant : empire.stations()) {
            var archetype = require(stations.findArchetype(variant.stage18ArchetypeId()), variant.stage18ArchetypeId());
            if (!archetype.installedFacilityDefinitionIds().containsAll(variant.requiredFacilityIds())) {
                throw new IllegalArgumentException("Empire station claims facilities absent from Stage-18 archetype: "
                        + variant.id());
            }
        }
    }

    private static void validateMissionIssuers(Stage22EmpirePackageCatalog empire) {
        for (Stage22EmpirePackageCatalog.MissionTemplateDefinition mission : empire.missions()) {
            require(empire.findNpc(mission.issuerNpcId()), "mission issuer " + mission.issuerNpcId());
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
                throw new IllegalArgumentException("Empire module lacks Stage-22 authored provenance: " + module.id());
            }
            var binding = require(manufacturing.findProductBinding(module.id()), "manufacturing binding " + module.id());
            var profile = require(manufacturing.findProductProfile(binding.profileId()), binding.profileId());
            if (!yardCapabilities.containsAll(profile.requiredCapabilityTags())) {
                throw new IllegalArgumentException("Empire yard facilities cannot manufacture " + module.id());
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
            throw new IllegalArgumentException("Stale Empire visual fingerprint: " + visual.id());
        }
        URL asset = Stage22EmpirePackageValidator.class.getClassLoader().getResource(visual.assetRef());
        if (asset == null) {
            throw new IllegalArgumentException("Missing Empire silhouette resource: " + visual.assetRef());
        }
        String lower = visual.assetRef().toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith("_silhouette.svg")) {
            throw new IllegalArgumentException("Empire Stage-22 visual must bind a silhouette asset: " + visual.id());
        }
    }

    private static void validateYardEnvelope(Stage18ShipyardCatalog.YardDefinition yard, HullDefinition hull) {
        if (yard.maxServiceMassKg() + 1e-6d < hull.maxOperationalMassKg()) {
            throw new IllegalArgumentException("Empire yard mass envelope cannot service hull: " + hull.id());
        }
        if (yard.berthDimensionsM().lengthM() + 1e-6d < hull.boundingDimensionsM().lengthM()
                || yard.berthDimensionsM().widthM() + 1e-6d < hull.boundingDimensionsM().widthM()
                || yard.berthDimensionsM().heightM() + 1e-6d < hull.boundingDimensionsM().heightM()) {
            throw new IllegalArgumentException("Empire yard berth cannot fit hull: " + hull.id());
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
            throw new IllegalArgumentException("Empire primary fit exceeds hull operational mass: " + fit.id());
        }
        if (supply + 1e-6d < demand) {
            throw new IllegalArgumentException("Empire primary fit has negative continuous power margin: " + fit.id());
        }
        if (rejection + 1e-6d < waste) {
            throw new IllegalArgumentException("Empire primary fit has negative continuous thermal margin: " + fit.id());
        }
        return new FamilyMetrics(
                fit.id(), fittedDryMass, hull.maxOperationalMassKg() - fittedDryMass,
                supply - demand, rejection - waste, hull.crewBaseline() + moduleCrew,
                hull.lifeSupportCapacity());
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required M22.3 reference: " + label);
        }
        return value;
    }

    /** Deterministic cross-authority M22.3 validation report. */
    public record ValidationReport(
            String packageFingerprint,
            String productionFingerprint,
            String engineeringFingerprint,
            String manufacturingFingerprint,
            String shipyardFingerprint,
            String stationFingerprint,
            Map<String, FamilyMetrics> familyMetrics) {
        /** Validates and freezes the per-role diagnostic metrics map. */
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

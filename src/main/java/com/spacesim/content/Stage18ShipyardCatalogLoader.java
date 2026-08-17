package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityKind;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;
import com.spacesim.content.Stage18ShipyardCatalog.CompartmentRepairProfile;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and strictly validates Stage-18G physical shipyard content. */
public final class Stage18ShipyardCatalogLoader {
    /** Current supported Stage-18G shipyard schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in Stage-18G physical shipyard resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-shipyards-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final double MASS_TOLERANCE_KG = 1e-3d;

    private Stage18ShipyardCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the production Stage-18G shipyard catalog against all upstream authoritative catalogs.
     *
     * @return immutable validated physical shipyard catalog
     */
    public static Stage18ShipyardCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial = ShipyardIndustrialCatalogLoader.loadDefault(engineering);
        ClassLoader classLoader = Stage18ShipyardCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18G shipyard catalog: " + DEFAULT_RESOURCE);
            }
            Stage18ShipyardCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    ontology,
                    facilities,
                    engineering,
                    industrial);
            validateProductionCoverage(catalog, industrial);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18G shipyard catalog", exception);
        }
    }

    /**
     * Parses and validates one Stage-18G physical shipyard document.
     *
     * @param json non-empty JSON document
     * @param ontology authoritative Stage-18 resource ontology
     * @param facilities authoritative Stage-18E facility catalog
     * @param engineering authoritative Stage-17.5 engineering catalog
     * @param industrial authoritative Stage-17.5G planner requirement catalog
     * @return immutable validated Stage-18G shipyard catalog
     */
    public static Stage18ShipyardCatalog parse(
            String json,
            Stage18ResourceOntologyCatalog ontology,
            Stage18FacilityCatalog facilities,
            ShipEngineeringCatalog engineering,
            ShipyardIndustrialCatalog industrial) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilities, "facilities");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        ShipyardIndustrialCatalog checkedIndustrial = Objects.requireNonNull(industrial, "industrial");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-18G shipyard JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Stage-18G shipyard JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-18G shipyard root must be an object");
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18G shipyard schema: " + schemaVersion);
        }

        Set<String> knownPlannerCapabilities = plannerCapabilities(checkedIndustrial);
        Set<String> knownPlannerRequirements = plannerRequirementIds(checkedIndustrial);
        Set<String> knownTooling = plannerTooling(checkedIndustrial);
        List<YardDefinition> yards = parseYards(
                root,
                checkedOntology,
                checkedFacilities,
                knownPlannerCapabilities,
                knownPlannerRequirements,
                knownTooling);
        List<HullPhysicalProfile> hulls = parseHulls(root, checkedOntology, checkedEngineering);
        List<ModuleServiceProfile> modules = parseModules(root, checkedOntology, checkedEngineering);
        if (yards.isEmpty() || hulls.isEmpty() || modules.isEmpty()) {
            throw new IllegalArgumentException("Stage-18G shipyard catalog requires yards, hulls and module profiles");
        }
        return new Stage18ShipyardCatalog(schemaVersion, yards, hulls, modules);
    }

    private static List<YardDefinition> parseYards(
            JsonValue root,
            Stage18ResourceOntologyCatalog ontology,
            Stage18FacilityCatalog facilities,
            Set<String> plannerCapabilities,
            Set<String> plannerRequirements,
            Set<String> plannerTooling) {
        List<YardDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(root, "yards").child; node != null; node = node.next) {
            requireObject(node, "yard definition");
            String id = requiredId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate Stage-18G yard: " + id);
            }
            Set<String> support = stringSet(node, "requiredSupportFacilityDefinitionIds");
            for (String facilityId : support) {
                if (facilities.findFacility(facilityId) == null) {
                    throw new IllegalArgumentException("Unknown Stage-18E support facility " + facilityId + " for " + id);
                }
            }
            Set<String> fabrication = stringSet(node, "stage175FabricationCapabilities");
            if (!plannerCapabilities.containsAll(fabrication)) {
                Set<String> unknown = new HashSet<>(fabrication);
                unknown.removeAll(plannerCapabilities);
                throw new IllegalArgumentException("Unknown Stage-17.5G fabrication capabilities for " + id + ": " + unknown);
            }
            Set<String> handledRequirements = stringSet(node, "stage175HandledRequirementIds");
            if (!plannerRequirements.containsAll(handledRequirements)) {
                Set<String> unknown = new HashSet<>(handledRequirements);
                unknown.removeAll(plannerRequirements);
                throw new IllegalArgumentException("Unknown Stage-17.5G requirement IDs for " + id + ": " + unknown);
            }
            Set<String> tooling = stringSet(node, "toolingTags");
            if (!plannerTooling.containsAll(tooling)) {
                Set<String> unknown = new HashSet<>(tooling);
                unknown.removeAll(plannerTooling);
                throw new IllegalArgumentException("Unknown Stage-17.5G tooling for " + id + ": " + unknown);
            }
            Set<String> storageClasses = stringSet(node, "handledStorageClassIds");
            for (String storageClass : storageClasses) {
                if (ontology.findStorageClass(storageClass) == null) {
                    throw new IllegalArgumentException("Unknown Stage-18 storage class " + storageClass + " for " + id);
                }
            }
            result.add(new YardDefinition(
                    id,
                    requiredString(node, "displayName"),
                    support,
                    dimensions(requiredObject(node, "berthDimensionsM")),
                    positive(node, "maxServiceMassKg"),
                    fabrication,
                    handledRequirements,
                    tooling,
                    unitInterval(node, "precisionCapability"),
                    positive(node, "ratedIntegrationPowerW"),
                    positive(node, "ratedEngineeringWorkRate"),
                    nonNegativeInt(node, "laborCapacity"),
                    nonNegativeInt(node, "automationCapacity"),
                    storageClasses,
                    positive(node, "maxHandledUnitMassKg"),
                    stringSet(node, "allowedLocationTags")));
        }
        return result;
    }

    private static List<HullPhysicalProfile> parseHulls(
            JsonValue root,
            Stage18ResourceOntologyCatalog ontology,
            ShipEngineeringCatalog engineering) {
        List<HullPhysicalProfile> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(root, "hullProfiles").child; node != null; node = node.next) {
            requireObject(node, "hull physical profile");
            String hullId = requiredId(node, "hullId");
            HullDefinition hull = engineering.findHull(hullId);
            if (hull == null) {
                throw new IllegalArgumentException("Unknown Stage-17.5 hull in Stage-18G: " + hullId);
            }
            if (!ids.add(hullId)) {
                throw new IllegalArgumentException("Duplicate Stage-18G hull profile: " + hullId);
            }
            List<PhysicalInputDefinition> build = physicalInputs(node, "buildInputsKg", ontology);
            double totalBuildMass = build.stream().mapToDouble(PhysicalInputDefinition::massKg).sum();
            if (Math.abs(totalBuildMass - hull.bareHullMassKg()) > MASS_TOLERANCE_KG) {
                throw new IllegalArgumentException(
                        "Stage-18G bare hull mass does not close for " + hullId
                                + ": inputs=" + totalBuildMass + ",hull=" + hull.bareHullMassKg());
            }
            Set<String> validCompartments = new HashSet<>();
            hull.compartments().forEach(value -> validCompartments.add(value.id()));
            Set<String> seenCompartments = new HashSet<>();
            List<CompartmentRepairProfile> repairs = new ArrayList<>();
            for (JsonValue repair = requiredArray(node, "compartmentRepairs").child;
                    repair != null;
                    repair = repair.next) {
                requireObject(repair, "compartment repair profile");
                String compartmentId = requiredString(repair, "compartmentId");
                if (!validCompartments.contains(compartmentId)) {
                    throw new IllegalArgumentException("Unknown hull compartment " + hullId + " -> " + compartmentId);
                }
                if (!seenCompartments.add(compartmentId)) {
                    throw new IllegalArgumentException("Duplicate compartment repair profile: " + compartmentId);
                }
                repairs.add(new CompartmentRepairProfile(
                        compartmentId,
                        physicalInputs(repair, "inputsAtFullLossKg", ontology)));
            }
            if (!seenCompartments.equals(validCompartments)) {
                throw new IllegalArgumentException("Stage-18G hull profile must cover every compartment: " + hullId);
            }
            result.add(new HullPhysicalProfile(hullId, build, repairs));
        }
        return result;
    }

    private static List<ModuleServiceProfile> parseModules(
            JsonValue root,
            Stage18ResourceOntologyCatalog ontology,
            ShipEngineeringCatalog engineering) {
        List<ModuleServiceProfile> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(root, "moduleServiceProfiles").child; node != null; node = node.next) {
            requireObject(node, "module service profile");
            String moduleId = requiredId(node, "moduleId");
            ModuleDefinition module = engineering.findModule(moduleId);
            if (module == null) {
                throw new IllegalArgumentException("Unknown Stage-17.5 module in Stage-18G: " + moduleId);
            }
            if (!ids.add(moduleId)) {
                throw new IllegalArgumentException("Duplicate Stage-18G module service profile: " + moduleId);
            }
            List<PhysicalInputDefinition> repair = physicalInputs(node, "repairInputsAtFullLossKg", ontology);
            List<PhysicalInputDefinition> maintenance = physicalInputs(node, "maintenanceInputsKg", ontology);
            double repairMass = repair.stream().mapToDouble(PhysicalInputDefinition::massKg).sum();
            double maintenanceMass = maintenance.stream().mapToDouble(PhysicalInputDefinition::massKg).sum();
            if (repairMass > module.massKg() + MASS_TOLERANCE_KG) {
                throw new IllegalArgumentException("Module repair inputs exceed module physical mass: " + moduleId);
            }
            if (maintenanceMass >= repairMass) {
                throw new IllegalArgumentException("Scheduled service inputs must be below full-loss repair inputs: " + moduleId);
            }
            result.add(new ModuleServiceProfile(moduleId, repair, maintenance));
        }
        return result;
    }

    private static List<PhysicalInputDefinition> physicalInputs(
            JsonValue parent,
            String field,
            Stage18ResourceOntologyCatalog ontology) {
        List<PhysicalInputDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(parent, field).child; node != null; node = node.next) {
            requireObject(node, "physical input");
            String commodityId = requiredId(node, "commodityId");
            Stage18ResourceOntologyCatalog.CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null || commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
                throw new IllegalArgumentException("Unknown/non-mass Stage-18G commodity: " + commodityId);
            }
            if (commodity.kind() == CommodityKind.EXTRACTED_FEEDSTOCK) {
                throw new IllegalArgumentException("Shipyard cannot consume raw extracted feedstock directly: " + commodityId);
            }
            if (!ids.add(commodityId)) {
                throw new IllegalArgumentException("Duplicate physical input commodity: " + commodityId);
            }
            result.add(new PhysicalInputDefinition(commodityId, positive(node, "massKg")));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return result;
    }

    private static void validateProductionCoverage(
            Stage18ShipyardCatalog catalog,
            ShipyardIndustrialCatalog industrial) {
        if (catalog.getYards().isEmpty()) {
            throw new IllegalStateException("Stage-18G production catalog requires at least one physical yard");
        }
        for (ShipyardIndustrialCatalog.HullIndustrialProfile profile : industrial.getHullProfiles()) {
            if (catalog.findHullProfile(profile.hullId()) == null) {
                throw new IllegalStateException("Missing Stage-18G physical hull profile: " + profile.hullId());
            }
        }
        for (ShipyardIndustrialCatalog.ModuleIndustrialProfile profile : industrial.getModuleProfiles()) {
            if (catalog.findModuleProfile(profile.moduleId()) == null) {
                throw new IllegalStateException("Missing Stage-18G module service profile: " + profile.moduleId());
            }
        }
    }

    private static Set<String> plannerCapabilities(ShipyardIndustrialCatalog industrial) {
        Set<String> result = new HashSet<>();
        industrial.getHullProfiles().forEach(profile -> result.addAll(profile.fabricationCapabilities()));
        industrial.getModuleProfiles().forEach(profile -> result.addAll(profile.fabricationCapabilities()));
        return result;
    }

    private static Set<String> plannerRequirementIds(ShipyardIndustrialCatalog industrial) {
        Set<String> result = new HashSet<>();
        industrial.getHullProfiles().forEach(profile -> {
            profile.constructionInputs().forEach(input -> result.add(input.contentId()));
            profile.compartmentRepairs().forEach(repair ->
                    repair.repairInputsAtFullLoss().forEach(input -> result.add(input.contentId())));
        });
        return result;
    }

    private static Set<String> plannerTooling(ShipyardIndustrialCatalog industrial) {
        Set<String> result = new HashSet<>();
        industrial.getHullProfiles().forEach(profile -> result.addAll(profile.toolingTags()));
        industrial.getModuleProfiles().forEach(profile -> result.addAll(profile.toolingTags()));
        return result;
    }

    private static Dimensions3d dimensions(JsonValue node) {
        return new Dimensions3d(
                positive(node, "lengthM"),
                positive(node, "widthM"),
                positive(node, "heightM"));
    }

    private static Set<String> stringSet(JsonValue parent, String field) {
        JsonValue array = requiredArray(parent, field);
        Set<String> result = new LinkedHashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isString() || node.asString().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            String value = node.asString();
            if (!result.add(value)) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + value);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return result;
    }

    private static JsonValue requiredObject(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static JsonValue requiredArray(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static void requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }

    private static String requiredId(JsonValue parent, String field) {
        String value = requiredString(parent, field);
        if (!ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has invalid stable ID: " + value);
        }
        return value;
    }

    private static String requiredString(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return value.asString();
    }

    private static int requiredInt(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double number = value.asDouble();
        int integer = value.asInt();
        if (!Double.isFinite(number) || number != integer) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer;
    }

    private static int nonNegativeInt(JsonValue parent, String field) {
        int value = requiredInt(parent, field);
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static double positive(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
        return result;
    }

    private static double unitInterval(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result < 0d || result > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
        return result;
    }
}

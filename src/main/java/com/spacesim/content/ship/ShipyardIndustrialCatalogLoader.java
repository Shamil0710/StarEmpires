package com.spacesim.content.ship;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.CompartmentRepairProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Headless loader and reference validator for Stage-17.5G industrial requirement content. */
public final class ShipyardIndustrialCatalogLoader {
    /** Current Stage-17.5G industrial schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in engineering demonstrator requirements. */
    public static final String DEFAULT_RESOURCE = "data/content/shipyard-industrial-v1.json";

    private static final int MAX_PROFILES = 512;
    private static final int MAX_CHILDREN = 256;

    private ShipyardIndustrialCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the built-in Stage-17.5G industrial requirements against the engineering catalog.
     *
     * @param engineering authoritative Stage-17.5 engineering catalog
     * @return immutable validated industrial requirement catalog
     */
    public static ShipyardIndustrialCatalog loadDefault(ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(engineering, "engineering");
        ClassLoader classLoader = ShipyardIndustrialCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing shipyard industrial catalog: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read shipyard industrial catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Parses one Stage-17.5G industrial requirement document.
     *
     * @param json JSON document
     * @param engineering authoritative Stage-17.5 engineering catalog
     * @return immutable validated industrial catalog
     */
    public static ShipyardIndustrialCatalog parse(String json, ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(engineering, "engineering");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Shipyard industrial JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed shipyard industrial JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Shipyard industrial root must be an object");
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported shipyard industrial schemaVersion: " + schemaVersion);
        }
        List<HullIndustrialProfile> hulls = parseHullProfiles(root, engineering);
        List<ModuleIndustrialProfile> modules = parseModuleProfiles(root, engineering);
        if (hulls.isEmpty() || modules.isEmpty()) {
            throw new IllegalArgumentException("Shipyard industrial catalog requires hull and module profiles");
        }
        return new ShipyardIndustrialCatalog(schemaVersion, hulls, modules);
    }

    private static List<HullIndustrialProfile> parseHullProfiles(
            JsonValue root, ShipEngineeringCatalog engineering) {
        JsonValue array = boundedArray(root, "hullProfiles", MAX_PROFILES);
        List<HullIndustrialProfile> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "hull industrial profile");
            String hullId = requiredString(node, "hullId");
            HullDefinition hull = engineering.findHull(hullId);
            if (hull == null) {
                throw new IllegalArgumentException("Unknown industrial hull: " + hullId);
            }
            if (!ids.add(hullId)) {
                throw new IllegalArgumentException("Duplicate hull industrial profile: " + hullId);
            }
            List<ConstructionInputDefinition> constructionInputs = parseInputs(node, "constructionInputs");
            Set<String> fabrication = parseStringSet(node, "fabricationCapabilities");
            Set<String> tooling = parseStringSet(node, "toolingTags");
            double precision = unitInterval(node, "precisionRequirement");
            double power = nonNegative(node, "industrialPowerW");
            int labor = nonNegativeInt(node, "laborRequirement");
            int automation = nonNegativeInt(node, "automationRequirement");
            double work = positive(node, "assemblyWorkSeconds");

            JsonValue repairArray = boundedArray(node, "compartmentRepairs", MAX_CHILDREN);
            List<CompartmentRepairProfile> repairs = new ArrayList<>();
            Set<String> compartmentIds = new HashSet<>();
            Set<String> validCompartments = new HashSet<>();
            hull.compartments().forEach(value -> validCompartments.add(value.id()));
            for (JsonValue child = repairArray.child; child != null; child = child.next) {
                requireObject(child, "compartment repair profile");
                String compartmentId = requiredString(child, "compartmentId");
                if (!validCompartments.contains(compartmentId)) {
                    throw new IllegalArgumentException(
                            "Industrial hull profile references unknown compartment: " + hullId + " -> " + compartmentId);
                }
                if (!compartmentIds.add(compartmentId)) {
                    throw new IllegalArgumentException("Duplicate compartment repair profile: " + compartmentId);
                }
                repairs.add(new CompartmentRepairProfile(
                        compartmentId,
                        parseInputs(child, "repairInputsAtFullLoss"),
                        positive(child, "repairWorkSecondsAtFullLoss")));
            }
            if (!compartmentIds.equals(validCompartments)) {
                throw new IllegalArgumentException(
                        "Industrial hull profile must define repair requirements for every compartment: " + hullId);
            }
            result.add(new HullIndustrialProfile(
                    hullId, constructionInputs, fabrication, tooling, precision, power,
                    labor, automation, work, repairs));
        }
        return result;
    }

    private static List<ModuleIndustrialProfile> parseModuleProfiles(
            JsonValue root, ShipEngineeringCatalog engineering) {
        JsonValue array = boundedArray(root, "moduleProfiles", MAX_PROFILES);
        List<ModuleIndustrialProfile> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "module industrial profile");
            String moduleId = requiredString(node, "moduleId");
            ModuleDefinition module = engineering.findModule(moduleId);
            if (module == null) {
                throw new IllegalArgumentException("Unknown industrial module: " + moduleId);
            }
            if (!ids.add(moduleId)) {
                throw new IllegalArgumentException("Duplicate module industrial profile: " + moduleId);
            }
            result.add(new ModuleIndustrialProfile(
                    moduleId,
                    parseStringSet(node, "fabricationCapabilities"),
                    parseStringSet(node, "toolingTags"),
                    unitInterval(node, "precisionRequirement"),
                    nonNegative(node, "industrialPowerW"),
                    nonNegativeInt(node, "laborRequirement"),
                    nonNegativeInt(node, "automationRequirement"),
                    positive(node, "manufacturingWorkSeconds"),
                    positive(node, "installationWorkSeconds"),
                    positive(node, "removalWorkSeconds")));
        }
        return result;
    }

    private static List<ConstructionInputDefinition> parseInputs(JsonValue parent, String name) {
        JsonValue array = boundedArray(parent, name, MAX_CHILDREN);
        List<ConstructionInputDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "industrial input");
            String id = requiredString(node, "contentId");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate industrial input: " + id);
            }
            result.add(new ConstructionInputDefinition(id, positive(node, "amount")));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return result;
    }

    private static Set<String> parseStringSet(JsonValue parent, String name) {
        JsonValue array = boundedArray(parent, name, MAX_CHILDREN);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isString() || node.asString().isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank strings");
            }
            if (!result.add(node.asString())) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + node.asString());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return result;
    }

    private static JsonValue boundedArray(JsonValue parent, String name, int maximum) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        if (value.size > maximum) {
            throw new IllegalArgumentException(name + " exceeds maximum size " + maximum);
        }
        return value;
    }

    private static void requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }

    private static String requiredString(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank text");
        }
        return value.asString();
    }

    private static int requiredInt(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.asInt();
    }

    private static int nonNegativeInt(JsonValue node, String name) {
        int value = requiredInt(node, name);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static double positive(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return result;
    }

    private static double nonNegative(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return result;
    }

    private static double unitInterval(JsonValue node, String name) {
        double result = nonNegative(node, name);
        if (result > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
        return result;
    }
}

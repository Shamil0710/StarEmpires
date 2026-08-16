package com.spacesim.content.ship;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.SlotDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.CompartmentDamageDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.HeavyImpactModel;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.content.ship.ShipProtectionCatalog.MountDamageDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Headless loader/semantic validator for Stage-17.5F protection and subsystem-location content. */
public final class ShipProtectionCatalogLoader {
    /** Current protection-runtime JSON schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in production demonstrator resource. */
    public static final String DEFAULT_RESOURCE = "data/content/ship-protection-runtime-v1.json";

    private static final int MAX_DEFINITIONS = 512;
    private static final int MAX_CHILDREN = 256;

    private ShipProtectionCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and validates the built-in Stage-17.5F content against the production engineering catalog.
     *
     * @param engineering authoritative engineering catalog
     * @return immutable protection catalog
     */
    public static ShipProtectionCatalog loadDefault(ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(engineering, "engineering");
        ClassLoader classLoader = ShipProtectionCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing protection catalog: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read protection catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Parses one Stage-17.5F protection document and resolves all references against engineering data.
     *
     * @param json JSON document
     * @param engineering authoritative Stage-17.5A engineering catalog
     * @return validated immutable protection catalog
     */
    public static ShipProtectionCatalog parse(String json, ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(engineering, "engineering");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Protection catalog JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed protection catalog JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Protection catalog root must be an object");
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported protection schemaVersion: " + schemaVersion);
        }

        List<HeavyImpactModel> impactModels = parseImpactModels(root, engineering);
        List<HullDamageLayout> layouts = parseLayouts(root, engineering);
        if (impactModels.isEmpty() || layouts.isEmpty()) {
            throw new IllegalArgumentException("Protection catalog requires response models and hull layouts");
        }
        return new ShipProtectionCatalog(schemaVersion, impactModels, layouts);
    }

    private static List<HeavyImpactModel> parseImpactModels(
            JsonValue root, ShipEngineeringCatalog engineering) {
        JsonValue array = boundedArray(root, "heavyImpactModels", MAX_DEFINITIONS);
        List<HeavyImpactModel> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "heavy impact model");
            String responseId = requiredString(node, "responseSurfaceId");
            if (engineering.findResponseSurface(responseId) == null) {
                throw new IllegalArgumentException("Unknown heavy-impact response surface: " + responseId);
            }
            if (!ids.add(responseId)) {
                throw new IllegalArgumentException("Duplicate heavy-impact response model: " + responseId);
            }
            result.add(new HeavyImpactModel(
                    responseId,
                    positive(node, "specificAbsorptionJPerKg"),
                    unitInterval(node, "spallMassFraction"),
                    unitInterval(node, "spallEnergyFraction"),
                    boundedAngle(node, "ricochetCriticalAngleRad"),
                    unitInterval(node, "ricochetRetainedEnergyFraction")));
        }
        return result;
    }

    private static List<HullDamageLayout> parseLayouts(
            JsonValue root, ShipEngineeringCatalog engineering) {
        JsonValue array = boundedArray(root, "hullDamageLayouts", MAX_DEFINITIONS);
        List<HullDamageLayout> result = new ArrayList<>();
        Set<String> hullIds = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "hull damage layout");
            String hullId = requiredString(node, "hullId");
            HullDefinition hull = engineering.findHull(hullId);
            if (hull == null) {
                throw new IllegalArgumentException("Unknown damage-layout hull: " + hullId);
            }
            if (!hullIds.add(hullId)) {
                throw new IllegalArgumentException("Duplicate hull damage layout: " + hullId);
            }

            Set<String> validCompartments = new HashSet<>();
            hull.compartments().forEach(value -> validCompartments.add(value.id()));
            Set<String> validMounts = new HashSet<>();
            for (SlotDefinition slot : hull.slots()) {
                validMounts.add(slot.id());
            }
            for (HardpointDefinition hardpoint : hull.hardpoints()) {
                validMounts.add(hardpoint.id());
            }

            JsonValue compartmentArray = boundedArray(node, "compartments", MAX_CHILDREN);
            List<CompartmentDamageDefinition> compartments = new ArrayList<>();
            Set<String> compartmentIds = new HashSet<>();
            for (JsonValue child = compartmentArray.child; child != null; child = child.next) {
                requireObject(child, "compartment damage definition");
                String compartmentId = requiredString(child, "compartmentId");
                if (!validCompartments.contains(compartmentId)) {
                    throw new IllegalArgumentException(
                            "Damage layout references unknown compartment: " + hullId + " -> " + compartmentId);
                }
                if (!compartmentIds.add(compartmentId)) {
                    throw new IllegalArgumentException("Duplicate compartment damage definition: " + compartmentId);
                }
                compartments.add(new CompartmentDamageDefinition(
                        compartmentId,
                        positive(child, "structuralDamageCapacityJ"),
                        unitInterval(child, "subsystemCouplingFraction")));
            }
            if (!compartmentIds.equals(validCompartments)) {
                throw new IllegalArgumentException("Damage layout must define every hull compartment: " + hullId);
            }

            JsonValue mountArray = boundedArray(node, "mounts", MAX_CHILDREN);
            List<MountDamageDefinition> mounts = new ArrayList<>();
            Set<String> mountIds = new HashSet<>();
            for (JsonValue child = mountArray.child; child != null; child = child.next) {
                requireObject(child, "mount damage definition");
                String mountId = requiredString(child, "mountId");
                String compartmentId = requiredString(child, "compartmentId");
                if (!validMounts.contains(mountId)) {
                    throw new IllegalArgumentException(
                            "Damage layout references unknown mount: " + hullId + " -> " + mountId);
                }
                if (!validCompartments.contains(compartmentId)) {
                    throw new IllegalArgumentException(
                            "Mount damage layout references unknown compartment: " + compartmentId);
                }
                if (!mountIds.add(mountId)) {
                    throw new IllegalArgumentException("Duplicate mount damage definition: " + mountId);
                }
                mounts.add(new MountDamageDefinition(
                        mountId, compartmentId, positive(child, "subsystemDamageCapacityJ")));
            }
            if (!mountIds.equals(validMounts)) {
                throw new IllegalArgumentException("Damage layout must locate every hull mount: " + hullId);
            }
            result.add(new HullDamageLayout(hullId, compartments, mounts));
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

    private static int requiredInt(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.asInt();
    }

    private static String requiredString(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank text");
        }
        return value.asString();
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

    private static double unitInterval(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result < 0d || result > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
        return result;
    }

    private static double boundedAngle(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result <= 0d || result > Math.PI / 2d) {
            throw new IllegalArgumentException(name + " must be in (0,pi/2]");
        }
        return result;
    }
}

package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and strictly validates the versioned Stage-18F station-infrastructure catalog. */
public final class Stage18StationInfrastructureCatalogLoader {
    /** Current supported station-infrastructure schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production station-infrastructure resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-stations-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_ARCHETYPES = Set.of(
            "station.infrastructure.mining_outpost",
            "station.infrastructure.volatile_depot",
            "station.infrastructure.refinery_complex",
            "station.infrastructure.industrial_station",
            "station.infrastructure.high_tech_hub",
            "station.infrastructure.trade_logistics_hub",
            "station.infrastructure.naval_ordnance_depot",
            "station.infrastructure.frontier_multipurpose");

    private Stage18StationInfrastructureCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the production Stage-18F station catalog against the Stage-18 ontology/facility catalog.
     *
     * @return immutable validated station-infrastructure catalog
     */
    public static Stage18StationInfrastructureCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        ClassLoader classLoader = Stage18StationInfrastructureCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 station catalog: " + DEFAULT_RESOURCE);
            }
            Stage18StationInfrastructureCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology, facilities);
            validateProductionBaseline(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 station catalog", exception);
        }
    }

    /**
     * Parses one station-infrastructure document and validates facility/storage references.
     *
     * @param json non-empty station JSON document
     * @param ontology authoritative Stage-18 resource ontology
     * @param facilities authoritative Stage-18E facility catalog
     * @return immutable validated station-infrastructure catalog
     */
    public static Stage18StationInfrastructureCatalog parse(
            String json,
            Stage18ResourceOntologyCatalog ontology,
            Stage18FacilityCatalog facilities) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilities, "facilities");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Station JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 station JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Station root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 station schema: " + schemaVersion);
        }

        List<StationArchetypeDefinition> archetypes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requireArray(root, "archetypes").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate Stage-18 station archetype: " + id);
            }
            List<String> installedFacilities = parseIdList(node, "installedFacilityDefinitionIds");
            for (String facilityId : installedFacilities) {
                if (checkedFacilities.findFacility(facilityId) == null) {
                    throw new IllegalArgumentException("Unknown facility " + facilityId + " for " + id);
                }
            }
            Map<String, Double> capacities = parseCapacityMap(node, "storageCapacityByClassKg", checkedOntology, id);
            Set<String> transferClasses = parseIdSet(node, "transferStorageClassIds");
            for (String storageClassId : transferClasses) {
                if (checkedOntology.findStorageClass(storageClassId) == null) {
                    throw new IllegalArgumentException("Unknown transfer storage class " + storageClassId + " for " + id);
                }
            }
            archetypes.add(new StationArchetypeDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    installedFacilities,
                    capacities,
                    transferClasses,
                    requireDouble(node, "transferMassRateKgPerSecond"),
                    requireDouble(node, "maxTransferUnitMassKg"),
                    parseIdSet(node, "allowedLocationTags")));
        }
        if (archetypes.isEmpty()) {
            throw new IllegalArgumentException("Station catalog must contain archetypes");
        }
        return new Stage18StationInfrastructureCatalog(schemaVersion, archetypes);
    }

    private static void validateProductionBaseline(Stage18StationInfrastructureCatalog catalog) {
        Set<String> missing = new HashSet<>(REQUIRED_ARCHETYPES);
        for (StationArchetypeDefinition archetype : catalog.getArchetypes()) {
            missing.remove(archetype.id());
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production station catalog missing archetypes: " + missing);
        }
        StationArchetypeDefinition logistics = catalog.findArchetype("station.infrastructure.trade_logistics_hub");
        if (logistics == null || !logistics.installedFacilityDefinitionIds().isEmpty()) {
            throw new IllegalStateException("Trade/logistics hub must not gain hidden production facilities");
        }
    }

    private static Map<String, Double> parseCapacityMap(
            JsonValue node,
            String field,
            Stage18ResourceOntologyCatalog ontology,
            String subject) {
        JsonValue object = node.get(field);
        if (object == null || !object.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        Map<String, Double> capacities = new LinkedHashMap<>();
        for (JsonValue child = object.child; child != null; child = child.next) {
            String storageClassId = child.name;
            validateId(storageClassId, field);
            if (ontology.findStorageClass(storageClassId) == null) {
                throw new IllegalArgumentException("Unknown storage class " + storageClassId + " for " + subject);
            }
            if (!child.isNumber()) {
                throw new IllegalArgumentException(field + " values must be numbers");
            }
            if (capacities.putIfAbsent(storageClassId, child.asDouble()) != null) {
                throw new IllegalArgumentException("Duplicate storage class " + storageClassId + " for " + subject);
            }
        }
        return capacities;
    }

    private static List<String> parseIdList(JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            String id = child.asString();
            validateId(id, field);
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + id);
            }
            result.add(id);
        }
        return result;
    }

    private static Set<String> parseIdSet(JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        Set<String> result = new LinkedHashSet<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            String id = child.asString();
            validateId(id, field);
            if (!result.add(id)) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + id);
            }
        }
        return result;
    }

    private static JsonValue requireArray(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static int requireInt(JsonValue node, String field) {
        JsonValue value = node.get(field);
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

    private static double requireDouble(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asDouble();
    }

    private static String requireString(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asString();
    }

    private static String requireNonBlank(JsonValue node, String field) {
        String value = requireString(node, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static String requireId(JsonValue node, String field) {
        String value = requireString(node, field);
        validateId(value, field);
        return value;
    }

    private static void validateId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has invalid stable ID: " + value);
        }
    }
}

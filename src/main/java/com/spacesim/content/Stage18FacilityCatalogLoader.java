package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18FacilityCatalog.FacilityFamily;

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

/** Loads and strictly validates the versioned Stage-18E industrial facility catalog. */
public final class Stage18FacilityCatalogLoader {
    /** Current supported facility schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production facility resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-facilities-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_FACILITIES = Set.of(
            "facility.extraction.asteroid",
            "facility.extraction.surface",
            "facility.extraction.deep",
            "facility.extraction.atmospheric",
            "facility.processing.volatiles",
            "facility.processing.bulk_refinery",
            "facility.processing.advanced_materials",
            "facility.processing.chemical",
            "facility.processing.recycling",
            "facility.fabrication.heavy",
            "facility.fabrication.electrical",
            "facility.fabrication.precision",
            "facility.fabrication.ordnance",
            "facility.fabrication.assembly");
    private static final Set<String> REQUIRED_CAPABILITIES = Set.of(
            "capability.extraction.asteroid_excavation",
            "capability.extraction.surface_mining",
            "capability.extraction.deep_mining",
            "capability.extraction.thermal_volatiles",
            "capability.extraction.atmospheric_harvesting",
            "capability.process.beneficiation",
            "capability.process.volatile_processing",
            "capability.process.bulk_refining",
            "capability.process.advanced_materials",
            "capability.process.chemical_processing",
            "capability.process.recycling",
            "capability.fabrication.heavy",
            "capability.fabrication.electrical",
            "capability.fabrication.precision",
            "capability.fabrication.assembly",
            "capability.fabrication.ordnance");

    private Stage18FacilityCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the built-in Stage-18E facility catalog against the production resource ontology.
     *
     * @return immutable validated production facility catalog
     */
    public static Stage18FacilityCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        ClassLoader classLoader = Stage18FacilityCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 facility catalog: " + DEFAULT_RESOURCE);
            }
            Stage18FacilityCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology);
            validateProductionBaseline(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 facility catalog", exception);
        }
    }

    /**
     * Parses one facility document and validates capability/storage references.
     *
     * @param json non-empty facility JSON document
     * @param ontology authoritative Stage-18 ontology
     * @return immutable validated facility catalog
     */
    public static Stage18FacilityCatalog parse(String json, Stage18ResourceOntologyCatalog ontology) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Facility JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 facility JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Facility root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 facility schema: " + schemaVersion);
        }

        List<FacilityDefinition> facilities = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requireArray(root, "facilities").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate facility definition: " + id);
            }
            FacilityFamily family = enumValue(
                    FacilityFamily.class, requireString(node, "family"), "facility family", id);
            Set<String> capabilities = parseIdSet(node, "capabilityTags");
            for (String capability : capabilities) {
                if (checkedOntology.findCapabilityTag(capability) == null) {
                    throw new IllegalArgumentException("Unknown capability " + capability + " for " + id);
                }
            }
            Set<String> storageClasses = parseIdSet(node, "storageClassInterfaces");
            for (String storageClass : storageClasses) {
                if (checkedOntology.findStorageClass(storageClass) == null) {
                    throw new IllegalArgumentException("Unknown storage class " + storageClass + " for " + id);
                }
            }
            Set<String> locationTags = parseIdSet(node, "allowedLocationTags");
            facilities.add(new FacilityDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    family,
                    capabilities,
                    requireDouble(node, "ratedProcessPowerW"),
                    requireDouble(node, "engineeringWorkRate"),
                    requireDouble(node, "maintenanceWorkRate"),
                    requireDouble(node, "heatRejectionWPerProcessW"),
                    requireDouble(node, "requiredLaborUnitsAtFullRate"),
                    requireDouble(node, "automationFloorFraction"),
                    storageClasses,
                    requireDouble(node, "maxHandledUnitMassKg"),
                    locationTags));
        }
        if (facilities.isEmpty()) {
            throw new IllegalArgumentException("Facility catalog must contain facilities");
        }
        return new Stage18FacilityCatalog(schemaVersion, facilities);
    }

    private static void validateProductionBaseline(Stage18FacilityCatalog catalog) {
        Set<String> missingFacilities = new HashSet<>(REQUIRED_FACILITIES);
        Set<String> coveredCapabilities = new HashSet<>();
        for (FacilityDefinition facility : catalog.getFacilities()) {
            missingFacilities.remove(facility.id());
            coveredCapabilities.addAll(facility.capabilityTags());
        }
        if (!missingFacilities.isEmpty()) {
            throw new IllegalStateException("Production facility catalog missing facilities: " + missingFacilities);
        }
        Set<String> missingCapabilities = new HashSet<>(REQUIRED_CAPABILITIES);
        missingCapabilities.removeAll(coveredCapabilities);
        if (!missingCapabilities.isEmpty()) {
            throw new IllegalStateException("Production facility catalog missing capability coverage: " + missingCapabilities);
        }
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

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String field, String subject) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + field + " for " + subject + ": " + value, exception);
        }
    }
}

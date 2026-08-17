package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;

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

/** Loads and strictly validates the versioned Stage-18B extraction-method catalog. */
public final class Stage18ExtractionCatalogLoader {
    /** Current supported extraction schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production extraction-method resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-extraction-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_METHODS = Set.of(
            "extraction.asteroid_excavation",
            "extraction.surface_mining",
            "extraction.deep_mining",
            "extraction.thermal_volatiles",
            "extraction.salvage_recovery");

    private Stage18ExtractionCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the production Stage-18B extraction catalog against the production ontology.
     *
     * @return immutable validated extraction catalog
     */
    public static Stage18ExtractionCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        ClassLoader classLoader = Stage18ExtractionCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 extraction catalog: " + DEFAULT_RESOURCE);
            }
            Stage18ExtractionCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology);
            validateProductionBaseline(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 extraction catalog", exception);
        }
    }

    /**
     * Parses extraction methods and validates all ontology references.
     *
     * @param json non-empty extraction JSON document
     * @param ontology Stage-18 resource ontology referenced by the document
     * @return immutable validated catalog
     */
    public static Stage18ExtractionCatalog parse(String json, Stage18ResourceOntologyCatalog ontology) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Extraction JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 extraction JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Extraction root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 extraction schema: " + schemaVersion);
        }

        JsonValue methodsNode = requireArray(root, "methods");
        List<ExtractionMethodDefinition> methods = new ArrayList<>();
        Map<String, ExtractionMethodDefinition> byId = new LinkedHashMap<>();
        for (JsonValue node = methodsNode.child; node != null; node = node.next) {
            String id = requireId(node, "id");
            SourceKind sourceKind = enumValue(SourceKind.class, requireString(node, "sourceKind"), "sourceKind", id);
            ExtractionEnvironment environment = enumValue(
                    ExtractionEnvironment.class, requireString(node, "environment"), "environment", id);
            Set<String> occurrenceIds = parseIdSet(node, "compatibleOccurrenceTypeIds");
            Set<String> capabilityTags = parseIdSet(node, "requiredCapabilityTags");

            for (String occurrenceId : occurrenceIds) {
                if (checkedOntology.findOccurrenceType(occurrenceId) == null) {
                    throw new IllegalArgumentException("Unknown occurrence type " + occurrenceId + " for " + id);
                }
            }
            for (String capabilityTag : capabilityTags) {
                if (checkedOntology.findCapabilityTag(capabilityTag) == null) {
                    throw new IllegalArgumentException("Unknown capability tag " + capabilityTag + " for " + id);
                }
            }

            ExtractionMethodDefinition method = new ExtractionMethodDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    sourceKind,
                    environment,
                    occurrenceIds,
                    capabilityTags,
                    requireDouble(node, "workSecondsPerSourceKg"),
                    requireDouble(node, "energyJPerSourceKg"),
                    requireDouble(node, "maintenanceWorkSecondsPerSourceKg"),
                    requireDouble(node, "maxSourceKgPerSecond"),
                    requireDouble(node, "recoveryFraction"));
            if (byId.putIfAbsent(id, method) != null) {
                throw new IllegalArgumentException("Duplicate extraction method: " + id);
            }
            methods.add(method);
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Extraction catalog must contain methods");
        }
        return new Stage18ExtractionCatalog(schemaVersion, methods);
    }

    private static void validateProductionBaseline(Stage18ExtractionCatalog catalog) {
        Set<String> missing = new HashSet<>(REQUIRED_METHODS);
        for (ExtractionMethodDefinition method : catalog.getMethods()) {
            missing.remove(method.id());
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production extraction catalog missing methods: " + missing);
        }
        requireMethod(catalog, "extraction.asteroid_excavation", SourceKind.NATURAL_OCCURRENCE, ExtractionEnvironment.FREE_BODY);
        requireMethod(catalog, "extraction.surface_mining", SourceKind.NATURAL_OCCURRENCE, ExtractionEnvironment.SURFACE);
        requireMethod(catalog, "extraction.deep_mining", SourceKind.NATURAL_OCCURRENCE, ExtractionEnvironment.DEEP_SUBSURFACE);
        requireMethod(catalog, "extraction.thermal_volatiles", SourceKind.NATURAL_OCCURRENCE, ExtractionEnvironment.VOLATILE_BEARING);
        requireMethod(catalog, "extraction.salvage_recovery", SourceKind.SALVAGE_STREAM, ExtractionEnvironment.SALVAGE_SITE);
    }

    private static void requireMethod(
            Stage18ExtractionCatalog catalog,
            String id,
            SourceKind sourceKind,
            ExtractionEnvironment environment) {
        ExtractionMethodDefinition method = catalog.findMethod(id);
        if (method == null || method.sourceKind() != sourceKind || method.environment() != environment) {
            throw new IllegalStateException("Production extraction method has wrong source semantics: " + id);
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

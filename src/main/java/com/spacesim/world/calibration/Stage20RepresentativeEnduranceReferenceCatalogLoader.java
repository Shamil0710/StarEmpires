package com.spacesim.world.calibration;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceReferenceCatalog.ReferenceDefinition;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads the versioned Stage-20 representative endurance calibration resource. */
public final class Stage20RepresentativeEnduranceReferenceCatalogLoader {
    /** Current supported schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Packaged Stage-20 endurance calibration resource. */
    public static final String DEFAULT_RESOURCE = "data/calibration/stage20-representative-endurance-v1.json";

    private Stage20RepresentativeEnduranceReferenceCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the packaged provisional endurance policy.
     *
     * @return immutable validated endurance catalog
     */
    public static Stage20RepresentativeEnduranceReferenceCatalog loadDefault() {
        ClassLoader classLoader = Stage20RepresentativeEnduranceReferenceCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-20 endurance resource: " + DEFAULT_RESOURCE);
            }
            Stage20RepresentativeEnduranceReferenceCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
                throw new IllegalStateException("Stage-20 endurance catalog must remain provisional");
            }
            if (!catalog.stage22ReviewRequired()) {
                throw new IllegalStateException("Stage-20 endurance catalog must require Stage-22 review");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-20 endurance resource", exception);
        }
    }

    /**
     * Parses one standalone endurance calibration document.
     *
     * @param json non-empty JSON document
     * @return immutable validated catalog
     */
    public static Stage20RepresentativeEnduranceReferenceCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-20 endurance JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-20 endurance JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-20 endurance root must be an object");
        }

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 endurance schema: " + schemaVersion);
        }
        String version = requireString(root, "version");
        CalibrationAuthority status = enumValue(
                CalibrationAuthority.class, requireString(root, "status"), "status");
        boolean stage22ReviewRequired = requireBoolean(root, "stage22ReviewRequired");
        String policyEvidence = requireString(root, "policyEvidence");

        JsonValue array = root.get("references");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("references must be an array");
        }
        List<ReferenceDefinition> references = new ArrayList<>();
        Set<String> classes = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("reference entries must be objects");
            }
            ReferenceDefinition reference = new ReferenceDefinition(
                    requireString(node, "representativeClass"),
                    requirePositiveDouble(node, "sustainedThrustN"),
                    requireString(node, "sustainedThrustSourceEvidenceId"),
                    requirePositiveDouble(node, "missionStoresEnduranceS"),
                    requireString(node, "missionStoresSourceEvidenceId"));
            if (!classes.add(reference.representativeClass())) {
                throw new IllegalArgumentException(
                        "Duplicate Stage-20 endurance role: " + reference.representativeClass());
            }
            references.add(reference);
        }
        if (references.isEmpty()) {
            throw new IllegalArgumentException("Stage-20 endurance catalog must not be empty");
        }
        return new Stage20RepresentativeEnduranceReferenceCatalog(
                schemaVersion,
                version,
                status,
                stage22ReviewRequired,
                policyEvidence,
                references);
    }

    private static JsonValue requireField(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static int requireInt(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        return value.asInt();
    }

    private static boolean requireBoolean(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.asBoolean();
    }

    private static String requireString(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asString();
    }

    private static double requirePositiveDouble(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        double number = value.asDouble();
        if (!Double.isFinite(number) || number <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
        return number;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value, exception);
        }
    }
}

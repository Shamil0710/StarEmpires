package com.spacesim.world.calibration;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads the Stage-20 local-route semantic distance-band authoring resource. */
public final class Stage20LocalRouteSemanticBandCatalogLoader {
    /** Current supported schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Packaged semantic-band resource. */
    public static final String DEFAULT_RESOURCE = "data/calibration/stage20-local-route-semantic-bands-v1.json";

    private Stage20LocalRouteSemanticBandCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the packaged provisional Stage-20 route-band policy.
     *
     * @return immutable validated local-route semantic-band catalog
     */
    public static Stage20LocalRouteSemanticBandCatalog loadDefault() {
        ClassLoader classLoader = Stage20LocalRouteSemanticBandCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-20 local route resource: " + DEFAULT_RESOURCE);
            }
            Stage20LocalRouteSemanticBandCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
                throw new IllegalStateException("Stage-20 local route bands must remain provisional");
            }
            if (!catalog.stage22ReviewRequired()) {
                throw new IllegalStateException("Stage-20 local route bands must require Stage-22 review");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-20 local route resource", exception);
        }
    }

    /**
     * Parses one standalone local-route band document.
     *
     * @param json non-empty local-route semantic-band JSON document
     * @return immutable validated local-route semantic-band catalog
     */
    public static Stage20LocalRouteSemanticBandCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-20 local route JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-20 local route JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-20 local route root must be an object");
        }
        int schema = requireInt(root, "schemaVersion");
        if (schema != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 local route schema: " + schema);
        }
        CalibrationAuthority status = enumValue(
                CalibrationAuthority.class, requireString(root, "status"), "status");
        JsonValue array = root.get("bands");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("bands must be an array");
        }
        List<BandDefinition> bands = new ArrayList<>();
        Set<BandId> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            BandDefinition band = new BandDefinition(
                    enumValue(BandId.class, requireString(node, "id"), "id"),
                    requirePositiveDouble(node, "minDistanceM"),
                    requirePositiveDouble(node, "maxDistanceM"),
                    requireString(node, "sourceEvidenceId"));
            if (!ids.add(band.id())) {
                throw new IllegalArgumentException("Duplicate local route band: " + band.id());
            }
            bands.add(band);
        }
        if (!ids.equals(EnumSet.allOf(BandId.class))) {
            throw new IllegalArgumentException("Local route catalog must define every required Stage-20A band");
        }
        return new Stage20LocalRouteSemanticBandCatalog(
                schema,
                requireString(root, "version"),
                status,
                requireBoolean(root, "stage22ReviewRequired"),
                requireString(root, "policyEvidence"),
                bands);
    }

    private static JsonValue requireField(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static String requireString(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asString();
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

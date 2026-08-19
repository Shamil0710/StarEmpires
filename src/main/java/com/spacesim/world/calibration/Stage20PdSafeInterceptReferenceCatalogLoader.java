package com.spacesim.world.calibration;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.world.calibration.Stage20PdSafeInterceptReferenceCatalog.DebrisRiskSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and validates packaged Stage-20A PD debris-risk calibration evidence. */
public final class Stage20PdSafeInterceptReferenceCatalogLoader {
    /** Current supported packaged-evidence schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Current packaged calibration resource. */
    public static final String DEFAULT_RESOURCE = "data/calibration/stage20-pd-safe-intercept-v1.json";

    private Stage20PdSafeInterceptReferenceCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the packaged v0.7-derived evidence and provisional Stage-20 risk policy.
     *
     * @return immutable validated catalog
     */
    public static Stage20PdSafeInterceptReferenceCatalog loadDefault() {
        ClassLoader classLoader = Stage20PdSafeInterceptReferenceCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-20 PD calibration resource: " + DEFAULT_RESOURCE);
            }
            Stage20PdSafeInterceptReferenceCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
                throw new IllegalStateException("Stage-20 PD safe-intercept evidence must remain provisional");
            }
            if (!catalog.stage22ReviewRequired()) {
                throw new IllegalStateException("Stage-20 PD safe-intercept policy must require Stage-22 review");
            }
            if (!"authoring-benchmark-only".equals(catalog.sourceBenchmarkStatus())) {
                throw new IllegalStateException("v0.7 source must not be silently promoted to production physics");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-20 PD calibration resource", exception);
        }
    }

    /**
     * Parses one standalone Stage-20 PD evidence document.
     *
     * @param json non-empty JSON document
     * @return immutable validated evidence catalog
     */
    public static Stage20PdSafeInterceptReferenceCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-20 PD calibration JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-20 PD calibration JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-20 PD calibration root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 PD schema: " + schemaVersion);
        }
        JsonValue sampleArray = requireField(root, "samples");
        if (!sampleArray.isArray()) {
            throw new IllegalArgumentException("samples must be an array");
        }
        List<DebrisRiskSample> samples = new ArrayList<>();
        Set<String> uniqueCoordinates = new HashSet<>();
        for (JsonValue node = sampleArray.child; node != null; node = node.next) {
            DebrisRiskSample sample = new DebrisRiskSample(
                    requirePositiveDouble(node, "lateralSigmaMps"),
                    requirePositiveDouble(node, "standOffM"),
                    requireNonNegativeDouble(node, "shipHitFraction"),
                    requireNonNegativeDouble(node, "intersectingEnergyJ"));
            String coordinate = sample.lateralSigmaMps() + ":" + sample.standOffM();
            if (!uniqueCoordinates.add(coordinate)) {
                throw new IllegalArgumentException("Duplicate Stage-20 PD sensitivity coordinate: " + coordinate);
            }
            samples.add(sample);
        }
        return new Stage20PdSafeInterceptReferenceCatalog(
                schemaVersion,
                requireString(root, "version"),
                enumValue(CalibrationAuthority.class, requireString(root, "status"), "status"),
                requireBoolean(root, "stage22ReviewRequired"),
                requireString(root, "sourceBenchmark"),
                requireString(root, "sourceBenchmarkStatus"),
                requireString(root, "sourceThreat"),
                requirePositiveDouble(root, "sourceThreatKineticEnergyJ"),
                requireString(root, "projectedTarget"),
                requireString(root, "policyEvidence"),
                requirePositiveDouble(root, "maxProjectedHitFraction"),
                samples);
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
        double value = requireNumeric(object, field);
        if (value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static double requireNonNegativeDouble(JsonValue object, String field) {
        double value = requireNumeric(object, field);
        if (value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static double requireNumeric(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        double number = value.asDouble();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(field + " must be finite");
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

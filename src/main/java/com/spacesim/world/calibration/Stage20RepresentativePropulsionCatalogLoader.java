package com.spacesim.world.calibration;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and physically validates the versioned Stage-20 propulsion-reference calibration catalog. */
public final class Stage20RepresentativePropulsionCatalogLoader {
    /** Current supported resource schema. */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    /** Default production-packaged calibration evidence resource. */
    public static final String DEFAULT_RESOURCE = "data/calibration/stage20-representative-propulsion-v2.json";

    private static final String ACCEPTED_BASELINE_ID = "ship_mathematics_v1_0_design_baseline";

    private Stage20RepresentativePropulsionCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the packaged Stage-20A reference catalog and verifies its authority boundary.
     *
     * @return immutable validated calibration reference catalog
     */
    public static Stage20RepresentativePropulsionCatalog loadDefault() {
        ClassLoader classLoader = Stage20RepresentativePropulsionCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-20 propulsion calibration resource: " + DEFAULT_RESOURCE);
            }
            Stage20RepresentativePropulsionCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
                throw new IllegalStateException("Default Stage-20 reference catalog must remain provisional");
            }
            if (!catalog.stage22ReviewRequired()) {
                throw new IllegalStateException("Default Stage-20 references must require Stage-22 review");
            }
            if (!ACCEPTED_BASELINE_ID.equals(catalog.sourceBaselineId())) {
                throw new IllegalStateException("Unexpected Stage-20 propulsion architecture baseline");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-20 propulsion calibration resource", exception);
        }
    }

    /**
     * Parses and validates a standalone Stage-20 propulsion-reference document.
     *
     * @param json non-empty JSON document
     * @return immutable validated catalog
     */
    public static Stage20RepresentativePropulsionCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-20 propulsion reference JSON must not be blank");
        }

        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-20 propulsion reference JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-20 propulsion reference root must be an object");
        }

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 propulsion schema: " + schemaVersion);
        }
        String version = requireString(root, "version");
        CalibrationAuthority status = enumValue(
                CalibrationAuthority.class, requireString(root, "status"), "status");
        String sourceBaselineId = requireString(root, "sourceBaselineId");
        String sourceEvidence = requireString(root, "sourceEvidence");
        boolean stage22ReviewRequired = requireBoolean(root, "stage22ReviewRequired");

        JsonValue array = root.get("references");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("references must be an array");
        }

        List<ReferenceDefinition> references = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> classes = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("reference entries must be objects");
            }
            ReferenceDefinition reference = new ReferenceDefinition(
                    requireString(node, "id"),
                    requireString(node, "representativeClass"),
                    requireString(node, "sourceEvidenceId"),
                    requireNonNegativeDouble(node, "designDryMassKg"),
                    requireNonNegativeDouble(node, "ammunitionMassKg"),
                    requireNonNegativeDouble(node, "missionCargoStoresMassKg"),
                    requirePositiveDouble(node, "reactionMassKg"),
                    requirePositiveDouble(node, "departureMassKg"),
                    requirePositiveDouble(node, "thrustN"),
                    requirePositiveDouble(node, "exhaustVelocityMps"),
                    requirePositiveDouble(node, "expectedAccelerationMps2"),
                    requirePositiveDouble(node, "expectedDeltaVMps"));
            validatePhysicalClosure(reference);
            if (!ids.add(reference.id())) {
                throw new IllegalArgumentException("Duplicate Stage-20 reference id: " + reference.id());
            }
            if (!classes.add(reference.representativeClass())) {
                throw new IllegalArgumentException(
                        "Duplicate Stage-20 representative class: " + reference.representativeClass());
            }
            references.add(reference);
        }
        if (references.isEmpty()) {
            throw new IllegalArgumentException("Stage-20 propulsion reference catalog must not be empty");
        }

        return new Stage20RepresentativePropulsionCatalog(
                schemaVersion,
                version,
                status,
                sourceBaselineId,
                sourceEvidence,
                stage22ReviewRequired,
                references);
    }

    private static void validatePhysicalClosure(ReferenceDefinition reference) {
        if (!(reference.reactionMassKg() < reference.departureMassKg())) {
            throw new IllegalArgumentException("Reaction mass must be smaller than departure mass: " + reference.id());
        }
        double recomposedMassKg = reference.designDryMassKg()
                + reference.ammunitionMassKg()
                + reference.missionCargoStoresMassKg()
                + reference.reactionMassKg();
        assertClose(reference.departureMassKg(), recomposedMassKg, "mass closure", reference.id());

        double accelerationMps2 = reference.thrustN() / reference.departureMassKg();
        assertClose(reference.expectedAccelerationMps2(), accelerationMps2, "acceleration closure", reference.id());

        double deltaVMps = reference.exhaustVelocityMps()
                * Math.log(reference.departureMassKg()
                / (reference.departureMassKg() - reference.reactionMassKg()));
        assertClose(reference.expectedDeltaVMps(), deltaVMps, "delta-v closure", reference.id());
    }

    private static void assertClose(double expected, double actual, String label, String id) {
        double tolerance = Math.max(1.0e-12, Math.abs(expected) * 1.0e-12);
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(label + " failed for " + id
                    + ": expected=" + expected + ", actual=" + actual);
        }
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
        double value = requireFiniteDouble(object, field);
        if (value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static double requireNonNegativeDouble(JsonValue object, String field) {
        double value = requireFiniteDouble(object, field);
        if (value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static double requireFiniteDouble(JsonValue object, String field) {
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

    private static JsonValue requireField(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value, exception);
        }
    }
}

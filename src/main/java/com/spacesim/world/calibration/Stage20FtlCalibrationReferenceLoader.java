package com.spacesim.world.calibration;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.CalibrationGap;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.JumpTopologyMode;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.ReferenceClosure;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.ReferenceDrive;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads and closes the accepted-reference Stage-20 FTL calibration resource. */
public final class Stage20FtlCalibrationReferenceLoader {
    /** Current supported FTL calibration schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default packaged FTL calibration resource. */
    public static final String DEFAULT_RESOURCE = "data/calibration/stage20-ftl-jump-reference-v1.json";

    private static final String ACCEPTED_BASELINE_ID = "ship_mathematics_v1_0_design_baseline";

    private Stage20FtlCalibrationReferenceLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the packaged accepted-reference FTL input and verifies its authority/gap boundary.
     *
     * @return immutable validated FTL calibration reference
     */
    public static Stage20FtlCalibrationReference loadDefault() {
        ClassLoader classLoader = Stage20FtlCalibrationReferenceLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-20 FTL calibration resource: " + DEFAULT_RESOURCE);
            }
            Stage20FtlCalibrationReference reference = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (reference.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
                throw new IllegalStateException("Default Stage-20 FTL reference must remain provisional");
            }
            if (!reference.stage22ReviewRequired()) {
                throw new IllegalStateException("Default Stage-20 FTL reference must require Stage-22 review");
            }
            if (!ACCEPTED_BASELINE_ID.equals(reference.sourceBaselineId())) {
                throw new IllegalStateException("Unexpected Stage-20 FTL reference baseline");
            }
            if (reference.topologyMode() != JumpTopologyMode.NEIGHBOR_EDGE_ONLY) {
                throw new IllegalStateException("Ordinary Stage-20 FTL must remain neighbor-edge-only");
            }
            List<CalibrationGap> requiredGaps = List.of(
                    CalibrationGap.PRODUCTION_FTL_MODULE_NOT_AUTHORED,
                    CalibrationGap.EDGE_TRANSIT_DISTRIBUTION_NOT_YET_WORLD_AUTHORED,
                    CalibrationGap.DRIVE_HEAT_COEFFICIENT_NOT_NUMERIC_IN_V1_BASELINE);
            if (!reference.unresolvedGaps().containsAll(requiredGaps)) {
                throw new IllegalStateException("Default Stage-20 FTL reference hides an unresolved baseline gap");
            }
            return reference;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-20 FTL calibration resource", exception);
        }
    }

    /**
     * Parses and physically closes one Stage-20 FTL reference document.
     *
     * @param json non-empty JSON document
     * @return immutable validated reference
     */
    public static Stage20FtlCalibrationReference parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-20 FTL reference JSON must not be blank");
        }

        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-20 FTL reference JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-20 FTL reference root must be an object");
        }

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 FTL schema: " + schemaVersion);
        }
        String version = requireString(root, "version");
        CalibrationAuthority status = enumValue(
                CalibrationAuthority.class, requireString(root, "status"), "status");
        String sourceBaselineId = requireString(root, "sourceBaselineId");
        String sourceEvidence = requireString(root, "sourceEvidence");
        boolean stage22ReviewRequired = requireBoolean(root, "stage22ReviewRequired");
        JumpTopologyMode topologyMode = enumValue(
                JumpTopologyMode.class, requireString(root, "topologyMode"), "topologyMode");

        JsonValue driveNode = requireObject(root, "referenceDrive");
        ReferenceDrive drive = new ReferenceDrive(
                requireString(driveNode, "id"),
                requirePositiveDouble(driveNode, "maxTranslatedMassKg"),
                requirePositiveDouble(driveNode, "translationEnergyPerKgJ"),
                requirePositiveDouble(driveNode, "chargePowerW"),
                requirePositiveDouble(driveNode, "chargeEfficiency"),
                requireNonNegativeDouble(driveNode, "cooldownS"));

        JsonValue closureNode = requireObject(root, "referenceClosure");
        ReferenceClosure closure = new ReferenceClosure(
                requirePositiveDouble(closureNode, "translatedMassKg"),
                requirePositiveDouble(closureNode, "requiredTranslationEnergyJ"),
                requirePositiveDouble(closureNode, "spoolTimeS"),
                requirePositiveDouble(closureNode, "exampleEdgeTransitTimeS"));
        validateClosure(drive, closure);

        JsonValue gapsNode = root.get("unresolvedGaps");
        if (gapsNode == null || !gapsNode.isArray()) {
            throw new IllegalArgumentException("unresolvedGaps must be an array");
        }
        List<CalibrationGap> gaps = new ArrayList<>();
        for (JsonValue gapNode = gapsNode.child; gapNode != null; gapNode = gapNode.next) {
            if (!gapNode.isString()) {
                throw new IllegalArgumentException("unresolvedGaps entries must be strings");
            }
            gaps.add(enumValue(CalibrationGap.class, gapNode.asString(), "unresolvedGaps entry"));
        }

        return new Stage20FtlCalibrationReference(
                schemaVersion,
                version,
                status,
                sourceBaselineId,
                sourceEvidence,
                stage22ReviewRequired,
                topologyMode,
                drive,
                closure,
                gaps);
    }

    private static void validateClosure(ReferenceDrive drive, ReferenceClosure closure) {
        if (closure.translatedMassKg() > drive.maxTranslatedMassKg()) {
            throw new IllegalArgumentException("Reference closure exceeds reference-drive translated-mass limit");
        }
        double expectedEnergyJ = closure.translatedMassKg() * drive.translationEnergyPerKgJ();
        assertClose(closure.requiredTranslationEnergyJ(), expectedEnergyJ, "translation-energy closure");
        double usefulChargePowerW = drive.chargePowerW() * drive.chargeEfficiency();
        double expectedSpoolS = expectedEnergyJ / usefulChargePowerW;
        assertClose(closure.spoolTimeS(), expectedSpoolS, "spool-time closure");
    }

    private static void assertClose(double expected, double actual, String label) {
        double tolerance = Math.max(1.0e-12, Math.abs(expected) * 1.0e-12);
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(label + " failed: expected=" + expected + ", actual=" + actual);
        }
    }

    private static JsonValue requireObject(JsonValue object, String field) {
        JsonValue value = requireField(object, field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
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

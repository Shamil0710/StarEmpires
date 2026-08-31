package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.ProductionManifestDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.SupportEnduranceRequirement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads the Stage-22.2 component/hull/facility manifest and support-endurance contract. */
public final class Stage22CoreProductionManifestLoader {
    /** Exact supported schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in faction-neutral resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-core-production-manifest-v1.json";

    private static final List<String> FORBIDDEN_COMMON_TOKENS = List.of(
            "core.empire",
            "faction.imperial_directorate",
            "core.industrial_union",
            "faction.industrial_combine");

    private Stage22CoreProductionManifestLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the built-in common manifest document.
     *
     * @return immutable parsed catalog
     */
    public static Stage22CoreProductionManifestCatalog loadDefault() {
        ClassLoader classLoader = Stage22CoreProductionManifestLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22.2 production manifest: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22.2 production manifest", exception);
        }
    }

    /**
     * Parses one bounded manifest document. External authority references are validated by
     * {@link Stage22CoreContentSeamValidator} so the parser remains a deterministic data boundary.
     *
     * @param json complete JSON document
     * @return immutable parsed catalog
     */
    public static Stage22CoreProductionManifestCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-22.2 production manifest JSON must not be blank");
        }
        rejectFactionBias(json);
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-22.2 production manifest JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-22.2 production manifest root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-22.2 production manifest schema: " + schemaVersion);
        }

        List<ProductionManifestDefinition> manifests = new ArrayList<>();
        for (JsonValue node = requireArray(root, "productionManifests").child; node != null; node = node.next) {
            manifests.add(new ProductionManifestDefinition(
                    requireText(node, "id"),
                    requireText(node, "fitId"),
                    requireText(node, "hullId"),
                    stringArray(node, "componentIds"),
                    requireText(node, "shipyardId"),
                    stringArray(node, "requiredFacilityIds"),
                    enumValue(ContentMaturity.class, node, "contentMaturity"),
                    requireText(node, "semanticIntent")));
        }
        if (manifests.isEmpty()) {
            throw new IllegalArgumentException("Stage-22.2 requires an end-to-end physical exemplar manifest");
        }

        List<SupportEnduranceRequirement> endurance = new ArrayList<>();
        for (JsonValue node = requireArray(root, "supportEnduranceRequirements").child;
                node != null;
                node = node.next) {
            endurance.add(new SupportEnduranceRequirement(
                    requireText(node, "roleId"),
                    requireText(node, "referenceId"),
                    requireDouble(node, "minimumMissionEnduranceS"),
                    requireText(node, "semanticReason")));
        }
        if (endurance.isEmpty()) {
            throw new IllegalArgumentException("Stage-22.2 requires explicit support-endurance requirements");
        }

        return new Stage22CoreProductionManifestCatalog(
                schemaVersion,
                requireText(root, "catalogVersion"),
                manifests,
                endurance);
    }

    private static void rejectFactionBias(String json) {
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        for (String token : FORBIDDEN_COMMON_TOKENS) {
            if (lower.contains(token)) {
                throw new IllegalArgumentException(
                        "Common Stage-22.2 production manifest contains faction-specific package token: " + token);
            }
        }
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
        double raw = value.asDouble();
        int result = value.asInt();
        if (!Double.isFinite(raw) || raw != result) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return result;
    }

    private static double requireDouble(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return result;
    }

    private static String requireText(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asString().strip();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonValue node, String field) {
        String value = requireText(node, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field + ": " + value, exception);
        }
    }

    private static List<String> stringArray(JsonValue node, String field) {
        List<String> result = new ArrayList<>();
        for (JsonValue value = requireArray(node, field).child; value != null; value = value.next) {
            if (!value.isString() || value.asString().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(value.asString().strip());
        }
        return List.copyOf(result);
    }
}

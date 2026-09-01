package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22EmpirePackageCatalog.MissionTemplateDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.RecurringNpcDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.ShipFamilyDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.StationVariantDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.StoryChainDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.VisualRuleDefinition;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict data loader for the M22.3 Empire package catalog. */
public final class Stage22EmpirePackageLoader {
    /** Exact supported schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in Empire package resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-empire-package-v1.json";
    private static final int MAX_ITEMS = 128;

    private Stage22EmpirePackageLoader() {
        throw new AssertionError("utility class");
    }

    /** @return validated built-in Empire package */
    public static Stage22EmpirePackageCatalog loadDefault() {
        try (InputStream stream = Stage22EmpirePackageLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire package resource: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Empire package resource", exception);
        }
    }

    /**
     * Parses and validates one Empire package document.
     *
     * @param json JSON document
     * @return immutable validated package
     */
    public static Stage22EmpirePackageCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Empire package JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Empire package JSON", exception);
        }
        object(root, "root");
        int schemaVersion = integer(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Empire package schema: " + schemaVersion);
        }
        return new Stage22EmpirePackageCatalog(
                schemaVersion,
                string(root, "catalogVersion"),
                string(root, "packageKey"),
                string(root, "stableFactionId"),
                ships(root),
                stations(root),
                npcs(root),
                missions(root),
                chains(root),
                visualRules(root));
    }

    private static List<ShipFamilyDefinition> ships(JsonValue root) {
        List<ShipFamilyDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "shipFamilies").child; node != null; node = node.next) {
            object(node, "ship family");
            result.add(new ShipFamilyDefinition(
                    string(node, "familyId"),
                    string(node, "roleId"),
                    string(node, "primaryFitId"),
                    string(node, "refitFitId"),
                    string(node, "productionManifestId"),
                    string(node, "visualBindingId"),
                    string(node, "lineageId"),
                    string(node, "fleetUse"),
                    string(node, "counterplay")));
        }
        return result;
    }

    private static List<StationVariantDefinition> stations(JsonValue root) {
        List<StationVariantDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "stations").child; node != null; node = node.next) {
            object(node, "station");
            result.add(new StationVariantDefinition(
                    string(node, "id"),
                    string(node, "stage18ArchetypeId"),
                    stringList(node, "requiredFacilityIds"),
                    string(node, "visualBrief")));
        }
        return result;
    }

    private static List<RecurringNpcDefinition> npcs(JsonValue root) {
        List<RecurringNpcDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "recurringNpcs").child; node != null; node = node.next) {
            object(node, "recurring NPC");
            result.add(new RecurringNpcDefinition(
                    string(node, "id"),
                    string(node, "nameKey"),
                    enumValue(node, "role", NpcRole.class),
                    string(node, "characterOverlayId"),
                    string(node, "publicVoice"),
                    string(node, "privateVoice")));
        }
        return result;
    }

    private static List<MissionTemplateDefinition> missions(JsonValue root) {
        List<MissionTemplateDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "missions").child; node != null; node = node.next) {
            object(node, "mission");
            result.add(new MissionTemplateDefinition(
                    string(node, "id"),
                    string(node, "issuerNpcId"),
                    enumValue(node, "authority", ObjectiveAuthority.class),
                    enumValue(node, "objectiveKind", ObjectiveKind.class),
                    string(node, "semanticIntent")));
        }
        return result;
    }

    private static List<StoryChainDefinition> chains(JsonValue root) {
        List<StoryChainDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "storyChains").child; node != null; node = node.next) {
            object(node, "story chain");
            result.add(new StoryChainDefinition(
                    string(node, "id"), stringList(node, "missionTemplateIds"), string(node, "semanticIntent")));
        }
        return result;
    }

    private static List<VisualRuleDefinition> visualRules(JsonValue root) {
        List<VisualRuleDefinition> result = new ArrayList<>();
        for (JsonValue node = array(root, "visualRules").child; node != null; node = node.next) {
            object(node, "visual rule");
            result.add(new VisualRuleDefinition(
                    string(node, "id"), string(node, "medium"),
                    string(node, "authorityDocument"), string(node, "requirement")));
        }
        return result;
    }

    private static JsonValue array(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        if (value.size > MAX_ITEMS) {
            throw new IllegalArgumentException(name + " exceeds maximum size " + MAX_ITEMS);
        }
        return value;
    }

    private static List<String> stringList(JsonValue parent, String name) {
        List<String> result = new ArrayList<>();
        for (JsonValue node = array(parent, name).child; node != null; node = node.next) {
            if (!node.isString() || node.asString().isBlank()) {
                throw new IllegalArgumentException(name + " must contain non-blank strings");
            }
            result.add(node.asString());
        }
        return result;
    }

    private static String string(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank text");
        }
        return value.asString();
    }

    private static int integer(JsonValue node, String name) {
        JsonValue value = node.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.asInt();
    }

    private static <E extends Enum<E>> E enumValue(JsonValue node, String name, Class<E> type) {
        String value = string(node, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + name + ": " + value, exception);
        }
    }

    private static void object(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }
}

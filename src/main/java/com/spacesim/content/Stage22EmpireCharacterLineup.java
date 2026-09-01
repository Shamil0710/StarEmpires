package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Machine-readable M22.3 Empire character overlay lineup.
 *
 * <p>The lineup is presentation metadata only. It composes the canonical shared Character Master
 * Prompt with the Empire visual bible and role-specific practical requirements; it owns no NPC,
 * dialogue, rank, equipment inventory or gameplay state.</p>
 */
public final class Stage22EmpireCharacterLineup {
    /** Built-in M22.3 character overlay resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-empire-character-lineup-v1.json";
    private static final Set<String> REQUIRED_ROLE_KEYS = Set.of(
            "industrial_worker_technician",
            "fleet_enlisted_specialist",
            "line_officer",
            "senior_officer",
            "civil_administrator",
            "noble_high_official",
            "field_damaged_tired_variant");

    private Stage22EmpireCharacterLineup() {
        throw new AssertionError("utility class");
    }

    /** Loads and strictly validates the built-in Empire character overlay lineup. */
    public static Catalog loadDefault() {
        try (InputStream stream = Stage22EmpireCharacterLineup.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire character lineup: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Empire character lineup", exception);
        }
    }

    /** Parses one complete immutable character overlay document. */
    public static Catalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Empire character lineup JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Empire character lineup JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Empire character lineup root must be an object");
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported Empire character lineup schema: " + schemaVersion);
        }
        List<OverlayDefinition> overlays = new ArrayList<>();
        for (JsonValue node = requiredArray(root, "overlays").child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("Empire character overlay must be an object");
            }
            overlays.add(new OverlayDefinition(
                    requiredText(node, "id"),
                    requiredText(node, "roleKey"),
                    requiredText(node, "roleBrief"),
                    requiredText(node, "statusReadability"),
                    requiredText(node, "practicalGear"),
                    requiredText(node, "condition")));
        }
        return new Catalog(
                schemaVersion,
                requiredText(root, "catalogVersion"),
                requiredText(root, "masterPromptRef"),
                requiredText(root, "factionVisualRef"),
                overlays);
    }

    /** Immutable validated lineup and deterministic semantic fingerprint. */
    public record Catalog(
            int schemaVersion,
            String catalogVersion,
            String masterPromptRef,
            String factionVisualRef,
            List<OverlayDefinition> overlays,
            String fingerprint) {
        /** Validates and canonicalizes one character lineup. */
        public Catalog(
                int schemaVersion,
                String catalogVersion,
                String masterPromptRef,
                String factionVisualRef,
                List<OverlayDefinition> overlays) {
            this(
                    schemaVersion,
                    requireText(catalogVersion, "catalogVersion"),
                    requireDocument(masterPromptRef, "masterPromptRef"),
                    requireDocument(factionVisualRef, "factionVisualRef"),
                    canonicalOverlays(overlays),
                    "");
        }

        public Catalog {
            if (schemaVersion != 1) {
                throw new IllegalArgumentException("Empire character lineup schema must be 1");
            }
            if (!"docs/characters/character_master_prompt.md".equals(masterPromptRef)) {
                throw new IllegalArgumentException("Empire lineup must compose the canonical Character Master Prompt");
            }
            if (!"docs/factions/empire_visual_bible.md".equals(factionVisualRef)) {
                throw new IllegalArgumentException("Empire lineup must compose the canonical Empire visual bible");
            }
            TreeSet<String> roleKeys = new TreeSet<>();
            LinkedHashMap<String, OverlayDefinition> ids = new LinkedHashMap<>();
            for (OverlayDefinition overlay : overlays) {
                if (ids.putIfAbsent(overlay.id(), overlay) != null) {
                    throw new IllegalArgumentException("Duplicate Empire character overlay: " + overlay.id());
                }
                roleKeys.add(overlay.roleKey());
            }
            if (!roleKeys.containsAll(REQUIRED_ROLE_KEYS)) {
                TreeSet<String> missing = new TreeSet<>(REQUIRED_ROLE_KEYS);
                missing.removeAll(roleKeys);
                throw new IllegalArgumentException("Empire character lineup misses required roles: " + missing);
            }
            fingerprint = computeFingerprint(
                    schemaVersion, catalogVersion, masterPromptRef, factionVisualRef, overlays);
        }

        /** Finds one overlay by stable ID. */
        public OverlayDefinition findOverlay(String id) {
            for (OverlayDefinition overlay : overlays) {
                if (overlay.id().equals(id)) {
                    return overlay;
                }
            }
            return null;
        }
    }

    /** One role/faction overlay composed on top of the shared Character Master style authority. */
    public record OverlayDefinition(
            String id,
            String roleKey,
            String roleBrief,
            String statusReadability,
            String practicalGear,
            String condition) {
        public OverlayDefinition {
            id = requireContentId(id, "overlay id");
            if (!id.startsWith("character_overlay.empire.")) {
                throw new IllegalArgumentException("Character overlay escapes Empire namespace: " + id);
            }
            roleKey = requireLocalKey(roleKey, "roleKey");
            roleBrief = requireText(roleBrief, "roleBrief");
            statusReadability = requireText(statusReadability, "statusReadability");
            practicalGear = requireText(practicalGear, "practicalGear");
            condition = requireText(condition, "condition");
        }
    }

    private static List<OverlayDefinition> canonicalOverlays(List<OverlayDefinition> source) {
        List<OverlayDefinition> copy = new ArrayList<>(Objects.requireNonNull(source, "overlays"));
        copy.replaceAll(value -> Objects.requireNonNull(value, "overlay"));
        copy.sort(Comparator.comparing(OverlayDefinition::id));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Empire character lineup must not be empty");
        }
        return List.copyOf(copy);
    }

    private static String computeFingerprint(
            int schemaVersion,
            String catalogVersion,
            String masterPromptRef,
            String factionVisualRef,
            List<OverlayDefinition> overlays) {
        StringBuilder canonical = new StringBuilder(4096);
        canonical.append("schema|").append(schemaVersion).append('|').append(catalogVersion).append('|')
                .append(masterPromptRef).append('|').append(factionVisualRef).append('\n');
        for (OverlayDefinition overlay : overlays) {
            canonical.append(overlay.id()).append('|').append(overlay.roleKey()).append('|')
                    .append(overlay.roleBrief()).append('|').append(overlay.statusReadability()).append('|')
                    .append(overlay.practicalGear()).append('|').append(overlay.condition()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static JsonValue requiredArray(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static int requiredInt(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double raw = value.asDouble();
        int result = value.asInt();
        if (raw != result) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return result;
    }

    private static String requiredText(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return requireText(value.asString(), field);
    }

    private static String requireDocument(String value, String field) {
        String checked = requireText(value, field);
        if (!checked.startsWith("docs/") || checked.contains("..")) {
            throw new IllegalArgumentException(field + " must reference a repository docs path");
        }
        return checked;
    }

    private static String requireContentId(String value, String field) {
        String checked = requireText(value, field);
        if (!checked.matches("[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+")) {
            throw new IllegalArgumentException(field + " must use dotted content ID syntax: " + checked);
        }
        return checked;
    }

    private static String requireLocalKey(String value, String field) {
        String checked = requireText(value, field);
        if (!checked.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException(field + " must use lower-case local-key syntax: " + checked);
        }
        return checked;
    }

    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }
}

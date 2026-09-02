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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Machine-readable M22.4 Industrial Union character-overlay lineup.
 *
 * <p>The lineup is presentation metadata only. It composes the canonical Character Master Prompt
 * with the Industrial Union visual bible and function-specific practical requirements. It owns no
 * NPC lifecycle, equipment inventory, rank, dialogue or gameplay state.</p>
 */
public final class Stage22IndustrialUnionCharacterLineup {
    /** Built-in M22.4 character-overlay resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-industrial-union-character-lineup-v1.json";
    private static final Set<String> REQUIRED_ROLE_KEYS = Set.of(
            "assembly_worker",
            "maintenance_specialist",
            "production_engineer",
            "ship_fleet_officer",
            "logistics_coordinator",
            "plant_director_technical_administrator",
            "field_repair_variant");

    private Stage22IndustrialUnionCharacterLineup() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and strictly validates the built-in Industrial Union character lineup.
     *
     * @return immutable validated character catalog
     */
    public static Catalog loadDefault() {
        try (InputStream stream = Stage22IndustrialUnionCharacterLineup.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Industrial Union character lineup: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Industrial Union character lineup", exception);
        }
    }

    /**
     * Parses one complete character-overlay document.
     *
     * @param json complete lineup JSON document
     * @return immutable validated character catalog
     */
    public static Catalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Industrial Union character lineup JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Industrial Union character lineup JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Industrial Union character lineup root must be an object");
        }
        int schema = requiredInt(root, "schemaVersion");
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported Industrial Union character lineup schema: " + schema);
        }
        List<OverlayDefinition> overlays = new ArrayList<>();
        for (JsonValue node = requiredArray(root, "overlays").child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("Industrial Union character overlay must be an object");
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
                schema,
                requiredText(root, "catalogVersion"),
                requiredText(root, "masterPromptRef"),
                requiredText(root, "factionVisualRef"),
                overlays);
    }

    /**
     * Immutable validated character lineup.
     *
     * @param schemaVersion supported schema version
     * @param catalogVersion stable catalog version
     * @param masterPromptRef canonical Character Master Prompt document
     * @param factionVisualRef canonical Industrial Union visual bible
     * @param overlays authored function overlays
     * @param fingerprint deterministic semantic fingerprint
     */
    public record Catalog(
            int schemaVersion,
            String catalogVersion,
            String masterPromptRef,
            String factionVisualRef,
            List<OverlayDefinition> overlays,
            String fingerprint) {
        /**
         * Canonicalizes one lineup and computes its fingerprint.
         *
         * @param schemaVersion supported schema version
         * @param catalogVersion stable catalog version
         * @param masterPromptRef canonical Character Master Prompt document
         * @param factionVisualRef canonical Industrial Union visual bible
         * @param overlays authored function overlays
         */
        public Catalog(
                int schemaVersion,
                String catalogVersion,
                String masterPromptRef,
                String factionVisualRef,
                List<OverlayDefinition> overlays) {
            this(schemaVersion,
                    requireText(catalogVersion, "catalogVersion"),
                    requireDocument(masterPromptRef, "masterPromptRef"),
                    requireDocument(factionVisualRef, "factionVisualRef"),
                    canonicalOverlays(overlays),
                    "");
        }

        /**
         * Validates canonical lineup state and computes the semantic fingerprint.
         *
         * @param schemaVersion supported schema version
         * @param catalogVersion stable catalog version
         * @param masterPromptRef canonical Character Master Prompt document
         * @param factionVisualRef canonical Industrial Union visual bible
         * @param overlays authored function overlays
         * @param fingerprint derived semantic fingerprint, recomputed during validation
         */
        public Catalog {
            if (schemaVersion != 1) {
                throw new IllegalArgumentException("Industrial Union character lineup schema must be 1");
            }
            if (!"docs/characters/character_master_prompt.md".equals(masterPromptRef)) {
                throw new IllegalArgumentException("Industrial Union lineup must compose the canonical Character Master Prompt");
            }
            if (!"docs/factions/industrial_union_visual_bible.md".equals(factionVisualRef)) {
                throw new IllegalArgumentException("Industrial Union lineup must compose the canonical faction visual bible");
            }
            TreeSet<String> roleKeys = new TreeSet<>();
            TreeSet<String> ids = new TreeSet<>();
            for (OverlayDefinition overlay : overlays) {
                if (!ids.add(overlay.id())) {
                    throw new IllegalArgumentException("Duplicate Industrial Union character overlay: " + overlay.id());
                }
                roleKeys.add(overlay.roleKey());
            }
            if (!roleKeys.containsAll(REQUIRED_ROLE_KEYS)) {
                TreeSet<String> missing = new TreeSet<>(REQUIRED_ROLE_KEYS);
                missing.removeAll(roleKeys);
                throw new IllegalArgumentException("Industrial Union character lineup misses required functions: " + missing);
            }
            fingerprint = computeFingerprint(schemaVersion, catalogVersion, masterPromptRef, factionVisualRef, overlays);
        }

        /**
         * Finds one overlay by stable ID.
         *
         * @param id stable overlay ID
         * @return matching overlay or {@code null}
         */
        public OverlayDefinition findOverlay(String id) {
            for (OverlayDefinition overlay : overlays) {
                if (overlay.id().equals(id)) {
                    return overlay;
                }
            }
            return null;
        }
    }

    /**
     * One function-specific Industrial Union overlay on the shared Character Master style.
     *
     * @param id stable overlay content ID
     * @param roleKey required functional role key
     * @param roleBrief profession/responsibility brief
     * @param statusReadability qualification/responsibility readability rule
     * @param practicalGear functional equipment rule
     * @param condition wear/fatigue condition rule
     */
    public record OverlayDefinition(
            String id,
            String roleKey,
            String roleBrief,
            String statusReadability,
            String practicalGear,
            String condition) {
        /**
         * Validates and normalizes one character overlay.
         *
         * @param id stable overlay content ID
         * @param roleKey required functional role key
         * @param roleBrief profession/responsibility brief
         * @param statusReadability qualification/responsibility readability rule
         * @param practicalGear functional equipment rule
         * @param condition wear/fatigue condition rule
         */
        public OverlayDefinition {
            id = requireContentId(id, "overlay id");
            if (!id.startsWith("character_overlay.industrial_union.")) {
                throw new IllegalArgumentException("Character overlay escapes Industrial Union namespace: " + id);
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
            throw new IllegalArgumentException("Industrial Union character lineup must not be empty");
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
        canonical.append(schemaVersion).append('|').append(catalogVersion).append('|')
                .append(masterPromptRef).append('|').append(factionVisualRef).append('\n');
        for (OverlayDefinition overlay : overlays) {
            canonical.append(overlay).append('\n');
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
        return value.asInt();
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
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }
}

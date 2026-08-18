package com.spacesim.content.weapon;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Headless versioned loader and semantic validator for Stage-17.5E physical ammunition content. */
public final class WeaponAmmunitionCatalogLoader {
    /** Current ammunition JSON schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Current explicit ammunition migration version. */
    public static final int CURRENT_MIGRATION_VERSION = 1;
    /** Production classpath ammunition resource. */
    public static final String DEFAULT_RESOURCE = "data/content/weapon-ammunition-v1.json";

    private static final int MAX_DEFINITIONS = 512;
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> FORBIDDEN_ABSTRACTION_FIELDS = Set.of(
            "weaponAccuracy",
            "missileHitChance",
            "pdChance",
            "PDChance",
            "hardRangeM",
            "maxRangeM",
            "weaponRange");

    private WeaponAmmunitionCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the built-in ammunition catalog against the built-in engineering material catalog.
     *
     * @return validated immutable ammunition catalog
     */
    public static WeaponAmmunitionCatalog loadDefault() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ClassLoader classLoader = WeaponAmmunitionCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing ammunition catalog: " + DEFAULT_RESOURCE);
            }
            WeaponAmmunitionCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    engineering);
            if (catalog.getKineticAmmunition().isEmpty() && catalog.getGuidedAmmunition().isEmpty()) {
                throw new IllegalStateException("Production ammunition catalog is empty");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read ammunition catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Parses one ammunition document against the authoritative engineering material catalog.
     *
     * @param json ammunition JSON document
     * @param engineering engineering catalog supplying authoritative material IDs
     * @return validated immutable ammunition catalog
     */
    public static WeaponAmmunitionCatalog parse(String json, ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(json, "json");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Ammunition catalog JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed ammunition catalog JSON", exception);
        }
        requireObject(root, "root");
        rejectForbiddenAbstractions(root, "root");
        int schemaVersion = requireInt(root, "schemaVersion");
        int migrationVersion = requireInt(root, "migrationVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported ammunition schemaVersion: " + schemaVersion);
        }
        if (migrationVersion != CURRENT_MIGRATION_VERSION) {
            throw new IllegalArgumentException("Unsupported ammunition migrationVersion: " + migrationVersion);
        }

        List<KineticAmmunitionDefinition> kinetic = parseKinetic(root, checkedEngineering);
        List<GuidedAmmunitionDefinition> guided = parseGuided(root, checkedEngineering);
        ensureUniqueAcrossFamilies(kinetic, guided);
        return new WeaponAmmunitionCatalog(schemaVersion, migrationVersion, kinetic, guided);
    }

    private static List<KineticAmmunitionDefinition> parseKinetic(
            JsonValue root,
            ShipEngineeringCatalog engineering) {
        JsonValue array = requireBoundedArray(root, "kineticAmmunition", MAX_DEFINITIONS);
        List<KineticAmmunitionDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "kinetic ammunition");
            rejectForbiddenAbstractions(node, "kinetic ammunition");
            String id = requireContentId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate kinetic ammunition ID: " + id);
            }
            String materialId = requireContentId(node, "materialId");
            requireKnownMaterial(engineering, id, materialId);
            values.add(new KineticAmmunitionDefinition(
                    id,
                    materialId,
                    requireEnum(node, "shape", ProjectileShape.class),
                    requirePositiveFinite(node, "lengthM"),
                    requirePositiveFinite(node, "diameterM"),
                    requirePositiveFinite(node, "massKg")));
        }
        return values;
    }

    private static List<GuidedAmmunitionDefinition> parseGuided(
            JsonValue root,
            ShipEngineeringCatalog engineering) {
        JsonValue array = requireBoundedArray(root, "guidedAmmunition", MAX_DEFINITIONS);
        List<GuidedAmmunitionDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "guided ammunition");
            rejectForbiddenAbstractions(node, "guided ammunition");
            String id = requireContentId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate guided ammunition ID: " + id);
            }
            String materialId = requireContentId(node, "materialId");
            requireKnownMaterial(engineering, id, materialId);
            values.add(new GuidedAmmunitionDefinition(
                    id,
                    materialId,
                    requireEnum(node, "shape", ProjectileShape.class),
                    optionalEnum(node, "engagementRole", GuidedEngagementRole.class, GuidedEngagementRole.STRIKE),
                    requirePositiveFinite(node, "lengthM"),
                    requirePositiveFinite(node, "diameterM"),
                    optionalContentId(node, "impactPayloadId"),
                    requireContentId(node, "seekerId"),
                    requirePositiveFinite(node, "dryMassKg"),
                    requireNonNegativeFinite(node, "propellantMassKg"),
                    requirePositiveFinite(node, "thrustN"),
                    requirePositiveFinite(node, "exhaustVelocityMps"),
                    requirePositiveFinite(node, "burnTimeSeconds"),
                    requireNonNegativeFinite(node, "seekerAngularSigmaRad"),
                    requireNonNegativeFinite(node, "terminalReserveMps")));
        }
        return values;
    }

    private static void ensureUniqueAcrossFamilies(
            List<KineticAmmunitionDefinition> kinetic,
            List<GuidedAmmunitionDefinition> guided) {
        Set<String> ids = new HashSet<>();
        for (KineticAmmunitionDefinition value : kinetic) {
            ids.add(value.id());
        }
        for (GuidedAmmunitionDefinition value : guided) {
            if (!ids.add(value.id())) {
                throw new IllegalArgumentException("Duplicate ammunition ID across families: " + value.id());
            }
        }
    }

    private static void requireKnownMaterial(
            ShipEngineeringCatalog engineering,
            String ammunitionId,
            String materialId) {
        if (engineering.findMaterial(materialId) == null) {
            throw new IllegalArgumentException(
                    "Ammunition references unknown engineering material: " + ammunitionId + " -> " + materialId);
        }
    }

    private static void rejectForbiddenAbstractions(JsonValue value, String context) {
        if (value == null) {
            return;
        }
        if (value.isObject()) {
            for (JsonValue child = value.child; child != null; child = child.next) {
                if (FORBIDDEN_ABSTRACTION_FIELDS.contains(child.name)) {
                    throw new IllegalArgumentException(
                            "Forbidden Stage-17.5E probability/range abstraction in " + context + ": " + child.name);
                }
                rejectForbiddenAbstractions(child, context);
            }
        } else if (value.isArray()) {
            for (JsonValue child = value.child; child != null; child = child.next) {
                rejectForbiddenAbstractions(child, context);
            }
        }
    }

    private static JsonValue requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value;
    }

    private static JsonValue requireBoundedArray(JsonValue parent, String name, int maxSize) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        if (value.size > maxSize) {
            throw new IllegalArgumentException(name + " exceeds maximum size " + maxSize);
        }
        return value;
    }

    private static int requireInt(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        double number = value.asDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return (int) number;
    }

    private static String requireContentId(JsonValue parent, String name) {
        String value = requireNonBlank(parent, name);
        if (!CONTENT_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a stable content ID: " + value);
        }
        return value;
    }

    private static String optionalContentId(JsonValue parent, String name) {
        JsonValue node = parent.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        return requireContentId(parent, name);
    }

    private static String requireNonBlank(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        String result = value.asString();
        if (result.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return result;
    }

    private static double requirePositiveFinite(JsonValue parent, String name) {
        double value = requireFinite(parent, name);
        if (value <= 0d) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static double requireNonNegativeFinite(JsonValue parent, String name) {
        double value = requireFinite(parent, name);
        if (value < 0d) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static double requireFinite(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return result;
    }

    private static <E extends Enum<E>> E requireEnum(JsonValue parent, String name, Class<E> type) {
        String value = requireNonBlank(parent, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + name + ": " + value, exception);
        }
    }

    private static <E extends Enum<E>> E optionalEnum(
            JsonValue parent,
            String name,
            Class<E> type,
            E defaultValue) {
        JsonValue node = parent.get(name);
        if (node == null || node.isNull()) {
            return Objects.requireNonNull(defaultValue, "defaultValue");
        }
        return requireEnum(parent, name, type);
    }
}

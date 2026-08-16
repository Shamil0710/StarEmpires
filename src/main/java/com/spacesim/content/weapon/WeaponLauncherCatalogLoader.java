package com.spacesim.content.weapon;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.WeaponDefinition.Family;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Headless loader validating Stage-17.5E launcher profiles against engineering modules. */
public final class WeaponLauncherCatalogLoader {
    /** Current launcher profile schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Current explicit launcher profile migration version. */
    public static final int CURRENT_MIGRATION_VERSION = 1;
    /** Production launcher profile resource. */
    public static final String DEFAULT_RESOURCE = "data/content/weapon-launchers-v1.json";

    private static final int MAX_PROFILES = 512;
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    private WeaponLauncherCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads built-in launcher profiles and validates all module/interface references.
     *
     * @return validated immutable launcher profile catalog
     */
    public static WeaponLauncherCatalog loadDefault() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ClassLoader classLoader = WeaponLauncherCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing launcher catalog: " + DEFAULT_RESOURCE);
            }
            WeaponLauncherCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), engineering);
            if (catalog.getProfiles().isEmpty()) {
                throw new IllegalStateException("Production launcher profile catalog is empty");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read launcher catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Parses launcher profiles against authoritative engineering content.
     *
     * @param json launcher profile JSON
     * @param engineering authoritative module/material engineering catalog
     * @return validated immutable launcher profile catalog
     */
    public static WeaponLauncherCatalog parse(String json, ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(json, "json");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Launcher catalog JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed launcher catalog JSON", exception);
        }
        requireObject(root, "root");
        int schemaVersion = requireInt(root, "schemaVersion");
        int migrationVersion = requireInt(root, "migrationVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported launcher schemaVersion: " + schemaVersion);
        }
        if (migrationVersion != CURRENT_MIGRATION_VERSION) {
            throw new IllegalArgumentException("Unsupported launcher migrationVersion: " + migrationVersion);
        }
        JsonValue array = requireArray(root, "profiles", MAX_PROFILES);
        List<LauncherProfile> profiles = new ArrayList<>();
        Set<String> moduleIds = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "launcher profile");
            String moduleId = requireContentId(node, "moduleId");
            if (!moduleIds.add(moduleId)) {
                throw new IllegalArgumentException("Duplicate launcher profile moduleId: " + moduleId);
            }
            ModuleDefinition module = checkedEngineering.findModule(moduleId);
            if (module == null) {
                throw new IllegalArgumentException("Launcher profile references unknown module: " + moduleId);
            }
            if (module.family() != ModuleFamily.WEAPON_AMMUNITION) {
                throw new IllegalArgumentException("Launcher profile requires WEAPON_AMMUNITION module: " + moduleId);
            }
            String interfaceId = requireLocalId(node, "ammunitionInterfaceId");
            InterfaceDefinition ammunitionInterface = module.interfaces().stream()
                    .filter(value -> value.kind() == InterfaceKind.AMMUNITION && value.id().equals(interfaceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Launcher profile references missing AMMUNITION interface: " + moduleId + " -> " + interfaceId));
            double amountPerShot = requirePositiveFinite(node, "ammunitionAmountPerShot");
            if (amountPerShot > ammunitionInterface.capacity()) {
                throw new IllegalArgumentException("One launcher shot exceeds interface capacity: " + moduleId);
            }
            profiles.add(new LauncherProfile(
                    moduleId,
                    requireEnum(node, "family", Family.class),
                    interfaceId,
                    amountPerShot,
                    requirePositiveFinite(node, "cycleTimeSeconds"),
                    requirePositiveInt(node, "supportChannelCount"),
                    requireNonNegativeFinite(node, "pointingJitterRad"),
                    requirePositiveFinite(node, "maxProjectileMassKg"),
                    requirePositiveFinite(node, "maxProjectileLengthM"),
                    requirePositiveFinite(node, "maxProjectileDiameterM")));
        }
        return new WeaponLauncherCatalog(schemaVersion, migrationVersion, profiles);
    }

    private static JsonValue requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value;
    }

    private static JsonValue requireArray(JsonValue parent, String name, int maxSize) {
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

    private static int requirePositiveInt(JsonValue parent, String name) {
        int value = requireInt(parent, name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireContentId(JsonValue parent, String name) {
        String value = requireString(parent, name);
        if (!CONTENT_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a stable content ID: " + value);
        }
        return value;
    }

    private static String requireLocalId(JsonValue parent, String name) {
        String value = requireString(parent, name);
        if (!LOCAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a local ID: " + value);
        }
        return value;
    }

    private static String requireString(JsonValue parent, String name) {
        JsonValue value = parent.get(name);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return value.asString();
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
        String value = requireString(parent, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + name + ": " + value, exception);
        }
    }
}

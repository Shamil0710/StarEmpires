package com.spacesim.content.ship;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ArmorModuleProtectionCatalog.ArmorProfile;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict bounded loader for fitted armor-module protection mappings. */
public final class ArmorModuleProtectionCatalogLoader {
    /** Current supported schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_PROFILES = 256;

    private ArmorModuleProtectionCatalogLoader() {
        throw new AssertionError("utility namespace");
    }

    /**
     * Parses armor mappings and validates all module/stack references against production engineering content.
     *
     * @param json JSON document
     * @param engineering ordinary production engineering catalog
     * @return immutable armor protection catalog
     */
    public static ArmorModuleProtectionCatalog parse(String json, ShipEngineeringCatalog engineering) {
        String source = Objects.requireNonNull(json, "json");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        JsonValue root;
        try {
            root = new JsonReader().parse(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed armor-module protection JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Armor-module protection root must be an object");
        }
        int schemaVersion = root.getInt("schemaVersion", -1);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported armor-module protection schema: " + schemaVersion);
        }
        JsonValue array = root.get("profiles");
        if (array == null || !array.isArray() || array.size < 1 || array.size > MAX_PROFILES) {
            throw new IllegalArgumentException("profiles must be a non-empty bounded array");
        }
        List<ArmorProfile> profiles = new ArrayList<>();
        Set<String> moduleIds = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("armor profile row must be an object");
            }
            String moduleId = requireNonBlank(node.getString("moduleId", null), "moduleId");
            String stackId = requireNonBlank(
                    node.getString("externalProtectionStackId", null), "externalProtectionStackId");
            if (!moduleIds.add(moduleId)) {
                throw new IllegalArgumentException("duplicate armor profile module: " + moduleId);
            }
            ModuleDefinition module = checkedEngineering.findModule(moduleId);
            if (module == null || module.family() != ModuleFamily.ARMOR_PROTECTION) {
                throw new IllegalArgumentException("armor profile requires ARMOR_PROTECTION module: " + moduleId);
            }
            if (checkedEngineering.findProtectionStack(stackId) == null) {
                throw new IllegalArgumentException("armor profile references unknown protection stack: " + stackId);
            }
            profiles.add(new ArmorProfile(moduleId, stackId));
        }
        return new ArmorModuleProtectionCatalog(schemaVersion, profiles);
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }
}

package com.spacesim.combat.acceptance;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.Doctrine;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.FleetDefinition;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.InformationQuality;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.MatchupDefinition;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.ShipEntry;
import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.VariationDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict bounded loader for the data-driven Stage-17.5I fleet/scenario manifest. */
public final class Stage175ICombatTestManifestLoader {
    /** Supported manifest schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_FLEETS = 32;
    private static final int MAX_SHIPS_PER_FLEET = 64;
    private static final int MAX_MATCHUPS = 128;
    private static final int MAX_VARIATIONS = 64;
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9._-]*$");
    private static final String REQUIRED_STATUS = "PRODUCTION_VALID_CONTENT_PROVISIONAL";

    private Stage175ICombatTestManifestLoader() {
        throw new AssertionError("utility namespace");
    }

    /**
     * Parses and cross-validates one acceptance manifest against ordinary production engineering fits.
     *
     * @param json UTF-8 JSON text
     * @param engineering production engineering catalog referenced by fleet rows
     * @return immutable validated manifest
     */
    public static Stage175ICombatTestManifest parse(String json, ShipEngineeringCatalog engineering) {
        String source = Objects.requireNonNull(json, "json");
        ShipEngineeringCatalog catalog = Objects.requireNonNull(engineering, "engineering");
        JsonValue root;
        try {
            root = new JsonReader().parse(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-17.5I manifest JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Stage-17.5I manifest root must be an object");
        }
        int schemaVersion = root.getInt("schemaVersion", -1);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-17.5I manifest schema: " + schemaVersion);
        }
        String contentStatus = requireNonBlank(root.getString("contentStatus", null), "contentStatus");
        if (!REQUIRED_STATUS.equals(contentStatus)) {
            throw new IllegalArgumentException("Stage-17.5I content must remain explicitly provisional");
        }
        boolean stage22ReviewRequired = root.getBoolean("stage22ReviewRequired", false);
        if (!stage22ReviewRequired) {
            throw new IllegalArgumentException("Stage-17.5I manifest must require Stage-22 review");
        }

        List<FleetDefinition> fleets = parseFleets(root.get("fleets"), catalog);
        Set<String> fleetIds = new HashSet<>();
        for (FleetDefinition fleet : fleets) {
            if (!fleetIds.add(fleet.id())) {
                throw new IllegalArgumentException("Duplicate fleet ID: " + fleet.id());
            }
        }
        List<MatchupDefinition> matchups = parseMatchups(root.get("matchups"), fleetIds);
        List<VariationDefinition> variations = parseVariations(root.get("variations"));
        return new Stage175ICombatTestManifest(
                schemaVersion, contentStatus, stage22ReviewRequired, fleets, matchups, variations);
    }

    private static List<FleetDefinition> parseFleets(JsonValue array, ShipEngineeringCatalog engineering) {
        requireArray(array, "fleets", MAX_FLEETS);
        List<FleetDefinition> result = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "fleet");
            String id = requireId(node.getString("id", null), "fleet.id");
            Doctrine doctrine = parseEnum(node.getString("doctrine", null), Doctrine.class, "fleet.doctrine");
            JsonValue ships = node.get("ships");
            requireArray(ships, id + ".ships", MAX_SHIPS_PER_FLEET);
            if (ships.size == 0) {
                throw new IllegalArgumentException("Fleet must contain ships: " + id);
            }
            List<ShipEntry> entries = new ArrayList<>();
            Set<String> fitIds = new HashSet<>();
            for (JsonValue ship = ships.child; ship != null; ship = ship.next) {
                requireObject(ship, "ship entry");
                String fitId = requireId(ship.getString("fitId", null), id + ".fitId");
                if (engineering.findDemonstratorFit(fitId) == null) {
                    throw new IllegalArgumentException("Fleet references unknown production fit: " + fitId);
                }
                if (!fitIds.add(fitId)) {
                    throw new IllegalArgumentException("Fleet repeats fit row instead of increasing count: " + fitId);
                }
                int count = ship.getInt("count", -1);
                entries.add(new ShipEntry(fitId, count));
            }
            result.add(new FleetDefinition(id, doctrine, entries));
        }
        return List.copyOf(result);
    }

    private static List<MatchupDefinition> parseMatchups(JsonValue array, Set<String> fleetIds) {
        requireArray(array, "matchups", MAX_MATCHUPS);
        List<MatchupDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "matchup");
            String id = requireId(node.getString("id", null), "matchup.id");
            String fleetAId = requireId(node.getString("fleetAId", null), id + ".fleetAId");
            String fleetBId = requireId(node.getString("fleetBId", null), id + ".fleetBId");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate matchup ID: " + id);
            }
            if (!fleetIds.contains(fleetAId) || !fleetIds.contains(fleetBId)) {
                throw new IllegalArgumentException("Matchup references unknown fleet: " + id);
            }
            result.add(new MatchupDefinition(id, fleetAId, fleetBId));
        }
        return List.copyOf(result);
    }

    private static List<VariationDefinition> parseVariations(JsonValue array) {
        requireArray(array, "variations", MAX_VARIATIONS);
        List<VariationDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            requireObject(node, "variation");
            String id = requireId(node.getString("id", null), "variation.id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate variation ID: " + id);
            }
            result.add(new VariationDefinition(
                    id,
                    node.getDouble("initialSeparationM", Double.NaN),
                    node.getDouble("formationSpacingM", Double.NaN),
                    node.getDouble("ammunitionLoadFraction", Double.NaN),
                    node.getDouble("preDamageIntegrity", Double.NaN),
                    node.getDouble("initialThermalLoadFraction", Double.NaN),
                    parseEnum(node.getString("informationQuality", null), InformationQuality.class,
                            id + ".informationQuality")));
        }
        return List.copyOf(result);
    }

    private static void requireArray(JsonValue value, String label, int maxSize) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        if (value.size < 1 || value.size > maxSize) {
            throw new IllegalArgumentException(label + " size outside accepted bounds: " + value.size);
        }
    }

    private static void requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }

    private static String requireId(String value, String label) {
        String checked = requireNonBlank(value, label);
        if (!ID.matcher(checked).matches()) {
            throw new IllegalArgumentException(label + " is not a stable lowercase ID: " + checked);
        }
        return checked;
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String label) {
        String checked = requireNonBlank(value, label);
        try {
            return Enum.valueOf(type, checked);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + checked, exception);
        }
    }
}

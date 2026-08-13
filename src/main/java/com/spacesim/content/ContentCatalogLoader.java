package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.constants.Constants;
import com.spacesim.model.ItemCategory;
import com.spacesim.model.ShipType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Загружает и валидирует versioned JSON-каталог игрового контента.
 *
 * <p>Loader не использует OpenGL/desktop backend и безопасен для headless simulation. Items и
 * recipes являются минимальным ядром parse-контракта; factions/shipArchetypes/stationArchetypes
 * допускаются пустыми для узких тестовых/инструментальных каталогов. Встроенный production catalog
 * обязан содержать все группы и проверяется строже в {@link #loadDefault()}.</p>
 */
public final class ContentCatalogLoader {
    /** Текущая поддерживаемая версия JSON schema каталога. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Classpath-путь встроенного каталога игры. */
    public static final String DEFAULT_RESOURCE = "data/content/catalog-v1.json";

    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");

    private ContentCatalogLoader() {
        throw new AssertionError("ContentCatalogLoader не создаёт экземпляров");
    }

    /**
     * Загружает полный встроенный production-каталог из classpath.
     *
     * @return полностью провалидированный каталог
     * @throws IllegalStateException если resource отсутствует/не читается или неполон
     * @throws IllegalArgumentException если содержимое нарушает schema/invariants
     */
    public static ContentCatalog loadDefault() {
        ClassLoader classLoader = ContentCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Не найден встроенный content catalog: " + DEFAULT_RESOURCE);
            }
            ContentCatalog catalog = parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.getFactions().isEmpty()
                    || catalog.getShipArchetypes().isEmpty()
                    || catalog.getStationArchetypes().isEmpty()) {
                throw new IllegalStateException("Встроенный content catalog не содержит все production-группы");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать content catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Разбирает каталог из JSON-строки.
     *
     * @param json непустой JSON document
     * @return полностью провалидированный каталог
     * @throws NullPointerException если JSON не задан
     * @throws IllegalArgumentException если JSON синтаксически или семантически некорректен
     */
    public static ContentCatalog parse(String json) {
        Objects.requireNonNull(json, "JSON каталога не задан");
        if (json.isBlank()) {
            throw new IllegalArgumentException("JSON каталога не должен быть пустым");
        }

        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Некорректный JSON content catalog", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Корень content catalog должен быть JSON object");
        }

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия content schema: " + schemaVersion);
        }

        Map<String, ContentCatalog.ItemDefinition> itemsById = new LinkedHashMap<>();
        Map<Integer, ContentCatalog.ItemDefinition> itemsByRuntimeId = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "items").child; node != null; node = node.next) {
            ContentCatalog.ItemDefinition item = parseItem(requireObject(node, "item"));
            validateNewId(item.id(), "item");
            if (item.runtimeId() < 0 || item.runtimeId() >= Constants.MAX_ITEMS) {
                throw new IllegalArgumentException("Item runtimeId вне slot-capacity: " + item.runtimeId());
            }
            putUnique(itemsById, item.id(), item, "item content ID");
            putUnique(itemsByRuntimeId, item.runtimeId(), item, "item runtimeId");
        }
        if (itemsById.isEmpty()) {
            throw new IllegalArgumentException("Content catalog должен содержать хотя бы один item");
        }
        validateDenseRuntimeIds(itemsByRuntimeId, itemsById.size(), "Item");
        List<ContentCatalog.ItemDefinition> orderedItems = new ArrayList<>(itemsById.values());
        orderedItems.sort(Comparator.comparingInt(ContentCatalog.ItemDefinition::runtimeId));

        Map<String, ContentCatalog.RecipeDefinition> recipesById = new LinkedHashMap<>();
        List<ContentCatalog.RecipeDefinition> recipes = new ArrayList<>();
        for (JsonValue node = requireArray(root, "recipes").child; node != null; node = node.next) {
            ContentCatalog.RecipeDefinition recipe = parseRecipe(requireObject(node, "recipe"), itemsById);
            validateNewId(recipe.id(), "recipe");
            putUnique(recipesById, recipe.id(), recipe, "recipe content ID");
            recipes.add(recipe);
        }

        Map<String, ContentCatalog.FactionDefinition> factionsById = new LinkedHashMap<>();
        Map<Integer, ContentCatalog.FactionDefinition> factionsByRuntimeId = new LinkedHashMap<>();
        List<ContentCatalog.FactionDefinition> factions = new ArrayList<>();
        JsonValue factionNodes = optionalArray(root, "factions");
        if (factionNodes != null) {
            for (JsonValue node = factionNodes.child; node != null; node = node.next) {
                ContentCatalog.FactionDefinition faction = parseFaction(requireObject(node, "faction"));
                validateNewId(faction.id(), "faction");
                if (faction.runtimeId() < 0 || faction.runtimeId() >= Constants.MAX_FACTIONS) {
                    throw new IllegalArgumentException("Faction runtimeId вне текущей schema: " + faction.runtimeId());
                }
                putUnique(factionsById, faction.id(), faction, "faction content ID");
                putUnique(factionsByRuntimeId, faction.runtimeId(), faction, "faction runtimeId");
                factions.add(faction);
            }
            validateDenseRuntimeIds(factionsByRuntimeId, factions.size(), "Faction");
            factions.sort(Comparator.comparingInt(ContentCatalog.FactionDefinition::runtimeId));
        }

        Map<String, ContentCatalog.ShipArchetypeDefinition> shipsById = new LinkedHashMap<>();
        List<ContentCatalog.ShipArchetypeDefinition> ships = new ArrayList<>();
        JsonValue shipNodes = optionalArray(root, "shipArchetypes");
        if (shipNodes != null) {
            for (JsonValue node = shipNodes.child; node != null; node = node.next) {
                ContentCatalog.ShipArchetypeDefinition ship = parseShip(requireObject(node, "ship archetype"));
                validateNewId(ship.id(), "ship archetype");
                validateShip(ship);
                putUnique(shipsById, ship.id(), ship, "ship archetype ID");
                ships.add(ship);
            }
        }

        Map<String, ContentCatalog.StationArchetypeDefinition> stationsById = new LinkedHashMap<>();
        List<ContentCatalog.StationArchetypeDefinition> stations = new ArrayList<>();
        JsonValue stationNodes = optionalArray(root, "stationArchetypes");
        if (stationNodes != null) {
            for (JsonValue node = stationNodes.child; node != null; node = node.next) {
                ContentCatalog.StationArchetypeDefinition station = parseStation(
                        requireObject(node, "station archetype"),
                        itemsById,
                        recipesById,
                        factionsById);
                validateNewId(station.id(), "station archetype");
                putUnique(stationsById, station.id(), station, "station archetype ID");
                stations.add(station);
            }
        }

        return new ContentCatalog(
                schemaVersion,
                orderedItems,
                recipes,
                factions,
                ships,
                stations,
                itemsById,
                itemsByRuntimeId,
                recipesById,
                factionsById,
                factionsByRuntimeId,
                shipsById,
                stationsById);
    }

    private static ContentCatalog.ItemDefinition parseItem(JsonValue node) {
        String id = requireString(node, "id");
        int runtimeId = requireInt(node, "runtimeId");
        String codeName = requireNonBlank(node, "codeName");
        String displayName = requireNonBlank(node, "displayName");
        ItemCategory category;
        try {
            category = ItemCategory.valueOf(requireString(node, "category"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Неизвестная item category для " + id, exception);
        }
        float basePrice = requireFloat(node, "basePrice");
        if (!Float.isFinite(basePrice) || basePrice <= 0f) {
            throw new IllegalArgumentException("Item basePrice должна быть конечной и положительной: " + id);
        }
        boolean mineable = requireBoolean(node, "mineable");
        if (mineable && category != ItemCategory.MATERIAL) {
            throw new IllegalArgumentException("Добываемый item должен иметь category MATERIAL: " + id);
        }
        return new ContentCatalog.ItemDefinition(
                id, runtimeId, codeName, displayName, category, basePrice, mineable);
    }

    private static ContentCatalog.RecipeDefinition parseRecipe(
            JsonValue node,
            Map<String, ContentCatalog.ItemDefinition> itemsById) {
        String id = requireString(node, "id");
        String displayName = requireNonBlank(node, "displayName");
        float durationSeconds = requireFloat(node, "durationSeconds");
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) {
            throw new IllegalArgumentException("Recipe duration должна быть конечной и положительной: " + id);
        }
        Map<String, Integer> inputs = parseAmounts(node.get("inputs"), id, "inputs", itemsById);
        Map<String, Integer> outputs = parseAmounts(node.get("outputs"), id, "outputs", itemsById);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Recipe должен иметь хотя бы один output: " + id);
        }
        return new ContentCatalog.RecipeDefinition(id, displayName, durationSeconds, inputs, outputs);
    }

    private static ContentCatalog.FactionDefinition parseFaction(JsonValue node) {
        return new ContentCatalog.FactionDefinition(
                requireString(node, "id"),
                requireInt(node, "runtimeId"),
                requireNonBlank(node, "displayName"));
    }

    private static ContentCatalog.ShipArchetypeDefinition parseShip(JsonValue node) {
        String id = requireString(node, "id");
        ShipType role;
        try {
            role = ShipType.valueOf(requireString(node, "role"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Неизвестная ship role для " + id, exception);
        }
        return new ContentCatalog.ShipArchetypeDefinition(
                id,
                requireNonBlank(node, "displayName"),
                role,
                requireInt(node, "cargoCapacity"),
                requireFloat(node, "movementSpeed"),
                requireDouble(node, "startingCredits"),
                requireFloat(node, "extractionPerSecond"),
                requireFloat(node, "extractionRange"),
                requireFloat(node, "dockingRange"),
                requireFloat(node, "hull"),
                requireFloat(node, "shields"),
                requireFloat(node, "damagePerSecond"),
                requireFloat(node, "weaponRange"));
    }

    private static void validateShip(ContentCatalog.ShipArchetypeDefinition ship) {
        if (ship.cargoCapacity() < 0
                || !isNonNegativeFinite(ship.movementSpeed())
                || !Double.isFinite(ship.startingCredits()) || ship.startingCredits() < 0d
                || !isNonNegativeFinite(ship.extractionPerSecond())
                || !isNonNegativeFinite(ship.extractionRange())
                || !isNonNegativeFinite(ship.dockingRange())
                || !isNonNegativeFinite(ship.hull())
                || !isNonNegativeFinite(ship.shields())
                || !isNonNegativeFinite(ship.damagePerSecond())
                || !isNonNegativeFinite(ship.weaponRange())) {
            throw new IllegalArgumentException("Некорректные числовые параметры ship archetype: " + ship.id());
        }
        if (ship.role().isCarrier() && (ship.cargoCapacity() <= 0 || ship.movementSpeed() <= 0f)) {
            throw new IllegalArgumentException("Carrier archetype требует cargoCapacity и movementSpeed: " + ship.id());
        }
        if (ship.role().isMining()
                && (ship.cargoCapacity() <= 0 || ship.movementSpeed() <= 0f
                || ship.extractionPerSecond() <= 0f || ship.extractionRange() <= 0f
                || ship.dockingRange() <= 0f)) {
            throw new IllegalArgumentException("Mining archetype требует mining-параметры: " + ship.id());
        }
        if (ship.role().isCombat()
                && (ship.hull() <= 0f || ship.damagePerSecond() <= 0f || ship.weaponRange() <= 0f)) {
            throw new IllegalArgumentException("Combat archetype требует combat-параметры: " + ship.id());
        }
    }

    private static ContentCatalog.StationArchetypeDefinition parseStation(
            JsonValue node,
            Map<String, ContentCatalog.ItemDefinition> itemsById,
            Map<String, ContentCatalog.RecipeDefinition> recipesById,
            Map<String, ContentCatalog.FactionDefinition> factionsById) {
        String id = requireString(node, "id");
        int capacity = requireInt(node, "inventoryCapacity");
        double credits = requireDouble(node, "startingCredits");
        String factionId = requireString(node, "factionId");
        String recipeId = optionalString(node, "recipeId");
        if (capacity <= 0 || !Double.isFinite(credits) || credits < 0d) {
            throw new IllegalArgumentException("Некорректные station capacity/credits: " + id);
        }
        if (!factionsById.containsKey(factionId)) {
            throw new IllegalArgumentException("Station " + id + " ссылается на неизвестную faction: " + factionId);
        }
        if (recipeId != null && !recipesById.containsKey(recipeId)) {
            throw new IllegalArgumentException("Station " + id + " ссылается на неизвестный recipe: " + recipeId);
        }

        JsonValue marketNodes = requireArray(node, "markets");
        List<ContentCatalog.MarketDefinition> markets = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        long totalInitialStock = 0L;
        for (JsonValue marketNode = marketNodes.child; marketNode != null; marketNode = marketNode.next) {
            JsonValue value = requireObject(marketNode, "station market");
            String itemId = requireString(value, "itemId");
            if (!itemsById.containsKey(itemId)) {
                throw new IllegalArgumentException("Station " + id + " ссылается на неизвестный item: " + itemId);
            }
            if (!seenItems.add(itemId)) {
                throw new IllegalArgumentException("Station " + id + " повторяет market item: " + itemId);
            }
            int initialStock = requireInt(value, "initialStock");
            int targetStock = requireInt(value, "targetStock");
            float consumption = requireFloat(value, "consumptionPerSecond");
            if (initialStock < 0 || targetStock <= 0 || !isNonNegativeFinite(consumption)) {
                throw new IllegalArgumentException("Некорректный market rule станции " + id + " для " + itemId);
            }
            totalInitialStock += initialStock;
            markets.add(new ContentCatalog.MarketDefinition(itemId, initialStock, targetStock, consumption));
        }
        if (markets.isEmpty()) {
            throw new IllegalArgumentException("Station archetype должен иметь хотя бы один market: " + id);
        }
        if (totalInitialStock > capacity) {
            throw new IllegalArgumentException("Стартовый stock station archetype превышает capacity: " + id);
        }
        ContentCatalog.ConstructionDefinition construction = parseConstruction(
                node.get("construction"), id, itemsById);
        return new ContentCatalog.StationArchetypeDefinition(
                id,
                requireNonBlank(node, "displayName"),
                capacity,
                credits,
                factionId,
                recipeId,
                markets,
                construction);
    }

    private static ContentCatalog.ConstructionDefinition parseConstruction(
            JsonValue node,
            String stationId,
            Map<String, ContentCatalog.ItemDefinition> itemsById) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonValue value = requireObject(node, "station construction");
        double fundingCredits = requireDouble(value, "fundingCredits");
        float buildSeconds = requireFloat(value, "buildSeconds");
        if (!Double.isFinite(fundingCredits) || fundingCredits <= 0d
                || !Float.isFinite(buildSeconds) || buildSeconds <= 0f) {
            throw new IllegalArgumentException("Некорректные construction funding/buildSeconds: " + stationId);
        }
        Map<String, Integer> materials = parseAmounts(
                value.get("materials"), stationId, "construction.materials", itemsById);
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("Station construction требует materials: " + stationId);
        }
        return new ContentCatalog.ConstructionDefinition(fundingCredits, buildSeconds, materials);
    }

    private static Map<String, Integer> parseAmounts(
            JsonValue object,
            String recipeId,
            String field,
            Map<String, ContentCatalog.ItemDefinition> itemsById) {
        if (object == null || !object.isObject()) {
            throw new IllegalArgumentException("Recipe " + recipeId + " должен содержать object " + field);
        }
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (JsonValue amountNode = object.child; amountNode != null; amountNode = amountNode.next) {
            String itemId = amountNode.name;
            if (!itemsById.containsKey(itemId)) {
                throw new IllegalArgumentException(
                        "Recipe " + recipeId + " ссылается на неизвестный item: " + itemId);
            }
            int amount;
            try {
                amount = amountNode.asInt();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Recipe amount должен быть целым: " + recipeId + " -> " + itemId, exception);
            }
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "Recipe amount должен быть положительным: " + recipeId + " -> " + itemId);
            }
            amounts.put(itemId, amount);
        }
        return amounts;
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value, String label) {
        if (map.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Повторяющийся " + label + ": " + key);
        }
    }

    private static void validateDenseRuntimeIds(Map<Integer, ?> values, int count, String label) {
        for (int runtimeId = 0; runtimeId < count; runtimeId++) {
            if (!values.containsKey(runtimeId)) {
                throw new IllegalArgumentException(label + " runtime IDs должны быть плотными; пропущен " + runtimeId);
            }
        }
    }

    private static void validateNewId(String id, String type) {
        if (id == null || !CONTENT_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный " + type + " content ID: " + id
                            + "; ожидается namespace.name в нижнем регистре");
        }
    }

    private static boolean isNonNegativeFinite(float value) {
        return Float.isFinite(value) && value >= 0f;
    }

    private static JsonValue requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Каждый " + label + " должен быть JSON object");
        }
        return value;
    }

    private static JsonValue requireArray(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть JSON array");
        }
        return value;
    }

    private static JsonValue optionalArray(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть JSON array");
        }
        return value;
    }

    private static String optionalString(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть string или null");
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    private static String requireNonBlank(JsonValue parent, String field) {
        String value = requireString(parent, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " не должно быть пустым");
        }
        return value;
    }

    private static String requireString(JsonValue parent, String field) {
        try {
            String value = parent.getString(field);
            if (value == null) {
                throw new IllegalArgumentException("Поле " + field + " не задано");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть string", exception);
        }
    }

    private static int requireInt(JsonValue parent, String field) {
        try {
            return parent.getInt(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть int", exception);
        }
    }

    private static float requireFloat(JsonValue parent, String field) {
        try {
            return parent.getFloat(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть number", exception);
        }
    }

    private static double requireDouble(JsonValue parent, String field) {
        try {
            return parent.getDouble(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть number", exception);
        }
    }

    private static boolean requireBoolean(JsonValue parent, String field) {
        try {
            return parent.getBoolean(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть boolean", exception);
        }
    }
}

package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.constants.Constants;
import com.spacesim.model.ItemCategory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Загружает и валидирует versioned JSON-каталог игрового контента.
 *
 * <p>Loader не использует OpenGL или desktop backend и потому безопасен для headless simulation.
 * Любая неоднозначность persistent ID, runtime ID либо ссылки рецепта приводит к fail-fast ошибке
 * до создания мира.</p>
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
     * Загружает встроенный production-каталог из classpath.
     *
     * @return полностью провалидированный каталог
     * @throws IllegalStateException если resource отсутствует или не читается
     * @throws IllegalArgumentException если содержимое нарушает schema/invariants
     */
    public static ContentCatalog loadDefault() {
        ClassLoader classLoader = ContentCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Не найден встроенный content catalog: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать content catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Разбирает каталог из JSON-строки. Метод предназначен также для тестов и будущих внешних packs.
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

        JsonValue itemNodes = requireArray(root, "items");
        JsonValue recipeNodes = requireArray(root, "recipes");

        Map<String, ContentCatalog.ItemDefinition> itemsById = new LinkedHashMap<>();
        Map<Integer, ContentCatalog.ItemDefinition> itemsByRuntimeId = new LinkedHashMap<>();
        for (JsonValue node = itemNodes.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("Каждый item должен быть JSON object");
            }
            ContentCatalog.ItemDefinition item = parseItem(node);
            validateNewId(item.id(), "item");
            if (item.runtimeId() < 0 || item.runtimeId() >= Constants.MAX_ITEMS) {
                throw new IllegalArgumentException("Item runtimeId вне текущей dense schema: " + item.runtimeId());
            }
            if (itemsById.putIfAbsent(item.id(), item) != null) {
                throw new IllegalArgumentException("Повторяющийся item content ID: " + item.id());
            }
            if (itemsByRuntimeId.putIfAbsent(item.runtimeId(), item) != null) {
                throw new IllegalArgumentException("Повторяющийся item runtimeId: " + item.runtimeId());
            }
        }
        if (itemsById.isEmpty()) {
            throw new IllegalArgumentException("Content catalog должен содержать хотя бы один item");
        }
        validateDenseRuntimeIds(itemsByRuntimeId, itemsById.size());

        List<ContentCatalog.ItemDefinition> orderedItems = new ArrayList<>(itemsById.values());
        orderedItems.sort(Comparator.comparingInt(ContentCatalog.ItemDefinition::runtimeId));

        List<ContentCatalog.RecipeDefinition> recipes = new ArrayList<>();
        Map<String, ContentCatalog.RecipeDefinition> recipesById = new LinkedHashMap<>();
        for (JsonValue node = recipeNodes.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("Каждый recipe должен быть JSON object");
            }
            ContentCatalog.RecipeDefinition recipe = parseRecipe(node, itemsById);
            validateNewId(recipe.id(), "recipe");
            if (recipesById.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalArgumentException("Повторяющийся recipe content ID: " + recipe.id());
            }
            recipes.add(recipe);
        }

        return new ContentCatalog(
                schemaVersion,
                orderedItems,
                recipes,
                itemsById,
                itemsByRuntimeId,
                recipesById);
    }

    private static ContentCatalog.ItemDefinition parseItem(JsonValue node) {
        String id = requireString(node, "id");
        int runtimeId = requireInt(node, "runtimeId");
        String codeName = requireNonBlank(node, "codeName");
        String displayName = requireNonBlank(node, "displayName");
        String categoryName = requireString(node, "category");
        ItemCategory category;
        try {
            category = ItemCategory.valueOf(categoryName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Неизвестная item category для " + id + ": " + categoryName, exception);
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
                id,
                runtimeId,
                codeName,
                displayName,
                category,
                basePrice,
                mineable);
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

    private static void validateDenseRuntimeIds(
            Map<Integer, ContentCatalog.ItemDefinition> itemsByRuntimeId,
            int itemCount) {
        for (int runtimeId = 0; runtimeId < itemCount; runtimeId++) {
            if (!itemsByRuntimeId.containsKey(runtimeId)) {
                throw new IllegalArgumentException("Item runtime IDs должны быть плотными; пропущен " + runtimeId);
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

    private static JsonValue requireArray(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Поле " + field + " должно быть JSON array");
        }
        return value;
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

    private static boolean requireBoolean(JsonValue parent, String field) {
        try {
            return parent.getBoolean(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Поле " + field + " должно быть boolean", exception);
        }
    }
}

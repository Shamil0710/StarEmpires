package com.spacesim.content;

import com.spacesim.model.ItemCategory;
import com.spacesim.model.Recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Неизменяемый runtime-каталог versioned игрового контента.
 *
 * <p>Persistent контент адресуется стабильными строковыми ID, а горячий simulation path продолжает
 * работать с плотными целочисленными runtime ID. Каталог является единственной границей между
 * этими двумя представлениями для вынесенных в данные сущностей.</p>
 */
public final class ContentCatalog {
    private final int schemaVersion;
    private final List<ItemDefinition> items;
    private final List<RecipeDefinition> recipes;
    private final Map<String, ItemDefinition> itemsById;
    private final Map<Integer, ItemDefinition> itemsByRuntimeId;
    private final Map<String, RecipeDefinition> recipesById;

    ContentCatalog(
            int schemaVersion,
            List<ItemDefinition> items,
            List<RecipeDefinition> recipes,
            Map<String, ItemDefinition> itemsById,
            Map<Integer, ItemDefinition> itemsByRuntimeId,
            Map<String, RecipeDefinition> recipesById) {
        this.schemaVersion = schemaVersion;
        this.items = List.copyOf(items);
        this.recipes = List.copyOf(recipes);
        this.itemsById = immutableOrderedCopy(itemsById);
        this.itemsByRuntimeId = immutableOrderedCopy(itemsByRuntimeId);
        this.recipesById = immutableOrderedCopy(recipesById);
    }

    /** @return версия schema загруженного каталога */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return неизменяемый список товаров в deterministic runtime-порядке */
    public List<ItemDefinition> getItems() {
        return items;
    }

    /** @return неизменяемый список рецептов в порядке файла */
    public List<RecipeDefinition> getRecipes() {
        return recipes;
    }

    /**
     * Находит товар по persistent content ID.
     *
     * @param contentId стабильный строковый ID
     * @return описание товара либо {@code null}
     */
    public ItemDefinition findItem(String contentId) {
        return itemsById.get(contentId);
    }

    /**
     * Находит товар по плотному runtime ID.
     *
     * @param runtimeId целочисленный индекс simulation-массивов
     * @return описание товара либо {@code null}
     */
    public ItemDefinition findItem(int runtimeId) {
        return itemsByRuntimeId.get(runtimeId);
    }

    /**
     * Находит рецепт по persistent content ID.
     *
     * @param contentId стабильный строковый ID рецепта
     * @return описание рецепта либо {@code null}
     */
    public RecipeDefinition findRecipe(String contentId) {
        return recipesById.get(contentId);
    }

    /**
     * Создаёт runtime-рецепт для существующего dense simulation API.
     *
     * @param contentId persistent ID рецепта
     * @return новый независимый runtime-рецепт
     * @throws IllegalArgumentException если рецепт не найден
     */
    public Recipe createRuntimeRecipe(String contentId) {
        RecipeDefinition definition = recipesById.get(contentId);
        if (definition == null) {
            throw new IllegalArgumentException("Неизвестный recipe content ID: " + contentId);
        }
        Recipe recipe = new Recipe(definition.displayName(), definition.durationSeconds());
        applyAmounts(recipe, definition.inputs(), true);
        applyAmounts(recipe, definition.outputs(), false);
        return recipe;
    }

    private void applyAmounts(Recipe recipe, Map<String, Integer> amounts, boolean input) {
        for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
            ItemDefinition item = itemsById.get(entry.getKey());
            if (item == null) {
                throw new IllegalStateException("Каталог содержит неразрешённый item ID: " + entry.getKey());
            }
            if (input) {
                recipe.input(item.runtimeId(), entry.getValue());
            } else {
                recipe.output(item.runtimeId(), entry.getValue());
            }
        }
    }

    private static <K, V> Map<K, V> immutableOrderedCopy(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * Описание одного товара из data catalog.
     *
     * @param id стабильный persistent content ID
     * @param runtimeId плотный runtime ID для simulation-массивов
     * @param codeName английское техническое имя
     * @param displayName отображаемое имя
     * @param category категория грузового хранения
     * @param basePrice базовая цена в кредитах
     * @param mineable может ли ресурс добываться напрямую
     */
    public record ItemDefinition(
            String id,
            int runtimeId,
            String codeName,
            String displayName,
            ItemCategory category,
            float basePrice,
            boolean mineable) {
        /**
         * Проверяет обязательные ссылочные значения определения товара.
         *
         * @param id стабильный persistent content ID
         * @param runtimeId плотный runtime ID для simulation-массивов
         * @param codeName английское техническое имя
         * @param displayName отображаемое имя
         * @param category категория грузового хранения
         * @param basePrice базовая цена в кредитах
         * @param mineable может ли ресурс добываться напрямую
         */
        public ItemDefinition {
            Objects.requireNonNull(id, "Item ID не задан");
            Objects.requireNonNull(codeName, "Item codeName не задан");
            Objects.requireNonNull(displayName, "Item displayName не задан");
            Objects.requireNonNull(category, "Item category не задана");
        }
    }

    /**
     * Описание одного производственного рецепта.
     *
     * @param id стабильный persistent content ID
     * @param displayName отображаемое имя рецепта
     * @param durationSeconds длительность цикла в игровых секундах
     * @param inputs входные количества по item content ID
     * @param outputs выходные количества по item content ID
     */
    public record RecipeDefinition(
            String id,
            String displayName,
            float durationSeconds,
            Map<String, Integer> inputs,
            Map<String, Integer> outputs) {
        /**
         * Делает входные карты неизменяемыми и сохраняет deterministic порядок.
         *
         * @param id стабильный persistent content ID
         * @param displayName отображаемое имя рецепта
         * @param durationSeconds длительность цикла в игровых секундах
         * @param inputs входные количества по item content ID
         * @param outputs выходные количества по item content ID
         */
        public RecipeDefinition {
            Objects.requireNonNull(id, "Recipe ID не задан");
            Objects.requireNonNull(displayName, "Recipe displayName не задан");
            inputs = immutableOrderedCopy(Objects.requireNonNull(inputs, "Recipe inputs не заданы"));
            outputs = immutableOrderedCopy(Objects.requireNonNull(outputs, "Recipe outputs не заданы"));
        }
    }
}

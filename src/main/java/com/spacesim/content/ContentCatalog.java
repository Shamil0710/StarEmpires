package com.spacesim.content;

import com.spacesim.model.ItemCategory;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Неизменяемый runtime-каталог versioned игрового контента.
 *
 * <p>Persistent контент адресуется стабильными строковыми ID, а горячий simulation path продолжает
 * работать с плотными целочисленными runtime ID. Каталог хранит товары, рецепты, фракционные
 * метаданные, корабельные и станционные archetypes. Малые Java enum вроде {@link ShipType}
 * обозначают только функциональные runtime-роли систем, а не конкретные модели контента.</p>
 *
 * <p>Semantic SHA-256 fingerprint не зависит от JSON whitespace или порядка независимых записей,
 * но меняется при изменении любого игрового параметра каталога. После завершения Stage 4 он входит
 * в save contract и предотвращает продолжение сохранения на несовместимых данных.</p>
 */
public final class ContentCatalog {
    private final int schemaVersion;
    private final List<ItemDefinition> items;
    private final List<RecipeDefinition> recipes;
    private final List<FactionDefinition> factions;
    private final List<ShipArchetypeDefinition> shipArchetypes;
    private final List<StationArchetypeDefinition> stationArchetypes;
    private final Map<String, ItemDefinition> itemsById;
    private final Map<Integer, ItemDefinition> itemsByRuntimeId;
    private final Map<String, RecipeDefinition> recipesById;
    private final Map<String, FactionDefinition> factionsById;
    private final Map<Integer, FactionDefinition> factionsByRuntimeId;
    private final Map<String, ShipArchetypeDefinition> shipsById;
    private final Map<String, StationArchetypeDefinition> stationsById;
    private final String fingerprint;

    ContentCatalog(
            int schemaVersion,
            List<ItemDefinition> items,
            List<RecipeDefinition> recipes,
            List<FactionDefinition> factions,
            List<ShipArchetypeDefinition> shipArchetypes,
            List<StationArchetypeDefinition> stationArchetypes,
            Map<String, ItemDefinition> itemsById,
            Map<Integer, ItemDefinition> itemsByRuntimeId,
            Map<String, RecipeDefinition> recipesById,
            Map<String, FactionDefinition> factionsById,
            Map<Integer, FactionDefinition> factionsByRuntimeId,
            Map<String, ShipArchetypeDefinition> shipsById,
            Map<String, StationArchetypeDefinition> stationsById) {
        this.schemaVersion = schemaVersion;
        this.items = List.copyOf(items);
        this.recipes = List.copyOf(recipes);
        this.factions = List.copyOf(factions);
        this.shipArchetypes = List.copyOf(shipArchetypes);
        this.stationArchetypes = List.copyOf(stationArchetypes);
        this.itemsById = immutableOrderedCopy(itemsById);
        this.itemsByRuntimeId = immutableOrderedCopy(itemsByRuntimeId);
        this.recipesById = immutableOrderedCopy(recipesById);
        this.factionsById = immutableOrderedCopy(factionsById);
        this.factionsByRuntimeId = immutableOrderedCopy(factionsByRuntimeId);
        this.shipsById = immutableOrderedCopy(shipsById);
        this.stationsById = immutableOrderedCopy(stationsById);
        this.fingerprint = computeFingerprint();
    }

    /** @return версия schema загруженного каталога */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return товары в deterministic runtime-порядке */
    public List<ItemDefinition> getItems() {
        return items;
    }

    /** @return рецепты каталога */
    public List<RecipeDefinition> getRecipes() {
        return recipes;
    }

    /** @return фракции в runtime-порядке */
    public List<FactionDefinition> getFactions() {
        return factions;
    }

    /** @return корабельные archetypes */
    public List<ShipArchetypeDefinition> getShipArchetypes() {
        return shipArchetypes;
    }

    /** @return станционные archetypes */
    public List<StationArchetypeDefinition> getStationArchetypes() {
        return stationArchetypes;
    }

    /** @return lowercase SHA-256 semantic fingerprint длиной 64 символа */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Ищет товар по persistent content ID.
     *
     * @param contentId persistent item ID
     * @return описание товара либо {@code null}
     */
    public ItemDefinition findItem(String contentId) {
        return itemsById.get(contentId);
    }

    /**
     * Ищет товар по плотному runtime ID.
     *
     * @param runtimeId плотный item ID
     * @return описание товара либо {@code null}
     */
    public ItemDefinition findItem(int runtimeId) {
        return itemsByRuntimeId.get(runtimeId);
    }

    /**
     * Ищет рецепт по persistent content ID.
     *
     * @param contentId persistent recipe ID
     * @return описание рецепта либо {@code null}
     */
    public RecipeDefinition findRecipe(String contentId) {
        return recipesById.get(contentId);
    }

    /**
     * Ищет фракцию по persistent content ID.
     *
     * @param contentId persistent faction ID
     * @return описание фракции либо {@code null}
     */
    public FactionDefinition findFaction(String contentId) {
        return factionsById.get(contentId);
    }

    /**
     * Ищет фракцию по runtime ID.
     *
     * @param runtimeId runtime faction ID
     * @return описание фракции либо {@code null}
     */
    public FactionDefinition findFaction(int runtimeId) {
        return factionsByRuntimeId.get(runtimeId);
    }

    /**
     * Ищет корабельный archetype по persistent content ID.
     *
     * @param contentId persistent ship archetype ID
     * @return описание archetype либо {@code null}
     */
    public ShipArchetypeDefinition findShipArchetype(String contentId) {
        return shipsById.get(contentId);
    }

    /**
     * Ищет станционный archetype по persistent content ID.
     *
     * @param contentId persistent station archetype ID
     * @return описание archetype либо {@code null}
     */
    public StationArchetypeDefinition findStationArchetype(String contentId) {
        return stationsById.get(contentId);
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

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(4096);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (ItemDefinition item : items) {
            canonical.append("item|")
                    .append(item.runtimeId()).append('|').append(item.id()).append('|')
                    .append(item.codeName()).append('|').append(item.displayName()).append('|')
                    .append(item.category().name()).append('|')
                    .append(Float.floatToIntBits(item.basePrice())).append('|')
                    .append(item.mineable()).append('\n');
        }
        List<RecipeDefinition> orderedRecipes = new ArrayList<>(recipes);
        orderedRecipes.sort(Comparator.comparing(RecipeDefinition::id));
        for (RecipeDefinition recipe : orderedRecipes) {
            canonical.append("recipe|").append(recipe.id()).append('|')
                    .append(recipe.displayName()).append('|')
                    .append(Float.floatToIntBits(recipe.durationSeconds())).append('|');
            appendAmounts(canonical, recipe.inputs());
            canonical.append('|');
            appendAmounts(canonical, recipe.outputs());
            canonical.append('\n');
        }
        List<FactionDefinition> orderedFactions = new ArrayList<>(factions);
        orderedFactions.sort(Comparator.comparingInt(FactionDefinition::runtimeId));
        for (FactionDefinition faction : orderedFactions) {
            canonical.append("faction|").append(faction.runtimeId()).append('|')
                    .append(faction.id()).append('|').append(faction.displayName()).append('\n');
        }
        List<ShipArchetypeDefinition> orderedShips = new ArrayList<>(shipArchetypes);
        orderedShips.sort(Comparator.comparing(ShipArchetypeDefinition::id));
        for (ShipArchetypeDefinition ship : orderedShips) {
            canonical.append("ship|").append(ship.id()).append('|').append(ship.displayName()).append('|')
                    .append(ship.role().name()).append('|').append(ship.cargoCapacity()).append('|')
                    .append(Float.floatToIntBits(ship.movementSpeed())).append('|')
                    .append(Double.doubleToLongBits(ship.startingCredits())).append('|')
                    .append(Float.floatToIntBits(ship.extractionPerSecond())).append('|')
                    .append(Float.floatToIntBits(ship.extractionRange())).append('|')
                    .append(Float.floatToIntBits(ship.dockingRange())).append('|')
                    .append(Float.floatToIntBits(ship.hull())).append('|')
                    .append(Float.floatToIntBits(ship.shields())).append('|')
                    .append(Float.floatToIntBits(ship.damagePerSecond())).append('|')
                    .append(Float.floatToIntBits(ship.weaponRange())).append('\n');
        }
        List<StationArchetypeDefinition> orderedStations = new ArrayList<>(stationArchetypes);
        orderedStations.sort(Comparator.comparing(StationArchetypeDefinition::id));
        for (StationArchetypeDefinition station : orderedStations) {
            canonical.append("station|").append(station.id()).append('|')
                    .append(station.displayName()).append('|').append(station.inventoryCapacity()).append('|')
                    .append(Double.doubleToLongBits(station.startingCredits())).append('|')
                    .append(station.factionId()).append('|').append(station.recipeId()).append('|');
            ConstructionDefinition construction = station.construction();
            if (construction == null) {
                canonical.append("construction=none|");
            } else {
                canonical.append("construction=")
                        .append(Double.doubleToLongBits(construction.fundingCredits())).append(',')
                        .append(Float.floatToIntBits(construction.buildSeconds())).append(',');
                appendAmounts(canonical, construction.materials());
                canonical.append('|');
            }
            List<MarketDefinition> markets = new ArrayList<>(station.markets());
            markets.sort(Comparator.comparing(MarketDefinition::itemId));
            for (MarketDefinition market : markets) {
                canonical.append(market.itemId()).append('=')
                        .append(market.initialStock()).append(',')
                        .append(market.targetStock()).append(',')
                        .append(Float.floatToIntBits(market.consumptionPerSecond())).append(';');
            }
            canonical.append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM не поддерживает обязательный SHA-256", exception);
        }
    }

    private static void appendAmounts(StringBuilder target, Map<String, Integer> amounts) {
        boolean first = true;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(amounts).entrySet()) {
            if (!first) {
                target.append(',');
            }
            target.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
    }

    private static <K, V> Map<K, V> immutableOrderedCopy(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * @param id стабильный persistent content ID
     * @param runtimeId плотный runtime ID
     * @param codeName английское техническое имя
     * @param displayName отображаемое имя
     * @param category категория хранения
     * @param basePrice базовая цена в кредитах
     * @param mineable признак непосредственной добычи
     */
    public record ItemDefinition(
            String id, int runtimeId, String codeName, String displayName,
            ItemCategory category, float basePrice, boolean mineable) {
        /**
         * @param id стабильный persistent content ID
         * @param runtimeId плотный runtime ID
         * @param codeName английское техническое имя
         * @param displayName отображаемое имя
         * @param category категория хранения
         * @param basePrice базовая цена в кредитах
         * @param mineable признак непосредственной добычи
         */
        public ItemDefinition {
            Objects.requireNonNull(id, "Item ID не задан");
            Objects.requireNonNull(codeName, "Item codeName не задан");
            Objects.requireNonNull(displayName, "Item displayName не задан");
            Objects.requireNonNull(category, "Item category не задана");
        }
    }

    /**
     * @param id стабильный persistent content ID
     * @param displayName отображаемое имя рецепта
     * @param durationSeconds длительность цикла
     * @param inputs входы по item content ID
     * @param outputs выходы по item content ID
     */
    public record RecipeDefinition(
            String id, String displayName, float durationSeconds,
            Map<String, Integer> inputs, Map<String, Integer> outputs) {
        /**
         * @param id стабильный persistent content ID
         * @param displayName отображаемое имя рецепта
         * @param durationSeconds длительность цикла
         * @param inputs входы по item content ID
         * @param outputs выходы по item content ID
         */
        public RecipeDefinition {
            Objects.requireNonNull(id, "Recipe ID не задан");
            Objects.requireNonNull(displayName, "Recipe displayName не задан");
            inputs = immutableOrderedCopy(Objects.requireNonNull(inputs, "Recipe inputs не заданы"));
            outputs = immutableOrderedCopy(Objects.requireNonNull(outputs, "Recipe outputs не заданы"));
        }
    }

    /**
     * @param id стабильный persistent faction ID
     * @param runtimeId плотный ID массива репутации
     * @param displayName отображаемое имя
     */
    public record FactionDefinition(String id, int runtimeId, String displayName) {
        /**
         * @param id стабильный persistent faction ID
         * @param runtimeId плотный ID массива репутации
         * @param displayName отображаемое имя
         */
        public FactionDefinition {
            Objects.requireNonNull(id, "Faction ID не задан");
            Objects.requireNonNull(displayName, "Faction displayName не задан");
        }
    }

    /**
     * Конкретный data-driven корабельный archetype поверх небольшой runtime-роли {@link ShipType}.
     *
     * @param id стабильный persistent archetype ID
     * @param displayName отображаемое имя типа
     * @param role функциональная runtime-роль
     * @param cargoCapacity физическая вместимость
     * @param movementSpeed скорость AI-перемещения
     * @param startingCredits начальный капитал экземпляра
     * @param extractionPerSecond скорость добычи для miner-role, иначе 0
     * @param extractionRange радиус добычи для miner-role, иначе 0
     * @param dockingRange радиус разгрузки для miner-role, иначе 0
     * @param hull корпус combat-role, иначе 0
     * @param shields щит combat-role, иначе 0
     * @param damagePerSecond урон combat-role, иначе 0
     * @param weaponRange дальность combat-role, иначе 0
     */
    public record ShipArchetypeDefinition(
            String id, String displayName, ShipType role, int cargoCapacity, float movementSpeed,
            double startingCredits, float extractionPerSecond, float extractionRange,
            float dockingRange, float hull, float shields, float damagePerSecond, float weaponRange) {
        /**
         * @param id стабильный persistent archetype ID
         * @param displayName отображаемое имя типа
         * @param role функциональная runtime-роль
         * @param cargoCapacity физическая вместимость
         * @param movementSpeed скорость AI-перемещения
         * @param startingCredits начальный капитал экземпляра
         * @param extractionPerSecond скорость добычи
         * @param extractionRange радиус добычи
         * @param dockingRange радиус разгрузки
         * @param hull корпус
         * @param shields щит
         * @param damagePerSecond урон
         * @param weaponRange дальность
         */
        public ShipArchetypeDefinition {
            Objects.requireNonNull(id, "Ship archetype ID не задан");
            Objects.requireNonNull(displayName, "Ship displayName не задан");
            Objects.requireNonNull(role, "Ship role не задана");
        }
    }

    /**
     * @param itemId persistent item ID рынка
     * @param initialStock стартовый физический запас
     * @param targetStock целевой рыночный запас
     * @param consumptionPerSecond базовое потребление в секунду
     */
    public record MarketDefinition(
            String itemId, int initialStock, int targetStock, float consumptionPerSecond) {
        /**
         * @param itemId persistent item ID рынка
         * @param initialStock стартовый физический запас
         * @param targetStock целевой рыночный запас
         * @param consumptionPerSecond базовое потребление в секунду
         */
        public MarketDefinition {
            Objects.requireNonNull(itemId, "Market item ID не задан");
        }
    }

    /**
     * @param id стабильный persistent archetype ID
     * @param displayName отображаемое имя типа
     * @param inventoryCapacity вместимость склада
     * @param startingCredits начальный капитал станции
     * @param factionId persistent faction ID владельца
     * @param recipeId persistent recipe ID либо {@code null}
     * @param markets рыночные правила по товарам
     */
    /**
     * Data-driven physical construction requirements of a station archetype.
     *
     * @param fundingCredits minimum project liquidity funded from faction treasury
     * @param buildSeconds build duration after all materials have arrived
     * @param materials required positive item amounts by persistent item ID
     */
    public record ConstructionDefinition(
            double fundingCredits, float buildSeconds, Map<String, Integer> materials) {
        /** Validates immutable construction requirements. */
        public ConstructionDefinition {
            if (!Double.isFinite(fundingCredits) || fundingCredits <= 0d
                    || !Float.isFinite(buildSeconds) || buildSeconds <= 0f) {
                throw new IllegalArgumentException("Construction funding/build duration должны быть положительными");
            }
            materials = immutableOrderedCopy(Objects.requireNonNull(materials, "Construction materials не заданы"));
            if (materials.isEmpty()) {
                throw new IllegalArgumentException("ConstructionDefinition требует materials");
            }
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "Construction item ID не задан");
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    throw new IllegalArgumentException("Construction material amount должен быть положительным");
                }
            }
        }
    }

    public record StationArchetypeDefinition(
            String id, String displayName, int inventoryCapacity, double startingCredits,
            String factionId, String recipeId, List<MarketDefinition> markets,
            ConstructionDefinition construction) {
        /**
         * @param id стабильный persistent archetype ID
         * @param displayName отображаемое имя типа
         * @param inventoryCapacity вместимость склада
         * @param startingCredits начальный капитал станции
         * @param factionId persistent faction ID владельца
         * @param recipeId persistent recipe ID либо {@code null}
         * @param markets рыночные правила по товарам
         * @param construction optional data-driven construction requirements; null means archetype is not constructible
         */
        public StationArchetypeDefinition {
            Objects.requireNonNull(id, "Station archetype ID не задан");
            Objects.requireNonNull(displayName, "Station displayName не задан");
            Objects.requireNonNull(factionId, "Station faction ID не задан");
            markets = List.copyOf(Objects.requireNonNull(markets, "Station markets не заданы"));
        }
    }
}

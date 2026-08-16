package com.spacesim.persistence;

import com.spacesim.constants.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure value-layer migrations старых {@link GameState} к текущей persistent schema.
 *
 * <p>Stage 3 schema v1 имела ровно пять item slots и не содержала stable content archetype ID.
 * Schema v2 расширила item arrays и добавила stable archetype IDs. Schema v3 сохраняет configured
 * market target отдельно от effective target. Schema v4 добавляет optional fitted engineering
 * state; при миграции v1-v3 он нейтрально отсутствует, поэтому никакой fit, топливо или энергия не
 * изобретаются из legacy class/archetype данных.</p>
 */
public final class GameStateMigration {
    /** Число товарных slots в Stage 3 schema v1. */
    public static final int LEGACY_STAGE3_ITEM_SLOTS = 5;

    private GameStateMigration() {
        throw new AssertionError("GameStateMigration не создаёт экземпляров");
    }

    /**
     * Возвращает state текущей schema, выполнив известные последовательные миграции.
     *
     * <p>Даже snapshot текущей logical schema проходит через neutral Stage-17 normalization:
     * ранние v2-файлы могли содержать трёхэлементный reputation vector, тогда как новые runtime
     * snapshots используют полную bounded faction capacity.</p>
     *
     * @param source декодированный snapshot поддерживаемой старой или текущей версии
     * @return snapshot schema {@link GameState#CURRENT_VERSION} с current runtime capacities
     * @throws NullPointerException если snapshot не задан
     * @throws IllegalArgumentException если версия неизвестна или legacy array shape повреждён
     */
    public static GameState toCurrent(GameState source) {
        GameState state = Objects.requireNonNull(source, "GameState не задан");
        if (state.schemaVersion() == GameState.CURRENT_VERSION) {
            return normalizeCurrentFactionCapacity(state);
        }
        if (state.schemaVersion() == GameState.CONFIGURED_MARKET_TARGET_VERSION) {
            return normalizeCurrentFactionCapacity(migrateVersion3(state));
        }
        if (state.schemaVersion() == GameState.ITEM_CAPACITY_ARCHETYPE_VERSION) {
            return normalizeCurrentFactionCapacity(migrateVersion2(state));
        }
        if (state.schemaVersion() != GameState.LEGACY_STAGE3_VERSION) {
            throw new IllegalArgumentException(
                    "Нет миграции persistent schema version: " + state.schemaVersion());
        }

        List<EntityState> migratedEntities = new ArrayList<>(state.entities().size());
        for (EntityState entity : state.entities()) {
            migratedEntities.add(migrateLegacyStage3Entity(entity));
        }
        return normalizeCurrentFactionCapacity(new GameState(
                GameState.CURRENT_VERSION,
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                List.copyOf(migratedEntities)));
    }

    private static GameState migrateVersion3(GameState state) {
        return new GameState(
                GameState.CURRENT_VERSION,
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                List.copyOf(state.entities()));
    }

    private static GameState migrateVersion2(GameState state) {
        List<EntityState> migratedEntities = new ArrayList<>(state.entities().size());
        for (EntityState entity : state.entities()) {
            migratedEntities.add(migrateVersion2Entity(entity));
        }
        return new GameState(
                GameState.CURRENT_VERSION,
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                List.copyOf(migratedEntities));
    }

    private static EntityState migrateVersion2Entity(EntityState entity) {
        EntityState value = Objects.requireNonNull(entity, "Schema-v2 EntityState не задан");
        EntityState.MarketState market = value.market();
        if (market == null) {
            return value;
        }
        List<Integer> effectiveTarget = List.copyOf(
                Objects.requireNonNull(market.targetStock(), "Schema-v2 market.targetStock не задан"));
        EntityState.MarketState migratedMarket = new EntityState.MarketState(
                effectiveTarget,
                effectiveTarget,
                market.baseConsumption(),
                market.sellPrices(),
                market.buyPrices(),
                market.consumptionRemainder(),
                market.tradableItems(),
                market.dirty());
        return new EntityState(
                value.id(),
                value.identity(),
                value.transform(),
                value.inventory(),
                value.wallet(),
                migratedMarket,
                value.production(),
                value.priceHistory(),
                value.faction(),
                value.reputation(),
                value.ship(),
                value.tradeAi(),
                value.mining(),
                value.combat(),
                value.asteroid(),
                value.archetype());
    }

    private static GameState normalizeCurrentFactionCapacity(GameState state) {
        List<EntityState> normalized = new ArrayList<>(state.entities().size());
        boolean changed = false;
        for (EntityState entity : state.entities()) {
            EntityState value = normalizeCurrentEntity(entity);
            normalized.add(value);
            changed |= value != entity;
        }
        if (!changed) {
            return state;
        }
        return new GameState(
                state.schemaVersion(),
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                List.copyOf(normalized));
    }

    private static EntityState normalizeCurrentEntity(EntityState entity) {
        Objects.requireNonNull(entity, "EntityState не задан");
        EntityState.ReputationState reputation = normalizeReputation(entity.reputation());
        if (reputation == entity.reputation()) {
            return entity;
        }
        return new EntityState(
                entity.id(),
                entity.identity(),
                entity.transform(),
                entity.inventory(),
                entity.wallet(),
                entity.market(),
                entity.production(),
                entity.priceHistory(),
                entity.faction(),
                reputation,
                entity.ship(),
                entity.tradeAi(),
                entity.mining(),
                entity.combat(),
                entity.asteroid(),
                entity.archetype(),
                entity.engineering());
    }

    private static EntityState migrateLegacyStage3Entity(EntityState entity) {
        Objects.requireNonNull(entity, "Legacy EntityState не задан");
        EntityState.InventoryState inventory = entity.inventory() == null
                ? null
                : new EntityState.InventoryState(
                        entity.inventory().capacity(),
                        padIntegers(entity.inventory().stock(), "inventory.stock"));

        EntityState.MarketState market = null;
        if (entity.market() != null) {
            List<Integer> target = padIntegers(entity.market().targetStock(), "market.targetStock");
            market = new EntityState.MarketState(
                    target,
                    target,
                    padFloats(entity.market().baseConsumption(), "market.baseConsumption"),
                    padFloats(entity.market().sellPrices(), "market.sellPrices"),
                    padFloats(entity.market().buyPrices(), "market.buyPrices"),
                    padDoubles(entity.market().consumptionRemainder(), "market.consumptionRemainder"),
                    padBooleans(entity.market().tradableItems(), "market.tradableItems"),
                    entity.market().dirty());
        }

        EntityState.ProductionState production = null;
        if (entity.production() != null) {
            List<EntityState.RecipeState> recipes = new ArrayList<>(entity.production().recipes().size());
            for (EntityState.RecipeState recipe : entity.production().recipes()) {
                recipes.add(new EntityState.RecipeState(
                        recipe.name(),
                        recipe.durationSeconds(),
                        padIntegers(recipe.inputs(), "recipe.inputs"),
                        padIntegers(recipe.outputs(), "recipe.outputs")));
            }
            production = new EntityState.ProductionState(
                    List.copyOf(recipes),
                    entity.production().activeRecipeIndex(),
                    entity.production().progressSeconds());
        }

        EntityState.PriceHistoryState history = null;
        if (entity.priceHistory() != null) {
            requireLegacyItemSize(entity.priceHistory().history(), "priceHistory.history");
            List<List<Float>> series = new ArrayList<>(Constants.MAX_ITEMS);
            for (List<Float> values : entity.priceHistory().history()) {
                series.add(List.copyOf(values));
            }
            while (series.size() < Constants.MAX_ITEMS) {
                series.add(List.of());
            }
            history = new EntityState.PriceHistoryState(
                    entity.priceHistory().maxPoints(), List.copyOf(series));
        }

        return new EntityState(
                entity.id(),
                entity.identity(),
                entity.transform(),
                inventory,
                entity.wallet(),
                market,
                production,
                history,
                entity.faction(),
                normalizeReputation(entity.reputation()),
                entity.ship(),
                entity.tradeAi(),
                entity.mining(),
                entity.combat(),
                entity.asteroid(),
                null);
    }

    private static EntityState.ReputationState normalizeReputation(EntityState.ReputationState state) {
        if (state == null) {
            return null;
        }
        List<Float> values = Objects.requireNonNull(state.values(), "Reputation.values не задан");
        if (values.size() == Constants.FACTION_RUNTIME_CAPACITY) {
            return state;
        }
        if (values.size() != Constants.LEGACY_FACTION_COUNT) {
            throw new IllegalArgumentException(
                    "Reputation.values должен содержать либо legacy "
                            + Constants.LEGACY_FACTION_COUNT + ", либо current "
                            + Constants.FACTION_RUNTIME_CAPACITY + " значений");
        }
        List<Float> result = new ArrayList<>(Constants.FACTION_RUNTIME_CAPACITY);
        result.addAll(values);
        while (result.size() < Constants.FACTION_RUNTIME_CAPACITY) {
            result.add(0f);
        }
        return new EntityState.ReputationState(List.copyOf(result));
    }

    private static List<Integer> padIntegers(List<Integer> values, String label) {
        requireLegacyItemSize(values, label);
        List<Integer> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0);
        }
        return List.copyOf(result);
    }

    private static List<Float> padFloats(List<Float> values, String label) {
        requireLegacyItemSize(values, label);
        List<Float> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0f);
        }
        return List.copyOf(result);
    }

    private static List<Double> padDoubles(List<Double> values, String label) {
        requireLegacyItemSize(values, label);
        List<Double> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0d);
        }
        return List.copyOf(result);
    }

    private static List<Boolean> padBooleans(List<Boolean> values, String label) {
        requireLegacyItemSize(values, label);
        List<Boolean> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(false);
        }
        return List.copyOf(result);
    }

    private static void requireLegacyItemSize(List<?> values, String label) {
        if (values == null || values.size() != LEGACY_STAGE3_ITEM_SLOTS) {
            throw new IllegalArgumentException(
                    "Legacy " + label + " должен содержать ровно "
                            + LEGACY_STAGE3_ITEM_SLOTS + " значений");
        }
    }
}

package com.spacesim.persistence;

import com.spacesim.constants.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure value-layer migrations старых {@link GameState} к текущей persistent schema.
 *
 * <p>Stage 3 schema v1 имела ровно пять item slots. Stage 4 превращает массивы в фиксированную
 * capacity, внутри которой data catalog может определять переменное число плотных runtime ID.
 * Миграция сохраняет первые пять значений побитово и дополняет новые slots нейтральными значениями.</p>
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
     * @param source декодированный snapshot поддерживаемой старой или текущей версии
     * @return snapshot schema {@link GameState#CURRENT_VERSION}
     * @throws NullPointerException если snapshot не задан
     * @throws IllegalArgumentException если версия неизвестна или старые item lists повреждены
     */
    public static GameState toCurrent(GameState source) {
        GameState state = Objects.requireNonNull(source, "GameState не задан");
        if (state.schemaVersion() == GameState.CURRENT_VERSION) {
            return state;
        }
        if (state.schemaVersion() != GameState.LEGACY_STAGE3_VERSION) {
            throw new IllegalArgumentException(
                    "Нет миграции persistent schema version: " + state.schemaVersion());
        }

        List<EntityState> migratedEntities = new ArrayList<>(state.entities().size());
        for (EntityState entity : state.entities()) {
            migratedEntities.add(migrateEntity(entity));
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

    private static EntityState migrateEntity(EntityState entity) {
        Objects.requireNonNull(entity, "Legacy EntityState не задан");
        EntityState.InventoryState inventory = entity.inventory() == null
                ? null
                : new EntityState.InventoryState(
                        entity.inventory().capacity(),
                        padIntegers(entity.inventory().stock(), "inventory.stock"));

        EntityState.MarketState market = entity.market() == null
                ? null
                : new EntityState.MarketState(
                        padIntegers(entity.market().targetStock(), "market.targetStock"),
                        padFloats(entity.market().baseConsumption(), "market.baseConsumption"),
                        padFloats(entity.market().sellPrices(), "market.sellPrices"),
                        padFloats(entity.market().buyPrices(), "market.buyPrices"),
                        padDoubles(entity.market().consumptionRemainder(), "market.consumptionRemainder"),
                        padBooleans(entity.market().tradableItems(), "market.tradableItems"),
                        entity.market().dirty());

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
            requireLegacySize(entity.priceHistory().history(), "priceHistory.history");
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
                entity.reputation(),
                entity.ship(),
                entity.tradeAi(),
                entity.mining(),
                entity.combat(),
                entity.asteroid());
    }

    private static List<Integer> padIntegers(List<Integer> values, String label) {
        requireLegacySize(values, label);
        List<Integer> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0);
        }
        return List.copyOf(result);
    }

    private static List<Float> padFloats(List<Float> values, String label) {
        requireLegacySize(values, label);
        List<Float> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0f);
        }
        return List.copyOf(result);
    }

    private static List<Double> padDoubles(List<Double> values, String label) {
        requireLegacySize(values, label);
        List<Double> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(0d);
        }
        return List.copyOf(result);
    }

    private static List<Boolean> padBooleans(List<Boolean> values, String label) {
        requireLegacySize(values, label);
        List<Boolean> result = new ArrayList<>(Constants.MAX_ITEMS);
        result.addAll(values);
        while (result.size() < Constants.MAX_ITEMS) {
            result.add(false);
        }
        return List.copyOf(result);
    }

    private static void requireLegacySize(List<?> values, String label) {
        if (values == null || values.size() != LEGACY_STAGE3_ITEM_SLOTS) {
            throw new IllegalArgumentException(
                    "Legacy " + label + " должен содержать ровно "
                            + LEGACY_STAGE3_ITEM_SLOTS + " значений");
        }
    }
}

package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Преобразует runtime Ashley-сущности в value-based {@link EntityState} и обратно.
 *
 * <p>Mapper является единственной границей между ECS и persistent DTO. Capture копирует все
 * поддерживаемые mutable поля по значениям. Restore всегда создаёт новый экземпляр Ashley
 * {@link Entity}; persistent-связи TradeAI/Mining остаются {@link EntityId} и позднее разрешаются
 * через {@link EntityRegistry}. Data-driven archetype сохраняется как стабильная строка, а не как
 * runtime-ссылка на объект каталога.</p>
 */
public final class EntityStateMapper {
    private EntityStateMapper() {
        throw new AssertionError("EntityStateMapper не создаёт экземпляров");
    }

    /**
     * Создаёт immutable value-snapshot одной идентифицированной сущности.
     *
     * @param entity runtime-сущность с обязательным {@link EntityIdComponent}
     * @return полный поддерживаемый persistent state
     * @throws NullPointerException если сущность не задана
     * @throws IllegalArgumentException если у сущности отсутствует persistent ID
     */
    public static EntityState capture(Entity entity) {
        Objects.requireNonNull(entity, "Entity не задана");
        EntityIdComponent idComponent = entity.getComponent(EntityIdComponent.class);
        if (idComponent == null) {
            throw new IllegalArgumentException("Persistent Entity должна иметь EntityIdComponent");
        }

        IdentityComponent identity = entity.getComponent(IdentityComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        WalletComponent wallet = entity.getComponent(WalletComponent.class);
        MarketComponent market = entity.getComponent(MarketComponent.class);
        ProductionComponent production = entity.getComponent(ProductionComponent.class);
        PriceHistoryComponent priceHistory = entity.getComponent(PriceHistoryComponent.class);
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        ReputationComponent reputation = entity.getComponent(ReputationComponent.class);
        ShipComponent ship = entity.getComponent(ShipComponent.class);
        TradeAIComponent tradeAi = entity.getComponent(TradeAIComponent.class);
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        CombatComponent combat = entity.getComponent(CombatComponent.class);
        AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
        ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);

        return new EntityState(
                idComponent.id,
                captureIdentity(identity),
                captureTransform(transform),
                captureInventory(inventory),
                wallet == null ? null : new EntityState.WalletState(wallet.getBalanceMilliCredits()),
                captureMarket(market),
                captureProduction(production),
                capturePriceHistory(priceHistory),
                faction == null ? null : new EntityState.FactionState(faction.factionId),
                captureReputation(reputation),
                ship == null ? null : new EntityState.ShipState(ship.type == null ? null : ship.type.name()),
                captureTradeAi(tradeAi),
                captureMining(mining),
                captureCombat(combat),
                captureAsteroid(asteroid),
                archetype == null ? null : new EntityState.ArchetypeState(archetype.contentId));
    }

    /**
     * Восстанавливает новый Ashley-объект из persistent snapshot.
     *
     * @param state value-based состояние сущности
     * @return новый runtime Entity с теми же компонентами и ID
     * @throws NullPointerException если snapshot или его обязательный ID не задан
     * @throws IllegalArgumentException если размеры persistent-массивов не совпадают с runtime schema
     */
    public static Entity restore(EntityState state) {
        Objects.requireNonNull(state, "EntityState не задан");
        Objects.requireNonNull(state.id(), "EntityState.id не задан");
        Entity entity = new Entity().add(new EntityIdComponent(state.id()));

        if (state.identity() != null) {
            EntityState.IdentityState value = state.identity();
            IdentityComponent.Kind kind = IdentityComponent.Kind.valueOf(
                    Objects.requireNonNull(value.kindName(), "Identity kind не задан"));
            entity.add(new IdentityComponent(value.name(), kind));
        }
        if (state.transform() != null) {
            EntityState.TransformState value = state.transform();
            TransformComponent component = new TransformComponent();
            component.position.set(value.x(), value.y());
            component.velocity.set(value.velocityX(), value.velocityY());
            entity.add(component);
        }
        if (state.inventory() != null) {
            EntityState.InventoryState value = state.inventory();
            requireSize(value.stock(), Constants.MAX_ITEMS, "Inventory.stock");
            InventoryComponent component = new InventoryComponent();
            component.capacity = value.capacity();
            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                component.stock[itemId] = value.stock().get(itemId);
            }
            entity.add(component);
        }
        if (state.wallet() != null) {
            entity.add(new WalletComponent(state.wallet().balanceMilliCredits()));
        }
        if (state.market() != null) {
            entity.add(restoreMarket(state.market()));
        }
        if (state.production() != null) {
            entity.add(restoreProduction(state.production()));
        }
        if (state.priceHistory() != null) {
            entity.add(restorePriceHistory(state.priceHistory()));
        }
        if (state.faction() != null) {
            entity.add(new FactionComponent(state.faction().factionId()));
        }
        if (state.reputation() != null) {
            entity.add(restoreReputation(state.reputation()));
        }
        if (state.ship() != null) {
            ShipComponent component = new ShipComponent();
            component.type = state.ship().typeName() == null
                    ? null
                    : ShipType.valueOf(state.ship().typeName());
            entity.add(component);
        }
        if (state.tradeAi() != null) {
            entity.add(restoreTradeAi(state.tradeAi()));
        }
        if (state.mining() != null) {
            entity.add(restoreMining(state.mining()));
        }
        if (state.combat() != null) {
            entity.add(restoreCombat(state.combat()));
        }
        if (state.asteroid() != null) {
            EntityState.AsteroidState value = state.asteroid();
            AsteroidComponent component = new AsteroidComponent(
                    value.spawnPointId(),
                    value.resourceItem(),
                    value.initialResource());
            if (value.remainingResource() < 0L
                    || value.remainingResource() > value.initialResource()) {
                throw new IllegalArgumentException("Остаток астероида находится вне initialResource");
            }
            component.remainingResource = value.remainingResource();
            entity.add(component);
        }
        if (state.archetype() != null) {
            entity.add(new ArchetypeComponent(state.archetype().contentId()));
        }
        return entity;
    }

    private static EntityState.IdentityState captureIdentity(IdentityComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.IdentityState(
                component.name,
                component.kind == null ? null : component.kind.name());
    }

    private static EntityState.TransformState captureTransform(TransformComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.TransformState(
                component.position.x,
                component.position.y,
                component.velocity.x,
                component.velocity.y);
    }

    private static EntityState.InventoryState captureInventory(InventoryComponent component) {
        if (component == null) {
            return null;
        }
        List<Integer> stock = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            stock.add(component.stock[itemId]);
        }
        return new EntityState.InventoryState(component.capacity, List.copyOf(stock));
    }

    private static EntityState.MarketState captureMarket(MarketComponent component) {
        if (component == null) {
            return null;
        }
        List<Integer> target = new ArrayList<>(Constants.MAX_ITEMS);
        List<Integer> configuredTarget = new ArrayList<>(Constants.MAX_ITEMS);
        List<Float> consumption = new ArrayList<>(Constants.MAX_ITEMS);
        List<Float> sell = new ArrayList<>(Constants.MAX_ITEMS);
        List<Float> buy = new ArrayList<>(Constants.MAX_ITEMS);
        List<Double> remainder = new ArrayList<>(Constants.MAX_ITEMS);
        List<Boolean> tradable = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            target.add(component.targetStock[itemId]);
            configuredTarget.add(component.configuredTargetStock[itemId]);
            consumption.add(component.baseConsumption[itemId]);
            sell.add(component.sellPrices[itemId]);
            buy.add(component.buyPrices[itemId]);
            remainder.add(component.consumptionRemainder[itemId]);
            tradable.add(component.tradableItems[itemId]);
        }
        return new EntityState.MarketState(
                List.copyOf(target),
                List.copyOf(configuredTarget),
                List.copyOf(consumption),
                List.copyOf(sell),
                List.copyOf(buy),
                List.copyOf(remainder),
                List.copyOf(tradable),
                component.isDirty);
    }

    private static MarketComponent restoreMarket(EntityState.MarketState value) {
        requireSize(value.targetStock(), Constants.MAX_ITEMS, "Market.targetStock");
        requireSize(value.configuredTargetStock(), Constants.MAX_ITEMS, "Market.configuredTargetStock");
        requireSize(value.baseConsumption(), Constants.MAX_ITEMS, "Market.baseConsumption");
        requireSize(value.sellPrices(), Constants.MAX_ITEMS, "Market.sellPrices");
        requireSize(value.buyPrices(), Constants.MAX_ITEMS, "Market.buyPrices");
        requireSize(value.consumptionRemainder(), Constants.MAX_ITEMS, "Market.consumptionRemainder");
        requireSize(value.tradableItems(), Constants.MAX_ITEMS, "Market.tradableItems");
        MarketComponent component = new MarketComponent();
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            component.targetStock[itemId] = value.targetStock().get(itemId);
            component.configuredTargetStock[itemId] = value.configuredTargetStock().get(itemId);
            component.baseConsumption[itemId] = value.baseConsumption().get(itemId);
            component.sellPrices[itemId] = value.sellPrices().get(itemId);
            component.buyPrices[itemId] = value.buyPrices().get(itemId);
            component.consumptionRemainder[itemId] = value.consumptionRemainder().get(itemId);
            component.tradableItems[itemId] = value.tradableItems().get(itemId);
        }
        component.isDirty = value.dirty();
        return component;
    }

    private static EntityState.ProductionState captureProduction(ProductionComponent component) {
        if (component == null) {
            return null;
        }
        List<EntityState.RecipeState> recipes = new ArrayList<>(component.recipes.size());
        for (Recipe recipe : component.recipes) {
            List<Integer> inputs = new ArrayList<>(Constants.MAX_ITEMS);
            List<Integer> outputs = new ArrayList<>(Constants.MAX_ITEMS);
            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                inputs.add(recipe.getInputAmount(itemId));
                outputs.add(recipe.getOutputAmount(itemId));
            }
            recipes.add(new EntityState.RecipeState(
                    recipe.name,
                    recipe.durationSeconds,
                    List.copyOf(inputs),
                    List.copyOf(outputs)));
        }
        return new EntityState.ProductionState(
                List.copyOf(recipes),
                component.activeRecipeIndex,
                component.progressSeconds);
    }

    private static ProductionComponent restoreProduction(EntityState.ProductionState value) {
        ProductionComponent component = new ProductionComponent();
        for (EntityState.RecipeState recipeState : value.recipes()) {
            requireSize(recipeState.inputs(), Constants.MAX_ITEMS, "Recipe.inputs");
            requireSize(recipeState.outputs(), Constants.MAX_ITEMS, "Recipe.outputs");
            Recipe recipe = new Recipe(recipeState.name(), recipeState.durationSeconds());
            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                int input = recipeState.inputs().get(itemId);
                int output = recipeState.outputs().get(itemId);
                if (input < 0 || output < 0) {
                    throw new IllegalArgumentException("Количество ресурса рецепта не может быть отрицательным");
                }
                if (input > 0) {
                    recipe.input(itemId, input);
                }
                if (output > 0) {
                    recipe.output(itemId, output);
                }
            }
            component.recipes.add(recipe);
        }
        component.activeRecipeIndex = value.activeRecipeIndex();
        component.progressSeconds = value.progressSeconds();
        return component;
    }

    private static EntityState.PriceHistoryState capturePriceHistory(PriceHistoryComponent component) {
        if (component == null) {
            return null;
        }
        List<List<Float>> history = new ArrayList<>(Constants.MAX_ITEMS);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            List<Float> points = new ArrayList<>(component.history[itemId].size);
            for (int pointIndex = 0; pointIndex < component.history[itemId].size; pointIndex++) {
                points.add(component.history[itemId].get(pointIndex));
            }
            history.add(List.copyOf(points));
        }
        return new EntityState.PriceHistoryState(component.maxPoints, List.copyOf(history));
    }

    private static PriceHistoryComponent restorePriceHistory(EntityState.PriceHistoryState value) {
        requireSize(value.history(), Constants.MAX_ITEMS, "PriceHistory.history");
        PriceHistoryComponent component = new PriceHistoryComponent();
        component.maxPoints = value.maxPoints();
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            for (Float point : value.history().get(itemId)) {
                component.history[itemId].add(point);
            }
        }
        return component;
    }

    private static EntityState.ReputationState captureReputation(ReputationComponent component) {
        if (component == null) {
            return null;
        }
        List<Float> values = new ArrayList<>(Constants.FACTION_RUNTIME_CAPACITY);
        for (int factionId = 0; factionId < Constants.FACTION_RUNTIME_CAPACITY; factionId++) {
            values.add(component.getReputation(factionId));
        }
        return new EntityState.ReputationState(List.copyOf(values));
    }

    private static ReputationComponent restoreReputation(EntityState.ReputationState value) {
        requireSize(value.values(), Constants.FACTION_RUNTIME_CAPACITY, "Reputation.values");
        ReputationComponent component = new ReputationComponent();
        for (int factionId = 0; factionId < Constants.FACTION_RUNTIME_CAPACITY; factionId++) {
            float reputation = value.values().get(factionId);
            if (reputation != 0f) {
                component.addReputation(factionId, reputation);
            }
        }
        return component;
    }

    private static EntityState.TradeAiState captureTradeAi(TradeAIComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.TradeAiState(
                component.state == null ? null : component.state.name(),
                component.buyStationId,
                component.sellStationId,
                component.targetStationId,
                component.targetItem,
                component.specializedItem,
                component.targetAmount,
                component.cargoSpace,
                component.movementSpeed,
                component.expectedProfitMilliCredits,
                component.routeSearchCooldown);
    }

    private static TradeAIComponent restoreTradeAi(EntityState.TradeAiState value) {
        TradeAIComponent component = new TradeAIComponent();
        component.state = value.stateName() == null
                ? null
                : TradeAIComponent.State.valueOf(value.stateName());
        component.buyStationId = value.buyStationId();
        component.sellStationId = value.sellStationId();
        component.targetStationId = value.targetStationId();
        component.targetItem = value.targetItem();
        component.specializedItem = value.specializedItem();
        component.targetAmount = value.targetAmount();
        component.cargoSpace = value.cargoSpace();
        component.movementSpeed = value.movementSpeed();
        component.expectedProfitMilliCredits = value.expectedProfitMilliCredits();
        component.routeSearchCooldown = value.routeSearchCooldown();
        return component;
    }

    private static EntityState.MiningState captureMining(MiningComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.MiningState(
                component.resourceItem,
                component.extractionPerSecond,
                component.movementSpeed,
                component.extractionRange,
                component.dockingRange,
                component.extractionRemainder,
                component.totalMined,
                component.totalDelivered,
                component.active,
                component.state == null ? null : component.state.name(),
                component.targetAsteroidId,
                component.homeBaseId);
    }

    private static MiningComponent restoreMining(EntityState.MiningState value) {
        MiningComponent component = new MiningComponent();
        component.resourceItem = value.resourceItem();
        component.extractionPerSecond = value.extractionPerSecond();
        component.movementSpeed = value.movementSpeed();
        component.extractionRange = value.extractionRange();
        component.dockingRange = value.dockingRange();
        component.extractionRemainder = value.extractionRemainder();
        component.totalMined = value.totalMined();
        component.totalDelivered = value.totalDelivered();
        component.active = value.active();
        component.state = value.stateName() == null
                ? null
                : MiningComponent.State.valueOf(value.stateName());
        component.targetAsteroidId = value.targetAsteroidId();
        component.homeBaseId = value.homeBaseId();
        return component;
    }

    private static EntityState.CombatState captureCombat(CombatComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.CombatState(
                component.hull,
                component.maxHull,
                component.shields,
                component.maxShields,
                component.damagePerSecond,
                component.weaponRange);
    }

    private static CombatComponent restoreCombat(EntityState.CombatState value) {
        CombatComponent component = new CombatComponent();
        component.hull = value.hull();
        component.maxHull = value.maxHull();
        component.shields = value.shields();
        component.maxShields = value.maxShields();
        component.damagePerSecond = value.damagePerSecond();
        component.weaponRange = value.weaponRange();
        return component;
    }

    private static EntityState.AsteroidState captureAsteroid(AsteroidComponent component) {
        if (component == null) {
            return null;
        }
        return new EntityState.AsteroidState(
                component.spawnPointId,
                component.resourceItem,
                component.initialResource,
                component.remainingResource);
    }

    private static void requireSize(List<?> values, int expected, String label) {
        if (values == null || values.size() != expected) {
            throw new IllegalArgumentException(label + " должен содержать ровно " + expected + " значений");
        }
    }
}

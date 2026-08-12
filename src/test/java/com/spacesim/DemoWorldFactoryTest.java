package com.spacesim;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatComponent;
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
import com.spacesim.constants.Constants;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.ItemType;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.MarketSystem;
import com.spacesim.systems.MiningSystem;
import com.spacesim.systems.PriceRecorderSystem;
import com.spacesim.systems.ProductionSystem;
import com.spacesim.systems.TradeAISystem;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoWorldFactoryTest {
    @Test
    void createsIndependentWorldsWithValidAshleyStructure() {
        List<Entity> firstWorld = DemoWorldFactory.createEntities();
        List<Entity> secondWorld = DemoWorldFactory.createEntities();

        assertEquals(13, firstWorld.size());
        assertEquals(firstWorld.size(), secondWorld.size());
        assertEquals(6, entitiesOfKind(firstWorld, IdentityComponent.Kind.STATION).size());
        assertEquals(7, entitiesOfKind(firstWorld, IdentityComponent.Kind.FLEET).size());
        assertEquals(5, carrierShips(firstWorld).size());

        Set<String> names = new HashSet<>();
        int miningShips = 0;
        int combatShips = 0;
        for (int index = 0; index < firstWorld.size(); index++) {
            Entity first = firstWorld.get(index);
            Entity second = secondWorld.get(index);
            IdentityComponent firstIdentity = first.getComponent(IdentityComponent.class);
            IdentityComponent secondIdentity = second.getComponent(IdentityComponent.class);

            assertNotSame(first, second);
            assertNotNull(firstIdentity);
            assertNotNull(secondIdentity);
            assertEquals(firstIdentity.name, secondIdentity.name);
            assertEquals(firstIdentity.kind, secondIdentity.kind);
            assertTrue(names.add(firstIdentity.name), () -> "Повторяющееся имя: " + firstIdentity.name);

            TransformComponent firstTransform = first.getComponent(TransformComponent.class);
            InventoryComponent firstInventory = first.getComponent(InventoryComponent.class);
            assertNotNull(firstTransform, firstIdentity.name);
            assertNotNull(firstInventory, firstIdentity.name);
            assertNotSame(firstTransform, second.getComponent(TransformComponent.class));
            assertNotSame(firstInventory, second.getComponent(InventoryComponent.class));
            assertNotSame(firstInventory.stock,
                    second.getComponent(InventoryComponent.class).stock);
            assertTrue(Float.isFinite(firstTransform.position.x), firstIdentity.name);
            assertTrue(Float.isFinite(firstTransform.position.y), firstIdentity.name);
            assertTrue(firstTransform.position.x >= 0f
                            && firstTransform.position.x <= Constants.WORLD_WIDTH,
                    firstIdentity.name);
            assertTrue(firstTransform.position.y >= 0f
                            && firstTransform.position.y <= Constants.WORLD_HEIGHT,
                    firstIdentity.name);
            assertInventoryIsValid(firstInventory, firstIdentity.name);

            if (firstIdentity.kind == IdentityComponent.Kind.STATION) {
                MarketComponent firstMarket = first.getComponent(MarketComponent.class);
                FactionComponent faction = first.getComponent(FactionComponent.class);
                assertNotNull(firstMarket, firstIdentity.name);
                assertNotNull(faction, firstIdentity.name);
                assertNotNull(first.getComponent(PriceHistoryComponent.class), firstIdentity.name);
                assertNotSame(firstMarket, second.getComponent(MarketComponent.class));
                assertTrue(faction.factionId >= 0 && faction.factionId < Constants.MAX_FACTIONS,
                        firstIdentity.name);
                assertNull(first.getComponent(TradeAIComponent.class), firstIdentity.name);
                assertNull(first.getComponent(ShipComponent.class), firstIdentity.name);
                assertNull(first.getComponent(MiningComponent.class), firstIdentity.name);
                assertNull(first.getComponent(CombatComponent.class), firstIdentity.name);
            } else {
                ShipComponent ship = first.getComponent(ShipComponent.class);
                ShipComponent secondShip = second.getComponent(ShipComponent.class);
                assertNotNull(ship, firstIdentity.name);
                assertNotNull(ship.type, firstIdentity.name);
                assertNotSame(ship, secondShip, firstIdentity.name);
                assertEquals(ship.type, secondShip.type, firstIdentity.name);
                assertNull(first.getComponent(MarketComponent.class), firstIdentity.name);

                if (ship.type.isCarrier()) {
                    TradeAIComponent tradeAI = first.getComponent(TradeAIComponent.class);
                    assertNotNull(tradeAI, firstIdentity.name);
                    assertNotNull(first.getComponent(ReputationComponent.class), firstIdentity.name);
                    assertNull(first.getComponent(MiningComponent.class), firstIdentity.name);
                    assertNull(first.getComponent(CombatComponent.class), firstIdentity.name);
                    assertEquals(firstInventory.capacity, tradeAI.cargoSpace, firstIdentity.name);
                    assertTrue(Float.isFinite(tradeAI.credits) && tradeAI.credits >= 0f,
                            firstIdentity.name);
                    assertTrue(Float.isFinite(tradeAI.movementSpeed) && tradeAI.movementSpeed >= 0f,
                            firstIdentity.name);
                    assertNotSame(tradeAI, second.getComponent(TradeAIComponent.class));
                } else if (ship.type == ShipType.MINING_SHIP) {
                    miningShips++;
                    MiningComponent mining = first.getComponent(MiningComponent.class);
                    MiningComponent secondMining = second.getComponent(MiningComponent.class);
                    assertNotNull(mining, firstIdentity.name);
                    assertNotSame(mining, secondMining, firstIdentity.name);
                    assertEquals(mining.resourceItem, secondMining.resourceItem, firstIdentity.name);
                    assertTrue(ship.canCarryItem(mining.resourceItem), firstIdentity.name);
                    ItemType resource = ItemType.fromId(mining.resourceItem);
                    assertNotNull(resource, firstIdentity.name);
                    assertTrue(resource.isMineable(), firstIdentity.name);
                    assertTrue(Float.isFinite(mining.extractionPerSecond)
                                    && mining.extractionPerSecond > 0f,
                            firstIdentity.name);
                    assertEquals(0L, mining.totalMined, firstIdentity.name);
                    assertEquals(0L, mining.totalDelivered, firstIdentity.name);
                    assertNotNull(mining.homeBase, firstIdentity.name);
                    assertNotSame(mining.homeBase, secondMining.homeBase, firstIdentity.name);
                    assertNotNull(mining.homeBase.getComponent(MarketComponent.class),
                            firstIdentity.name);
                    assertNull(first.getComponent(TradeAIComponent.class), firstIdentity.name);
                    assertNull(first.getComponent(CombatComponent.class), firstIdentity.name);
                } else if (ship.type == ShipType.COMBAT_SHIP) {
                    combatShips++;
                    CombatComponent combat = first.getComponent(CombatComponent.class);
                    CombatComponent secondCombat = second.getComponent(CombatComponent.class);
                    assertNotNull(combat, firstIdentity.name);
                    assertNotSame(combat, secondCombat, firstIdentity.name);
                    assertTrue(combat.isOperational(), firstIdentity.name);
                    assertNull(first.getComponent(TradeAIComponent.class), firstIdentity.name);
                    assertNull(first.getComponent(MiningComponent.class), firstIdentity.name);
                } else {
                    throw new AssertionError("Неизвестная специальная роль: " + ship.type);
                }
            }

            ProductionComponent firstProduction = first.getComponent(ProductionComponent.class);
            ProductionComponent secondProduction = second.getComponent(ProductionComponent.class);
            if (firstProduction != null) {
                assertNotSame(firstProduction, secondProduction);
                assertEquals(1, firstProduction.recipes.size(), firstIdentity.name);
                assertEquals(1, secondProduction.recipes.size(), secondIdentity.name);
                assertNotSame(firstProduction.recipes.get(0), secondProduction.recipes.get(0));
            }
        }

        InventoryComponent firstInventory = firstWorld.get(0).getComponent(InventoryComponent.class);
        InventoryComponent secondInventory = secondWorld.get(0).getComponent(InventoryComponent.class);
        int secondOreStock = secondInventory.stock[Constants.ITEM_ORE];
        firstInventory.stock[Constants.ITEM_ORE]++;
        assertEquals(secondOreStock, secondInventory.stock[Constants.ITEM_ORE]);
        assertEquals(1, miningShips);
        assertEquals(1, combatShips);
    }

    @Test
    void everyItemHasSupplyDemandAndUniqueCompatibleCarrierSpecialization() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        List<Entity> stations = entitiesOfKind(entities, IdentityComponent.Kind.STATION);
        List<Entity> carriers = carrierShips(entities);
        boolean[] specializations = new boolean[Constants.MAX_ITEMS];

        assertEquals(Constants.MAX_ITEMS, carriers.size());
        for (Entity carrier : carriers) {
            TradeAIComponent tradeAI = carrier.getComponent(TradeAIComponent.class);
            ShipComponent ship = carrier.getComponent(ShipComponent.class);
            int itemId = tradeAI.specializedItem;
            assertTrue(itemId >= 0 && itemId < Constants.MAX_ITEMS,
                    () -> "Некорректная специализация: " + itemId);
            assertTrue(ship.canPurchaseItem(itemId),
                    () -> ship.type + " не может купить " + Constants.ITEM_NAMES[itemId]);
            assertFalse(specializations[itemId],
                    () -> "Повторяющаяся специализация: " + Constants.ITEM_NAMES[itemId]);
            specializations[itemId] = true;
        }

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            int tradableMarketCount = 0;
            boolean hasSupplier = false;
            boolean hasDemand = false;

            for (Entity station : stations) {
                MarketComponent market = station.getComponent(MarketComponent.class);
                if (!market.isTradable(itemId)) {
                    continue;
                }
                tradableMarketCount++;
                int stock = station.getComponent(InventoryComponent.class).stock[itemId];
                hasSupplier |= stock > market.targetStock[itemId];
                hasDemand |= stock < market.targetStock[itemId];
            }

            String itemName = Constants.ITEM_NAMES[itemId];
            assertTrue(specializations[itemId], () -> "Нет транспорта для " + itemName);
            assertTrue(tradableMarketCount >= 2,
                    "Недостаточно рынков для " + itemName + ": " + tradableMarketCount);
            assertTrue(hasSupplier, () -> "Нет начального предложения для " + itemName);
            assertTrue(hasDemand, () -> "Нет начального спроса на " + itemName);
        }
    }

    @Test
    void activeRecipesFormReachableCompleteProductionDag() {
        List<Recipe> recipes = activeRecipes(DemoWorldFactory.createEntities());
        Recipe[] producerByItem = new Recipe[Constants.MAX_ITEMS];

        assertEquals(Constants.MAX_ITEMS - 1, recipes.size());
        for (Recipe recipe : recipes) {
            int outputCount = 0;
            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                if (recipe.getOutputAmount(itemId) <= 0) {
                    continue;
                }
                outputCount++;
                assertNull(producerByItem[itemId],
                        "Несколько активных рецептов производят " + Constants.ITEM_NAMES[itemId]);
                producerByItem[itemId] = recipe;
            }
            assertEquals(1, outputCount, () -> "Рецепт должен иметь один выход: " + recipe.name);
        }

        assertNull(producerByItem[Constants.ITEM_ORE],
                "Руда должна поступать из конечных астероидов, а не из бесконечного рецепта");
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (itemId == Constants.ITEM_ORE) {
                continue;
            }
            int checkedItemId = itemId;
            assertNotNull(producerByItem[itemId],
                    () -> "Нет активного рецепта для " + Constants.ITEM_NAMES[checkedItemId]);
        }

        assertInputItems(producerByItem[Constants.ITEM_ENERGY]);
        assertInputItems(producerByItem[Constants.ITEM_FOOD], Constants.ITEM_ENERGY);
        assertInputItems(producerByItem[Constants.ITEM_STEEL],
                Constants.ITEM_ORE, Constants.ITEM_ENERGY);
        assertInputItems(producerByItem[Constants.ITEM_WEAPONS],
                Constants.ITEM_ENERGY, Constants.ITEM_STEEL);

        boolean[] reachableItems = new boolean[Constants.MAX_ITEMS];
        reachableItems[Constants.ITEM_ORE] = true;
        Set<Recipe> completedRecipes = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean madeProgress;
        do {
            madeProgress = false;
            for (Recipe recipe : recipes) {
                if (completedRecipes.contains(recipe) || !inputsAreReachable(recipe, reachableItems)) {
                    continue;
                }
                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    if (recipe.getOutputAmount(itemId) > 0) {
                        reachableItems[itemId] = true;
                    }
                }
                completedRecipes.add(recipe);
                madeProgress = true;
            }
        } while (madeProgress);

        assertEquals(recipes.size(), completedRecipes.size(),
                "В графе производства есть недостижимый рецепт или цикл");
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            int checkedItemId = itemId;
            assertTrue(reachableItems[itemId],
                    () -> "Недостижим товар " + Constants.ITEM_NAMES[checkedItemId]);
        }
    }

    @Test
    void everyProducerCanCompleteExactlyOneConfiguredCycle() {
        List<Entity> producers = new ArrayList<>();
        for (Entity entity : DemoWorldFactory.createEntities()) {
            if (entity.getComponent(ProductionComponent.class) != null) {
                producers.add(entity);
            }
        }
        assertEquals(Constants.MAX_ITEMS - 1, producers.size());

        for (Entity producer : producers) {
            IdentityComponent identity = producer.getComponent(IdentityComponent.class);
            InventoryComponent inventory = producer.getComponent(InventoryComponent.class);
            ProductionComponent production = producer.getComponent(ProductionComponent.class);
            Recipe recipe = production.getActiveRecipe();
            int[] before = inventory.stock.clone();

            Engine engine = new Engine();
            engine.addSystem(new ProductionSystem());
            engine.addEntity(producer);
            engine.update(recipe.durationSeconds);

            boolean inventoryChanged = false;
            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                int expected = before[itemId]
                        - recipe.getInputAmount(itemId)
                        + recipe.getOutputAmount(itemId);
                assertEquals(expected, inventory.stock[itemId],
                        identity.name + ", товар " + Constants.ITEM_NAMES[itemId]);
                inventoryChanged |= before[itemId] != inventory.stock[itemId];
            }
            assertTrue(inventoryChanged, () -> "Рецепт не изменил склад: " + recipe.name);
            assertEquals(0f, production.progressSeconds, 0.0001f, identity.name);
            assertInventoryIsValid(inventory, identity.name);
        }
    }

    @Test
    void specializedCarriersChooseCompatibleProfitableRoutesForEveryItemOnFirstUpdate() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        GlobalEventManager eventManager = new GlobalEventManager(0d);
        Engine engine = new Engine();
        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE)));
        for (Entity entity : entities) {
            engine.addEntity(entity);
        }

        engine.update(0f);

        boolean[] routedItems = new boolean[Constants.MAX_ITEMS];
        List<Entity> carriers = carrierShips(entities);
        assertEquals(Constants.MAX_ITEMS, carriers.size());
        for (Entity carrier : carriers) {
            IdentityComponent identity = carrier.getComponent(IdentityComponent.class);
            TradeAIComponent tradeAI = carrier.getComponent(TradeAIComponent.class);
            ShipComponent ship = carrier.getComponent(ShipComponent.class);
            int itemId = tradeAI.specializedItem;

            assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, tradeAI.state, identity.name);
            assertEquals(itemId, tradeAI.targetItem, identity.name);
            assertTrue(ship.canPurchaseItem(itemId), identity.name);
            assertNotNull(tradeAI.buyStation, identity.name);
            assertNotNull(tradeAI.sellStation, identity.name);
            assertNotSame(tradeAI.buyStation, tradeAI.sellStation, identity.name);
            assertSame(tradeAI.buyStation, tradeAI.targetStation, identity.name);
            assertTrue(tradeAI.targetAmount > 0, identity.name);
            assertTrue(Float.isFinite(tradeAI.expectedProfit) && tradeAI.expectedProfit > 0f,
                    identity.name);

            MarketComponent buyMarket = tradeAI.buyStation.getComponent(MarketComponent.class);
            MarketComponent sellMarket = tradeAI.sellStation.getComponent(MarketComponent.class);
            InventoryComponent buyInventory = tradeAI.buyStation.getComponent(InventoryComponent.class);
            assertTrue(buyMarket.isTradable(itemId), identity.name);
            assertTrue(sellMarket.isTradable(itemId), identity.name);
            assertTrue(buyInventory.stock[itemId] >= tradeAI.targetAmount, identity.name);
            assertTrue(buyMarket.sellPrices[itemId] > 0f, identity.name);
            assertTrue(sellMarket.buyPrices[itemId] > buyMarket.sellPrices[itemId], identity.name);
            routedItems[itemId] = true;
        }

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            int checkedItemId = itemId;
            assertTrue(routedItems[itemId],
                    () -> "Не выбран маршрут для " + Constants.ITEM_NAMES[checkedItemId]);
        }

        Entity miningShip = onlyShipOfType(entities, ShipType.MINING_SHIP);
        Entity combatShip = onlyShipOfType(entities, ShipType.COMBAT_SHIP);
        assertNull(miningShip.getComponent(TradeAIComponent.class));
        assertNull(combatShip.getComponent(TradeAIComponent.class));
    }

    @Test
    void deterministicHeadlessEconomyRunPreservesCoreInvariants() {
        List<Entity> entities = DemoWorldFactory.createEntities();
        GlobalEventManager eventManager = new GlobalEventManager(0d);
        Engine engine = new Engine();
        engine.addSystem(new MarketSystem(eventManager));
        engine.addSystem(new ConsumptionSystem(eventManager));
        engine.addSystem(new ProductionSystem());
        AsteroidSpawnSystem asteroidSpawnSystem =
                new AsteroidSpawnSystem(AsteroidSpawnConfig.demoWorld());
        engine.addSystem(asteroidSpawnSystem);
        engine.addSystem(new MiningSystem());
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE)));
        engine.addSystem(new PriceRecorderSystem());
        for (Entity entity : entities) {
            engine.addEntity(entity);
        }

        List<Entity> carriers = carrierShips(entities);
        Entity miningShip = onlyShipOfType(entities, ShipType.MINING_SHIP);
        Entity combatShip = onlyShipOfType(entities, ShipType.COMBAT_SHIP);
        InventoryComponent miningInventory = miningShip.getComponent(InventoryComponent.class);
        MiningComponent mining = miningShip.getComponent(MiningComponent.class);
        TransformComponent combatTransform = combatShip.getComponent(TransformComponent.class);
        CombatComponent combat = combatShip.getComponent(CombatComponent.class);
        Vector2 initialCombatPosition = combatTransform.position.cpy();
        int initialMiningStock = miningInventory.stock[mining.resourceItem];
        long initialTotalMined = mining.totalMined;
        long initialTotalDelivered = mining.totalDelivered;
        float initialMiningCredits = mining.credits;
        float initialHull = combat.hull;
        float initialShields = combat.shields;
        float initialDamage = combat.damagePerSecond;
        float initialWeaponRange = combat.weaponRange;
        float[] initialCredits = new float[Constants.MAX_ITEMS];
        boolean[] observedCargo = new boolean[Constants.MAX_ITEMS];
        boolean[] observedProfitableSale = new boolean[Constants.MAX_ITEMS];
        assertEquals(Constants.MAX_ITEMS, carriers.size());
        for (Entity carrier : carriers) {
            TradeAIComponent tradeAI = carrier.getComponent(TradeAIComponent.class);
            initialCredits[tradeAI.specializedItem] = tradeAI.credits;
        }

        float stepSeconds = 0.25f;
        for (int step = 0; step < 240; step++) {
            eventManager.update(stepSeconds);
            engine.update(stepSeconds);
            for (Entity carrier : carriers) {
                TradeAIComponent tradeAI = carrier.getComponent(TradeAIComponent.class);
                ShipComponent ship = carrier.getComponent(ShipComponent.class);
                int itemId = tradeAI.specializedItem;
                InventoryComponent inventory = carrier.getComponent(InventoryComponent.class);
                observedCargo[itemId] |= inventory.stock[itemId] > 0;
                observedProfitableSale[itemId] |= tradeAI.credits > initialCredits[itemId];
                if (tradeAI.targetItem >= 0) {
                    assertTrue(ship.canPurchaseItem(tradeAI.targetItem),
                            carrier.getComponent(IdentityComponent.class).name);
                }
            }
        }

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            assertTrue(observedCargo[itemId],
                    "Товар ни разу не перевозился: " + Constants.ITEM_NAMES[itemId]);
            assertTrue(observedProfitableSale[itemId],
                    "Не завершена прибыльная продажа: " + Constants.ITEM_NAMES[itemId]);
        }

        int finalMiningStock = miningInventory.stock[mining.resourceItem];
        long minedDuringRun = mining.totalMined - initialTotalMined;
        long deliveredDuringRun = mining.totalDelivered - initialTotalDelivered;
        assertTrue(minedDuringRun > 0L,
                "Счётчик добычи не увеличился");
        assertTrue(deliveredDuringRun > 0L,
                "Добывающий корабль не доставил руду на рынок");
        assertTrue(mining.credits > initialMiningCredits,
                "Продажа добытой руды не увеличила баланс");
        assertEquals(initialMiningStock + minedDuringRun,
                (long) finalMiningStock + deliveredDuringRun,
                "Добытая руда потерялась между трюмом и рынком");
        assertTrue(finalMiningStock <= miningInventory.capacity,
                "Добывающий корабль переполнил трюм");
        assertTrue(Double.isFinite(mining.extractionRemainder)
                        && mining.extractionRemainder >= 0d
                        && mining.extractionRemainder < 1d,
                "Некорректный остаток добычи");

        int activeAsteroids = 0;
        for (Entity entity : engine.getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid == null) {
                continue;
            }
            activeAsteroids++;
            assertTrue(asteroid.remainingResource > 0L
                            && asteroid.remainingResource <= asteroid.initialResource,
                    "Некорректный остаток астероида");
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            assertNotNull(transform);
            assertTrue(transform.position.x >= 0f
                            && transform.position.x <= Constants.WORLD_WIDTH);
            assertTrue(transform.position.y >= 0f
                            && transform.position.y <= Constants.WORLD_HEIGHT);
        }
        assertTrue(activeAsteroids > 0
                        && activeAsteroids <= AsteroidSpawnConfig.demoWorld().maxActiveAsteroids(),
                "Спавнер не поддерживает допустимое число астероидов");
        assertTrue(asteroidSpawnSystem.getSpawnedAsteroidCount() >= activeAsteroids,
                "Счётчик появлений меньше числа активных астероидов");

        assertTrue(combat.isOperational(), "Боевой корабль потерял работоспособность");
        assertEquals(initialCombatPosition.x, combatTransform.position.x, 0f);
        assertEquals(initialCombatPosition.y, combatTransform.position.y, 0f);
        assertEquals(initialHull, combat.hull, 0f);
        assertEquals(initialShields, combat.shields, 0f);
        assertEquals(initialDamage, combat.damagePerSecond, 0f);
        assertEquals(initialWeaponRange, combat.weaponRange, 0f);

        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            assertTrue(Float.isFinite(transform.position.x), identity.name);
            assertTrue(Float.isFinite(transform.position.y), identity.name);
            assertInventoryIsValid(inventory, identity.name);

            MarketComponent market = entity.getComponent(MarketComponent.class);
            if (market != null) {
                PriceHistoryComponent history = entity.getComponent(PriceHistoryComponent.class);
                assertNotNull(history, identity.name);
                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    assertTrue(Double.isFinite(market.consumptionRemainder[itemId]), identity.name);
                    assertTrue(market.consumptionRemainder[itemId] >= 0d
                                    && market.consumptionRemainder[itemId] < 1d,
                            identity.name);
                    if (market.isTradable(itemId)) {
                        assertTrue(Float.isFinite(market.sellPrices[itemId])
                                        && market.sellPrices[itemId] > 0f,
                                identity.name + ", товар " + Constants.ITEM_NAMES[itemId]);
                        assertTrue(Float.isFinite(market.buyPrices[itemId])
                                        && market.buyPrices[itemId] > 0f,
                                identity.name + ", товар " + Constants.ITEM_NAMES[itemId]);
                        assertTrue(history.history[itemId].size > 0,
                                identity.name + ", товар " + Constants.ITEM_NAMES[itemId]);
                    }
                }
            }

            ProductionComponent production = entity.getComponent(ProductionComponent.class);
            if (production != null) {
                Recipe recipe = production.getActiveRecipe();
                assertNotNull(recipe, identity.name);
                assertTrue(Float.isFinite(production.progressSeconds)
                                && production.progressSeconds >= 0f
                                && production.progressSeconds < recipe.durationSeconds,
                        identity.name);
            }

            TradeAIComponent tradeAI = entity.getComponent(TradeAIComponent.class);
            if (tradeAI != null) {
                ShipComponent ship = entity.getComponent(ShipComponent.class);
                assertNotNull(ship, identity.name);
                assertTrue(ship.type.isCarrier(), identity.name);
                assertNotNull(tradeAI.state, identity.name);
                assertTrue(Float.isFinite(tradeAI.credits) && tradeAI.credits >= 0f,
                        identity.name);
                assertTrue(tradeAI.targetItem == -1
                                || tradeAI.targetItem >= 0 && tradeAI.targetItem < Constants.MAX_ITEMS,
                        identity.name);
                if (tradeAI.targetItem >= 0) {
                    assertTrue(ship.canPurchaseItem(tradeAI.targetItem), identity.name);
                }
                if (tradeAI.targetStation != null) {
                    assertNotNull(tradeAI.targetStation.getComponent(MarketComponent.class), identity.name);
                }
            }
        }
    }

    private static List<Entity> entitiesOfKind(
            List<Entity> entities,
            IdentityComponent.Kind kind) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && identity.kind == kind) {
                result.add(entity);
            }
        }
        return result;
    }

    private static List<Entity> carrierShips(List<Entity> entities) {
        List<Entity> carriers = new ArrayList<>();
        for (Entity entity : entities) {
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            if (ship != null && ship.type != null && ship.type.isCarrier()) {
                carriers.add(entity);
            }
        }
        return carriers;
    }

    private static Entity onlyShipOfType(List<Entity> entities, ShipType expectedType) {
        Entity result = null;
        for (Entity entity : entities) {
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            if (ship == null || ship.type != expectedType) {
                continue;
            }
            assertNull(result, "Несколько кораблей типа " + expectedType);
            result = entity;
        }
        assertNotNull(result, "Нет корабля типа " + expectedType);
        return result;
    }

    private static List<Recipe> activeRecipes(List<Entity> entities) {
        List<Recipe> recipes = new ArrayList<>();
        for (Entity entity : entities) {
            ProductionComponent production = entity.getComponent(ProductionComponent.class);
            if (production == null) {
                continue;
            }
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            assertEquals(1, production.recipes.size(), identity.name);
            assertEquals(0, production.activeRecipeIndex, identity.name);
            Recipe recipe = production.getActiveRecipe();
            assertNotNull(recipe, identity.name);
            recipes.add(recipe);
        }
        return recipes;
    }

    private static void assertInputItems(Recipe recipe, int... expectedItems) {
        boolean[] expectedInputs = new boolean[Constants.MAX_ITEMS];
        for (int itemId : expectedItems) {
            expectedInputs[itemId] = true;
        }
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            boolean actualInput = recipe.getInputAmount(itemId) > 0;
            assertEquals(expectedInputs[itemId], actualInput,
                    recipe.name + ", вход " + Constants.ITEM_NAMES[itemId]);
        }
    }

    private static boolean inputsAreReachable(Recipe recipe, boolean[] reachableItems) {
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (recipe.getInputAmount(itemId) > 0 && !reachableItems[itemId]) {
                return false;
            }
        }
        return true;
    }

    private static void assertInventoryIsValid(InventoryComponent inventory, String entityName) {
        assertTrue(inventory.capacity >= 0, entityName);
        long totalStock = 0L;
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            assertTrue(inventory.stock[itemId] >= 0,
                    entityName + ", товар " + Constants.ITEM_NAMES[itemId]);
            totalStock += inventory.stock[itemId];
        }
        assertTrue(totalStock <= inventory.capacity,
                entityName + ": склад переполнен, " + totalStock + "/" + inventory.capacity);
    }
}

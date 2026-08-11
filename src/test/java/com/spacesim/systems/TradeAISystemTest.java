package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TradeAISystemTest {
    private static final float FLOAT_EPSILON = 0.001f;

    @Test
    void специализацияПоУмолчаниюСохраняетВыборСамогоПрибыльногоТовара() {
        Engine engine = createEngine();
        Entity supplier = createStation(
                0f, 0f, 5, 100, 5, 10f, 0f, Constants.FACTION_NEUTRAL);
        configureTradableItem(supplier, Constants.ITEM_WEAPONS, 5, 5, 20f, 0f);
        Entity buyer = createStation(
                0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        configureTradableItem(buyer, Constants.ITEM_WEAPONS, 0, 5, 0f, 100f);
        Entity fleet = createFleet(0f, 0f, 0, 1_000f);
        engine.addEntity(supplier);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(-1, ai.specializedItem);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai.state);
        assertEquals(Constants.ITEM_WEAPONS, ai.targetItem);
        assertSame(supplier, ai.buyStation);
        assertSame(buyer, ai.sellStation);
        assertEquals(400f, ai.expectedProfit, FLOAT_EPSILON);
    }

    @Test
    void специализацияЗаставляетВыбратьМенееПрибыльныйРазрешённыйТовар() {
        Engine engine = createEngine();
        Entity supplier = createStation(
                0f, 0f, 5, 100, 5, 10f, 0f, Constants.FACTION_NEUTRAL);
        configureTradableItem(supplier, Constants.ITEM_WEAPONS, 5, 5, 20f, 0f);
        Entity buyer = createStation(
                0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        configureTradableItem(buyer, Constants.ITEM_WEAPONS, 0, 5, 0f, 100f);
        Entity fleet = createFleet(0f, 0f, 0, 1_000f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.specializedItem = Constants.ITEM_FOOD;
        engine.addEntity(supplier);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai.state);
        assertEquals(Constants.ITEM_FOOD, ai.targetItem);
        assertSame(supplier, ai.buyStation);
        assertSame(buyer, ai.sellStation);
        assertEquals(50f, ai.expectedProfit, FLOAT_EPSILON);
    }

    @Test
    void специализированныйФлотПродаётУжеЗагруженныйДругойТовар() {
        Engine engine = createEngine();
        Entity buyer = createStation(
                0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 3, 0f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.specializedItem = Constants.ITEM_WEAPONS;
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
        assertEquals(Constants.ITEM_FOOD, ai.targetItem);
        assertSame(buyer, ai.sellStation);

        engine.update(0f);
        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(Constants.ITEM_WEAPONS, ai.specializedItem);
        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(3, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(60f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void некорректнаяСпециализацияБезопасноОставляетФлотВОжидании() {
        int[] invalidSpecializations = {-2, Constants.MAX_ITEMS};

        for (int invalidSpecialization : invalidSpecializations) {
            Engine engine = createEngine();
            Entity supplier = createStation(
                    0f, 0f, 5, 100, 5, 10f, 0f, Constants.FACTION_NEUTRAL);
            Entity buyer = createStation(
                    0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
            Entity fleet = createFleet(0f, 0f, 0, 100f);
            TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
            ai.specializedItem = invalidSpecialization;
            engine.addEntity(supplier);
            engine.addEntity(buyer);
            engine.addEntity(fleet);

            assertDoesNotThrow(() -> engine.update(0f));

            assertEquals(TradeAIComponent.State.IDLE, ai.state);
            assertEquals(-1, ai.targetItem);
            assertNull(ai.buyStation);
            assertNull(ai.sellStation);
            assertNull(ai.targetStation);
            assertEquals(1f, ai.routeSearchCooldown, FLOAT_EPSILON);
        }
    }

    @Test
    void флотСНачальнымГрузомСначалаИщетПокупателя() {
        Engine engine = createEngine();
        Entity buyer = createStation(0f, 0f, 0, 100, 10, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 3, 0f);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
        assertNull(ai.buyStation);
        assertSame(buyer, ai.sellStation);
        assertEquals(3, ai.targetAmount);

        engine.update(0f);
        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(3, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(60f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void частичнаяПродажаОграниченаАктуальнойВместимостьюСтанции() {
        Engine engine = createEngine();
        Entity buyer = createStation(0f, 0f, 0, 10, 10, 0f, 10f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 10, 0f);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);
        inventory(buyer).stock[Constants.ITEM_ORE] = 6;
        engine.update(0f);
        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(6, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(4, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(6, inventory(buyer).stock[Constants.ITEM_ORE]);
        assertEquals(40f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void заполненнаяВоВремяПолётаСтанцияНеЗапираетГруз() {
        Engine engine = createEngine();
        Entity preferredBuyer = createStation(100f, 0f, 0, 5, 5, 0f, 30f, Constants.FACTION_NEUTRAL);
        Entity fallbackBuyer = createStation(0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        engine.addEntity(preferredBuyer);
        engine.addEntity(fallbackBuyer);
        engine.addEntity(fleet);

        engine.update(0f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertSame(preferredBuyer, ai.sellStation);

        inventory(preferredBuyer).stock[Constants.ITEM_ORE] = 5;
        engine.update(1f);
        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(5, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(0f, ai.credits, FLOAT_EPSILON);

        engine.update(1f);
        assertSame(fallbackBuyer, ai.sellStation);
        engine.update(1f);
        engine.update(0f);

        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(5, inventory(fallbackBuyer).stock[Constants.ITEM_FOOD]);
        assertEquals(100f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void удалённаяСтанцияНеСчитаетсяДействующимНазначением() {
        Engine engine = createEngine();
        Entity removedBuyer = createStation(100f, 0f, 0, 100, 5, 0f, 30f, Constants.FACTION_NEUTRAL);
        Entity fallbackBuyer = createStation(0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        engine.addEntity(removedBuyer);
        engine.addEntity(fallbackBuyer);
        engine.addEntity(fleet);

        engine.update(0f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertSame(removedBuyer, ai.sellStation);

        engine.removeEntity(removedBuyer);
        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(5, inventory(fleet).stock[Constants.ITEM_FOOD]);

        engine.update(1f);
        assertSame(fallbackBuyer, ai.sellStation);
        engine.update(1f);
        engine.update(0f);

        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(5, inventory(fallbackBuyer).stock[Constants.ITEM_FOOD]);
        assertEquals(100f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void скидкаРепутацииИспользуетсяПриРасчётеИОплатеПокупки() {
        Engine engine = createEngine();
        Entity supplier = createStation(
                0f,
                0f,
                1,
                100,
                1,
                100f,
                0f,
                Constants.FACTION_TRADE_LEAGUE
        );
        Entity buyer = createStation(0f, 0f, 0, 100, 1, 0f, 90f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 0, 85f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.cargoSpace = 1;
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, Constants.MAX_REPUTATION);
        fleet.add(reputation);
        engine.addEntity(supplier);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai.state);
        assertSame(supplier, ai.buyStation);
        assertSame(buyer, ai.sellStation);

        engine.update(0f);
        engine.update(0f);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
        assertEquals(1, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(supplier).stock[Constants.ITEM_FOOD]);
        assertEquals(0f, ai.credits, FLOAT_EPSILON);

        engine.update(0f);
        engine.update(0f);

        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(1, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(90f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void исчезнувшаяДоПокупкиМаржаОтменяетСделку() {
        Engine engine = createEngine();
        Entity supplier = createStation(0f, 0f, 1, 100, 1, 10f, 0f, Constants.FACTION_NEUTRAL);
        Entity buyer = createStation(0f, 0f, 0, 100, 1, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 0, 10f);
        fleet.getComponent(TradeAIComponent.class).cargoSpace = 1;
        engine.addEntity(supplier);
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);
        engine.update(0f);
        buyer.getComponent(MarketComponent.class).buyPrices[Constants.ITEM_FOOD] = 5f;
        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(1, inventory(supplier).stock[Constants.ITEM_FOOD]);
        assertEquals(10f, ai.credits, FLOAT_EPSILON);
    }

    @Test
    void некорректныйШагВремениНеМеняетМаршрутИПозицию() {
        Engine engine = createEngine();
        Entity buyer = createStation(100f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        engine.addEntity(buyer);
        engine.addEntity(fleet);
        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        TransformComponent transform = fleet.getComponent(TransformComponent.class);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);

        engine.update(-1f);
        engine.update(Float.NaN);
        engine.update(Float.POSITIVE_INFINITY);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
        assertEquals(0f, transform.position.x, FLOAT_EPSILON);
        assertEquals(0f, transform.position.y, FLOAT_EPSILON);
        assertSame(buyer, ai.sellStation);
    }

    @Test
    void пользовательскаяСкоростьОпределяетПройденноеРасстояние() {
        Engine engine = createEngine();
        Entity buyer = createStation(100f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.movementSpeed = 25f;
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        engine.update(0f);
        engine.update(1f);

        TransformComponent transform = fleet.getComponent(TransformComponent.class);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
        assertEquals(25f, transform.position.x, FLOAT_EPSILON);
        assertEquals(0f, transform.position.y, FLOAT_EPSILON);
        assertSame(buyer, ai.sellStation);
    }

    @Test
    void некорректнаяСкоростьПриостанавливаетНоНеЛомаетМаршрут() {
        Engine engine = createEngine();
        Entity buyer = createStation(100f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        engine.addEntity(buyer);
        engine.addEntity(fleet);
        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        TransformComponent transform = fleet.getComponent(TransformComponent.class);
        float[] invalidSpeeds = {-1f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY};

        for (float invalidSpeed : invalidSpeeds) {
            ai.movementSpeed = invalidSpeed;
            engine.update(1f);

            assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
            assertEquals(0f, transform.position.x, FLOAT_EPSILON);
            assertEquals(0f, transform.position.y, FLOAT_EPSILON);
            assertSame(buyer, ai.sellStation);
        }

        ai.movementSpeed = 25f;
        engine.update(1f);
        assertEquals(25f, transform.position.x, FLOAT_EPSILON);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai.state);
    }

    @Test
    void скоростьФлотаПоУмолчаниюРавнаСтаМировымЕдиницамВСекунду() {
        assertEquals(100f, new TradeAIComponent().movementSpeed, FLOAT_EPSILON);
    }

    @Test
    void повреждённыйБалансНеПриводитКИсключениюИлиПередачеГруза() {
        Engine engine = createEngine();
        Entity buyer = createStation(0f, 0f, 0, 100, 5, 0f, 20f, Constants.FACTION_NEUTRAL);
        Entity fleet = createFleet(0f, 0f, 5, 0f);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.state = TradeAIComponent.State.SELLING;
        ai.sellStation = buyer;
        ai.targetStation = buyer;
        ai.targetItem = Constants.ITEM_FOOD;
        ai.targetAmount = 5;
        ai.credits = Float.NaN;
        engine.addEntity(buyer);
        engine.addEntity(fleet);

        assertDoesNotThrow(() -> engine.update(0f));

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertEquals(5, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertNull(ai.sellStation);
    }

    @Test
    void поискНеВыбираетПродажуКотораяНеМожетИзменитьБаланс() {
        Engine roundedEngine = createEngine();
        Entity roundedBuyer = createStation(
                0f, 0f, 0, 100, 5, 0f, 1f, Constants.FACTION_NEUTRAL);
        Entity richFleet = createFleet(0f, 0f, 5, Float.MAX_VALUE);
        roundedEngine.addEntity(roundedBuyer);
        roundedEngine.addEntity(richFleet);

        roundedEngine.update(0f);

        TradeAIComponent richAi = richFleet.getComponent(TradeAIComponent.class);
        assertEquals(TradeAIComponent.State.IDLE, richAi.state);
        assertNull(richAi.sellStation);
        assertEquals(5, inventory(richFleet).stock[Constants.ITEM_FOOD]);

        Engine overflowEngine = createEngine();
        Entity overflowBuyer = createStation(
                0f, 0f, 0, 100, 5, 0f, Float.MAX_VALUE, Constants.FACTION_NEUTRAL);
        Entity loadedFleet = createFleet(0f, 0f, 2, 0f);
        overflowEngine.addEntity(overflowBuyer);
        overflowEngine.addEntity(loadedFleet);

        overflowEngine.update(0f);

        TradeAIComponent loadedAi = loadedFleet.getComponent(TradeAIComponent.class);
        assertEquals(TradeAIComponent.State.IDLE, loadedAi.state);
        assertNull(loadedAi.sellStation);
        assertEquals(2, inventory(loadedFleet).stock[Constants.ITEM_FOOD]);
    }

    private Engine createEngine() {
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(new SpatialHashGrid()));
        return engine;
    }

    private Entity createFleet(float x, float y, int foodCargo, float credits) {
        Entity fleet = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = foodCargo;
        TradeAIComponent ai = new TradeAIComponent();
        ai.credits = credits;
        fleet.add(transform);
        fleet.add(inventory);
        fleet.add(ai);
        return fleet;
    }

    private Entity createStation(float x, float y, int foodStock, int capacity, int targetStock,
                                 float sellPrice, float buyPrice, int factionId) {
        Entity station = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, targetStock, 0f);
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        station.add(transform);
        station.add(inventory);
        station.add(market);
        station.add(new FactionComponent(factionId));
        return station;
    }

    private void configureTradableItem(Entity station, int itemId, int stock, int targetStock,
                                       float sellPrice, float buyPrice) {
        InventoryComponent inventory = station.getComponent(InventoryComponent.class);
        MarketComponent market = station.getComponent(MarketComponent.class);
        inventory.stock[itemId] = stock;
        market.configureTradableItem(itemId, targetStock, 0f);
        market.sellPrices[itemId] = sellPrice;
        market.buyPrices[itemId] = buyPrice;
    }

    private InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }
}

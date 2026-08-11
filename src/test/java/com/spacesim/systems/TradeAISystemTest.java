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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TradeAISystemTest {
    private static final float FLOAT_EPSILON = 0.001f;

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

    private InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }
}

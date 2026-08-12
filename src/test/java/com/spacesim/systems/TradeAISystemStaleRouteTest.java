package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeAISystemStaleRouteTest {
    private long nextId = 1L;

    @Test
    void закрывшийсяСпредПередПокупкойОтменяетМаршрутБезTransfer() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE), ledger));

        Entity supplier = station(0f, 100, 9f, 10f);
        Entity consumer = station(100f, 0, 20f, 22f);
        Entity fleet = fleet(0f);
        engine.addEntity(supplier);
        engine.addEntity(consumer);
        engine.addEntity(fleet);

        long moneyBefore = totalMoney(supplier, consumer, fleet);
        int goodsBefore = totalFood(supplier, consumer, fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai(fleet).state);
        engine.update(0f);
        assertEquals(TradeAIComponent.State.BUYING, ai(fleet).state);

        MarketComponent consumerMarket = consumer.getComponent(MarketComponent.class);
        consumerMarket.buyPrices[Constants.ITEM_FOOD] = 5f;
        consumerMarket.isDirty = false;
        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertNull(ai(fleet).buyStationId);
        assertNull(ai(fleet).sellStationId);
        assertTrue(ai(fleet).routeSearchCooldown > 0f);
        assertEquals(goodsBefore, totalFood(supplier, consumer, fleet));
        assertEquals(moneyBefore, totalMoney(supplier, consumer, fleet));
        assertEquals(0, ledger.size());
    }

    private Entity station(float x, int foodStock, float buyPrice, float sellPrice) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.isDirty = false;
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d))));
    }

    private Entity fleet(float x) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 10;
        TradeAIComponent ai = new TradeAIComponent();
        ai.cargoSpace = 10;
        ai.movementSpeed = 100f;
        ai.specializedItem = Constants.ITEM_FOOD;
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(1_000d)))
                .add(ai)
                .add(new ReputationComponent()));
    }

    private Entity identified(Entity entity) {
        return entity.add(new EntityIdComponent(new EntityId(nextId++)));
    }

    private TradeAIComponent ai(Entity entity) {
        return entity.getComponent(TradeAIComponent.class);
    }

    private long totalMoney(Entity... entities) {
        long total = 0L;
        for (Entity entity : entities) {
            total = Math.addExact(total, entity.getComponent(WalletComponent.class).getBalanceMilliCredits());
        }
        return total;
    }

    private int totalFood(Entity... entities) {
        int total = 0;
        for (Entity entity : entities) {
            total += entity.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD];
        }
        return total;
    }
}

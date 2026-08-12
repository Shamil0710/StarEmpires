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
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.trade.TradeRoutePlanner;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeAISystemProfitTimeTest {
    private long nextId = 1L;

    @Test
    void productionDefaultВыбираетБлижнийМаршрутСЛучшейПрибыльюВСекунду() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE), ledger));

        Entity supplier = station(0f, 100, 1f, 10f);
        Entity nearConsumer = station(100f, 0, 20f, 30f);
        Entity farConsumer = station(2_000f, 0, 25f, 30f);
        Entity fleet = fleet(0f);
        engine.addEntity(supplier);
        engine.addEntity(nearConsumer);
        engine.addEntity(farConsumer);
        engine.addEntity(fleet);

        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(id(supplier), ai.buyStationId);
        assertEquals(id(nearConsumer), ai.sellStationId);
        assertEquals(Money.fromCredits(100d), ai.expectedProfitMilliCredits);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai.state);
    }

    @Test
    void explicitLegacyModeПоПрежнемуВыбираетМаксимальнуюАбсолютнуюПрибыль() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(
                new SpatialHashGrid(Constants.CELL_SIZE),
                ledger,
                new EntityRegistry(),
                ContentCatalogLoader.loadDefault(),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT));

        Entity supplier = station(0f, 100, 1f, 10f);
        Entity nearConsumer = station(100f, 0, 20f, 30f);
        Entity farConsumer = station(2_000f, 0, 25f, 30f);
        Entity fleet = fleet(0f);
        engine.addEntity(supplier);
        engine.addEntity(nearConsumer);
        engine.addEntity(farConsumer);
        engine.addEntity(fleet);

        engine.update(0f);

        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        assertEquals(id(farConsumer), ai.sellStationId);
        assertEquals(Money.fromCredits(150d), ai.expectedProfitMilliCredits);
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

    private EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}

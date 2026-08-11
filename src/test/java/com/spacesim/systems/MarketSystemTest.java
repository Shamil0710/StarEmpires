package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketSystemTest {
    @Test
    void событиеСразуМеняетТолькоЦелевойТоварИЦенаВосстанавливается() {
        GlobalEventManager eventManager = new GlobalEventManager(0d);
        TestMarket testMarket = createMarket(eventManager);

        testMarket.engine.update(0f);
        assertEquals(Constants.BASE_PRICES[Constants.ITEM_FOOD],
                testMarket.market.sellPrices[Constants.ITEM_FOOD], 0.001f);
        assertEquals(Constants.BASE_PRICES[Constants.ITEM_STEEL],
                testMarket.market.sellPrices[Constants.ITEM_STEEL], 0.001f);

        eventManager.activateEvent(new EconomyEvent(
                "Продовольственный кризис",
                Constants.ITEM_FOOD,
                3f,
                2f,
                1f,
                new Vector2(0f, 0f),
                100f));
        testMarket.engine.update(0f);

        assertEquals(Constants.BASE_PRICES[Constants.ITEM_FOOD] * 3f,
                testMarket.market.sellPrices[Constants.ITEM_FOOD], 0.001f);
        assertEquals(Constants.BASE_PRICES[Constants.ITEM_STEEL],
                testMarket.market.sellPrices[Constants.ITEM_STEEL], 0.001f);

        eventManager.update(1f);
        testMarket.engine.update(0f);

        assertEquals(Constants.BASE_PRICES[Constants.ITEM_FOOD],
                testMarket.market.sellPrices[Constants.ITEM_FOOD], 0.001f);
        assertEquals(Constants.BASE_PRICES[Constants.ITEM_STEEL],
                testMarket.market.sellPrices[Constants.ITEM_STEEL], 0.001f);
    }

    @Test
    void отключенныйТоварВсегдаИмеетНулевуюЦену() {
        GlobalEventManager eventManager = new GlobalEventManager(0d);
        Engine engine = new Engine();
        engine.addSystem(new MarketSystem(eventManager));

        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_ORE] = 500;
        MarketComponent market = new MarketComponent();
        market.sellPrices[Constants.ITEM_ORE] = 123f;
        market.buyPrices[Constants.ITEM_ORE] = 100f;
        station.add(inventory);
        station.add(market);
        station.add(new TransformComponent());
        engine.addEntity(station);

        engine.update(0f);

        assertEquals(0f, market.sellPrices[Constants.ITEM_ORE]);
        assertEquals(0f, market.buyPrices[Constants.ITEM_ORE]);
    }

    private TestMarket createMarket(GlobalEventManager eventManager) {
        Engine engine = new Engine();
        engine.addSystem(new MarketSystem(eventManager));

        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = 100;
        inventory.stock[Constants.ITEM_STEEL] = 100;

        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.configureTradableItem(Constants.ITEM_STEEL, 100, 0f);

        station.add(inventory);
        station.add(market);
        station.add(new TransformComponent());
        engine.addEntity(station);

        return new TestMarket(engine, market);
    }

    private record TestMarket(Engine engine, MarketComponent market) {
    }
}

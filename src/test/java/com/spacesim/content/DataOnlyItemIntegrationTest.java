package com.spacesim.content;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.ItemType;
import com.spacesim.model.ShipType;
import com.spacesim.systems.MarketSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataOnlyItemIntegrationTest {
    @Test
    void шестойТоварИзJsonПолучаетРыночнуюЦенуИCargoPolicyБезJavaEnum() {
        ContentCatalog catalog = ContentCatalogLoader.parse("""
                {
                  "schemaVersion": 1,
                  "items": [
                    {"id":"item.ore","runtimeId":0,"codeName":"Ore","displayName":"Руда","category":"MATERIAL","basePrice":10,"mineable":true},
                    {"id":"item.energy","runtimeId":1,"codeName":"Energy","displayName":"Энергия","category":"GAS_LIQUID","basePrice":5,"mineable":false},
                    {"id":"item.food","runtimeId":2,"codeName":"Food","displayName":"Продовольствие","category":"FINISHED_GOODS","basePrice":20,"mineable":false},
                    {"id":"item.steel","runtimeId":3,"codeName":"Steel","displayName":"Сталь","category":"MATERIAL","basePrice":50,"mineable":false},
                    {"id":"item.weapons","runtimeId":4,"codeName":"Weapons","displayName":"Вооружение","category":"FINISHED_GOODS","basePrice":150,"mineable":false},
                    {"id":"item.water","runtimeId":5,"codeName":"Water","displayName":"Вода","category":"GAS_LIQUID","basePrice":8,"mineable":false}
                  ],
                  "recipes": []
                }
                """);

        ContentCatalog.ItemDefinition water = catalog.findItem("item.water");
        assertNotNull(water);
        assertEquals(5, water.runtimeId());
        assertNull(ItemType.fromId(water.runtimeId()));
        assertTrue(ShipType.GAS_LIQUID_CARRIER.canPurchase(water.category(), water.mineable()));

        GlobalEventManager events = new GlobalEventManager(0d);
        Engine engine = new Engine();
        engine.addSystem(new MarketSystem(events, catalog));

        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[water.runtimeId()] = 100;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(water.runtimeId(), 100, 0f);
        Entity station = new Entity()
                .add(inventory)
                .add(market)
                .add(new TransformComponent());
        engine.addEntity(station);

        engine.update(0f);

        assertEquals(8f, market.sellPrices[water.runtimeId()], 0.0001f);
        assertEquals(7.2f, market.buyPrices[water.runtimeId()], 0.0001f);
    }
}

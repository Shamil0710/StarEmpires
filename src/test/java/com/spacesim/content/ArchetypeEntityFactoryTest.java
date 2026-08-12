package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.Money;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArchetypeEntityFactoryTest {
    @Test
    void новыйStationИShipArchetypeСоздаютсяИзJsonБезНовогоJavaТипа() {
        ContentCatalog catalog = ContentCatalogLoader.parse("""
                {
                  "schemaVersion": 1,
                  "items": [
                    {"id":"item.coolant","runtimeId":0,"codeName":"Coolant","displayName":"Хладагент","category":"GAS_LIQUID","basePrice":17,"mineable":false}
                  ],
                  "recipes": [
                    {"id":"recipe.coolant_refining","displayName":"Очистка хладагента","durationSeconds":3,"inputs":{},"outputs":{"item.coolant":11}}
                  ],
                  "factions": [
                    {"id":"faction.test","runtimeId":0,"displayName":"Тестовая фракция"}
                  ],
                  "shipArchetypes": [
                    {"id":"ship.fast_tanker","displayName":"Быстрый танкер","role":"GAS_LIQUID_CARRIER","cargoCapacity":222,"movementSpeed":333,"startingCredits":4444,"extractionPerSecond":0,"extractionRange":0,"dockingRange":0,"hull":0,"shields":0,"damagePerSecond":0,"weaponRange":0}
                  ],
                  "stationArchetypes": [
                    {"id":"station.coolant_refinery","displayName":"Очистительный комплекс","inventoryCapacity":777,"startingCredits":8888,"factionId":"faction.test","recipeId":"recipe.coolant_refining","markets":[{"itemId":"item.coolant","initialStock":55,"targetStock":66,"consumptionPerSecond":0.25}]}
                  ]
                }
                """);

        Entity station = ArchetypeEntityFactory.createStation(
                catalog, "station.coolant_refinery", "Новый завод", 10f, 20f);
        ArchetypeComponent stationType = station.getComponent(ArchetypeComponent.class);
        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        MarketComponent market = station.getComponent(MarketComponent.class);
        ProductionComponent production = station.getComponent(ProductionComponent.class);

        assertEquals("station.coolant_refinery", stationType.contentId);
        assertEquals(777, stationInventory.capacity);
        assertEquals(55, stationInventory.stock[0]);
        assertEquals(66, market.targetStock[0]);
        assertEquals(0.25f, market.baseConsumption[0], 0f);
        assertEquals(Money.fromCredits(8888d),
                station.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(0, station.getComponent(FactionComponent.class).factionId);
        assertNotNull(production);
        assertEquals(11, production.getActiveRecipe().getOutputAmount(0));

        Entity ship = ArchetypeEntityFactory.createTrader(
                catalog,
                "ship.fast_tanker",
                "Новый танкер",
                30f,
                40f,
                "item.coolant",
                "faction.test");
        assertEquals("ship.fast_tanker", ship.getComponent(ArchetypeComponent.class).contentId);
        assertEquals(222, ship.getComponent(InventoryComponent.class).capacity);
        assertEquals(333f, ship.getComponent(TradeAIComponent.class).movementSpeed, 0f);
        assertEquals(0, ship.getComponent(TradeAIComponent.class).specializedItem);
        assertEquals(ShipType.GAS_LIQUID_CARRIER, ship.getComponent(ShipComponent.class).type);
        assertEquals(Money.fromCredits(4444d),
                ship.getComponent(WalletComponent.class).getBalanceMilliCredits());
    }
}

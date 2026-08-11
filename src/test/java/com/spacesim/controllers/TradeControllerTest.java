package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeControllerTest {
    private final TradeController tradeController = new TradeController();

    @Test
    void покупкаСоСтанцииПереноситТоварИДеньги() {
        Entity station = new Entity();
        InventoryComponent stationInventory = new InventoryComponent();
        stationInventory.stock[Constants.ITEM_FOOD] = 50;
        station.add(stationInventory);

        MarketComponent market = new MarketComponent();
        market.sellPrices[Constants.ITEM_FOOD] = 10f;
        station.add(market);

        InventoryComponent buyerInventory = new InventoryComponent();
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);

        boolean result = tradeController.buyFromStation(station, buyerInventory, Constants.ITEM_FOOD, 5, credits);

        assertTrue(result);
        assertEquals(45, stationInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(5, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(50f, credits.credits);
        assertTrue(market.isDirty);
    }

    @Test
    void продажаНаСтанциюПереноситТоварИДеньги() {
        Entity station = new Entity();
        InventoryComponent stationInventory = new InventoryComponent();
        station.add(stationInventory);

        MarketComponent market = new MarketComponent();
        market.buyPrices[Constants.ITEM_STEEL] = 25f;
        station.add(market);

        InventoryComponent sellerInventory = new InventoryComponent();
        sellerInventory.stock[Constants.ITEM_STEEL] = 4;
        TradeController.CreditAccount credits = new TradeController.CreditAccount(10f);

        boolean result = tradeController.sellToStation(station, sellerInventory, Constants.ITEM_STEEL, 3, credits);

        assertTrue(result);
        assertEquals(3, stationInventory.stock[Constants.ITEM_STEEL]);
        assertEquals(1, sellerInventory.stock[Constants.ITEM_STEEL]);
        assertEquals(85f, credits.credits);
        assertTrue(market.isDirty);
    }
}

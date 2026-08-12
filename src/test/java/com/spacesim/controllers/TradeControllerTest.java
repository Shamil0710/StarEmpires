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
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
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
        market.configureTradableItem(Constants.ITEM_STEEL, 100, 0f);
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

    @Test
    void отключенныйТоварНельзяКупить() {
        Entity station = new Entity();
        InventoryComponent stationInventory = new InventoryComponent();
        stationInventory.stock[Constants.ITEM_FOOD] = 10;
        station.add(stationInventory);

        MarketComponent market = new MarketComponent();
        market.sellPrices[Constants.ITEM_FOOD] = 1f;
        station.add(market);

        InventoryComponent buyerInventory = new InventoryComponent();
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);

        boolean result = tradeController.buyFromStation(
                station, buyerInventory, Constants.ITEM_FOOD, 1, credits);

        assertFalse(result);
        assertEquals(10, stationInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(100f, credits.credits);
    }

    @Test
    void отрицательноеКоличествоНеМеняетСостояниеУпрощённойПокупки() {
        Entity station = createFoodStation(10, 10f);
        TradeController.PlayerProfile player = new TradeController.PlayerProfile();
        player.credits = 100f;

        boolean result = tradeController.buy(station, Constants.ITEM_FOOD, -1, player);

        assertFalse(result);
        assertEquals(10, station.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD]);
        assertEquals(100f, player.credits);
        assertEquals(0, player.cargo[Constants.ITEM_FOOD]);
    }

    @Test
    void некорректнаяЦенаОтклоняетсяБезЧастичныхИзменений() {
        Entity station = createFoodStation(10, Float.NaN);
        InventoryComponent buyerInventory = new InventoryComponent();
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);

        boolean result = tradeController.buyFromStation(
                station, buyerInventory, Constants.ITEM_FOOD, 1, credits);

        assertFalse(result);
        assertEquals(10, station.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD]);
        assertEquals(0, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(100f, credits.credits);
    }

    @Test
    void повреждённыйБалансОтклоняетсяБезИзмененияСкладов() {
        Entity station = createFoodStation(10, 10f);
        InventoryComponent buyerInventory = new InventoryComponent();
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);
        credits.credits = Float.NaN;

        boolean result = tradeController.buyFromStation(
                station, buyerInventory, Constants.ITEM_FOOD, 1, credits);

        assertFalse(result);
        assertEquals(10, station.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD]);
        assertEquals(0, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertTrue(Float.isNaN(credits.credits));
    }

    @Test
    void кредитныйСчётОтклоняетНекорректныйНачальныйБаланс() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradeController.CreditAccount(-1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradeController.CreditAccount(Float.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradeController.CreditAccount(Float.POSITIVE_INFINITY))
        );
    }

    @Test
    void складСтанцииНеМожетБытьВторойСторонойСобственнойСделки() {
        Entity station = createFoodStation(10, 10f);
        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        station.getComponent(MarketComponent.class).buyPrices[Constants.ITEM_FOOD] = 9f;
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);

        boolean purchase = tradeController.buyFromStation(
                station, stationInventory, Constants.ITEM_FOOD, 1, credits);
        boolean sale = tradeController.sellToStation(
                station, stationInventory, Constants.ITEM_FOOD, 1, credits);

        assertFalse(purchase);
        assertFalse(sale);
        assertEquals(10, stationInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(100f, credits.credits);
    }

    @Test
    void расчётЗаполненностиСкладаНеПереполняетЦелоеЧисло() {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = Integer.MAX_VALUE;
        inventory.stock[Constants.ITEM_FOOD] = Integer.MAX_VALUE;
        inventory.stock[Constants.ITEM_ORE] = Integer.MAX_VALUE;

        assertEquals(Integer.MAX_VALUE, inventory.getTotalStock());
        assertEquals(0, inventory.getFreeCapacity());

        inventory.stock[Constants.ITEM_FOOD] = -1;
        inventory.stock[Constants.ITEM_ORE] = 0;
        assertEquals(0, inventory.getFreeCapacity());
    }

    @Test
    void суммаСделкиДолжнаФактическиИзменятьБольшойБаланс() {
        Entity station = createFoodStation(10, 1f);
        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        station.getComponent(MarketComponent.class).buyPrices[Constants.ITEM_FOOD] = 1f;

        TradeController.PlayerProfile player = new TradeController.PlayerProfile();
        player.credits = Float.MAX_VALUE;
        InventoryComponent buyerInventory = new InventoryComponent();
        TradeController.CreditAccount buyerCredits =
                new TradeController.CreditAccount(Float.MAX_VALUE);
        InventoryComponent sellerInventory = new InventoryComponent();
        sellerInventory.stock[Constants.ITEM_FOOD] = 1;
        TradeController.CreditAccount sellerCredits =
                new TradeController.CreditAccount(Float.MAX_VALUE);

        assertFalse(tradeController.buy(station, Constants.ITEM_FOOD, 1, player));
        assertFalse(tradeController.buyFromStation(
                station, buyerInventory, Constants.ITEM_FOOD, 1, buyerCredits));
        assertFalse(tradeController.sellToStation(
                station, sellerInventory, Constants.ITEM_FOOD, 1, sellerCredits));

        assertEquals(10, stationInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0, player.cargo[Constants.ITEM_FOOD]);
        assertEquals(0, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(1, sellerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(Float.MAX_VALUE, player.credits);
        assertEquals(Float.MAX_VALUE, buyerCredits.credits);
        assertEquals(Float.MAX_VALUE, sellerCredits.credits);
    }

    @Test
    void упрощённаяПокупкаОтклоняетПсевдонимСкладскогоМассива() {
        Entity station = createFoodStation(10, 1f);
        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        TradeController.PlayerProfile player = new TradeController.PlayerProfile();
        player.credits = 100f;
        player.cargo = stationInventory.stock;

        assertFalse(tradeController.buy(station, Constants.ITEM_FOOD, 1, player));
        assertEquals(10, stationInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(100f, player.credits);
    }

    private Entity createFoodStation(int stock, float sellPrice) {
        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        station.add(inventory);
        station.add(market);
        return station;
    }
}

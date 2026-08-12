package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeControllerTest {
    private final TradeController tradeController = new TradeController();

    @Test
    void отключенныйТоварИНекорректноеКоличествоНеМеняютСостояние() {
        Entity station = station(10, 10f, 9f, false);
        Entity buyer = participant(0, 100d);
        long stationMoney = wallet(station).getBalanceMilliCredits();
        long buyerMoney = wallet(buyer).getBalanceMilliCredits();

        assertFalse(tradeController.buyFromStation(station, buyer, Constants.ITEM_FOOD, 1));
        assertFalse(tradeController.buyFromStation(station, buyer, Constants.ITEM_FOOD, 0));
        assertFalse(tradeController.buyFromStation(station, buyer, -1, 1));
        assertFalse(tradeController.buyFromStation(
                station, buyer, Constants.MAX_ITEMS, 1));

        assertEquals(10, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(stationMoney, wallet(station).getBalanceMilliCredits());
        assertEquals(buyerMoney, wallet(buyer).getBalanceMilliCredits());
    }

    @Test
    void некорректнаяЦенаОтклоняетсяБезЧастичныхИзменений() {
        Entity station = station(10, Float.NaN, 9f, true);
        Entity buyer = participant(0, 100d);
        long totalMoney = totalMoney(station, buyer);

        assertFalse(tradeController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, 1));

        assertEquals(10, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(totalMoney, totalMoney(station, buyer));
    }

    @Test
    void собственнаяСущностьИОбщиеКомпонентыНеМогутБытьВторойСторонойСделки() {
        Entity station = station(10, 10f, 9f, true);
        assertFalse(tradeController.buyFromStation(
                station, station, Constants.ITEM_FOOD, 1));

        Entity sharedInventoryBuyer = participant(0, 100d);
        sharedInventoryBuyer.remove(InventoryComponent.class);
        sharedInventoryBuyer.add(inventory(station));
        assertFalse(tradeController.buyFromStation(
                station, sharedInventoryBuyer, Constants.ITEM_FOOD, 1));

        Entity sharedWalletBuyer = participant(0, 100d);
        sharedWalletBuyer.remove(WalletComponent.class);
        sharedWalletBuyer.add(wallet(station));
        assertFalse(tradeController.buyFromStation(
                station, sharedWalletBuyer, Constants.ITEM_FOOD, 1));
    }

    @Test
    void отсутствующиеКомпонентыОтклоняютСделку() {
        Entity station = station(10, 10f, 9f, true);
        Entity buyerWithoutWallet = new Entity().add(new InventoryComponent());
        Entity buyerWithoutInventory = new Entity()
                .add(new WalletComponent(Money.fromCredits(100d)));

        assertFalse(tradeController.buyFromStation(
                station, buyerWithoutWallet, Constants.ITEM_FOOD, 1));
        assertFalse(tradeController.buyFromStation(
                station, buyerWithoutInventory, Constants.ITEM_FOOD, 1));

        station.remove(WalletComponent.class);
        assertFalse(tradeController.buyFromStation(
                station, participant(0, 100d), Constants.ITEM_FOOD, 1));
    }

    @Test
    void повреждённыйСкладОтклоняется() {
        Entity station = station(10, 10f, 9f, true);
        Entity buyer = participant(0, 100d);
        inventory(station).stock[Constants.ITEM_ORE] = -1;

        assertFalse(tradeController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, 1));
        assertEquals(10, inventory(station).stock[Constants.ITEM_FOOD]);
    }

    @Test
    void расчётЗаполненностиСкладаНеПереполняетЦелоеЧисло() {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = Integer.MAX_VALUE;
        inventory.stock[Constants.ITEM_FOOD] = Integer.MAX_VALUE;
        inventory.stock[Constants.ITEM_ORE] = Integer.MAX_VALUE;

        assertEquals(Integer.MAX_VALUE, tradeController.getTotalStock(inventory));
        assertEquals(0, tradeController.getFreeCapacity(inventory));
        assertEquals(0, tradeController.getTotalStock(null));
        assertEquals(0, tradeController.getFreeCapacity(null));
    }

    @Test
    void структурноНекорректныйЗапросЦеныВозвращаетNaN() {
        Entity noMarket = new Entity();
        assertTrue(Float.isNaN(tradeController.getEffectiveSellPrice(
                noMarket, Constants.ITEM_FOOD, null)));
        assertTrue(Float.isNaN(tradeController.getEffectiveBuyPrice(
                noMarket, Constants.ITEM_FOOD, null)));
        assertTrue(Float.isNaN(tradeController.getEffectiveSellPrice(
                null, Constants.ITEM_FOOD, null)));
        assertTrue(Float.isNaN(tradeController.getEffectiveBuyPrice(
                station(0, 10f, 9f, true), Constants.MAX_ITEMS, null)));
    }

    @Test
    void успешнаяСделкаПомечаетРынокИИспользуетЕдинственныйWalletApi() {
        Entity station = station(10, 10f, 9f, true);
        Entity buyer = participant(0, 100d);
        MarketComponent market = station.getComponent(MarketComponent.class);
        market.isDirty = false;

        assertTrue(tradeController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, 2));

        assertTrue(market.isDirty);
        assertEquals(Money.fromCredits(80d), wallet(buyer).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(1_020d), wallet(station).getBalanceMilliCredits());
    }

    private Entity station(
            int stock,
            float sellPrice,
            float buyPrice,
            boolean tradable) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = stock;
        MarketComponent market = new MarketComponent();
        if (tradable) {
            market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        }
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        return new Entity()
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(1_000d)));
    }

    private Entity participant(int foodStock, double credits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        return new Entity()
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(credits)));
    }

    private InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }

    private WalletComponent wallet(Entity entity) {
        return entity.getComponent(WalletComponent.class);
    }

    private long totalMoney(Entity first, Entity second) {
        return Math.addExact(
                wallet(first).getBalanceMilliCredits(),
                wallet(second).getBalanceMilliCredits());
    }
}

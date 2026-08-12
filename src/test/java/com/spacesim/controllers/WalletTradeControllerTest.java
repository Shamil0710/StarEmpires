package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletTradeControllerTest {
    @Test
    void покупкаПереноситТоварИДеньгиБезСозданияИУничтожения() {
        EconomicLedger ledger = new EconomicLedger();
        TradeController controller = new TradeController(ledger);
        Entity station = station("Seller", 100, 10f, 9f, 1_000d);
        Entity buyer = participant("Buyer", 0, 500d);

        long moneyBefore = totalMoney(station, buyer);
        int goodsBefore = totalGoods(station, buyer, Constants.ITEM_FOOD);

        assertTrue(controller.buyFromStation(station, buyer, Constants.ITEM_FOOD, 5));

        assertEquals(goodsBefore, totalGoods(station, buyer, Constants.ITEM_FOOD));
        assertEquals(moneyBefore, totalMoney(station, buyer));
        assertEquals(95, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(5, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(Money.fromCredits(450d), wallet(buyer).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(1_050d), wallet(station).getBalanceMilliCredits());
        assertEquals(1, ledger.size());
        EconomicTransaction transaction = ledger.getEntries().get(0);
        assertEquals(EconomicTransaction.Type.TRADE, transaction.type());
        assertEquals("Buyer", transaction.source());
        assertEquals("Seller", transaction.destination());
        assertEquals(Money.fromCredits(50d), transaction.moneyMilliCredits());
    }

    @Test
    void продажаСтанцииТожеСохраняетОбщуюСумму() {
        EconomicLedger ledger = new EconomicLedger();
        TradeController controller = new TradeController(ledger);
        Entity station = station("BuyerStation", 0, 10f, 8f, 1_000d);
        Entity seller = participant("Trader", 20, 100d);

        long moneyBefore = totalMoney(station, seller);
        int goodsBefore = totalGoods(station, seller, Constants.ITEM_FOOD);

        assertTrue(controller.sellToStation(station, seller, Constants.ITEM_FOOD, 10));

        assertEquals(goodsBefore, totalGoods(station, seller, Constants.ITEM_FOOD));
        assertEquals(moneyBefore, totalMoney(station, seller));
        assertEquals(10, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(10, inventory(seller).stock[Constants.ITEM_FOOD]);
        assertEquals(Money.fromCredits(920d), wallet(station).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(180d), wallet(seller).getBalanceMilliCredits());
        EconomicTransaction transaction = ledger.getEntries().get(0);
        assertEquals("BuyerStation", transaction.source());
        assertEquals("Trader", transaction.destination());
        assertEquals(Money.fromCredits(80d), transaction.moneyMilliCredits());
    }

    @Test
    void недостатокДенегУСтанцииДелаетПродажуПолностьюАтомарной() {
        EconomicLedger ledger = new EconomicLedger();
        TradeController controller = new TradeController(ledger);
        Entity station = station("PoorStation", 0, 10f, 8f, 5d);
        Entity seller = participant("Trader", 20, 100d);

        long stationMoney = wallet(station).getBalanceMilliCredits();
        long sellerMoney = wallet(seller).getBalanceMilliCredits();
        int stationStock = inventory(station).stock[Constants.ITEM_FOOD];
        int sellerStock = inventory(seller).stock[Constants.ITEM_FOOD];

        assertFalse(controller.sellToStation(station, seller, Constants.ITEM_FOOD, 10));

        assertEquals(stationMoney, wallet(station).getBalanceMilliCredits());
        assertEquals(sellerMoney, wallet(seller).getBalanceMilliCredits());
        assertEquals(stationStock, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(sellerStock, inventory(seller).stock[Constants.ITEM_FOOD]);
        assertEquals(0, ledger.size());
    }

    @Test
    void общийКошелёкИлиОтсутствующийКошелёкОтклоняются() {
        TradeController controller = new TradeController(new EconomicLedger());
        Entity station = station("Station", 100, 10f, 9f, 1_000d);
        Entity buyer = participant("Buyer", 0, 100d);
        buyer.remove(WalletComponent.class);

        assertFalse(controller.buyFromStation(station, buyer, Constants.ITEM_FOOD, 1));

        WalletComponent shared = wallet(station);
        buyer.add(shared);
        assertFalse(controller.buyFromStation(station, buyer, Constants.ITEM_FOOD, 1));
    }

    private Entity station(
            String name,
            int foodStock,
            float sellPrice,
            float buyPrice,
            double credits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.isDirty = false;
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(credits)));
    }

    private Entity participant(String name, int foodStock, double credits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
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
        return wallet(first).getBalanceMilliCredits() + wallet(second).getBalanceMilliCredits();
    }

    private int totalGoods(Entity first, Entity second, int itemId) {
        return inventory(first).stock[itemId] + inventory(second).stock[itemId];
    }
}

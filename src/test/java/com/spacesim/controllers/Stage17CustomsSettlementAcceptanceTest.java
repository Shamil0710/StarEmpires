package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17CustomsSettlementAcceptanceTest {
    private static final int ITEM_ID = 0;
    private static final long INITIAL_BUYER = 1_000_000L;
    private static final long INITIAL_STATION = 2_000_000L;
    private static final long INITIAL_TREASURY = 3_000_000L;

    @Test
    void foreignPurchaseSplitsBasePriceAndCustomsWithoutCreatingMoney() {
        EconomicLedger ledger = new EconomicLedger();
        WalletComponent treasury = new WalletComponent(INITIAL_TREASURY);
        TradeTransactionPolicy policy = (station, participant, direction, value) ->
                new TradeTransactionPolicy.Charge(
                        value / 10L,
                        treasury,
                        "faction:faction.trade_league:treasury",
                        "customs-tariff");
        TradeController controller = new TradeController(ledger, policy);
        Entity station = station(INITIAL_STATION, 10, 100f, 90f);
        Entity buyer = participant("Foreign buyer", INITIAL_BUYER, 0);

        long totalMoneyBefore = INITIAL_BUYER + INITIAL_STATION + INITIAL_TREASURY;
        assertTrue(controller.buyFromStation(station, buyer, ITEM_ID, 2));

        WalletComponent buyerWallet = buyer.getComponent(WalletComponent.class);
        WalletComponent stationWallet = station.getComponent(WalletComponent.class);
        long baseCost = 200_000L;
        long duty = 20_000L;
        assertEquals(INITIAL_BUYER - baseCost - duty, buyerWallet.getBalanceMilliCredits());
        assertEquals(INITIAL_STATION + baseCost, stationWallet.getBalanceMilliCredits());
        assertEquals(INITIAL_TREASURY + duty, treasury.getBalanceMilliCredits());
        assertEquals(totalMoneyBefore,
                buyerWallet.getBalanceMilliCredits()
                        + stationWallet.getBalanceMilliCredits()
                        + treasury.getBalanceMilliCredits());
        assertEquals(8, station.getComponent(InventoryComponent.class).stock[ITEM_ID]);
        assertEquals(2, buyer.getComponent(InventoryComponent.class).stock[ITEM_ID]);

        assertEquals(2, ledger.size());
        EconomicTransaction trade = ledger.getEntries().get(0);
        assertEquals(EconomicTransaction.Type.TRADE, trade.type());
        assertEquals(baseCost, trade.moneyMilliCredits());
        EconomicTransaction customs = ledger.getEntries().get(1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, customs.type());
        assertEquals("Foreign buyer", customs.source());
        assertEquals("faction:faction.trade_league:treasury", customs.destination());
        assertEquals(duty, customs.moneyMilliCredits());
        assertEquals("customs-tariff", customs.reason());
    }

    @Test
    void foreignSaleWithholdsCustomsFromGrossStationPaymentAndConservesMoney() {
        EconomicLedger ledger = new EconomicLedger();
        WalletComponent treasury = new WalletComponent(INITIAL_TREASURY);
        TradeTransactionPolicy policy = (station, participant, direction, value) ->
                new TradeTransactionPolicy.Charge(
                        value / 10L,
                        treasury,
                        "faction:faction.trade_league:treasury",
                        "customs-tariff");
        TradeController controller = new TradeController(ledger, policy);
        Entity station = station(INITIAL_STATION, 0, 100f, 80f);
        Entity seller = participant("Foreign seller", INITIAL_BUYER, 5);

        long totalMoneyBefore = INITIAL_BUYER + INITIAL_STATION + INITIAL_TREASURY;
        assertTrue(controller.sellToStation(station, seller, ITEM_ID, 2));

        WalletComponent sellerWallet = seller.getComponent(WalletComponent.class);
        WalletComponent stationWallet = station.getComponent(WalletComponent.class);
        long grossRevenue = 160_000L;
        long duty = 16_000L;
        long netRevenue = grossRevenue - duty;
        assertEquals(INITIAL_BUYER + netRevenue, sellerWallet.getBalanceMilliCredits());
        assertEquals(INITIAL_STATION - grossRevenue, stationWallet.getBalanceMilliCredits());
        assertEquals(INITIAL_TREASURY + duty, treasury.getBalanceMilliCredits());
        assertEquals(totalMoneyBefore,
                sellerWallet.getBalanceMilliCredits()
                        + stationWallet.getBalanceMilliCredits()
                        + treasury.getBalanceMilliCredits());
        assertEquals(2, station.getComponent(InventoryComponent.class).stock[ITEM_ID]);
        assertEquals(3, seller.getComponent(InventoryComponent.class).stock[ITEM_ID]);

        assertEquals(2, ledger.size());
        assertEquals(netRevenue, ledger.getEntries().get(0).moneyMilliCredits());
        EconomicTransaction customs = ledger.getEntries().get(1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, customs.type());
        assertEquals("Station", customs.source());
        assertEquals(duty, customs.moneyMilliCredits());
        assertEquals("customs-tariff", customs.reason());
    }

    private static Entity station(long wallet, int stock, float sellPrice, float buyPrice) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        inventory.stock[ITEM_ID] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(ITEM_ID, 50, 0f);
        market.sellPrices[ITEM_ID] = sellPrice;
        market.buyPrices[ITEM_ID] = buyPrice;
        return new Entity()
                .add(inventory)
                .add(market)
                .add(new WalletComponent(wallet))
                .add(new IdentityComponent("Station", IdentityComponent.Kind.STATION));
    }

    private static Entity participant(String name, long wallet, int stock) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        inventory.stock[ITEM_ID] = stock;
        return new Entity()
                .add(inventory)
                .add(new WalletComponent(wallet))
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET));
    }
}

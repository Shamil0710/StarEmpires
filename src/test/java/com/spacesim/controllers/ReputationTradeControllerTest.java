package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReputationTradeControllerTest {
    private final TradeController tradeController = new TradeController();

    @Test
    void положительнаяРепутацияДаётСкидкуПриПокупкеИРастётПослеСделки() {
        Entity station = createStation(Constants.FACTION_TRADE_LEAGUE);
        station.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD] = 20;
        station.getComponent(MarketComponent.class).sellPrices[Constants.ITEM_FOOD] = 100f;
        Entity buyer = participant(100d);
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 100f);

        boolean result = tradeController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, 1, reputation);

        assertTrue(result);
        assertEquals(1, buyer.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD]);
        assertEquals(Money.fromCredits(15d),
                buyer.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(Constants.MAX_REPUTATION,
                reputation.getReputation(Constants.FACTION_TRADE_LEAGUE));
    }

    @Test
    void положительнаяРепутацияДаётБонусПриПродаже() {
        Entity station = createStation(Constants.FACTION_MINERS);
        station.getComponent(MarketComponent.class).buyPrices[Constants.ITEM_STEEL] = 100f;
        Entity seller = participant(0d);
        seller.getComponent(InventoryComponent.class).stock[Constants.ITEM_STEEL] = 1;
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_MINERS, 100f);

        boolean result = tradeController.sellToStation(
                station, seller, Constants.ITEM_STEEL, 1, reputation);

        assertTrue(result);
        assertEquals(Money.fromCredits(115d),
                seller.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(Constants.MAX_REPUTATION,
                reputation.getReputation(Constants.FACTION_MINERS));
    }

    @Test
    void некорректноеИзменениеРепутацииОтклоняется() {
        ReputationComponent reputation = new ReputationComponent();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> reputation.addReputation(Constants.FACTION_MINERS, Float.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> reputation.addReputation(
                                Constants.FACTION_MINERS, Float.POSITIVE_INFINITY)),
                () -> assertEquals(0f, reputation.getReputation(Constants.FACTION_MINERS))
        );
    }

    private Entity createStation(int factionId) {
        Entity station = new Entity();
        station.add(new InventoryComponent());
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.configureTradableItem(Constants.ITEM_STEEL, 100, 0f);
        station.add(market);
        station.add(new FactionComponent(factionId));
        station.add(new WalletComponent(Money.fromCredits(1_000d)));
        return station;
    }

    private Entity participant(double credits) {
        return new Entity()
                .add(new InventoryComponent())
                .add(new WalletComponent(Money.fromCredits(credits)));
    }
}

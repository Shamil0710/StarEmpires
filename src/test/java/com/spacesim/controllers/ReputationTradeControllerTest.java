package com.spacesim.controllers;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReputationTradeControllerTest {
    private final TradeController tradeController = new TradeController();

    @Test
    void положительнаяРепутацияДаётСкидкуПриПокупкеИРастётПослеСделки() {
        Entity station = createStation(Constants.FACTION_TRADE_LEAGUE);
        station.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD] = 20;
        station.getComponent(MarketComponent.class).sellPrices[Constants.ITEM_FOOD] = 100f;

        InventoryComponent buyerInventory = new InventoryComponent();
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 100f);
        TradeController.CreditAccount credits = new TradeController.CreditAccount(100f);

        boolean result = tradeController.buyFromStation(station, buyerInventory, Constants.ITEM_FOOD, 1, credits, reputation);

        assertTrue(result);
        assertEquals(1, buyerInventory.stock[Constants.ITEM_FOOD]);
        assertEquals(15f, credits.credits, 0.001f);
        assertEquals(Constants.MAX_REPUTATION, reputation.getReputation(Constants.FACTION_TRADE_LEAGUE));
    }

    @Test
    void положительнаяРепутацияДаётБонусПриПродаже() {
        Entity station = createStation(Constants.FACTION_MINERS);
        station.getComponent(MarketComponent.class).buyPrices[Constants.ITEM_STEEL] = 100f;

        InventoryComponent sellerInventory = new InventoryComponent();
        sellerInventory.stock[Constants.ITEM_STEEL] = 1;
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_MINERS, 100f);
        TradeController.CreditAccount credits = new TradeController.CreditAccount(0f);

        boolean result = tradeController.sellToStation(station, sellerInventory, Constants.ITEM_STEEL, 1, credits, reputation);

        assertTrue(result);
        assertEquals(115f, credits.credits, 0.001f);
        assertEquals(Constants.MAX_REPUTATION, reputation.getReputation(Constants.FACTION_MINERS));
    }

    private Entity createStation(int factionId) {
        Entity station = new Entity();
        station.add(new InventoryComponent());
        station.add(new MarketComponent());
        station.add(new FactionComponent(factionId));
        return station;
    }
}

package com.spacesim.controllers;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.systems.FactionMarketAccessSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionMarketAccessIntegrationTest {

    @Test
    void authoritativeTradeНеМеняетДеньгиИТоварПриЗапрещённойFaction() {
        EconomicLedger ledger = new EconomicLedger();
        TradeController controller = new TradeController(ledger);
        Entity station = station(10, 0);
        Entity buyer = participant(1);
        FactionMarketAccessComponent access = new FactionMarketAccessComponent()
                .allowUnfactioned(false)
                .setFactionAllowed(0, true)
                .setFactionAllowed(1, false);
        station.add(access);
        long stationMoney = wallet(station).getBalanceMilliCredits();
        long buyerMoney = wallet(buyer).getBalanceMilliCredits();

        assertFalse(controller.buyFromStation(station, buyer, Constants.ITEM_FOOD, 2));
        assertEquals(10, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(stationMoney, wallet(station).getBalanceMilliCredits());
        assertEquals(buyerMoney, wallet(buyer).getBalanceMilliCredits());
        assertEquals(0, ledger.getEntries().size());

        access.setFactionAllowed(1, true);
        assertTrue(controller.buyFromStation(station, buyer, Constants.ITEM_FOOD, 2));
        assertEquals(8, inventory(station).stock[Constants.ITEM_FOOD]);
        assertEquals(2, inventory(buyer).stock[Constants.ITEM_FOOD]);
        assertEquals(1, ledger.getEntries().size());
    }

    @Test
    void postPlannerGateСбрасываетЗапрещённыйPersistentRouteДоMovementTick() {
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        registry.track(engine);

        EntityId stationId = new EntityId(1L);
        Entity station = new Entity()
                .add(new EntityIdComponent(stationId))
                .add(new FactionMarketAccessComponent()
                        .allowUnfactioned(false)
                        .setFactionAllowed(0, true)
                        .setFactionAllowed(1, false));
        TradeAIComponent ai = new TradeAIComponent();
        ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        ai.buyStationId = stationId;
        ai.targetStationId = stationId;
        ai.targetItem = Constants.ITEM_FOOD;
        ai.targetAmount = 5;
        Entity fleet = new Entity()
                .add(new EntityIdComponent(new EntityId(2L)))
                .add(new FactionComponent(1))
                .add(ai);

        engine.addEntity(station);
        engine.addEntity(fleet);
        engine.addSystem(new FactionMarketAccessSystem(registry));
        engine.update(0.1f);

        assertEquals(TradeAIComponent.State.IDLE, ai.state);
        assertNull(ai.buyStationId);
        assertNull(ai.targetStationId);
        assertEquals(-1, ai.targetItem);
        assertEquals(0, ai.targetAmount);
        assertTrue(ai.routeSearchCooldown > 0f);
    }

    private static Entity station(int foodStock, int factionId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.sellPrices[Constants.ITEM_FOOD] = 10f;
        market.buyPrices[Constants.ITEM_FOOD] = 9f;
        return new Entity()
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(1_000d)))
                .add(new FactionComponent(factionId));
    }

    private static Entity participant(int factionId) {
        return new Entity()
                .add(new InventoryComponent())
                .add(new WalletComponent(Money.fromCredits(100d)))
                .add(new FactionComponent(factionId));
    }

    private static InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }

    private static WalletComponent wallet(Entity entity) {
        return entity.getComponent(WalletComponent.class);
    }
}

package com.spacesim.systems;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.ConstructionComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ConstructionSiteFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionSystemIntegrationTest {

    @Test
    void siteПокупаетФизическиеМатериалыИПревращаетсяВСтанциюБезTemplateResources() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        SimulationSession session = SimulationSession.createDemo(0xC057A901L, content);
        int oreId = content.findItem("item.ore").runtimeId();
        int energyId = content.findItem("item.energy").runtimeId();

        Entity site = ConstructionSiteFactory.createStationSite(
                content,
                "station.arsenal",
                "Новый Арсенал",
                700f,
                120f,
                Map.of("item.ore", 10, "item.energy", 5));
        EntityId siteId = session.addEntityWithAllocatedId(site);
        ConstructionComponent construction = site.getComponent(ConstructionComponent.class);
        MarketComponent siteMarket = site.getComponent(MarketComponent.class);
        assertEquals(15, site.getComponent(InventoryComponent.class).capacity);
        assertEquals(10, siteMarket.targetStock[oreId]);
        assertEquals(5, siteMarket.targetStock[energyId]);
        assertEquals(0L, site.getComponent(WalletComponent.class).getBalanceMilliCredits());

        Entity financier = economicActor("Инвестор", 0, Money.fromCredits(10_000d));
        session.addEntityWithAllocatedId(financier);
        long constructionCapital = Money.fromCredits(1_000d);
        assertTrue(financier.getComponent(WalletComponent.class)
                .transferTo(site.getComponent(WalletComponent.class), constructionCapital));
        session.getLedger().recordMoneyTransfer(
                "Инвестор",
                "Новый Арсенал — стройплощадка",
                constructionCapital,
                "construction-financing");

        Entity supplier = economicActor("Материальный транспорт", 100, Money.fromCredits(100d));
        InventoryComponent supplierInventory = supplier.getComponent(InventoryComponent.class);
        supplierInventory.stock[oreId] = 10;
        supplierInventory.stock[energyId] = 5;
        session.addEntityWithAllocatedId(supplier);
        session.getLedger().recordResourceSource("Материальный транспорт", oreId, 10, "construction-test-source");
        session.getLedger().recordResourceSource("Материальный транспорт", energyId, 5, "construction-test-source");

        session.advanceFrame(0.1f);
        assertNotNull(site.getComponent(ConstructionComponent.class));
        assertTrue(site.getComponent(MarketComponent.class).buyPrices[oreId] > 0f);
        assertTrue(site.getComponent(MarketComponent.class).buyPrices[energyId] > 0f);

        TradeController controller = new TradeController(session.getLedger());
        assertTrue(controller.sellToStation(site, supplier, oreId, 10));
        assertTrue(controller.sellToStation(site, supplier, energyId, 5));
        long retainedBalance = site.getComponent(WalletComponent.class).getBalanceMilliCredits();
        assertTrue(construction.isFulfilled(site.getComponent(InventoryComponent.class)));

        session.advanceFrame(0.1f);

        Entity completed = session.getEntityRegistry().require(siteId);
        assertSame(site, completed);
        assertNull(completed.getComponent(ConstructionComponent.class));
        assertEquals("station.arsenal", completed.getComponent(ArchetypeComponent.class).contentId);
        assertEquals("Новый Арсенал", completed.getComponent(IdentityComponent.class).name);
        assertEquals(retainedBalance, completed.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(0, completed.getComponent(InventoryComponent.class).getTotalStock());
        assertNotNull(completed.getComponent(ProductionComponent.class));
        assertEquals(
                content.findFaction("faction.trade_league").runtimeId(),
                completed.getComponent(FactionComponent.class).factionId);

        long constructionSinks = session.getLedger().getEntries().stream()
                .filter(entry -> entry.type() == EconomicTransaction.Type.RESOURCE_SINK)
                .filter(entry -> entry.reason().startsWith("station-construction:"))
                .mapToLong(EconomicTransaction::itemAmount)
                .sum();
        assertEquals(15L, constructionSinks);
        ConstructionSystem system = session.getEngine().getSystem(ConstructionSystem.class);
        assertNotNull(system);
        assertEquals(1L, system.getCompletedStations());
    }

    private static Entity economicActor(String name, int capacity, long money) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(inventory)
                .add(new WalletComponent(money));
    }
}

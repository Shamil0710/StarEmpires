package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.controllers.TradeController;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage16ConstructionMarketAccessAcceptanceTest {
    @Test
    void blockedExternalSupplierCannotSellIntoConstructionDemandOrCreateMoney() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_970L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placement = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placement.x(), placement.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        assertNotNull(site);
        runtime.advanceFrame(0.2f);

        ContentCatalog.ItemDefinition steel = scenario.content().findItem("item.steel");
        assertNotNull(steel);
        Entity supplier = findFactionTrader(session);
        FactionComponent supplierFaction = supplier.getComponent(FactionComponent.class);
        InventoryComponent supplierCargo = supplier.getComponent(InventoryComponent.class);
        WalletComponent supplierWallet = supplier.getComponent(WalletComponent.class);
        InventoryComponent siteInventory = site.getComponent(InventoryComponent.class);
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        supplierCargo.stock[steel.runtimeId()] += 1;
        site.add(new FactionMarketAccessComponent()
                .allowUnfactioned(false)
                .setFactionAllowed(supplierFaction.factionId, false));

        int supplierCargoBefore = supplierCargo.stock[steel.runtimeId()];
        int siteCargoBefore = siteInventory.stock[steel.runtimeId()];
        long supplierMoneyBefore = supplierWallet.getBalanceMilliCredits();
        long siteMoneyBefore = siteWallet.getBalanceMilliCredits();
        int ledgerBefore = session.getLedger().size();

        TradeController controller = new TradeController(session.getLedger());
        assertFalse(controller.sellToStation(site, supplier, steel.runtimeId(), 1));

        assertEquals(supplierCargoBefore, supplierCargo.stock[steel.runtimeId()]);
        assertEquals(siteCargoBefore, siteInventory.stock[steel.runtimeId()]);
        assertEquals(supplierMoneyBefore, supplierWallet.getBalanceMilliCredits());
        assertEquals(siteMoneyBefore, siteWallet.getBalanceMilliCredits());
        assertEquals(ledgerBefore, session.getLedger().size());
        assertEquals(0, project.materials().stream()
                .filter(material -> steel.id().equals(material.itemContentId()))
                .findFirst().orElseThrow().deliveredAmount());
    }

    private static Entity findFactionTrader(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(FactionComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null
                    && entity.getComponent(com.spacesim.components.TradeAIComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Playable test system has no faction trader");
    }

    private static PlayerConstructionPlacementView findValidPlacement(PlayerConstructionService construction) {
        for (float y = 100f; y <= Constants.WORLD_HEIGHT - 100f; y += 100f) {
            for (float x = 100f; x <= Constants.WORLD_WIDTH - 100f; x += 100f) {
                PlayerConstructionPlacementView view = construction.previewPlacement(x, y);
                if (view.allowed()) {
                    return view;
                }
            }
        }
        throw new AssertionError("Playable test world has no valid construction placement");
    }
}

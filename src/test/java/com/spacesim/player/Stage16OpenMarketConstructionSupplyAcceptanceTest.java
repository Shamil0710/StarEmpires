package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16OpenMarketConstructionSupplyAcceptanceTest {
    @Test
    void ordinaryExternalFactionTraderCanProfitablySupplyPlayerConstructionSite() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_501L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState current = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                current,
                100_000_000L,
                current.ownedFleetIds(),
                current.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);

        Entity externalTrader = findExternalCivilianTrader(runtime);
        TransformComponent traderTransform = externalTrader.getComponent(TransformComponent.class);
        InventoryComponent traderInventory = externalTrader.getComponent(InventoryComponent.class);
        WalletComponent traderWallet = externalTrader.getComponent(WalletComponent.class);
        TradeAIComponent trade = externalTrader.getComponent(TradeAIComponent.class);
        FactionComponent traderFaction = externalTrader.getComponent(FactionComponent.class);
        assertNotNull(traderTransform);
        assertNotNull(traderInventory);
        assertNotNull(traderWallet);
        assertNotNull(trade);
        assertNotNull(traderFaction);

        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base",
                traderTransform.position.x + 35f,
                traderTransform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));

        Entity site = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        InventoryComponent siteInventory = site.getComponent(InventoryComponent.class);
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        MarketComponent siteMarket = site.getComponent(MarketComponent.class);
        assertNotNull(siteInventory);
        assertNotNull(siteWallet);
        assertNotNull(siteMarket);
        assertTrue(site.getComponent(FactionComponent.class) == null,
                "Independent player construction site must not masquerade as supplier faction");

        ConstructionMaterialState requirement = project.materials().stream()
                .filter(line -> line.remainingAmount() >= 3)
                .findFirst()
                .orElseThrow();
        ContentCatalog.ItemDefinition item = runtime.content().findItem(requirement.itemContentId());
        int itemId = item.runtimeId();

        Arrays.fill(traderInventory.stock, 0);
        traderInventory.stock[itemId] = 3;
        traderInventory.capacity = Math.max(traderInventory.capacity, 3);
        traderTransform.position.set(project.x() - 35f, project.y());
        traderTransform.velocity.setZero();
        trade.state = TradeAIComponent.State.IDLE;
        trade.resetRoute();
        trade.routeSearchCooldown = 0f;

        runtime.advanceFrame(0.1f);
        assertTrue(siteMarket.buyPrices[itemId] > 0f,
                "Funded construction site must publish a real positive procurement bid");

        long siteMoneyBefore = siteWallet.getBalanceMilliCredits();
        long traderMoneyBefore = traderWallet.getBalanceMilliCredits();
        int siteStockBefore = siteInventory.stock[itemId];
        int traderStockBefore = traderInventory.stock[itemId];

        for (int tick = 0; tick < 2_000 && siteInventory.stock[itemId] == siteStockBefore; tick++) {
            runtime.advanceFrame(0.1f);
        }

        assertTrue(siteInventory.stock[itemId] > siteStockBefore,
                "Generic external trader must physically sell required cargo to the construction market");
        assertTrue(traderInventory.stock[itemId] < traderStockBefore);
        assertTrue(siteWallet.getBalanceMilliCredits() < siteMoneyBefore);
        assertTrue(traderWallet.getBalanceMilliCredits() > traderMoneyBefore);
        assertTrue(traderTransform.position.dst2(project.x(), project.y()) <= 15f * 15f,
                "Trade must complete only after the supplier physically reaches the site");

        ConstructionMaterialState refreshed = runtime.world().findConstructionProject(projectId).orElseThrow()
                .materials().stream()
                .filter(line -> line.itemContentId().equals(item.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(siteInventory.stock[itemId], refreshed.deliveredAmount());
        assertTrue(runtime.player().ownedConstructionProjectIds().contains(projectId));
    }

    private static Entity findExternalCivilianTrader(PlayerRuntime runtime) {
        Integer playerFactionRuntimeId = null;
        if (runtime.player().factionContentId() != null) {
            ContentCatalog.FactionDefinition faction = runtime.content().findFaction(runtime.player().factionContentId());
            playerFactionRuntimeId = faction == null ? null : faction.runtimeId();
        }
        Entity fallback = null;
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !runtime.world().getActiveSystemId().equals(placement.systemId())
                    || runtime.player().ownedFleetIds().contains(placement.id())) {
                continue;
            }
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().find(placement.localEntityId());
            if (entity == null
                    || entity.getComponent(TradeAIComponent.class) == null
                    || entity.getComponent(InventoryComponent.class) == null
                    || entity.getComponent(WalletComponent.class) == null
                    || entity.getComponent(TransformComponent.class) == null
                    || entity.getComponent(FactionComponent.class) == null
                    || entity.getComponent(CombatComponent.class) != null) {
                continue;
            }
            if (fallback == null) {
                fallback = entity;
            }
            if (playerFactionRuntimeId == null
                    || entity.getComponent(FactionComponent.class).factionId != playerFactionRuntimeId) {
                return entity;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new AssertionError("Playable test world has no external civilian TradeAI fleet");
    }
}

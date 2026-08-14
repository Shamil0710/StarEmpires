package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMarketServiceTest {
    @Test
    void manualPurchaseUsesRealCargoStationWalletAndPlayerWallet() {
        MarketFixture fixture = fixture(12_201L);
        dockForTest(fixture);
        PlayerConstructionService construction = new PlayerConstructionService(fixture.runtime);
        ConstructionProjectId projectId = construction.createProject("station.mining_base", 720f, 620f);
        PlayerMarketService marketService = new PlayerMarketService(fixture.runtime, fixture.content);
        int itemId = fixture.item.runtimeId();
        int stationStockBefore = fixture.stationInventory.stock[itemId];
        int cargoBefore = fixture.shipInventory.stock[itemId];
        long playerMoneyBefore = fixture.runtime.player().walletMilliCredits();
        long stationMoneyBefore = fixture.stationWallet.getBalanceMilliCredits();
        long totalMoneyBefore = playerMoneyBefore + stationMoneyBefore;

        PlayerMarketView view = marketService.view().orElseThrow();
        assertTrue(view.marketAccessAllowed());
        assertEquals(playerMoneyBefore, view.walletMilliCredits());
        assertEquals(stationStockBefore,
                view.items().stream().filter(row -> row.runtimeItemId() == itemId).findFirst().orElseThrow().stationStock());

        assertTrue(marketService.buy(fixture.item.id(), 2));

        assertEquals(cargoBefore + 2, fixture.shipInventory.stock[itemId]);
        assertEquals(stationStockBefore - 2, fixture.stationInventory.stock[itemId]);
        assertTrue(fixture.runtime.player().walletMilliCredits() < playerMoneyBefore);
        assertTrue(fixture.stationWallet.getBalanceMilliCredits() > stationMoneyBefore);
        assertEquals(totalMoneyBefore,
                fixture.runtime.player().walletMilliCredits() + fixture.stationWallet.getBalanceMilliCredits());
        assertEquals(List.of(projectId), fixture.runtime.player().ownedConstructionProjectIds());
    }

    @Test
    void deniedFactionAccessRejectsManualTradeWithoutMutation() {
        MarketFixture fixture = fixture(12_202L);
        dockForTest(fixture);
        fixture.station.add(new FactionMarketAccessComponent().allowUnfactioned(false));
        PlayerMarketService marketService = new PlayerMarketService(fixture.runtime, fixture.content);
        int itemId = fixture.item.runtimeId();
        int stationStockBefore = fixture.stationInventory.stock[itemId];
        int cargoBefore = fixture.shipInventory.stock[itemId];
        long playerMoneyBefore = fixture.runtime.player().walletMilliCredits();
        long stationMoneyBefore = fixture.stationWallet.getBalanceMilliCredits();

        assertFalse(marketService.view().orElseThrow().marketAccessAllowed());
        assertFalse(marketService.buy(fixture.item.id(), 1));

        assertEquals(stationStockBefore, fixture.stationInventory.stock[itemId]);
        assertEquals(cargoBefore, fixture.shipInventory.stock[itemId]);
        assertEquals(playerMoneyBefore, fixture.runtime.player().walletMilliCredits());
        assertEquals(stationMoneyBefore, fixture.stationWallet.getBalanceMilliCredits());
    }

    private static MarketFixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(seed);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity ship = session.getEntityRegistry().find(placement.localEntityId());
            ShipComponent shipRole = ship == null ? null : ship.getComponent(ShipComponent.class);
            InventoryComponent shipInventory = ship == null ? null : ship.getComponent(InventoryComponent.class);
            if (shipRole == null || shipInventory == null) {
                continue;
            }
            for (Entity station : session.getEngine().getEntities()) {
                MarketComponent market = station.getComponent(MarketComponent.class);
                InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
                WalletComponent stationWallet = station.getComponent(WalletComponent.class);
                FactionComponent stationFaction = station.getComponent(FactionComponent.class);
                com.spacesim.components.EntityIdComponent stationIdentity =
                        station.getComponent(com.spacesim.components.EntityIdComponent.class);
                if (market == null || stationInventory == null || stationWallet == null
                        || stationFaction == null || stationIdentity == null) {
                    continue;
                }
                for (ContentCatalog.ItemDefinition item : content.getItems()) {
                    int id = item.runtimeId();
                    if (market.isTradable(id)
                            && stationInventory.stock[id] >= 2
                            && shipRole.canPurchaseItem(id)
                            && shipInventory.getFreeCapacity() >= 2) {
                        String factionId = content.findFaction(stationFaction.factionId).id();
                        PlayerState player = new PlayerState(
                                1_000_000_000L,
                                factionId,
                                List.of(new PlayerReputationState(factionId, 20f)),
                                List.of(placement.id()),
                                placement.id(),
                                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                                List.of(),
                                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
                        return new MarketFixture(
                                content,
                                PlayerRuntime.create(world, content, player),
                                placement,
                                ship,
                                shipInventory,
                                station,
                                stationIdentity.id,
                                stationInventory,
                                stationWallet,
                                item);
                    }
                }
            }
        }
        throw new AssertionError("Demo world has no compatible player market fixture");
    }

    private static void dockForTest(MarketFixture fixture) {
        fixture.runtime.advanceFrame(0.1f);
        TransformComponent shipTransform = fixture.ship.getComponent(TransformComponent.class);
        TransformComponent stationTransform = fixture.station.getComponent(TransformComponent.class);
        shipTransform.position.set(stationTransform.position);
        assertTrue(fixture.runtime.dockAt(fixture.stationId));
    }

    private record MarketFixture(
            ContentCatalog content,
            PlayerRuntime runtime,
            FleetPlacementState placement,
            Entity ship,
            InventoryComponent shipInventory,
            Entity station,
            EntityId stationId,
            InventoryComponent stationInventory,
            WalletComponent stationWallet,
            ContentCatalog.ItemDefinition item) {
    }
}

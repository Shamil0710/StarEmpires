package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage12PlayableTradeLoopAcceptanceTest {
    @Test
    void playerOwnsTravelsBuysCarriesAndSellsThroughSharedEconomicCore() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(12_300L);
        TradeSetup setup = findSetup(world, content);
        PlayerState player = new PlayerState(
                20_000_000L,
                setup.factionContentId,
                List.of(new PlayerReputationState(setup.factionContentId, 20f)),
                List.of(setup.fleet.id()),
                setup.fleet.id(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        PlayerMarketService market = new PlayerMarketService(runtime, content);

        Entity sourceStation = driveAndDock(runtime, setup.stationName);
        InventoryComponent sourceInventory = sourceStation.getComponent(InventoryComponent.class);
        WalletComponent sourceWallet = sourceStation.getComponent(WalletComponent.class);
        Entity sourceShip = activeShipEntity(runtime);
        InventoryComponent shipInventory = sourceShip.getComponent(InventoryComponent.class);
        int itemId = setup.item.runtimeId();
        int cargoBefore = shipInventory.stock[itemId];
        int sourceStockBefore = sourceInventory.stock[itemId];
        long playerBeforeBuy = runtime.player().walletMilliCredits();
        long sourceMoneyBefore = sourceWallet.getBalanceMilliCredits();

        PlayerMarketView sourceView = market.view().orElseThrow();
        assertTrue(sourceView.marketAccessAllowed());
        assertTrue(sourceView.items().stream()
                .filter(row -> row.runtimeItemId() == itemId)
                .findFirst().orElseThrow().tradable());
        assertTrue(market.buy(setup.item.id(), 3));
        assertEquals(cargoBefore + 3, shipInventory.stock[itemId]);
        assertEquals(sourceStockBefore - 3, sourceInventory.stock[itemId]);
        assertTrue(runtime.player().walletMilliCredits() < playerBeforeBuy);
        assertTrue(sourceWallet.getBalanceMilliCredits() > sourceMoneyBefore);
        assertEquals(playerBeforeBuy + sourceMoneyBefore,
                runtime.player().walletMilliCredits() + sourceWallet.getBalanceMilliCredits());

        assertTrue(runtime.undock());
        assertTrue(runtime.requestJump(DemoGalaxyFactory.INNER_SYSTEM_ID));
        advanceUntilArrival(runtime, setup.fleet.id(), DemoGalaxyFactory.INNER_SYSTEM_ID);
        assertEquals(setup.fleet.id(), runtime.player().activeFleetId());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, runtime.world().getActiveSystemId());

        Entity destinationStation = driveAndDock(runtime, setup.stationName);
        Entity destinationShip = activeShipEntity(runtime);
        InventoryComponent destinationShipInventory = destinationShip.getComponent(InventoryComponent.class);
        InventoryComponent destinationInventory = destinationStation.getComponent(InventoryComponent.class);
        WalletComponent destinationWallet = destinationStation.getComponent(WalletComponent.class);
        int destinationStockBefore = destinationInventory.stock[itemId];
        int carriedBeforeSale = destinationShipInventory.stock[itemId];
        long playerBeforeSale = runtime.player().walletMilliCredits();
        long destinationMoneyBefore = destinationWallet.getBalanceMilliCredits();

        assertEquals(cargoBefore + 3, carriedBeforeSale);
        PlayerMarketView destinationView = market.view().orElseThrow();
        assertTrue(destinationView.marketAccessAllowed());
        assertEquals(carriedBeforeSale,
                destinationView.items().stream()
                        .filter(row -> row.runtimeItemId() == itemId)
                        .findFirst().orElseThrow().playerCargo());
        assertTrue(market.sell(setup.item.id(), 3));
        assertEquals(carriedBeforeSale - 3, destinationShipInventory.stock[itemId]);
        assertEquals(destinationStockBefore + 3, destinationInventory.stock[itemId]);
        assertTrue(runtime.player().walletMilliCredits() > playerBeforeSale);
        assertTrue(destinationWallet.getBalanceMilliCredits() < destinationMoneyBefore);
        assertEquals(playerBeforeSale + destinationMoneyBefore,
                runtime.player().walletMilliCredits() + destinationWallet.getBalanceMilliCredits());

        PlayableWorldState persisted = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                persisted,
                content,
                DemoGalaxyFactory.INNER_SYSTEM_ID);
        Entity restoredShip = activeShipEntity(restored);
        assertEquals(runtime.player().walletMilliCredits(), restored.player().walletMilliCredits());
        assertEquals(destinationShipInventory.stock[itemId],
                restoredShip.getComponent(InventoryComponent.class).stock[itemId]);
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID,
                restored.activeShipView().orElseThrow().systemId());
        assertTrue(restored.player().reputations().stream()
                .filter(reputation -> reputation.factionContentId().equals(setup.factionContentId))
                .findFirst().orElseThrow().value() > 20f);
    }

    private static TradeSetup findSetup(WorldSimulation world, ContentCatalog content) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(fleet.systemId())) {
                continue;
            }
            Entity ship = session.getEntityRegistry().find(fleet.localEntityId());
            ShipComponent role = ship == null ? null : ship.getComponent(ShipComponent.class);
            InventoryComponent cargo = ship == null ? null : ship.getComponent(InventoryComponent.class);
            if (role == null || cargo == null) {
                continue;
            }
            for (Entity station : session.getEngine().getEntities()) {
                MarketComponent market = station.getComponent(MarketComponent.class);
                InventoryComponent inventory = station.getComponent(InventoryComponent.class);
                FactionComponent faction = station.getComponent(FactionComponent.class);
                IdentityComponent identity = station.getComponent(IdentityComponent.class);
                if (market == null || inventory == null || faction == null || identity == null) {
                    continue;
                }
                Entity destination = findStationByName(
                        world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow(), identity.name);
                if (destination == null || destination.getComponent(MarketComponent.class) == null) {
                    continue;
                }
                MarketComponent destinationMarket = destination.getComponent(MarketComponent.class);
                for (ContentCatalog.ItemDefinition item : content.getItems()) {
                    int id = item.runtimeId();
                    if (role.canPurchaseItem(id)
                            && market.isTradable(id)
                            && destinationMarket.isTradable(id)
                            && inventory.stock[id] >= 3
                            && cargo.getFreeCapacity() >= 3) {
                        return new TradeSetup(
                                fleet,
                                identity.name,
                                content.findFaction(faction.factionId).id(),
                                item);
                    }
                }
            }
        }
        throw new AssertionError("Demo galaxy has no compatible two-system player trade setup");
    }

    private static Entity driveAndDock(PlayerRuntime runtime, String stationName) {
        for (int step = 0; step < 2000; step++) {
            StarSystemId systemId = runtime.activeShipView().orElseThrow().systemId();
            SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
            Entity station = findStationByName(session, stationName);
            if (station == null) {
                throw new AssertionError("Target station not found in active system: " + stationName);
            }
            com.spacesim.components.EntityIdComponent stationId =
                    station.getComponent(com.spacesim.components.EntityIdComponent.class);
            if (runtime.dockAt(stationId.id)) {
                return station;
            }
            PlayerShipView ship = runtime.activeShipView().orElseThrow();
            TransformComponent target = station.getComponent(TransformComponent.class);
            float dx = target.position.x - ship.x();
            float dy = target.position.y - ship.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001f) {
                runtime.stopMovement();
            } else {
                runtime.setMovementIntent(0.25f * dx / length, 0.25f * dy / length);
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Player ship did not reach docking range: " + stationName);
    }

    private static void advanceUntilArrival(
            PlayerRuntime runtime,
            com.spacesim.world.FleetId fleetId,
            StarSystemId destination) {
        for (int step = 0; step < 1000; step++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            if (runtime.world().findFleetJump(fleetId).isEmpty()
                    && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && destination.equals(placement.systemId())) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Player fleet did not complete Stage-10 jump");
    }

    private static Entity activeShipEntity(PlayerRuntime runtime) {
        PlayerShipView view = runtime.activeShipView().orElseThrow();
        return runtime.world().findSession(view.systemId()).orElseThrow()
                .getEntityRegistry().find(view.localEntityId());
    }

    private static Entity findStationByName(SimulationSession session, String stationName) {
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null
                    && identity.kind == IdentityComponent.Kind.STATION
                    && stationName.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                return entity;
            }
        }
        return null;
    }

    private record TradeSetup(
            FleetPlacementState fleet,
            String stationName,
            String factionContentId,
            ContentCatalog.ItemDefinition item) {
    }
}

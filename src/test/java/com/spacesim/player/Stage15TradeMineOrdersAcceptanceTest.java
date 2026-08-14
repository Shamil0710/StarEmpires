package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage15TradeMineOrdersAcceptanceTest {
    @Test
    void delegatedTradePhysicallyBuysJumpsAndSellsWithoutPassiveIncome() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(15_201L);
        PlayerRuntime runtime = scenario.runtime();
        TradeFixture trade = tradeLeagueFixture(runtime, scenario.content());
        initializeOwnershipAndDiscovery(runtime, trade.fleet().id(), List.of(trade.sourceRef(), trade.destinationRef()));

        Entity delegatedShip = entity(runtime, trade.fleet().id());
        InventoryComponent cargo = delegatedShip.getComponent(InventoryComponent.class);
        clearInventory(cargo);
        // One real unit is enough to prove the complete economic loop and avoids reserving market
        // capacity from competing civilian traders. The trade-league hull also isolates this
        // economic acceptance from the separate Stage-15 survival/flee slice.
        cargo.capacity = 1;
        TransformComponent shipTransform = delegatedShip.getComponent(TransformComponent.class);
        TransformComponent sourceTransform = trade.source().getComponent(TransformComponent.class);
        shipTransform.position.set(sourceTransform.position.x - 60f, sourceTransform.position.y);
        shipTransform.velocity.setZero();

        long walletBefore = runtime.player().walletMilliCredits();
        assertTrue(new PlayerFleetOrderService(runtime).issue(PlayerFleetOrderState.trade(
                trade.fleet().id(), trade.sourceRef(), trade.destinationRef(), trade.item().id())));

        boolean bought = false;
        long walletAfterBuy = walletBefore;
        boolean observedTransit = false;
        boolean sold = false;
        for (int step = 0; step < 6000 && !sold; step++) {
            runtime.advanceFrame(0.1f);
            if (runtime.world().findFleetJump(trade.fleet().id()).isPresent()) {
                observedTransit = true;
            }
            Entity current = entityOrNull(runtime, trade.fleet().id());
            if (current == null) {
                continue;
            }
            InventoryComponent currentCargo = current.getComponent(InventoryComponent.class);
            int aboard = currentCargo.stock[trade.item().runtimeId()];
            if (!bought && aboard > 0) {
                bought = true;
                walletAfterBuy = runtime.player().walletMilliCredits();
            }
            FleetPlacementState placement = runtime.world().findFleet(trade.fleet().id()).orElseThrow();
            if (bought
                    && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(placement.systemId())
                    && aboard == 0) {
                sold = true;
            }
        }

        assertTrue(bought, "delegated trader must acquire physical cargo at the source market");
        assertTrue(walletAfterBuy < walletBefore, "buy must debit the shared player/company wallet");
        assertTrue(observedTransit, "inter-system trade must use the persistent jump FSM");
        assertTrue(sold, "the same FleetId must physically deliver and sell its cargo");
        assertTrue(runtime.player().walletMilliCredits() > walletAfterBuy,
                "sale revenue must come from the ordinary destination market transaction");
        assertEquals(FleetOrderType.TRADE,
                new PlayerFleetOrderService(runtime).order(trade.fleet().id()).orElseThrow().type());
    }

    @Test
    void delegatedMinerDepletesFiniteAsteroidThenPhysicallySellsRealCargo() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(15_202L);
        PlayerRuntime runtime = scenario.runtime();
        FleetPlacementState minerFleet = findMiningInactiveFleet(runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        Entity miner = entity(runtime, minerFleet.id());
        MiningComponent mining = miner.getComponent(MiningComponent.class);
        ContentCatalog.ItemDefinition item = scenario.content().getItems().stream()
                .filter(candidate -> candidate.runtimeId() == mining.resourceItem)
                .findFirst().orElseThrow();
        Entity asteroid = asteroidFor(runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, mining.resourceItem);
        Entity market = marketForItem(runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, mining.resourceItem);
        DiscoveredObjectRef asteroidRef = ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, asteroid);
        DiscoveredObjectRef marketRef = ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, market);
        initializeOwnershipAndDiscovery(runtime, minerFleet.id(), List.of(asteroidRef, marketRef));

        InventoryComponent cargo = miner.getComponent(InventoryComponent.class);
        clearInventory(cargo);
        cargo.capacity = 2;
        TransformComponent minerTransform = miner.getComponent(TransformComponent.class);
        TransformComponent asteroidTransform = asteroid.getComponent(TransformComponent.class);
        minerTransform.position.set(
                asteroidTransform.position.x - mining.extractionRange - 30f,
                asteroidTransform.position.y);
        minerTransform.velocity.setZero();

        AsteroidComponent finiteResource = asteroid.getComponent(AsteroidComponent.class);
        long reserveBefore = finiteResource.remainingResource;
        int stationStockBefore = market.getComponent(InventoryComponent.class).stock[mining.resourceItem];
        long walletBefore = runtime.player().walletMilliCredits();
        assertTrue(new PlayerFleetOrderService(runtime).issue(PlayerFleetOrderState.mine(
                minerFleet.id(), asteroidRef, marketRef, item.id())));

        boolean mined = false;
        boolean delivered = false;
        for (int step = 0; step < 5000 && !delivered; step++) {
            runtime.advanceFrame(0.1f);
            Entity currentMiner = entityOrNull(runtime, minerFleet.id());
            if (currentMiner == null) {
                continue;
            }
            InventoryComponent currentCargo = currentMiner.getComponent(InventoryComponent.class);
            if (!mined && currentCargo.stock[mining.resourceItem] > 0) {
                mined = true;
            }
            int stationStock = market.getComponent(InventoryComponent.class).stock[mining.resourceItem];
            if (mined && currentCargo.stock[mining.resourceItem] == 0 && stationStock > stationStockBefore) {
                delivered = true;
            }
        }

        assertTrue(mined, "delegated miner must use the shared finite extraction boundary");
        assertTrue(finiteResource.remainingResource < reserveBefore,
                "physical asteroid reserve must decrease when real ship cargo increases");
        assertTrue(delivered, "full mining cargo must physically reach and sell to the assigned market");
        assertTrue(runtime.player().walletMilliCredits() > walletBefore,
                "mining itself grants no credits; credits must appear only after ordinary sale");
        assertTrue(mining.totalMined > 0L);
        assertTrue(mining.totalDelivered > 0L);
    }

    private static TradeFixture tradeLeagueFixture(PlayerRuntime runtime, ContentCatalog content) {
        ContentCatalog.FactionDefinition tradeLeague = content.findFaction("faction.trade_league");
        if (tradeLeague == null) {
            throw new AssertionError("Trade League faction is required by demo content");
        }
        SimulationSession sourceSession = runtime.world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        SimulationSession destinationSession = runtime.world().findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        FleetId directActive = runtime.player().activeFleetId();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())
                    || placement.id().equals(directActive)) {
                continue;
            }
            Entity ship = sourceSession.getEntityRegistry().find(placement.localEntityId());
            FactionComponent faction = ship == null ? null : ship.getComponent(FactionComponent.class);
            TradeAIComponent trade = ship == null ? null : ship.getComponent(TradeAIComponent.class);
            ShipComponent role = ship == null ? null : ship.getComponent(ShipComponent.class);
            InventoryComponent inventory = ship == null ? null : ship.getComponent(InventoryComponent.class);
            if (faction == null || faction.factionId != tradeLeague.runtimeId()
                    || trade == null || trade.specializedItem < 0 || role == null || inventory == null) {
                continue;
            }
            ContentCatalog.ItemDefinition item = content.getItems().stream()
                    .filter(candidate -> candidate.runtimeId() == trade.specializedItem)
                    .findFirst().orElse(null);
            if (item == null || !role.canPurchaseItem(item.runtimeId())) {
                continue;
            }
            for (Entity source : sourceSession.getEngine().getEntities()) {
                MarketComponent sourceMarket = source.getComponent(MarketComponent.class);
                InventoryComponent sourceInventory = source.getComponent(InventoryComponent.class);
                IdentityComponent identity = source.getComponent(IdentityComponent.class);
                if (sourceMarket == null || sourceInventory == null || identity == null
                        || !sourceMarket.isTradable(item.runtimeId())
                        || sourceInventory.stock[item.runtimeId()] < 1) {
                    continue;
                }
                Entity destination = marketByNameOrNull(destinationSession, identity.name);
                MarketComponent destinationMarket = destination == null
                        ? null : destination.getComponent(MarketComponent.class);
                InventoryComponent destinationInventory = destination == null
                        ? null : destination.getComponent(InventoryComponent.class);
                if (destinationMarket == null || destinationInventory == null
                        || !destinationMarket.isTradable(item.runtimeId())
                        || destinationInventory.getFreeCapacity() < 1) {
                    continue;
                }
                return new TradeFixture(
                        placement,
                        item,
                        source,
                        destination,
                        ref(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, source),
                        ref(DemoGalaxyFactory.INNER_SYSTEM_ID, destination));
            }
        }
        throw new AssertionError("No safe Trade League delegated trade fixture found");
    }

    private static FleetPlacementState findMiningInactiveFleet(PlayerRuntime runtime, StarSystemId systemId) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        FleetId active = runtime.player().activeFleetId();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())
                    || placement.id().equals(active)) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && entity.getComponent(MiningComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                return placement;
            }
        }
        throw new AssertionError("No delegated mining fleet in " + systemId);
    }

    private static Entity asteroidFor(PlayerRuntime runtime, StarSystemId systemId, int resourceItem) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null
                    && asteroid.resourceItem == resourceItem
                    && asteroid.remainingResource > 2L
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("No finite asteroid for delegated miner");
    }

    private static Entity marketForItem(PlayerRuntime runtime, StarSystemId systemId, int itemId) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market != null && inventory != null && market.isTradable(itemId)
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("No market for delegated mining resource");
    }

    private static Entity marketByNameOrNull(SimulationSession session, String name) {
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && name.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        return null;
    }

    private static void initializeOwnershipAndDiscovery(
            PlayerRuntime runtime,
            FleetId delegated,
            List<DiscoveredObjectRef> addedObjects) {
        PlayerState previous = runtime.player();
        List<FleetId> owned = new ArrayList<>(previous.ownedFleetIds());
        if (!owned.contains(delegated)) {
            owned.add(delegated);
        }
        List<StarSystemId> systems = new ArrayList<>(previous.discoveredSystemIds());
        for (DiscoveredObjectRef reference : addedObjects) {
            if (!systems.contains(reference.systemId())) {
                systems.add(reference.systemId());
            }
        }
        List<DiscoveredObjectRef> objects = new ArrayList<>(previous.discoveredObjects());
        for (DiscoveredObjectRef reference : addedObjects) {
            if (!objects.contains(reference)) {
                objects.add(reference);
            }
        }
        runtime.replacePlayerState(new PlayerState(
                previous.walletMilliCredits(),
                previous.factionContentId(),
                previous.reputations(),
                owned,
                previous.activeFleetId(),
                systems,
                objects,
                previous.homeSystemId(),
                previous.dockedAt(),
                previous.fleetOrders()));
    }

    private static DiscoveredObjectRef ref(StarSystemId systemId, Entity entity) {
        EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
        assertNotNull(id);
        return new DiscoveredObjectRef(systemId, id.id);
    }

    private static Entity entity(PlayerRuntime runtime, FleetId fleetId) {
        Entity entity = entityOrNull(runtime, fleetId);
        assertNotNull(entity);
        return entity;
    }

    private static Entity entityOrNull(PlayerRuntime runtime, FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return null;
        }
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
        return session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
    }

    private static void clearInventory(InventoryComponent inventory) {
        for (int index = 0; index < inventory.stock.length; index++) {
            inventory.stock[index] = 0;
        }
    }

    private record TradeFixture(
            FleetPlacementState fleet,
            ContentCatalog.ItemDefinition item,
            Entity source,
            Entity destination,
            DiscoveredObjectRef sourceRef,
            DiscoveredObjectRef destinationRef) {
    }
}

package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
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
        PlayableTestWorldFactory.Route route = scenario.route();
        ContentCatalog.ItemDefinition item = scenario.content().findItem(route.itemContentId());
        FleetPlacementState delegated = findCompatibleInactiveFleet(
                runtime, route.sourceSystem(), item.runtimeId(), false);
        Entity source = marketByName(runtime, route.sourceSystem(), route.sourceStationName());
        Entity destination = marketByName(runtime, route.destinationSystem(), route.destinationStationName());
        DiscoveredObjectRef sourceRef = ref(route.sourceSystem(), source);
        DiscoveredObjectRef destinationRef = ref(route.destinationSystem(), destination);
        initializeOwnershipAndDiscovery(runtime, delegated.id(), List.of(sourceRef, destinationRef));

        Entity delegatedShip = entity(runtime, delegated.id());
        InventoryComponent cargo = delegatedShip.getComponent(InventoryComponent.class);
        clearInventory(cargo);
        // One real unit is sufficient to prove the complete economic loop without reserving
        // destination capacity from competing live NPC traders during the physical trip.
        cargo.capacity = 1;
        TransformComponent shipTransform = delegatedShip.getComponent(TransformComponent.class);
        TransformComponent sourceTransform = source.getComponent(TransformComponent.class);
        shipTransform.position.set(sourceTransform.position.x - 60f, sourceTransform.position.y);
        shipTransform.velocity.setZero();

        long walletBefore = runtime.player().walletMilliCredits();
        assertTrue(new PlayerFleetOrderService(runtime).issue(PlayerFleetOrderState.trade(
                delegated.id(), sourceRef, destinationRef, route.itemContentId())));

        boolean bought = false;
        long walletAfterBuy = walletBefore;
        boolean observedTransit = false;
        boolean sold = false;
        for (int step = 0; step < 6000 && !sold; step++) {
            runtime.advanceFrame(0.1f);
            if (runtime.world().findFleetJump(delegated.id()).isPresent()) {
                observedTransit = true;
            }
            Entity current = entityOrNull(runtime, delegated.id());
            if (current == null) {
                continue;
            }
            InventoryComponent currentCargo = current.getComponent(InventoryComponent.class);
            int aboard = currentCargo.stock[item.runtimeId()];
            if (!bought && aboard > 0) {
                bought = true;
                walletAfterBuy = runtime.player().walletMilliCredits();
            }
            FleetPlacementState placement = runtime.world().findFleet(delegated.id()).orElseThrow();
            if (bought
                    && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && route.destinationSystem().equals(placement.systemId())
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
                new PlayerFleetOrderService(runtime).order(delegated.id()).orElseThrow().type());
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

    private static FleetPlacementState findCompatibleInactiveFleet(
            PlayerRuntime runtime,
            StarSystemId systemId,
            int itemId,
            boolean miningRequired) {
        FleetId active = runtime.player().activeFleetId();
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())
                    || placement.id().equals(active)) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            ShipComponent ship = entity == null ? null : entity.getComponent(ShipComponent.class);
            InventoryComponent inventory = entity == null ? null : entity.getComponent(InventoryComponent.class);
            TransformComponent transform = entity == null ? null : entity.getComponent(TransformComponent.class);
            MiningComponent mining = entity == null ? null : entity.getComponent(MiningComponent.class);
            if (ship != null && inventory != null && transform != null
                    && ship.canPurchaseItem(itemId)
                    && (!miningRequired || mining != null)) {
                return placement;
            }
        }
        throw new AssertionError("No compatible delegated fleet in " + systemId);
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

    private static Entity marketByName(PlayerRuntime runtime, StarSystemId systemId, String name) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            var identity = entity.getComponent(com.spacesim.components.IdentityComponent.class);
            if (identity != null && name.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Market not found: " + name);
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
}

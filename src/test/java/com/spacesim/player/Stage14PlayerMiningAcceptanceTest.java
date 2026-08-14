package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage14PlayerMiningAcceptanceTest {
    @Test
    void playerPhysicallyMinesFiniteOrePersistsItAndOnlyEarnsCreditsByOrdinarySale() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(14_100L);
        FleetPlacementState minerFleet = findMiningFleet(world);
        PlayerState player = new PlayerState(
                Money.fromCredits(1_000d),
                "faction.miners",
                List.of(new PlayerReputationState("faction.miners", 25f)),
                List.of(minerFleet.fleetId()),
                minerFleet.fleetId(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        PlayerMiningService miningService = new PlayerMiningService(runtime);

        runtime.advanceFrame(0.1f);
        Entity ship = activeShip(runtime);
        MiningComponent mining = ship.getComponent(MiningComponent.class);
        InventoryComponent cargo = ship.getComponent(InventoryComponent.class);
        assertNotNull(mining);
        assertEquals(MiningCommandComponent.Status.IDLE, miningService.view().orElseThrow().status());

        Entity asteroid = nearestAsteroid(runtime, ship, mining.resourceItem);
        EntityId asteroidId = asteroid.getComponent(EntityIdComponent.class).id;
        driveIntoMiningRange(runtime, asteroid, mining.extractionRange);
        runtime.stopMovement();
        runtime.advanceFrame(0.1f);

        int cargoBefore = cargo.stock[mining.resourceItem];
        long reserveBefore = asteroid.getComponent(AsteroidComponent.class).remainingResource;
        long walletBefore = runtime.player().walletMilliCredits();
        assertTrue(miningService.selectTarget(asteroidId));
        assertTrue(miningService.setMiningRequested(true));

        advanceUntilCargoIncreases(runtime, miningService, cargoBefore);

        PlayerMiningView activeView = miningService.view().orElseThrow();
        int cargoAfterMining = cargo.stock[mining.resourceItem];
        int minedUnits = cargoAfterMining - cargoBefore;
        long reserveAfterMining = asteroid.getComponent(AsteroidComponent.class).remainingResource;
        assertTrue(minedUnits > 0);
        assertEquals(reserveBefore, reserveAfterMining + minedUnits);
        assertEquals(walletBefore, runtime.player().walletMilliCredits());
        assertTrue(activeView.status() == MiningCommandComponent.Status.MINING
                || activeView.status() == MiningCommandComponent.Status.DEPLETED);
        assertTrue(miningService.setMiningRequested(false));
        runtime.advanceFrame(0.1f);

        PlayableWorldState persisted = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                persisted,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerMiningService restoredMining = new PlayerMiningService(restored);
        Entity restoredShip = activeShip(restored);
        InventoryComponent restoredCargo = restoredShip.getComponent(InventoryComponent.class);
        Entity restoredAsteroid = restored.world()
                .findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().find(asteroidId);

        assertNotNull(restoredAsteroid);
        assertEquals(cargoAfterMining, restoredCargo.stock[mining.resourceItem]);
        assertEquals(reserveAfterMining,
                restoredAsteroid.getComponent(AsteroidComponent.class).remainingResource);
        PlayerMiningView restoredView = restoredMining.view().orElseThrow();
        assertEquals(MiningCommandComponent.Status.IDLE, restoredView.status());
        assertNull(restoredView.targetId());

        restored.advanceFrame(0.5f);
        assertEquals(cargoAfterMining, restoredCargo.stock[mining.resourceItem]);
        assertEquals(reserveAfterMining,
                restoredAsteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(walletBefore, restored.player().walletMilliCredits());

        Entity station = nearestOreMarket(restored, restoredShip, mining.resourceItem);
        driveAndDock(restored, station);
        PlayerMarketService market = new PlayerMarketService(restored, content);
        int stationStockBefore = station.getComponent(InventoryComponent.class).stock[mining.resourceItem];
        long saleWalletBefore = restored.player().walletMilliCredits();
        int shipOreBeforeSale = restoredCargo.stock[mining.resourceItem];

        assertTrue(market.sell("item.ore", 1));
        assertEquals(shipOreBeforeSale - 1, restoredCargo.stock[mining.resourceItem]);
        assertEquals(stationStockBefore + 1,
                station.getComponent(InventoryComponent.class).stock[mining.resourceItem]);
        assertTrue(restored.player().walletMilliCredits() > saleWalletBefore);
    }

    private static FleetPlacementState findMiningFleet(WorldSimulation world) {
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(fleet.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(fleet.localEntityId());
            ShipComponent ship = entity == null ? null : entity.getComponent(ShipComponent.class);
            MiningComponent mining = entity == null ? null : entity.getComponent(MiningComponent.class);
            if (ship != null && ship.type != null && ship.type.isMining() && mining != null) {
                return fleet;
            }
        }
        throw new AssertionError("Demo galaxy has no physical mining FleetId in active system");
    }

    private static Entity nearestAsteroid(PlayerRuntime runtime, Entity ship, int resourceItem) {
        SimulationSession session = runtime.world()
                .findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        Entity nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (Entity entity : session.getEngine().getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (asteroid == null || transform == null
                    || asteroid.resourceItem != resourceItem || asteroid.isDepleted()) {
                continue;
            }
            float distance = shipTransform.position.dst2(transform.position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        if (nearest == null) {
            throw new AssertionError("Asteroid spawner did not produce a compatible finite target");
        }
        return nearest;
    }

    private static void driveIntoMiningRange(
            PlayerRuntime runtime,
            Entity asteroid,
            float extractionRange) {
        TransformComponent target = asteroid.getComponent(TransformComponent.class);
        for (int step = 0; step < 10_000; step++) {
            PlayerShipView ship = runtime.activeShipView().orElseThrow();
            float dx = target.position.x - ship.x();
            float dy = target.position.y - ship.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= extractionRange * 0.9f) {
                return;
            }
            runtime.setMovementIntent(0.1f * dx / distance, 0.1f * dy / distance);
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Player miner did not reach extraction range");
    }

    private static void advanceUntilCargoIncreases(
            PlayerRuntime runtime,
            PlayerMiningService miningService,
            int cargoBefore) {
        for (int step = 0; step < 500; step++) {
            runtime.advanceFrame(0.1f);
            PlayerMiningView view = miningService.view().orElseThrow();
            if (view.cargoUnits() > cargoBefore) {
                return;
            }
            if (view.status() != MiningCommandComponent.Status.MINING) {
                throw new AssertionError("Manual mining stopped before extraction: "
                        + view.status().getDisplayName());
            }
        }
        throw new AssertionError("Manual mining did not produce physical cargo");
    }

    private static Entity nearestOreMarket(PlayerRuntime runtime, Entity ship, int resourceItem) {
        PlayerShipView shipView = runtime.activeShipView().orElseThrow();
        SimulationSession session = runtime.world().findSession(shipView.systemId()).orElseThrow();
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        Entity nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (market == null || inventory == null || wallet == null || transform == null
                    || !market.isTradable(resourceItem)
                    || inventory.getFreeCapacity() <= 0
                    || wallet.getBalanceMilliCredits() <= 0L) {
                continue;
            }
            float distance = shipTransform.position.dst2(transform.position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        if (nearest == null) {
            throw new AssertionError("No solvent ore market is available for the mined cargo");
        }
        return nearest;
    }

    private static void driveAndDock(PlayerRuntime runtime, Entity station) {
        EntityId stationId = station.getComponent(EntityIdComponent.class).id;
        TransformComponent target = station.getComponent(TransformComponent.class);
        for (int step = 0; step < 10_000; step++) {
            if (runtime.dockAt(stationId)) {
                return;
            }
            PlayerShipView ship = runtime.activeShipView().orElseThrow();
            float dx = target.position.x - ship.x();
            float dy = target.position.y - ship.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0001f) {
                runtime.stopMovement();
            } else {
                runtime.setMovementIntent(0.1f * dx / length, 0.1f * dy / length);
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Player miner did not reach an ore market");
    }

    private static Entity activeShip(PlayerRuntime runtime) {
        PlayerShipView view = runtime.activeShipView().orElseThrow();
        return runtime.world().findSession(view.systemId()).orElseThrow()
                .getEntityRegistry().find(view.localEntityId());
    }
}

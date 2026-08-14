package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ArchetypeEntityFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage14FirstHourAcceptanceTest {
    private static final double FIRST_HOUR_SECONDS = 3600d;
    private static final float TICK = 0.1f;

    @Test
    void firstPlayableHourUsesPhysicalTradeMiningProgressionCombatAndPersists() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(14_900L);
        ContentCatalog content = scenario.content();
        PlayerRuntime runtime = scenario.runtime();
        PlayerMarketService market = new PlayerMarketService(runtime, content);
        PlayerMiningService mining = new PlayerMiningService(runtime);
        PlayerShipProgressionService progression = new PlayerShipProgressionService(runtime);
        PlayerFlightService flight = new PlayerFlightService(runtime);
        Stage14TelemetryTracker telemetry = new Stage14TelemetryTracker(runtime);

        PlayableTestWorldFactory.Route route = scenario.route();
        Entity source = marketByName(runtime, route.sourceSystem(), route.sourceStationName());
        driveAndDock(runtime, flight, telemetry, source);

        int tradeUnits = Math.min(
                PlayableTestWorldFactory.RECOMMENDED_TEST_UNITS,
                market.view().orElseThrow().cargoCapacity() - market.view().orElseThrow().cargoUsed());
        long walletBeforeBuy = runtime.player().walletMilliCredits();
        assertTrue(market.buy(route.itemContentId(), tradeUnits));
        telemetry.recordTradeWalletChange(walletBeforeBuy, runtime.player().walletMilliCredits());
        assertTrue(runtime.undock());
        stopAndWait(runtime, flight, telemetry);
        jumpAndWait(runtime, telemetry, route.destinationSystem());

        Entity destination = marketByName(runtime, route.destinationSystem(), route.destinationStationName());
        driveAndDock(runtime, flight, telemetry, destination);
        long walletBeforeSale = runtime.player().walletMilliCredits();
        assertTrue(market.sell(route.itemContentId(), tradeUnits));
        telemetry.recordTradeWalletChange(walletBeforeSale, runtime.player().walletMilliCredits());

        FleetPlacementState minerFleet = findFleet(runtime, route.destinationSystem(), true, false);
        Entity minerSeller = sellerForFleet(runtime, minerFleet);
        if (!runtime.player().dockedAt().entityId().equals(idOf(minerSeller))) {
            assertTrue(runtime.undock());
            driveAndDock(runtime, flight, telemetry, minerSeller);
        }
        PlayerShipSaleOffer minerOffer = new PlayerShipSaleOffer(
                route.destinationSystem(),
                idOf(minerSeller),
                minerFleet.id(),
                Money.fromCredits(4_000d));
        long walletBeforeMiner = runtime.player().walletMilliCredits();
        assertTrue(progression.purchase(minerOffer));
        telemetry.recordShipPurchase(walletBeforeMiner, runtime.player().walletMilliCredits());
        assertTrue(runtime.undock());
        stopAndWait(runtime, flight, telemetry);
        assertTrue(progression.switchActiveFleet(minerFleet.id()));
        stopAndWait(runtime, flight, telemetry);

        Entity minerShip = activeShip(runtime);
        MiningComponent miningComponent = minerShip.getComponent(MiningComponent.class);
        InventoryComponent minerCargo = minerShip.getComponent(InventoryComponent.class);
        assertNotNull(miningComponent);
        Entity asteroid = nearestAsteroid(runtime, miningComponent.resourceItem);
        driveAndStopWithinRange(
                runtime,
                flight,
                telemetry,
                asteroid,
                miningComponent.extractionRange * 0.55f);
        int oreBefore = minerCargo.stock[miningComponent.resourceItem];
        assertTrue(mining.selectTarget(idOf(asteroid)));
        assertTrue(mining.setMiningRequested(true));
        for (int step = 0; step < 600 && minerCargo.stock[miningComponent.resourceItem] < oreBefore + 3; step++) {
            advance(runtime, telemetry, TICK);
        }
        assertTrue(minerCargo.stock[miningComponent.resourceItem] >= oreBefore + 3);
        assertTrue(mining.setMiningRequested(false));
        advance(runtime, telemetry, TICK);
        int minedUnits = minerCargo.stock[miningComponent.resourceItem] - oreBefore;

        Entity oreMarket = nearestSolventMarket(runtime, miningComponent.resourceItem);
        driveAndDock(runtime, flight, telemetry, oreMarket);
        long walletBeforeOreSale = runtime.player().walletMilliCredits();
        assertTrue(market.sell(content.findItem(miningComponent.resourceItem).id(), minedUnits));
        telemetry.recordMiningSaleWalletChange(walletBeforeOreSale, runtime.player().walletMilliCredits());

        FleetPlacementState combatFleet = findFleet(runtime, runtime.activeShipView().orElseThrow().systemId(), false, true);
        Entity combatSeller = sellerForFleet(runtime, combatFleet);
        if (!runtime.player().dockedAt().entityId().equals(idOf(combatSeller))) {
            assertTrue(runtime.undock());
            driveAndDock(runtime, flight, telemetry, combatSeller);
        }
        PlayerShipSaleOffer combatOffer = new PlayerShipSaleOffer(
                combatFleet.systemId(),
                idOf(combatSeller),
                combatFleet.id(),
                Money.fromCredits(4_000d));
        long walletBeforeCombatShip = runtime.player().walletMilliCredits();
        assertTrue(progression.purchase(combatOffer));
        telemetry.recordShipPurchase(walletBeforeCombatShip, runtime.player().walletMilliCredits());
        assertTrue(runtime.undock());
        stopAndWait(runtime, flight, telemetry);
        assertTrue(progression.switchActiveFleet(combatFleet.id()));
        runtime.clearCombatIntent();
        stopAndWait(runtime, flight, telemetry);

        Entity playerCombatShip = activeShip(runtime);
        TransformComponent playerTransform = playerCombatShip.getComponent(TransformComponent.class);
        SimulationSession combatSession = runtime.world()
                .findSession(runtime.activeShipView().orElseThrow().systemId()).orElseThrow();
        Entity hostile = ArchetypeEntityFactory.createCombatShip(
                content,
                "ship.guard_frigate",
                "Stage 14 damaged raider",
                playerTransform.position.x + 80f,
                playerTransform.position.y,
                "faction.miners");
        CombatComponent hostileCombat = hostile.getComponent(CombatComponent.class);
        hostileCombat.shields = 0f;
        hostileCombat.hull = Math.min(63f, hostileCombat.maxHull);
        EntityId hostileId = combatSession.createEntity(hostile);
        assertTrue(runtime.selectCombatTarget(hostileId));
        assertTrue(runtime.setFireIntent(true));
        for (int step = 0; step < 300 && combatSession.getEntityRegistry().find(hostileId) != null; step++) {
            advance(runtime, telemetry, TICK);
        }
        assertEquals(null, combatSession.getEntityRegistry().find(hostileId));
        runtime.clearCombatIntent();
        stopAndWait(runtime, flight, telemetry);

        while (telemetry.report().elapsedSeconds() + 0.0001d < FIRST_HOUR_SECONDS) {
            double remaining = FIRST_HOUR_SECONDS - telemetry.report().elapsedSeconds();
            float frame = (float) Math.min(5d, remaining);
            advance(runtime, telemetry, frame);
        }

        Stage14TelemetryReport report = telemetry.report();
        assertEquals(FIRST_HOUR_SECONDS, report.elapsedSeconds(), 0.11d);
        assertTrue(report.tradeProfitMilliCredits() > 0L);
        assertTrue(report.miningProfitMilliCredits() > 0L);
        assertTrue(report.shipPurchaseCostMilliCredits() >= Money.fromCredits(8_000d));
        assertTrue(report.travelSeconds() > 0d);
        assertTrue(report.miningSeconds() > 0d);
        assertTrue(report.combatSeconds() > 0d);
        assertTrue(report.idleSeconds() > 0d);
        assertTrue(report.averageCargoUtilization() >= 0d);
        assertTrue(report.peakCargoUtilization() > 0d);
        assertEquals(0, report.ownedFleetLosses());
        assertTrue(report.damageTaken() > 0d);
        assertTrue(report.firstProgressionObserved());
        assertTrue(report.secondsToFirstShipProgression() < FIRST_HOUR_SECONDS);
        assertTrue(Double.isFinite(report.creditsPerHour()));
        assertTrue(runtime.player().ownedFleetIds().size() >= 3);

        PlayableWorldState persisted = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        StarSystemId activeSystem = runtime.activeShipView().orElseThrow().systemId();
        PlayerRuntime restored = PlayerRuntime.restore(persisted, content, activeSystem);
        assertEquals(runtime.player().walletMilliCredits(), restored.player().walletMilliCredits());
        assertEquals(runtime.player().ownedFleetIds(), restored.player().ownedFleetIds());
        assertEquals(runtime.player().activeFleetId(), restored.player().activeFleetId());
        PlayerShipView beforeContinuation = restored.activeShipView().orElseThrow();
        assertTrue(restored.setMovementIntent(1f, 0f));
        restored.advanceFrame(TICK);
        PlayerShipView afterContinuation = restored.activeShipView().orElseThrow();
        assertTrue(afterContinuation.x() > beforeContinuation.x());
    }

    private static void advance(
            PlayerRuntime runtime,
            Stage14TelemetryTracker telemetry,
            float realDeltaSeconds) {
        SimulationSession active = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        double before = active.getClock().getSimulationTimeSeconds();
        runtime.advanceFrame(realDeltaSeconds);
        SimulationSession afterSession = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        double after = afterSession.getClock().getSimulationTimeSeconds();
        telemetry.sample(Math.max(0d, after - before));
    }

    private static void stopAndWait(
            PlayerRuntime runtime,
            PlayerFlightService flight,
            Stage14TelemetryTracker telemetry) {
        runtime.stopMovement();
        for (int step = 0; step < 300; step++) {
            PlayerFlightView view = flight.view().orElse(null);
            if (view == null || view.speed() <= 0.01f) {
                return;
            }
            advance(runtime, telemetry, TICK);
        }
        throw new AssertionError("Active ship did not finish physical braking");
    }

    private static void driveAndDock(
            PlayerRuntime runtime,
            PlayerFlightService flight,
            Stage14TelemetryTracker telemetry,
            Entity station) {
        EntityId stationId = idOf(station);
        TransformComponent target = station.getComponent(TransformComponent.class);
        for (int step = 0; step < 20_000; step++) {
            if (runtime.dockAt(stationId)) {
                return;
            }
            PlayerShipView ship = runtime.activeShipView().orElseThrow();
            float dx = target.position.x - ship.x();
            float dy = target.position.y - ship.y();
            float distance = (float) Math.hypot(dx, dy);
            if (distance <= 0.001f) {
                runtime.stopMovement();
            } else {
                float command = Math.max(0.05f, Math.min(1f, distance / 180f));
                runtime.setMovementIntent(command * dx / distance, command * dy / distance);
            }
            advance(runtime, telemetry, TICK);
        }
        throw new AssertionError("Active ship did not reach market through inertial flight");
    }

    private static void driveAndStopWithinRange(
            PlayerRuntime runtime,
            PlayerFlightService flight,
            Stage14TelemetryTracker telemetry,
            Entity targetEntity,
            float desiredRange) {
        TransformComponent target = targetEntity.getComponent(TransformComponent.class);
        for (int step = 0; step < 20_000; step++) {
            PlayerShipView ship = runtime.activeShipView().orElseThrow();
            float dx = target.position.x - ship.x();
            float dy = target.position.y - ship.y();
            float distance = (float) Math.hypot(dx, dy);
            PlayerFlightView currentFlight = flight.view().orElseThrow();
            if (distance <= desiredRange && currentFlight.speed() <= 0.05f) {
                return;
            }
            if (distance <= desiredRange * 0.75f
                    || currentFlight.estimatedStopDistance() + desiredRange >= distance) {
                runtime.stopMovement();
            } else {
                float command = Math.max(0.05f, Math.min(0.35f, distance / 250f));
                runtime.setMovementIntent(command * dx / distance, command * dy / distance);
            }
            advance(runtime, telemetry, TICK);
        }
        throw new AssertionError("Active ship did not settle inside requested physical range");
    }

    private static void jumpAndWait(
            PlayerRuntime runtime,
            Stage14TelemetryTracker telemetry,
            StarSystemId destination) {
        FleetId active = runtime.player().activeFleetId();
        assertNotNull(active);
        assertTrue(runtime.requestJump(destination));
        for (int step = 0; step < 2_000; step++) {
            PlayerShipView ship = runtime.activeShipView().orElse(null);
            if (ship != null && destination.equals(ship.systemId())
                    && runtime.world().findFleetJump(active).isEmpty()) {
                return;
            }
            advance(runtime, telemetry, TICK);
        }
        throw new AssertionError("Stage-10 jump did not complete inside bounded acceptance time");
    }

    private static FleetPlacementState findFleet(
            PlayerRuntime runtime,
            StarSystemId systemId,
            boolean miningRole,
            boolean combatRole) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())
                    || runtime.player().ownedFleetIds().contains(placement.id())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            ShipComponent ship = entity == null ? null : entity.getComponent(ShipComponent.class);
            if (ship != null && ship.type != null
                    && (!miningRole || ship.type.isMining())
                    && (!combatRole || ship.type.isCombat())) {
                return placement;
            }
        }
        throw new AssertionError("Required physical progression FleetId is unavailable");
    }

    private static Entity sellerForFleet(PlayerRuntime runtime, FleetPlacementState fleet) {
        SimulationSession session = runtime.world().findSession(fleet.systemId()).orElseThrow();
        Entity ship = session.getEntityRegistry().find(fleet.localEntityId());
        FactionComponent shipFaction = ship.getComponent(FactionComponent.class);
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null
                    && faction != null
                    && shipFaction != null
                    && faction.factionId == shipFaction.factionId) {
                return entity;
            }
        }
        throw new AssertionError("Fleet has no physical same-faction seller market");
    }

    private static Entity marketByName(PlayerRuntime runtime, StarSystemId systemId, String name) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && name.equals(identity.name)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Expected test-route market not found: " + name);
    }

    private static Entity nearestAsteroid(PlayerRuntime runtime, int resourceItem) {
        PlayerShipView ship = runtime.activeShipView().orElseThrow();
        SimulationSession session = runtime.world().findSession(ship.systemId()).orElseThrow();
        Entity best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (Entity entity : session.getEngine().getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (asteroid == null || transform == null
                    || asteroid.resourceItem != resourceItem || asteroid.isDepleted()) {
                continue;
            }
            float distance = (float) Math.hypot(transform.position.x - ship.x(), transform.position.y - ship.y());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        if (best == null) {
            throw new AssertionError("No compatible finite asteroid exists in active system");
        }
        return best;
    }

    private static Entity nearestSolventMarket(PlayerRuntime runtime, int resourceItem) {
        PlayerShipView ship = runtime.activeShipView().orElseThrow();
        SimulationSession session = runtime.world().findSession(ship.systemId()).orElseThrow();
        Entity best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
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
            float distance = (float) Math.hypot(transform.position.x - ship.x(), transform.position.y - ship.y());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        if (best == null) {
            throw new AssertionError("No solvent market can buy physically mined cargo");
        }
        return best;
    }

    private static Entity activeShip(PlayerRuntime runtime) {
        PlayerShipView view = runtime.activeShipView().orElseThrow();
        return runtime.world().findSession(view.systemId()).orElseThrow()
                .getEntityRegistry().find(view.localEntityId());
    }

    private static EntityId idOf(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}

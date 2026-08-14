package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final deterministic Stage-16 physical player-construction acceptance. */
class Stage16EndToEndAcceptanceTest {
    private static final String PROJECT_ARCHETYPE = "station.mining_base";
    private static final String STEEL_ID = "item.steel";
    private static final String ENERGY_ID = "item.energy";

    @Test
    void completePhysicalLoopCombinesExternalTradeOwnedSupplyRemoteBuildFinanceAndDestruction() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_999L);
        PlayerRuntime runtime = scenario.runtime();
        ContentCatalog content = scenario.content();
        ContentCatalog.ItemDefinition steel = content.findItem(STEEL_ID);
        ContentCatalog.ItemDefinition energy = content.findItem(ENERGY_ID);
        assertNotNull(steel);
        assertNotNull(energy);

        preparePlayerFixture(runtime, steel, energy);
        FleetPlacementState activeFleet = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        FleetPlacementState steelHauler = findFleetByArchetype(
                runtime, activeFleet.systemId(), "ship.steel_hauler", activeFleet.id());
        addOwnedFleet(runtime, steelHauler.id());

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView location = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(PROJECT_ARCHETYPE, location.x(), location.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));
        runtime.advanceFrame(0.2f);

        performExternalEnergyDelivery(runtime, content, projectId, energy);
        physicallyDeliverFixtureUnits(runtime, construction, projectId, STEEL_ID, 1);
        performOwnedSteelSupply(runtime, projectId, steelHauler.id());
        physicallyDeliverRemainingFixtureCargo(runtime, construction, projectId);
        advanceUntilStatus(runtime, projectId, ConstructionProjectStatus.BUILDING, 100);

        ConstructionProjectState building = runtime.world().findConstructionProject(projectId).orElseThrow();
        long buildStartedTick = building.buildStartedTick();
        long buildDurationTicks = building.buildDurationTicks();
        List<ConstructionMaterialState> fulfilledBill = building.materials();
        StarSystemId sourceSystem = building.systemId();
        StarSystemId remoteSystem = scenario.route().otherEnd(sourceSystem);
        assertNotNull(remoteSystem);

        assertTrue(runtime.requestJump(remoteSystem));
        advanceUntilFleetArrives(runtime, activeFleet.id(), remoteSystem, 2_000);
        assertEquals(ConstructionProjectStatus.BUILDING,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());
        assertFalse(sourceSystem.equals(runtime.world().getActiveSystemId()));

        PlayerRuntime restored = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(runtime.snapshot())),
                content,
                remoteSystem);
        ConstructionProjectState restoredBuild = restored.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, restoredBuild.status());
        assertEquals(buildStartedTick, restoredBuild.buildStartedTick());
        assertEquals(buildDurationTicks, restoredBuild.buildDurationTicks());
        assertEquals(fulfilledBill, restoredBuild.materials());

        advanceUntilStatus(restored, projectId, ConstructionProjectStatus.COMPLETED, 4_000);
        ConstructionProjectState completed = restored.world().findConstructionProject(projectId).orElseThrow();
        EntityId stationId = completed.completedStationEntityId();
        assertNotNull(stationId);
        OwnedStationRef ownedStation = new OwnedStationRef(sourceSystem, stationId);
        assertTrue(restored.player().ownedStations().contains(ownedStation));
        assertFalse(restored.player().ownedConstructionProjectIds().contains(projectId));

        assertTrue(restored.requestJump(sourceSystem));
        advanceUntilFleetArrives(restored, activeFleet.id(), sourceSystem, 2_000);
        dockAtCompletedStation(restored, activeFleet.id(), sourceSystem, stationId);
        PlayerStationFinanceService finance = new PlayerStationFinanceService(restored);
        PlayerStationFinanceView beforeFinance = finance.view().orElseThrow();
        long combinedBefore = Math.addExact(
                beforeFinance.playerWalletMilliCredits(), beforeFinance.stationWalletMilliCredits());
        assertTrue(finance.deposit(2_000_000L));
        assertTrue(finance.withdraw(750_000L));
        PlayerStationFinanceView afterFinance = finance.view().orElseThrow();
        assertEquals(combinedBefore,
                Math.addExact(afterFinance.playerWalletMilliCredits(), afterFinance.stationWalletMilliCredits()));
        assertTrue(restored.undock());

        PlayerRuntime finalRuntime = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(restored.snapshot())),
                content,
                sourceSystem);
        Entity persistedStation = finalRuntime.world().findSession(sourceSystem).orElseThrow()
                .getEntityRegistry().find(stationId);
        assertNotNull(persistedStation);
        assertEquals(afterFinance.stationWalletMilliCredits(),
                persistedStation.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(afterFinance.playerWalletMilliCredits(), finalRuntime.player().walletMilliCredits());
        assertTrue(finalRuntime.player().ownedStations().contains(ownedStation));
        long walletBeforeDestruction = finalRuntime.player().walletMilliCredits();

        finalRuntime.world().destroyEntity(sourceSystem, stationId, DestructionPolicy.destroyAll());
        finalRuntime.advanceFrame(0.1f);
        assertFalse(finalRuntime.player().ownedStations().contains(ownedStation));
        assertEquals(walletBeforeDestruction, finalRuntime.player().walletMilliCredits(),
                "ordinary station destruction must not create a refund or replacement grant");
        ConstructionProjectState historical = finalRuntime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, historical.status());
        assertEquals(stationId, historical.completedStationEntityId());
    }

    private static void preparePlayerFixture(
            PlayerRuntime runtime,
            ContentCatalog.ItemDefinition steel,
            ContentCatalog.ItemDefinition energy) {
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial, 100_000_000L, initial.ownedFleetIds(), initial.activeFleetId()));
        FleetPlacementState active = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        InventoryComponent cargo = entity(runtime, active).getComponent(InventoryComponent.class);
        cargo.capacity = Math.max(cargo.capacity, 10_000);
        cargo.stock[steel.runtimeId()] += 120;
        cargo.stock[energy.runtimeId()] += 60;
    }

    private static void addOwnedFleet(PlayerRuntime runtime, FleetId fleetId) {
        PlayerState player = runtime.player();
        List<FleetId> fleets = new ArrayList<>(player.ownedFleetIds());
        fleets.add(fleetId);
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                player, player.walletMilliCredits(), fleets, player.activeFleetId()));
    }

    private static void performExternalEnergyDelivery(
            PlayerRuntime runtime,
            ContentCatalog content,
            ConstructionProjectId projectId,
            ContentCatalog.ItemDefinition energy) {
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        Entity source = findMarketByArchetype(runtime, project.systemId(), "station.power_plant");
        Entity trader = findExternalTraderForItem(
                session, content.findFaction("faction.miners").runtimeId(), energy.runtimeId());
        configureOneUnitPhysicalTrade(trader, source, site, energy.runtimeId());
        int before = delivered(runtime, projectId, ENERGY_ID);
        advanceUntilDeliveredAtLeast(runtime, projectId, ENERGY_ID, before + 1, 8_000);
        assertTrue(delivered(runtime, projectId, ENERGY_ID) > before,
                "external generic TradeAI must physically satisfy part of construction energy demand");
        TradeAIComponent ai = trader.getComponent(TradeAIComponent.class);
        ai.state = TradeAIComponent.State.IDLE;
        ai.resetRoute();
        ai.routeSearchCooldown = Float.MAX_VALUE;
    }

    private static void performOwnedSteelSupply(
            PlayerRuntime runtime,
            ConstructionProjectId projectId,
            FleetId steelHauler) {
        PlayerFleetOrderService orders = new PlayerFleetOrderService(runtime);
        int before = delivered(runtime, projectId, STEEL_ID);
        assertTrue(orders.supplyProject(steelHauler, projectId, STEEL_ID));
        advanceUntilDeliveredAtLeast(runtime, projectId, STEEL_ID, before + 1, 10_000);
        assertTrue(delivered(runtime, projectId, STEEL_ID) > before,
                "owned SUPPLY_PROJECT must physically buy and deliver part of construction steel");
        assertTrue(orders.clear(steelHauler));
    }

    private static void physicallyDeliverFixtureUnits(
            PlayerRuntime runtime,
            PlayerConstructionService construction,
            ConstructionProjectId projectId,
            String itemContentId,
            int amount) {
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        FleetPlacementState active = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity site = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent shipTransform = entity(runtime, active).getComponent(TransformComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        shipTransform.position.set(siteTransform.position);
        shipTransform.velocity.setZero();
        assertEquals(amount, construction.deliverMaterial(projectId, active.id(), itemContentId, amount));
    }

    private static void physicallyDeliverRemainingFixtureCargo(
            PlayerRuntime runtime,
            PlayerConstructionService construction,
            ConstructionProjectId projectId) {
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        if (project.status() == ConstructionProjectStatus.BUILDING
                || project.status() == ConstructionProjectStatus.COMPLETED) {
            return;
        }
        FleetPlacementState active = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity site = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent shipTransform = entity(runtime, active).getComponent(TransformComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        shipTransform.position.set(siteTransform.position);
        shipTransform.velocity.setZero();
        for (ConstructionMaterialState material : project.materials()) {
            int remaining = material.remainingAmount();
            if (remaining > 0) {
                assertEquals(remaining, construction.deliverMaterial(
                        projectId, active.id(), material.itemContentId(), remaining));
            }
        }
    }

    private static void dockAtCompletedStation(
            PlayerRuntime runtime,
            FleetId fleetId,
            StarSystemId systemId,
            EntityId stationId) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        Entity station = session.getEntityRegistry().find(stationId);
        FleetPlacementState fleet = runtime.world().findFleet(fleetId).orElseThrow();
        Entity ship = entity(runtime, fleet);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        TransformComponent stationTransform = station.getComponent(TransformComponent.class);
        shipTransform.position.set(stationTransform.position);
        shipTransform.velocity.setZero();
        assertTrue(runtime.dockAt(stationId));
    }

    private static void configureOneUnitPhysicalTrade(Entity trader, Entity source, Entity destination, int itemId) {
        TradeAIComponent ai = trader.getComponent(TradeAIComponent.class);
        InventoryComponent cargo = trader.getComponent(InventoryComponent.class);
        EntityIdComponent sourceId = source.getComponent(EntityIdComponent.class);
        EntityIdComponent destinationId = destination.getComponent(EntityIdComponent.class);
        TransformComponent traderTransform = trader.getComponent(TransformComponent.class);
        TransformComponent sourceTransform = source.getComponent(TransformComponent.class);
        assertNotNull(ai);
        assertNotNull(cargo);
        assertNotNull(sourceId);
        assertNotNull(destinationId);
        cargo.stock[itemId] = 0;
        ai.buyStationId = sourceId.id;
        ai.sellStationId = destinationId.id;
        ai.targetStationId = sourceId.id;
        ai.targetItem = itemId;
        ai.targetAmount = 1;
        ai.expectedProfitMilliCredits = 1L;
        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        traderTransform.position.set(sourceTransform.position.x - 30f, sourceTransform.position.y);
        traderTransform.velocity.setZero();
    }

    private static Entity findExternalTraderForItem(
            SimulationSession session,
            int excludedFactionId,
            int itemId) {
        for (Entity entity : session.getEngine().getEntities()) {
            TradeAIComponent ai = entity.getComponent(TradeAIComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (ai != null && faction != null && faction.factionId != excludedFactionId
                    && ship != null && ship.canPurchaseItem(itemId)
                    && inventory != null && transform != null) {
                return entity;
            }
        }
        throw new AssertionError("No external trader from another faction can purchase requested item");
    }

    private static Entity findMarketByArchetype(PlayerRuntime runtime, StarSystemId systemId, String archetypeId) {
        for (Entity entity : runtime.world().findSession(systemId).orElseThrow().getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("No market archetype " + archetypeId + " in system " + systemId);
    }

    private static FleetPlacementState findFleetByArchetype(
            PlayerRuntime runtime,
            StarSystemId systemId,
            String archetypeId,
            FleetId excluded) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId()) || placement.id().equals(excluded)) {
                continue;
            }
            Entity candidate = session.getEntityRegistry().find(placement.localEntityId());
            ArchetypeComponent archetype = candidate == null ? null : candidate.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)) {
                return placement;
            }
        }
        throw new AssertionError("No fleet archetype " + archetypeId + " in system " + systemId);
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

    private static int delivered(PlayerRuntime runtime, ConstructionProjectId projectId, String itemContentId) {
        return runtime.world().findConstructionProject(projectId).orElseThrow().materials().stream()
                .filter(material -> itemContentId.equals(material.itemContentId()))
                .findFirst().orElseThrow().deliveredAmount();
    }

    private static void advanceUntilDeliveredAtLeast(
            PlayerRuntime runtime,
            ConstructionProjectId projectId,
            String itemContentId,
            int minimumDelivered,
            int maximumFrames) {
        for (int frame = 0; frame < maximumFrames; frame++) {
            if (delivered(runtime, projectId, itemContentId) >= minimumDelivered) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Construction item " + itemContentId
                + " did not reach delivered amount " + minimumDelivered + " within frame budget");
    }

    private static void advanceUntilStatus(
            PlayerRuntime runtime,
            ConstructionProjectId projectId,
            ConstructionProjectStatus status,
            int maximumFrames) {
        for (int frame = 0; frame < maximumFrames; frame++) {
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status() == status) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Construction project did not reach " + status + " within frame budget");
    }

    private static void advanceUntilFleetArrives(
            PlayerRuntime runtime,
            FleetId fleetId,
            StarSystemId destination,
            int maximumFrames) {
        for (int frame = 0; frame < maximumFrames; frame++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && destination.equals(placement.systemId())) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Fleet did not arrive at " + destination + " within frame budget");
    }

    private static Entity entity(PlayerRuntime runtime, FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
    }
}

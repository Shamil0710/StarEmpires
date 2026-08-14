package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
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
    void completePhysicalPlayerConstructionLoopSurvivesRemoteBuildFinanceAndDestruction() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_999L);
        PlayerRuntime runtime = scenario.runtime();
        ContentCatalog content = scenario.content();
        ContentCatalog.ItemDefinition steel = content.findItem(STEEL_ID);
        ContentCatalog.ItemDefinition energy = content.findItem(ENERGY_ID);
        assertNotNull(steel);
        assertNotNull(energy);

        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        FleetPlacementState activeFleet = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity activeShip = entity(runtime, activeFleet);
        InventoryComponent activeCargo = activeShip.getComponent(InventoryComponent.class);
        activeCargo.capacity = Math.max(activeCargo.capacity, 10_000);
        activeCargo.stock[steel.runtimeId()] += 120;
        activeCargo.stock[energy.runtimeId()] += 60;

        FleetPlacementState energyTanker = findFleetByArchetype(
                runtime,
                activeFleet.systemId(),
                "ship.energy_tanker",
                activeFleet.id());
        PlayerState beforeOwnership = runtime.player();
        List<FleetId> owned = new ArrayList<>(beforeOwnership.ownedFleetIds());
        owned.add(energyTanker.id());
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                beforeOwnership,
                beforeOwnership.walletMilliCredits(),
                owned,
                beforeOwnership.activeFleetId()));

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView validPlacement = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                PROJECT_ARCHETYPE,
                validPlacement.x(),
                validPlacement.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));
        runtime.advanceFrame(0.2f);

        SimulationSession constructionSession = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = constructionSession.getEntityRegistry().find(project.constructionSiteEntityId());
        assertNotNull(site);
        Entity foundry = findMarketByArchetype(runtime, project.systemId(), "station.foundry");
        Entity externalTrader = findTraderOfDifferentFaction(
                constructionSession,
                content.findFaction("faction.miners").runtimeId());
        configureOneUnitPhysicalTrade(
                externalTrader,
                foundry,
                site,
                steel.runtimeId());
        int steelBeforeExternal = delivered(runtime, projectId, STEEL_ID);
        advanceUntilDeliveredAtLeast(runtime, projectId, STEEL_ID, steelBeforeExternal + 1, 8_000);
        int steelAfterExternal = delivered(runtime, projectId, STEEL_ID);
        assertTrue(steelAfterExternal > steelBeforeExternal,
                "ordinary external TradeAI must physically satisfy part of construction steel demand");
        TradeAIComponent externalTrade = externalTrader.getComponent(TradeAIComponent.class);
        externalTrade.state = TradeAIComponent.State.IDLE;
        externalTrade.resetRoute();
        externalTrade.routeSearchCooldown = Float.MAX_VALUE;

        PlayerFleetOrderService fleetOrders = new PlayerFleetOrderService(runtime);
        int energyBeforeOwnedSupply = delivered(runtime, projectId, ENERGY_ID);
        assertTrue(fleetOrders.supplyProject(energyTanker.id(), projectId, ENERGY_ID));
        advanceUntilDeliveredAtLeast(runtime, projectId, ENERGY_ID, energyBeforeOwnedSupply + 1, 10_000);
        int energyAfterOwnedSupply = delivered(runtime, projectId, ENERGY_ID);
        assertTrue(energyAfterOwnedSupply > energyBeforeOwnedSupply,
                "owned SUPPLY_PROJECT must buy and physically deliver part of required energy");
        assertTrue(fleetOrders.clear(energyTanker.id()));

        ConstructionProjectState beforeManual = runtime.world().findConstructionProject(projectId).orElseThrow();
        Entity currentSite = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent siteTransform = currentSite.getComponent(TransformComponent.class);
        FleetPlacementState activeAtSource = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity currentActiveShip = entity(runtime, activeAtSource);
        TransformComponent activeTransform = currentActiveShip.getComponent(TransformComponent.class);
        activeTransform.position.set(siteTransform.position);
        activeTransform.velocity.setZero();
        for (ConstructionMaterialState material : beforeManual.materials()) {
            int remaining = material.remainingAmount();
            if (remaining <= 0) {
                continue;
            }
            assertEquals(remaining, construction.deliverMaterial(
                    projectId,
                    activeAtSource.id(),
                    material.itemContentId(),
                    remaining));
        }
        assertTrue(runtime.world().findConstructionProject(projectId).orElseThrow().materialsFulfilled());
        advanceUntilStatus(runtime, projectId, ConstructionProjectStatus.BUILDING, 100);
        ConstructionProjectState building = runtime.world().findConstructionProject(projectId).orElseThrow();
        long buildStartedTick = building.buildStartedTick();
        long buildDurationTicks = building.buildDurationTicks();
        List<ConstructionMaterialState> completedBill = building.materials();

        StarSystemId remoteSystem = scenario.route().otherEnd(project.systemId());
        assertNotNull(remoteSystem);
        assertTrue(runtime.requestJump(remoteSystem));
        advanceUntilFleetArrives(runtime, activeAtSource.id(), remoteSystem, 2_000);
        assertFalse(project.systemId().equals(runtime.world().getActiveSystemId()));
        assertEquals(ConstructionProjectStatus.BUILDING,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());

        byte[] midBuildSave = PlayableWorldStateCodec.encode(runtime.snapshot());
        PlayerRuntime restored = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(midBuildSave),
                content,
                remoteSystem);
        ConstructionProjectState restoredBuilding = restored.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, restoredBuilding.status());
        assertEquals(buildStartedTick, restoredBuilding.buildStartedTick());
        assertEquals(buildDurationTicks, restoredBuilding.buildDurationTicks());
        assertEquals(completedBill, restoredBuilding.materials());

        advanceUntilStatus(restored, projectId, ConstructionProjectStatus.COMPLETED, 4_000);
        ConstructionProjectState completed = restored.world().findConstructionProject(projectId).orElseThrow();
        EntityId stationId = completed.completedStationEntityId();
        assertNotNull(stationId);
        OwnedStationRef ownedStation = new OwnedStationRef(project.systemId(), stationId);
        assertTrue(restored.player().ownedStations().contains(ownedStation));
        assertFalse(restored.player().ownedConstructionProjectIds().contains(projectId));

        assertTrue(restored.requestJump(project.systemId()));
        advanceUntilFleetArrives(restored, activeAtSource.id(), project.systemId(), 2_000);
        SimulationSession completedSession = restored.world().findSession(project.systemId()).orElseThrow();
        Entity station = completedSession.getEntityRegistry().find(stationId);
        assertNotNull(station);
        FleetPlacementState returnedFleet = restored.world().findFleet(activeAtSource.id()).orElseThrow();
        Entity returnedShip = entity(restored, returnedFleet);
        TransformComponent returnedTransform = returnedShip.getComponent(TransformComponent.class);
        TransformComponent stationTransform = station.getComponent(TransformComponent.class);
        returnedTransform.position.set(stationTransform.position);
        returnedTransform.velocity.setZero();
        assertTrue(restored.dockAt(stationId));

        PlayerStationFinanceService finance = new PlayerStationFinanceService(restored);
        PlayerStationFinanceView financeBefore = finance.view().orElseThrow();
        long combinedMoneyBefore = Math.addExact(
                financeBefore.playerWalletMilliCredits(), financeBefore.stationWalletMilliCredits());
        long deposit = 2_000_000L;
        long withdraw = 750_000L;
        assertTrue(finance.deposit(deposit));
        assertTrue(finance.withdraw(withdraw));
        PlayerStationFinanceView financeAfter = finance.view().orElseThrow();
        assertEquals(combinedMoneyBefore,
                Math.addExact(financeAfter.playerWalletMilliCredits(), financeAfter.stationWalletMilliCredits()));
        assertTrue(restored.undock());

        byte[] postFinanceSave = PlayableWorldStateCodec.encode(restored.snapshot());
        PlayerRuntime finalRuntime = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(postFinanceSave),
                content,
                project.systemId());
        Entity persistedStation = finalRuntime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(stationId);
        assertNotNull(persistedStation);
        assertEquals(financeAfter.stationWalletMilliCredits(),
                persistedStation.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(financeAfter.playerWalletMilliCredits(), finalRuntime.player().walletMilliCredits());
        assertTrue(finalRuntime.player().ownedStations().contains(ownedStation));
        long walletBeforeDestruction = finalRuntime.player().walletMilliCredits();

        finalRuntime.world().destroyEntity(project.systemId(), stationId, DestructionPolicy.destroyAll());
        finalRuntime.advanceFrame(0.1f);

        assertFalse(finalRuntime.player().ownedStations().contains(ownedStation));
        assertEquals(walletBeforeDestruction, finalRuntime.player().walletMilliCredits(),
                "ordinary station destruction must not create a replacement/refund grant");
        ConstructionProjectState historical = finalRuntime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, historical.status());
        assertEquals(stationId, historical.completedStationEntityId());
    }

    private static void configureOneUnitPhysicalTrade(
            Entity trader,
            Entity source,
            Entity destination,
            int itemId) {
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
        assertNotNull(traderTransform);
        assertNotNull(sourceTransform);
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

    private static Entity findTraderOfDifferentFaction(SimulationSession session, int excludedFactionId) {
        for (Entity entity : session.getEngine().getEntities()) {
            TradeAIComponent ai = entity.getComponent(TradeAIComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (ai != null && faction != null && faction.factionId != excludedFactionId
                    && inventory != null && transform != null) {
                return entity;
            }
        }
        throw new AssertionError("No external trader from another faction in construction system");
    }

    private static Entity findMarketByArchetype(
            PlayerRuntime runtime,
            StarSystemId systemId,
            String archetypeId) {
        SimulationSession session = runtime.world().findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
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
                    || !systemId.equals(placement.systemId())
                    || placement.id().equals(excluded)) {
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

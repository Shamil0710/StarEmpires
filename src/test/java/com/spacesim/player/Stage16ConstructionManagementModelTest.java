package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16ConstructionManagementModelTest {
    @Test
    void snapshotReadsLiveFundingMaterialsCancellationSupplyOrdersAndOwnedStationWallet() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_911L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placement = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placement.x(), placement.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        long partialFunding = project.minimumFundingMilliCredits() / 2L;
        assertEquals(partialFunding, construction.fundProject(projectId, partialFunding));
        assertTrue(new PlayerFleetOrderService(runtime).supplyProject(
                runtime.player().activeFleetId(), projectId, "item.steel"));

        Entity existingStation = findStation(runtime, "station.power_plant");
        EntityIdComponent stationId = existingStation.getComponent(EntityIdComponent.class);
        OwnedStationRef stationRef = new OwnedStationRef(runtime.world().getActiveSystemId(), stationId.id);
        PlayerState withStation = runtime.player();
        List<OwnedStationRef> stations = new ArrayList<>(withStation.ownedStations());
        stations.add(stationRef);
        runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                withStation,
                withStation.ownedConstructionProjectIds(),
                stations));

        PlayerConstructionManagementSnapshot snapshot = new PlayerConstructionManagementModel(runtime).capture();
        assertEquals(1, snapshot.projects().size());
        PlayerConstructionProjectView view = snapshot.projects().get(0);
        assertEquals(projectId, view.projectId());
        assertEquals("station.mining_base", view.stationArchetypeContentId());
        assertEquals(partialFunding, view.siteWalletMilliCredits());
        assertEquals(project.minimumFundingMilliCredits() - partialFunding, view.fundingShortfallMilliCredits());
        assertEquals(project.materials().stream().mapToLong(ConstructionMaterialState::requiredAmount).sum(),
                view.totalRequiredUnits());
        assertEquals(0L, view.totalDeliveredUnits());
        assertEquals(view.totalRequiredUnits(), view.totalMissingUnits());
        assertTrue(view.cancellation().allowed());
        assertTrue(view.supplyFleetIds().contains(runtime.player().activeFleetId()));
        assertEquals(0d, view.buildProgress());
        assertEquals(project.buildDurationTicks(), view.remainingBuildTicks());

        PlayerOwnedStationView stationView = snapshot.stations().stream()
                .filter(candidate -> candidate.reference().equals(stationRef))
                .findFirst().orElseThrow();
        assertEquals("station.power_plant", stationView.stationArchetypeContentId());
        assertEquals(existingStation.getComponent(WalletComponent.class).getBalanceMilliCredits(),
                stationView.walletMilliCredits());
        assertTrue(Float.isFinite(stationView.x()));
        assertTrue(Float.isFinite(stationView.y()));
    }

    @Test
    void buildingProgressAndEtaComeFromAuthoritativeTargetSystemTicks() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_912L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placement = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placement.x(), placement.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));

        FleetPlacementState fleet = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity ship = session.getEntityRegistry().find(fleet.localEntityId());
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        InventoryComponent inventory = ship.getComponent(InventoryComponent.class);
        shipTransform.position.set(siteTransform.position);
        shipTransform.velocity.setZero();
        inventory.capacity = Math.max(inventory.capacity, 1000);
        ContentCatalog content = scenario.content();
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = content.findItem(material.itemContentId());
            assertNotNull(item);
            inventory.stock[item.runtimeId()] += material.requiredAmount();
            assertEquals(material.requiredAmount(), construction.deliverMaterial(
                    projectId,
                    fleet.id(),
                    item.id(),
                    material.requiredAmount()));
        }

        for (int step = 0; step < 20; step++) {
            runtime.advanceFrame(0.1f);
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status()
                    == ConstructionProjectStatus.BUILDING) {
                break;
            }
        }
        ConstructionProjectState building = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, building.status());
        runtime.advanceFrame(1.0f);

        PlayerConstructionProjectView view = new PlayerConstructionManagementModel(runtime).capture()
                .projects().stream().filter(candidate -> candidate.projectId().equals(projectId))
                .findFirst().orElseThrow();
        assertTrue(view.elapsedBuildTicks() > 0L);
        assertTrue(view.elapsedBuildTicks() < view.buildDurationTicks());
        assertEquals(view.buildDurationTicks() - view.elapsedBuildTicks(), view.remainingBuildTicks());
        assertTrue(view.buildProgress() > 0d && view.buildProgress() < 1d);
        assertFalse(view.cancellation().allowed());
        assertEquals(PlayerConstructionCancellationRejection.BUILDING, view.cancellation().rejection());
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

    private static Entity findStation(PlayerRuntime runtime, String archetypeId) {
        SimulationSession session = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)
                    && entity.getComponent(EntityIdComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null
                    && entity.getComponent(TransformComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("No station archetype " + archetypeId);
    }
}

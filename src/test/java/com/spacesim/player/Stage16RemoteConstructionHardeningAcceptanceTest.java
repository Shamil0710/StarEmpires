package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16RemoteConstructionHardeningAcceptanceTest {
    @Test
    void remoteMidBuildSaveLoadCompletesAndLaterDestructionRemovesOwnershipWithoutRefund() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_950L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placementView = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placementView.x(), placementView.y());
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));

        FleetPlacementState fleet = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        SimulationSession sourceSession = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity ship = sourceSession.getEntityRegistry().find(fleet.localEntityId());
        Entity site = sourceSession.getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        InventoryComponent inventory = ship.getComponent(InventoryComponent.class);
        shipTransform.position.set(siteTransform.position);
        shipTransform.velocity.setZero();
        inventory.capacity = Math.max(inventory.capacity, 10_000);
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = scenario.content().findItem(material.itemContentId());
            assertNotNull(item);
            inventory.stock[item.runtimeId()] += material.requiredAmount();
            assertEquals(material.requiredAmount(), construction.deliverMaterial(
                    projectId,
                    fleet.id(),
                    item.id(),
                    material.requiredAmount()));
        }

        advanceUntil(runtime, projectId, ConstructionProjectStatus.BUILDING, 100);
        ConstructionProjectState building = runtime.world().findConstructionProject(projectId).orElseThrow();
        long startedTick = building.buildStartedTick();
        long durationTicks = building.buildDurationTicks();
        List<ConstructionMaterialState> deliveredMaterials = building.materials();
        runtime.advanceFrame(0.5f);
        assertEquals(ConstructionProjectStatus.BUILDING,
                runtime.world().findConstructionProject(projectId).orElseThrow().status());

        StarSystemId destination = scenario.route().otherEnd(project.systemId());
        assertNotNull(destination);
        assertTrue(runtime.requestJump(destination));
        advanceUntilFleetArrives(runtime, fleet.id(), destination, 2_000);
        assertEquals(destination, runtime.world().getActiveSystemId());
        assertFalse(project.systemId().equals(runtime.world().getActiveSystemId()),
                "construction system must now be a remote coarse-simulation system");
        ConstructionProjectState remoteBeforeSave = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, remoteBeforeSave.status(),
                "project must remain mid-build after the short physical jump");
        assertEquals(startedTick, remoteBeforeSave.buildStartedTick());
        assertEquals(durationTicks, remoteBeforeSave.buildDurationTicks());
        assertEquals(deliveredMaterials, remoteBeforeSave.materials());

        byte[] save = PlayableWorldStateCodec.encode(runtime.snapshot());
        PlayerRuntime restored = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(save),
                scenario.content(),
                destination);
        ConstructionProjectState afterLoad = restored.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, afterLoad.status());
        assertEquals(startedTick, afterLoad.buildStartedTick());
        assertEquals(durationTicks, afterLoad.buildDurationTicks(),
                "persisted duration must not be recalculated after load");
        assertEquals(deliveredMaterials, afterLoad.materials());
        assertEquals(destination, restored.world().getActiveSystemId());

        advanceUntil(restored, projectId, ConstructionProjectStatus.COMPLETED, 3_000);
        ConstructionProjectState completed = restored.world().findConstructionProject(projectId).orElseThrow();
        assertNotNull(completed.completedStationEntityId());
        SimulationSession remoteSource = restored.world().findSession(project.systemId()).orElseThrow();
        Entity completedStation = remoteSource.getEntityRegistry().find(completed.completedStationEntityId());
        assertNotNull(completedStation);
        assertEquals(IdentityComponent.Kind.STATION,
                completedStation.getComponent(IdentityComponent.class).kind);
        OwnedStationRef ownedStation = new OwnedStationRef(project.systemId(), completed.completedStationEntityId());
        assertTrue(restored.player().ownedStations().contains(ownedStation),
                "remote completion must reconcile into PlayerState ownership");
        assertFalse(restored.player().ownedConstructionProjectIds().contains(projectId));
        long playerWalletBeforeDestruction = restored.player().walletMilliCredits();
        int ownedStationsBefore = restored.player().ownedStations().size();

        restored.world().destroyEntity(
                project.systemId(),
                completed.completedStationEntityId(),
                DestructionPolicy.destroyAll());
        restored.advanceFrame(0.1f);

        assertFalse(remoteSource.getEntityRegistry().contains(completed.completedStationEntityId()));
        assertFalse(restored.player().ownedStations().contains(ownedStation),
                "destroyed physical station must disappear from player ownership on reconciliation");
        assertEquals(ownedStationsBefore - 1, restored.player().ownedStations().size());
        assertEquals(playerWalletBeforeDestruction, restored.player().walletMilliCredits(),
                "station destruction must not grant an automatic player refund");
        assertEquals(ConstructionProjectStatus.COMPLETED,
                restored.world().findConstructionProject(projectId).orElseThrow().status(),
                "completed construction history remains historical after later station destruction");
        assertEquals(completed.completedStationEntityId(),
                restored.world().findConstructionProject(projectId).orElseThrow().completedStationEntityId());
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

    private static void advanceUntil(
            PlayerRuntime runtime,
            ConstructionProjectId projectId,
            ConstructionProjectStatus target,
            int maximumFrames) {
        for (int frame = 0; frame < maximumFrames; frame++) {
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status() == target) {
                return;
            }
            runtime.advanceFrame(0.1f);
        }
        throw new AssertionError("Construction project did not reach " + target + " within frame budget");
    }

    private static void advanceUntilFleetArrives(
            PlayerRuntime runtime,
            com.spacesim.world.FleetId fleetId,
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
        throw new AssertionError("Player fleet did not arrive at remote destination within frame budget");
    }
}

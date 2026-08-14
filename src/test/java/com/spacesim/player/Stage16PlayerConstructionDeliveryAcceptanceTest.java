package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerConstructionDeliveryAcceptanceTest {
    @Test
    void ownedFleetMustPhysicallyBerthBeforeRealCargoCanEnterSite() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_401L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        Entity ship = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship.getComponent(InventoryComponent.class);
        assertNotNull(shipTransform);
        assertNotNull(shipInventory);

        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", shipTransform.position.x, shipTransform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        ConstructionMaterialState requirement = project.materials().get(0);
        ContentCatalog.ItemDefinition item = runtime.content().findItem(requirement.itemContentId());
        Entity site = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        InventoryComponent siteInventory = site.getComponent(InventoryComponent.class);
        int cargoBefore = shipInventory.stock[item.runtimeId()];
        int siteBefore = siteInventory.stock[item.runtimeId()];
        int grant = 6;
        shipInventory.stock[item.runtimeId()] += grant;
        shipTransform.velocity.setZero();

        int delivered = construction.deliverMaterial(projectId, fleetId, item.id(), 4);

        assertEquals(4, delivered);
        assertEquals(cargoBefore + grant - 4, shipInventory.stock[item.runtimeId()]);
        assertEquals(siteBefore + 4, siteInventory.stock[item.runtimeId()]);
        ConstructionMaterialState refreshed = runtime.world().findConstructionProject(projectId).orElseThrow()
                .materials().stream().filter(line -> line.itemContentId().equals(item.id())).findFirst().orElseThrow();
        assertEquals(4, refreshed.deliveredAmount());
        assertTrue(runtime.player().ownedConstructionProjectIds().contains(projectId));
    }

    @Test
    void remoteFastAndNonOwnedSourcesCannotTransferConstructionCargo() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_402L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        Entity ship = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship.getComponent(InventoryComponent.class);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", shipTransform.position.x, shipTransform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        ConstructionMaterialState requirement = project.materials().get(0);
        ContentCatalog.ItemDefinition item = runtime.content().findItem(requirement.itemContentId());
        shipInventory.stock[item.runtimeId()] += 5;
        int stockBefore = shipInventory.stock[item.runtimeId()];

        shipTransform.position.add(100f, 100f);
        shipTransform.velocity.setZero();
        assertEquals(0, construction.deliverMaterial(projectId, fleetId, item.id(), 1));
        assertEquals(stockBefore, shipInventory.stock[item.runtimeId()]);

        shipTransform.position.set(project.x(), project.y());
        shipTransform.velocity.set(1f, 0f);
        assertEquals(0, construction.deliverMaterial(projectId, fleetId, item.id(), 1));
        assertEquals(stockBefore, shipInventory.stock[item.runtimeId()]);

        FleetId nonOwned = runtime.world().getFleetPlacements().stream()
                .filter(candidate -> candidate.locationKind() == FleetLocationKind.IN_SYSTEM)
                .map(FleetPlacementState::id)
                .filter(candidate -> !runtime.player().ownedFleetIds().contains(candidate))
                .findFirst()
                .orElseThrow();
        assertEquals(0, construction.deliverMaterial(projectId, nonOwned, item.id(), 1));
        assertEquals(0, runtime.world().findConstructionProject(projectId).orElseThrow()
                .materials().stream().filter(line -> line.itemContentId().equals(item.id())).findFirst().orElseThrow()
                .deliveredAmount());
    }
}

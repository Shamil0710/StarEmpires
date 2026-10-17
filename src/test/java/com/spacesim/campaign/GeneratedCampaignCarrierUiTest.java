package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ui.GeneratedCampaignCarrierUi;
import com.spacesim.world.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignCarrierUiTest {
    @Test void emptyCampaignDoesNotCreateCarrierAssetsDuringProjection() {
        var c = FoundedCampaignFixture.restore(); var before = c.captureState();
        for (var fleet : c.coordinator().runtime().world().getFleetPlacements())
            assertTrue(GeneratedCampaignCarrierUi.source(c).find(fleet.id()).isEmpty());
        assertEquals(before, c.captureState());
    }

    @Test void onlyAnOwnedExistingCarrierExposesItsRealBaysAndPersistentCraft() throws Exception {
        var c = FoundedCampaignFixture.restore(); var world = c.coordinator().runtime().world();
        var hostCatalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var carrier = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var entity = world.findSession(carrier.systemId()).orElseThrow().getEntityRegistry().require(carrier.localEntityId());
        var authored = hostCatalog.getDemonstratorFits().stream().filter(f -> f.installedModules().stream().anyMatch(m ->
                hostCatalog.findModule(m.moduleId()).family() == com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily.HANGAR_SMALL_CRAFT)).findFirst().orElseThrow();
        var hostFit = ShipEngineeringState.InstalledFit.fromDemonstrator(authored);
        // Explicit installed carrier fixture; generated Stage-21 ships do not have canonical hangars.
        var fitted = new EngineeringComponent(hostFit, new ShipEngineeringRuntime(hostCatalog).initialize(hostFit, ShipEngineeringState.ConsumableState.empty()));
        entity.add(fitted);
        String faction = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        String host = carrier.id().toString();
        var bay = new SmallCraftBayResolver(hostCatalog).resolveShipBays(host, fitted.fit, fitted.instanceState).get(0);
        var craftCatalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var design = craftCatalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(design);
        var id = c.smallCraft().reserveIdentityForCompletedProduction();
        // Explicit produced-craft/bay fixture; ordinary player acquisition is a separate gate.
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, faction, design.id(), fit,
                new ShipEngineeringRuntime(craftCatalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        var assign = SmallCraftHangarRegistry.class.getDeclaredMethod("assign", SmallCraftId.class,
                SmallCraftHangarCapacity.BayDefinition.class, SmallCraftHangarCapacity.OccupancyState.class);
        assign.setAccessible(true); assign.invoke(c.hangars(), id, bay, SmallCraftHangarCapacity.OccupancyState.SERVICING);
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(carrier.id(), host, faction, List.of(id))));
        c.commitPersonalCraftOwnership(List.of(id));
        var p = c.playerState().orElseThrow();
        var field = c.getClass().getDeclaredField("playerState"); field.setAccessible(true);
        field.set(c, new com.spacesim.player.PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), List.of(),
                null, p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), null, List.of(),
                p.threatIntel(), p.ownedConstructionProjectIds(), p.ownedStations()));
        assertTrue(GeneratedCampaignCarrierUi.find(c, carrier.id()).isEmpty());
        field.set(c, p);
        var beforeCraft = com.spacesim.persistence.Stage228SmallCraftPersistenceMapper.capture(c.smallCraft());
        var beforeHangars = com.spacesim.persistence.Stage228HangarPersistenceMapper.capture(c.hangars());
        var beforeFit = fitted.runtimeState;
        var view = GeneratedCampaignCarrierUi.source(c).find(carrier.id()).orElseThrow();
        assertEquals(carrier.id(), view.carrierFleetId());
        assertTrue(view.bays().stream().anyMatch(b -> b.id().equals(bay.id())));
        assertEquals(1, view.craft().size());
        assertEquals(beforeCraft, com.spacesim.persistence.Stage228SmallCraftPersistenceMapper.capture(c.smallCraft()));
        assertEquals(beforeHangars, com.spacesim.persistence.Stage228HangarPersistenceMapper.capture(c.hangars()));
        assertEquals(beforeFit, fitted.runtimeState); assertEquals(p, c.playerState().orElseThrow());
        assertEquals(view, GeneratedCampaignCarrierUi.source(c).find(carrier.id()).orElseThrow());
        entity.add(new EngineeringComponent(new ShipEngineeringState.InstalledFit("hull.unknown.carrier",
                fitted.fit.installedModules()), fitted.runtimeState, fitted.instanceState));
        assertTrue(GeneratedCampaignCarrierUi.source(c).find(carrier.id()).isEmpty(),
                "Known hangar modules cannot authorize an unknown physical host hull");
        entity.add(fitted);
        // Explicit deployed/AI-mission fixture: an opaque track string may coincide with an owned
        // host ID, but that coincidence is not a personal sensor measurement of the track.
        var release = SmallCraftHangarRegistry.class.getDeclaredMethod("release", SmallCraftId.class);
        release.setAccessible(true); release.invoke(c.hangars(), id);
        c.commitMissionState(SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id,
                FleetCommandState.OrderSource.AI, SmallCraftMissionState.MissionType.INTERCEPTION,
                new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.TRACK, carrier.id().toString()),
                world.getAuthoritativeWorldTick(), SmallCraftMissionState.MissionStatus.ACTIVE)));
        var hidden = GeneratedCampaignCarrierUi.source(c).find(carrier.id()).orElseThrow().craft().get(0).mission();
        assertNotNull(hidden); assertFalse(hidden.targetVisible()); assertEquals("", hidden.targetReferenceId());
    }
}

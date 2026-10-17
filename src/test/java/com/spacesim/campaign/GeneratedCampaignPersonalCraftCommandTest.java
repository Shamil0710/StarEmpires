package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.*;
import com.spacesim.ui.GeneratedCampaignCraftUi;
import com.spacesim.world.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPersonalCraftCommandTest {
    @Test void currentOwnedLaunchCancellationIsPureSingleUseAndRetainsThePhysicalCraft() throws Exception {
        var f = fixture(); var c = f.campaign; var before = c.captureState();
        assertEquals(1, GeneratedCampaignCraftUi.actions(c, "craft:" + f.id.value()).size());
        var preview = GeneratedCampaignCraftUi.preview(c, "craft:" + f.id.value(), "craft-cancel-launch");
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        var cancelled = c.submitPlayerFactionCommand(preview); var after = cancelled.captureState();
        assertEquals(before.smallCraft(), after.smallCraft()); assertEquals(before.playerState(), after.playerState());
        assertEquals(before.stage21Runtime(), after.stage21Runtime()); assertEquals(before.localFlights(), after.localFlights());
        assertEquals(SmallCraftMissionState.MissionStatus.CANCELLED, cancelled.missions().requireMission(1).status());
        assertEquals(SmallCraftHangarCapacity.OccupancyState.READY, cancelled.hangars().find(f.id).orElseThrow().state());
        assertTrue(cancelled.flightDeck().queued().isEmpty()); assertTrue(cancelled.flightDeck().active().isEmpty());
        assertEquals(1, after.playerJournal().entries().stream().filter(e -> e.action().equals("SMALL_CRAFT_LAUNCH_CANCELLED")).count());
        assertTrue(GeneratedCampaignCraftUi.actions(cancelled, "craft:" + f.id.value()).isEmpty());
        assertThrows(IllegalStateException.class, () -> c.submitPlayerFactionCommand(preview));
        assertEquals(after, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(after))).captureState());
    }

    @Test void ownershipChangesAndStaleConfirmationCannotCancelForeignWork() throws Exception {
        var f = fixture(); var c = f.campaign; var before = c.captureState();
        assertFalse(c.previewPlayerCraftLaunchCancellation(new SmallCraftId(f.id.value() + 100)).allowed());
        assertTrue(GeneratedCampaignCraftUi.actions(c, "craft:invalid").isEmpty());
        assertEquals(before, c.captureState());
        var stale = c.previewPlayerCraftLaunchCancellation(f.id); assertTrue(stale.allowed());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds(), tick -> Map.of(f.bay.id(), f.bay));
        c.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> c.submitPlayerFactionCommand(stale));
        assertEquals(SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED, c.missions().requireMission(1).status());
        c.commitCarrierWings(List.of()); // Operational association is independent of personal property.
        assertEquals(List.of(f.id), c.personalCraftOwnership());
        var foreign = c.captureState();
        assertTrue(GeneratedCampaignCraftUi.actions(c, "craft:" + f.id.value()).isEmpty());
        assertFalse(c.previewPlayerCraftLaunchCancellation(f.id).allowed());
        assertEquals(foreign, c.captureState());
    }

    @Test void explicitPropertyTransferRevokesAccessWithoutMovingTheCraftOrItsWing() throws Exception {
        var f = fixture(); var c = f.campaign; var before = c.captureState();
        var stale = c.previewPlayerCraftLaunchCancellation(f.id);
        assertTrue(stale.allowed());
        c.commitPersonalCraftOwnership(List.of());
        var after = c.captureState();
        assertEquals(before.smallCraft(), after.smallCraft());
        assertEquals(before.operations(), after.operations());
        assertEquals(before.hangars(), after.hangars());
        assertTrue(com.spacesim.ui.GeneratedCampaignCarrierUi.ownedCraftIds(c).isEmpty());
        assertFalse(c.previewPlayerCraftLaunchCancellation(f.id).allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPlayerFactionCommand(stale));
        assertEquals(after, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(after))).captureState());
        assertThrows(IllegalArgumentException.class, () -> c.commitPersonalCraftOwnership(List.of(new SmallCraftId(999999))));
        assertEquals(after, c.captureState());
    }

    @Test void worldCompositionRetainsExplicitRightsAndRejectsUnallocatedProperty() throws Exception {
        var f = fixture(); var c = f.campaign; var state = c.captureState();
        var h = state.stage21Runtime().stage21HRuntime();
        var runtime = h.stage21GRuntime().stage21FRuntime().stage21ERuntime().stage21DRuntime()
                .stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        var composed = GeneratedCampaignPlayerWorldTransition.composeRuntime(state, runtime, state.playerState());
        assertEquals(state, composed);
        assertEquals(List.of(f.id), Stage228CampaignAuthority.restore(composed).personalCraftOwnership());
        var historicalLoss = assertThrows(AssertionError.class, () -> HistoricalCampaignTimeControlFixture.withoutIdleTimeControl(
                Stage228GeneratedCampaignPersistenceCodec.encode(state)));
        assertTrue(historicalLoss.getMessage().contains("personal craft property rights"));
        assertThrows(IllegalArgumentException.class, () -> new com.spacesim.persistence.Stage228GeneratedCampaignPersistentState(
                state.schemaVersion(), state.runtimeVersion(), state.stage21Runtime(), state.smallCraft(), state.hangars(),
                state.flightDeck(), state.operations(), state.playerState(), state.playerJournal(), state.moduleCustody(),
                state.repairQueue(), state.refitQueue(), state.moduleTransfers(), state.productTransfers(), state.yardConstruction(),
                state.timeSkip(), state.crewManifest(), state.localFlights(), List.of(new SmallCraftId(state.smallCraft().nextCraftId()))));
    }

    private static Fixture fixture() throws Exception {
        var c = FoundedCampaignFixture.restore(); var world = c.coordinator().runtime().world();
        var host = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var entity = world.findSession(host.systemId()).orElseThrow().getEntityRegistry().require(host.localEntityId());
        String owner = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var design = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(design); var id = c.smallCraft().reserveIdentityForCompletedProduction();
        // Explicit produced craft, occupancy and queue isolate command authority, not ordinary carrier acquisition.
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, owner, design.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        var bay = new SmallCraftHangarCapacity.BayDefinition(new SmallCraftHangarCapacity.BayId(host.id().toString(), "explicit-bay"),
                SmallCraftHangarCapacity.HostKind.SHIP, new com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d(200, 100, 80),
                2_000_000, 100_000_000, 1);
        var assign = SmallCraftHangarRegistry.class.getDeclaredMethod("assign", SmallCraftId.class,
                SmallCraftHangarCapacity.BayDefinition.class, SmallCraftHangarCapacity.OccupancyState.class);
        assign.setAccessible(true); assign.invoke(c.hangars(), id, bay, SmallCraftHangarCapacity.OccupancyState.READY);
        var deck = new SmallCraftFlightDeckOperations(c.hangars(), List.of(new SmallCraftFlightDeckOperations.DeckProfile(bay.id(), 1, 1)));
        deck.requestLaunch(id, bay.id(), world.getAuthoritativeWorldTick());
        var field = Stage228CampaignAuthority.class.getDeclaredField("flightDeck"); field.setAccessible(true); field.set(c, deck);
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(host.id(), host.id().toString(), owner, List.of(id))));
        c.commitPersonalCraftOwnership(List.of(id));
        c.commitMissionState(SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id, FleetCommandState.OrderSource.PLAYER,
                SmallCraftMissionState.MissionType.CAP, new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.HOST, host.id().toString()),
                world.getAuthoritativeWorldTick(), SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED)));
        return new Fixture(c, id, bay);
    }

    private record Fixture(Stage228CampaignAuthority campaign, SmallCraftId id, SmallCraftHangarCapacity.BayDefinition bay) { }
}

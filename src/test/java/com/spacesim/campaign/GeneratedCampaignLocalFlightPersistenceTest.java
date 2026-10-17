package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.persistence.*;
import com.spacesim.ship.*;
import com.spacesim.world.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignLocalFlightPersistenceTest {
    @Test void actualWorldIntervalsContinueExactSavedLocalFlightAndPauseDoesNoWork() throws Exception {
        var saved = fixture();
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, c.captureState());
        assertFalse(c.previewPlayerCraftLaunchCancellation(saved.localFlights().flights().get(0).craftId()).allowed(),
                "A deployed individual cannot be erased by cancelling its old launch");
        assertEquals(saved, c.captureState());
        c.coordinator().setPaused(true); var paused = c.captureState();
        assertEquals(0, c.advanceFrame(c.coordinator().session().fixedStepSeconds()).fixedTicks());
        assertEquals(paused, c.captureState());
        var flight = saved.localFlights().flights().get(0);
        c.coordinator().setPaused(false); float step = c.coordinator().session().fixedStepSeconds();
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        var moved = c.localFlightState().flights().get(0);
        assertEquals(flight.physical().position().translated(123 * (double) step, -8 * (double) step), moved.physical().position());
        assertEquals(flight.craftId(), moved.craftId()); assertEquals(flight.hostStableId(), moved.hostStableId());
        var complete = c.captureState();
        assertEquals(complete, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(complete))).captureState());
        var runtime = complete.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        assertEquals(complete, GeneratedCampaignPlayerWorldTransition.compose(complete,
                new com.spacesim.player.PlayableWorldState(com.spacesim.player.PlayableWorldState.CURRENT_VERSION,
                        runtime.worldState(), complete.playerState())), "Personal transitions must retain the adjacent physical-flight owner");
    }

    @Test void genuineV24LayoutGetsNoInferredPositionsAndRelabelledV25CannotClaimFlightPayload() throws Exception {
        var state = fixture(); byte[] current = Stage228GeneratedCampaignPersistenceCodec.encode(state);
        var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(current)); var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
            out.writeInt(24); out.writeInt(24); out.writeUTF("m22.8.generated-campaign.v24");
            HistoricalCampaignContentFixture.copyPayloads(in, out, 16);
            for (int i = 0; i < 3; i++) { int length = in.readInt(); assertEquals(length, in.readNBytes(length).length); }
            HistoricalCampaignContentFixture.skipEmptyBuild(in); assertEquals(-1, in.read());
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(bytes.toByteArray());
        assertEquals(SmallCraftLocalFlightState.empty(), migrated.localFlights());
        assertTrue(migrated.personalCraftOwnership().isEmpty(), "Historical wings do not synthesize personal acquisition rights");
        assertEquals(state.smallCraft(), migrated.smallCraft()); assertEquals(state.operations(), migrated.operations());
        assertEquals(state.stage21Runtime(), migrated.stage21Runtime()); assertEquals(state.playerState(), migrated.playerState());
        assertEquals(migrated, Stage228CampaignAuthority.restore(migrated).captureState());
        byte[] forged = current.clone(); ByteBuffer.wrap(forged).putInt(4, 24).putInt(8, 24);
        byte[] identity = "m22.8.generated-campaign.v24".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(identity, 0, forged, 14, identity.length);
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(forged));
    }

    @Test void committedLossRemovesThePositionAndNotifiesOnceBeforeAnyFurtherWorldTick() throws Exception {
        var c = Stage228CampaignAuthority.restore(fixture());
        var before = c.captureState(); var flight = before.localFlights().flights().get(0);
        // Explicit physical-loss fixture tests immediate composed cleanup, not an ordinary battle.
        var remove = SmallCraftRegistry.class.getDeclaredMethod("removeDestroyedCraft", SmallCraftId.class);
        remove.setAccessible(true); remove.invoke(c.smallCraft(), flight.craftId());
        var failed = c.missions().replace(c.missions().requireMission(flight.missionId()).withStatus(SmallCraftMissionState.MissionStatus.FAILED));
        c.commitMissionState(failed); c.commitMissionState(failed);
        var complete = c.captureState();
        assertTrue(complete.localFlights().flights().isEmpty());
        assertEquals(before.localFlights().lastProcessedTick(), complete.localFlights().lastProcessedTick());
        assertEquals(before.smallCraft().nextCraftId(), complete.smallCraft().nextCraftId());
        assertEquals(before.personalCraftOwnership(), complete.personalCraftOwnership(), "Loss retains the property history without recreating the craft");
        assertEquals(before.playerState(), complete.playerState()); assertEquals(before.stage21Runtime(), complete.stage21Runtime());
        assertEquals(1, complete.playerJournal().entries().stream().filter(e -> e.action().equals("SMALL_CRAFT_LOST")).count());
        assertEquals(complete, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(complete))).captureState());
    }

    @Test void mapUsesOnlyCurrentOwnedPhysicalFlightAndNeverDisclosesItsOpaqueMissionTarget() throws Exception {
        var c = Stage228CampaignAuthority.restore(fixture());
        var flight = c.localFlightState().flights().get(0);
        c.coordinator().runtime().world().activateSystem(flight.systemId());
        var before = c.captureState();
        var source = com.spacesim.ui.GeneratedCampaignCarrierUi.source(c);
        var markers = source.localObjects(flight.systemId());
        assertEquals(1, markers.size()); assertEquals(flight.physical().position(), markers.get(0).position());
        assertFalse(markers.get(0).toString().contains("area.observed"));
        var model = new com.spacesim.ui.GeneratedWorldUiModel(c.coordinator().rootSeed(), c.coordinator().runtime(), c.coordinator().content(),
                source, () -> c.playerState().orElse(null));
        var snapshot = model.capture(); assertTrue(snapshot.localObjects().contains(markers.get(0)));
        var panel = new com.spacesim.ui.ProductionUiProjector(c.playerState().orElseThrow().factionContentId()).capture(c, snapshot);
        assertTrue(panel.rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SHIPS).stream()
                .anyMatch(row -> row.selection().stableId().equals(markers.get(0).stableId())));
        assertEquals(before, c.captureState(), "Reading the map cannot refresh, move or create a craft");
        var other = c.coordinator().runtime().world().snapshot().systems().stream().map(StarSystemSimulationState::systemId)
                .filter(id -> !id.equals(flight.systemId())).findFirst().orElseThrow();
        assertTrue(source.localObjects(other).isEmpty());
        var player = c.playerState().orElseThrow();
        var field = Stage228CampaignAuthority.class.getDeclaredField("playerState"); field.setAccessible(true);
        field.set(c, new com.spacesim.player.PlayerState(player.walletMilliCredits(), player.factionContentId(), player.reputations(),
                List.of(), null, player.discoveredSystemIds(), player.discoveredObjects(), player.homeSystemId(), null,
                List.of(), player.threatIntel(), player.ownedConstructionProjectIds(), player.ownedStations()));
        assertEquals(markers, source.localObjects(flight.systemId()), "Host transfer cannot revoke independent craft property");
        assertTrue(model.capture().localObjects().stream().anyMatch(row -> row.stableId().startsWith("small-craft:")));
        var hidden = new com.spacesim.ui.ProductionUiProjector(player.factionContentId()).capture(c, snapshot);
        assertTrue(hidden.rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SHIPS).stream()
                .anyMatch(row -> row.selection().stableId().startsWith("small-craft:") || row.selection().stableId().startsWith("craft:")));
        field.set(c, player);
        assertEquals(before, c.captureState());
    }

    @Test void queuedRecoveryRetainsExactOutsidePositionAcrossNativeRestoreAndMissingBayTicks() {
        var s = fixture(); var flight = s.localFlights().flights().get(0); var ops = s.operations();
        var original = ops.missions().get(0);
        var pending = new com.spacesim.persistence.Stage228OperationsPersistentState(ops.schemaVersion(), ops.runtimeVersion(),
                ops.semanticContract(), ops.nextMissionId(), List.of(new com.spacesim.persistence.Stage228OperationsPersistentState.MissionState(
                    original.missionId(), original.craftId(), original.source(), original.type(), original.targetKind(),
                    original.targetReferenceId(), original.submittedTick(), SmallCraftMissionState.MissionStatus.RECOVERY_PENDING)),
                ops.pendingDeliveries(), ops.carrierWings());
        var deck = new com.spacesim.persistence.Stage228FlightDeckPersistentState(1,
                com.spacesim.persistence.Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                com.spacesim.persistence.Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT, s.localFlights().lastProcessedTick(),
                List.of(new com.spacesim.persistence.Stage228FlightDeckPersistentState.DeckProfileState(flight.hostStableId(), "explicit-recovery-bay", .2, .2)),
                List.of(new com.spacesim.persistence.Stage228FlightDeckPersistentState.RequestState(flight.craftId(), flight.hostStableId(),
                    "explicit-recovery-bay", com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind.RECOVERY, s.localFlights().lastProcessedTick())), List.of());
        // Explicit queue/gate fixture: no ordinary carrier fit or recovery geometry is manufactured.
        var queued = new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(), s.smallCraft(),
                s.hangars(), deck, pending, s.playerState(), s.playerJournal(), s.moduleCustody(), s.repairQueue(),
                s.refitQueue(), s.moduleTransfers(), s.productTransfers(), s.yardConstruction(), s.timeSkip(), s.crewManifest(), s.localFlights(), s.personalCraftOwnership());
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(queued)));
        assertEquals(queued, c.captureState());
        c.coordinator().setPaused(false); float step = c.coordinator().session().fixedStepSeconds();
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(flight.physical().position().translated(123 * (double) step, -8 * (double) step),
                c.localFlightState().flights().get(0).physical().position());
        assertEquals(SmallCraftMissionState.MissionStatus.RECOVERY_PENDING, c.missions().requireMission(1).status());
        assertTrue(c.hangars().find(flight.craftId()).isEmpty());
        var advanced = c.captureState();
        assertEquals(advanced, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(advanced))).captureState());
        var resumed = Stage228CampaignAuthority.restore(advanced);
        var bay = new com.spacesim.world.SmallCraftHangarCapacity.BayDefinition(
                new com.spacesim.world.SmallCraftHangarCapacity.BayId(flight.hostStableId(), "explicit-recovery-bay"),
                com.spacesim.world.SmallCraftHangarCapacity.HostKind.SHIP,
                new com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d(200, 100, 80), 2_000_000, 100_000_000, 1);
        var consumables = resumed.smallCraft().find(flight.craftId()).orElseThrow().runtimeState().consumables();
        // Only the trusted integration seam supplies this fixture bay; no fitted carrier is claimed.
        resumed.advanceFrame(step, tick -> java.util.Map.of(bay.id(), bay));
        assertTrue(resumed.localFlightState().flights().isEmpty());
        assertEquals(1, resumed.captureState().playerJournal().entries().stream().filter(e -> e.action().equals("SMALL_CRAFT_RECOVERED")).count());
        var inside = resumed.captureState();
        resumed = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(inside)));
        for (int i = 0; i < 10 && resumed.missions().requireMission(1).status() != SmallCraftMissionState.MissionStatus.COMPLETE; i++)
            resumed.advanceFrame(step, tick -> java.util.Map.of(bay.id(), bay));
        assertEquals(SmallCraftMissionState.MissionStatus.COMPLETE, resumed.missions().requireMission(1).status());
        assertEquals(consumables, resumed.smallCraft().find(flight.craftId()).orElseThrow().runtimeState().consumables());
        assertEquals(1, resumed.captureState().playerJournal().entries().stream().filter(e -> e.action().equals("SMALL_CRAFT_RECOVERED")).count());
        var complete = resumed.captureState();
        assertEquals(complete, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(complete))).captureState());
    }

    private static Stage228GeneratedCampaignPersistentState fixture() {
        var c = FoundedCampaignFixture.restore();
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        var w = c.coordinator().runtime().world(); long tick = w.getAuthoritativeWorldTick();
        var carrier = w.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var owner = w.findFactionStableId(w.findSession(carrier.systemId()).orElseThrow().getEntityRegistry()
                .require(carrier.localEntityId()).getComponent(FactionComponent.class).factionId).orElseThrow();
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var definition = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(definition);
        var id = c.smallCraft().reserveIdentityForCompletedProduction();
        // Explicit produced/deployed craft fixture isolates composed persistence and world ticking.
        // It is not evidence of ordinary carrier or craft acquisition, or authored launch-mouth geometry.
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, owner, definition.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        c.commitMissionState(SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id,
                FleetCommandState.OrderSource.PLAYER, SmallCraftMissionState.MissionType.CAP,
                new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.AREA, "area.observed"), tick,
                SmallCraftMissionState.MissionStatus.ACTIVE)));
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(carrier.id(), carrier.id().toString(), owner, List.of(id))));
        c.commitPersonalCraftOwnership(List.of(id));
        var s = c.captureState();
        var flights = new SmallCraftLocalFlightState(tick, List.of(new SmallCraftLocalFlightState.Flight(id, carrier.systemId(),
                carrier.id().toString(), 1, new LocalPhysicalKinematics(new LocalPhysicalPosition(4_000_000_000L, -9_000_000_000L, 25, -72), 123, -8), tick)));
        return new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(), s.smallCraft(),
                s.hangars(), s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(), s.moduleCustody(), s.repairQueue(),
                s.refitQueue(), s.moduleTransfers(), s.productTransfers(), s.yardConstruction(), s.timeSkip(), s.crewManifest(), flights, s.personalCraftOwnership());
    }
}

package com.spacesim.campaign;

import com.spacesim.persistence.*;
import com.spacesim.ui.*;
import com.spacesim.world.*;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignLocalGuardTest {
    static Stage228CampaignAuthority ready() {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        c = c.submitPlayerFactionFoundation(c.previewPlayerFactionFoundation("faction.guard", "Охрана"));
        c = c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());
        var fleet = c.playerState().orElseThrow().activeFleetId();
        var system = c.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId();
        c = c.submitPlayerFactionCommand(c.previewPlayerCommandGroup("Местная группа", List.of(fleet), system));
        c.coordinator().setPaused(true); return c;
    }
    static long group(Stage228CampaignAuthority c) {
        return c.coordinator().commands().groups().stream().filter(g -> !g.retired()).findFirst().orElseThrow().id();
    }
    static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) {
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState()); return restored;
    }
    static void step(Stage228CampaignAuthority c) {
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
    }
    static Stage228CampaignAuthority withCommands(Stage228CampaignAuthority c, FleetCommandState commands,
                                                StrategicOperationState operations, Stage23FleetCrewManifest crew) {
        var r = c.coordinator(); var s = c.captureState();
        var stage21 = GeneratedCampaignAuthorityCheckpoint.capture(r.session(), r.actors(), r.strategicIntents(),
                r.diplomacy(), r.warfare(), commands, operations, r.transitions(), r.recovery(), r.npcMissions());
        return Stage228CampaignAuthority.restore(new Stage228GeneratedCampaignPersistentState(s.schemaVersion(),
                s.runtimeVersion(), stage21, s.smallCraft(), s.hangars(), s.flightDeck(), s.operations(), s.playerState(),
                s.playerJournal(), s.moduleCustody(), s.repairQueue(), s.refitQueue(), s.moduleTransfers(), s.productTransfers(),
                s.yardConstruction(), s.timeSkip(), crew, s.localFlights(), s.personalCraftOwnership(), s.industrialAdmissions(), s.smallCraftBuilds()));
    }
    @Test void historicalGuardWithoutOperationRemainsCancellableWithoutLocalLifecycle() {
        var c = ready(); long id = group(c);
        c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(id));
        c = withCommands(c, c.coordinator().commands(), StrategicOperationState.empty(), c.captureState().crewManifest());
        assertFalse(c.hasPlayerGroupGuard(id)); assertFalse(c.previewPlayerGroupGuardStop(id).allowed());
        step(c); assertTrue(c.coordinator().operations().operations().isEmpty());
        assertTrue(c.coordinator().commands().activeOrderFor(id).isPresent());
        c = c.submitPlayerFactionCommand(c.previewPlayerGroupMovementCancellation(id));
        assertTrue(c.coordinator().commands().activeOrderFor(id).isEmpty()); roundtrip(c);
    }
    @Test void historicalWithdrawingDefenseDoesNotAcquireLocalGuardLifecycle() {
        var c = ready(); long id = group(c);
        c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(id));
        var operation = c.coordinator().operations().activeForCommandGroup(id).orElseThrow();
        var retained = operation.withLifecycle(StrategicOperationState.OperationStatus.WITHDRAWING,
                operation.lastTransitionTick(), operation.unsupportedSinceTick(), null, null);
        c = withCommands(c, c.coordinator().commands(), c.coordinator().operations().replace(retained), c.captureState().crewManifest());
        assertFalse(c.hasPlayerGroupGuard(id)); assertFalse(c.previewPlayerGroupGuardStop(id).allowed());
        assertFalse(c.previewPlayerGroupMovementCancellation(id).allowed());
        step(c); assertEquals(retained, c.coordinator().operations().requireOperation(operation.id())); roundtrip(c);
    }
    @Test void historicalLongMultilineGroupNameCannotBreakGuardFailureJournal() {
        var c = ready(); long id = group(c);
        c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(id));
        var commands = c.coordinator().commands(); var g = commands.requireGroup(id);
        var renamed = new FleetCommandState.CommandGroupState(g.id(), g.factionId(), "Legacy\n" + "x".repeat(900),
                g.memberFleetIds(), g.homeSystemId(), g.reserve(), g.homeDefense(), g.maxStrategicRiskBps(), g.retired());
        commands = new FleetCommandState(commands.nextCommandGroupId(), commands.nextOrderId(), List.of(renamed), commands.orders());
        c = withCommands(c, commands, c.coordinator().operations(), Stage23FleetCrewManifest.unobserved());
        step(c);
        assertFalse(c.hasPlayerGroupGuard(id));
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("GROUP_GUARD_INTERRUPTED")).count());
        roundtrip(c);
    }
    @Test void ordinaryCommissionedCrewStartsPersistentGuardWithoutPhysicalOrSupplyGrants() {
        var c = ready(); long group = group(c); var before = c.captureState();
        var model = new GeneratedWorldUiModel(1, c.coordinator().runtime(), c.coordinator().content());
        String key = "player-fleet|group-guard|" + group;
        assertTrue(GeneratedCampaignFleetUi.rows(c, model.capture()).stream().anyMatch(r -> r.selection().stableId().equals(key)));
        var preview = GeneratedCampaignFleetUi.preview(c, key, "group-guard");
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        var active = c.submitPlayerFactionCommand(preview);
        assertThrows(IllegalStateException.class, () -> active.submitPlayerFactionCommand(preview));
        c = active;
        assertEquals(before.playerState(), c.captureState().playerState());
        assertEquals(before.crewManifest(), c.captureState().crewManifest());
        assertEquals(before.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                        .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime(),
                c.coordinator().runtime().captureState());
        var operation = c.coordinator().operations().activeForCommandGroup(group).orElseThrow();
        assertEquals(StrategicOperationState.OperationType.DEFENSE, operation.type());
        assertEquals(StrategicOperationState.RulesOfEngagement.SELF_DEFENSE_ONLY, operation.rulesOfEngagement());
        assertEquals(0, operation.supplyPolicy().minimumSupplyAccessBps());
        assertNull(operation.contact()); assertNull(operation.encounter());
        var paused = c.captureState(); c.advanceFrame(1); assertEquals(paused, c.captureState());
        c = roundtrip(c); step(c);
        assertEquals(operation.id(), c.coordinator().operations().activeForCommandGroup(group).orElseThrow().id());
        assertEquals(c.coordinator().runtime().world().getAuthoritativeWorldTick(),
                c.coordinator().operations().requireOperation(operation.id()).lastTransitionTick());
        var ticked = c.captureState(); c.advanceFrame(0); assertEquals(ticked, c.captureState());
        var stop = c.previewPlayerGroupGuardStop(group); assertTrue(stop.allowed()); assertEquals(ticked, c.captureState());
        c = c.submitPlayerFactionCommand(stop);
        assertEquals(StrategicOperationState.OperationStatus.COMPLETED, c.coordinator().operations().requireOperation(operation.id()).status());
        assertTrue(c.coordinator().commands().activeOrderFor(group).isEmpty());
        assertFalse(c.previewPlayerGroupGuardStop(group).allowed()); roundtrip(c);
    }
    @Test void historicalUnknownCrewCannotEnterGuardAndUnobservedCrewStopsExistingDutyOnce() {
        var old = FoundedCampaignFixture.restore(); old = old.submitPlayerFactionCommand(old.previewPlayerAssetAffiliation());
        var fleet = old.playerState().orElseThrow().activeFleetId();
        old = old.submitPlayerFactionCommand(old.previewPlayerCommandGroup("Без сведений", List.of(fleet),
                old.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId()));
        var untouched = old.captureState(); assertFalse(old.previewPlayerGroupGuard(group(old)).allowed()); assertEquals(untouched, old.captureState());
        var c = ready(); long group = group(c); c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(group));
        var s = c.captureState();
        var unknown = new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(),
                s.smallCraft(), s.hangars(), s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(), s.moduleCustody(),
                s.repairQueue(), s.refitQueue(), s.moduleTransfers(), s.productTransfers(), s.yardConstruction(), s.timeSkip(),
                Stage23FleetCrewManifest.unobserved(), s.localFlights(), s.personalCraftOwnership(), s.industrialAdmissions(), s.smallCraftBuilds());
        c = Stage228CampaignAuthority.restore(unknown); step(c);
        assertTrue(c.coordinator().operations().activeForCommandGroup(group).isEmpty());
        assertEquals(StrategicOperationState.OperationStatus.FAILED, c.coordinator().operations().operations().get(0).status());
        assertEquals(FleetCommandState.OrderStatus.FAILED, c.coordinator().commands().orders().get(0).status());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("GROUP_GUARD_INTERRUPTED")).count());
        c = roundtrip(c); step(c);
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("GROUP_GUARD_INTERRUPTED")).count());
    }
    @Test void directInputInvalidatesPreviewAndEndsExistingGuardWithoutTacticalEffects() {
        var c = ready(); long group = group(c); var preview = c.previewPlayerGroupGuard(group); assertTrue(preview.allowed());
        c.setPilotThrust(.1f, 0, false); var original = c;
        assertThrows(IllegalStateException.class, () -> original.submitPlayerFactionCommand(preview));
        c.setPilotThrust(0, 0, false); c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(group));
        assertTrue(c.setPilotThrust(0, 0, true));
        assertTrue(c.coordinator().operations().activeForCommandGroup(group).isEmpty());
        assertEquals(StrategicOperationState.OperationStatus.COMPLETED, c.coordinator().operations().operations().get(0).status());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("GROUP_GUARD_STOPPED")).count());
        roundtrip(c);
    }
    @Test void actualParticipantLossFailsOperationAndRetainsTerminalHistory() {
        var c = ready(); long group = group(c); c = c.submitPlayerFactionCommand(c.previewPlayerGroupGuard(group));
        var fleet = c.playerState().orElseThrow().activeFleetId();
        c.coordinator().runtime().destroyLocalFreighter(fleet, DestructionPolicy.destroyAll()); step(c);
        assertTrue(c.coordinator().commands().requireGroup(group).retired());
        assertEquals(StrategicOperationState.OperationStatus.FAILED, c.coordinator().operations().operations().get(0).status());
        assertFalse(c.playerState().orElseThrow().ownedFleetIds().contains(fleet)); roundtrip(c);
    }
}

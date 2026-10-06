package com.spacesim.campaign;

import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HNpcMissionService.PlayerCommand;
import com.spacesim.world.Stage21HNpcMissionState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignDiscoveryReportTest {
    @Test void actualVisitReportSharesOnlyPersonalEvidenceAndOrdinaryEscrowPaysOnce() {
        var c = fixture();
        var r = c.coordinator().runtime();
        var mission = c.coordinator().npcMissions().missions().get(0);
        var object = new StaticObjectRef(new com.spacesim.world.StarSystemId(mission.objective().systemId()),
                StaticObjectKind.INFRASTRUCTURE, mission.objective().subjectId());
        c.submitMissionCommand(c.previewMissionCommand(PlayerCommand.ACCEPT, mission.missionId()));
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", mission.missionId(), "", 0).allowed());
        var endpoint = r.infrastructure().endpoint(object.objectId());
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit berth fixture; docking, personal observation, report and payout are ordinary commands.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        var before = c.captureState();
        var knowledge = r.discoveryState();
        var personal = knowledge.knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        var preview = c.previewPilotAction("REPORT_DISCOVERY", mission.missionId(), "", 0);
        assertTrue(preview.allowed()); assertEquals(mission.rewardMilliCredits(), preview.walletChangeMilliCredits());
        assertEquals(before, c.captureState());
        var model = new GeneratedWorldUiModel(1, r, c.coordinator().content());
        assertTrue(new ProductionUiProjector(mission.issuerFactionId()).capture(c, model.capture()).rows(Tab.CONTACTS)
                .stream().anyMatch(row -> row.selection().stableId().equals("pilot-report|" + mission.missionId())));
        assertEquals(before, c.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(preview));
        c.submitPilotAction(preview);
        assertEquals(before.playerState().walletMilliCredits() + mission.rewardMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(before.playerState().ownedFleetIds(), c.playerState().orElseThrow().ownedFleetIds());
        assertEquals(MissionStatus.COMPLETED, c.coordinator().npcMissions().missions().get(0).status());
        assertEquals(personal, r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID));
        var received = r.discoveryState().knowledgeFor(mission.issuerFactionId()).knowledge(object).orElseThrow();
        assertEquals(DiscoveryState.KNOWN_STATIC_LOCATION, received.state());
        assertEquals(endpoint.position(), received.knownLocation().orElseThrow());
        assertEquals(ResourceKnowledge.none(), received.resourceKnowledge());
        assertTrue(received.evidence().stream().anyMatch(e -> e.source() == DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA
                && e.provenanceId().equals("personal-discovery-report:" + mission.missionId())));
        for (var owner : knowledge.knowledgeStates()) if (!owner.ownerId().equals(mission.issuerFactionId()))
            assertEquals(owner, r.discoveryState().knowledgeFor(owner.ownerId()));
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", mission.missionId(), "", 0).allowed());
        var reported = c.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(reported)));
        assertEquals(reported, loaded.captureState());
        loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertEquals(MissionStatus.COMPLETED, loaded.coordinator().npcMissions().missions().get(0).status());
        assertEquals(before.playerState().walletMilliCredits() + mission.rewardMilliCredits(), loaded.playerState().orElseThrow().walletMilliCredits());
        loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertEquals(before.playerState().walletMilliCredits() + mission.rewardMilliCredits(), loaded.playerState().orElseThrow().walletMilliCredits());
    }

    @Test void unacceptedMissingEvidenceAndStaleReportsCannotMutateRecipients() {
        var c = fixture();
        String id = c.coordinator().npcMissions().missions().get(0).missionId();
        var before = c.captureState();
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", id, "", 0).allowed());
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", "absent", "", 0).allowed());
        assertEquals(before, c.captureState());
        c.submitMissionCommand(c.previewMissionCommand(PlayerCommand.ACCEPT, id));
        before = c.captureState();
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", id, "", 0).allowed());
        assertEquals(before, c.captureState());
        var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoint(c.coordinator().npcMissions().missions().get(0).objective().subjectId());
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        var preview = c.previewPilotAction("REPORT_DISCOVERY", id, "", 0); assertTrue(preview.allowed());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        before = c.captureState();
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        assertEquals(before, c.captureState());
        c.submitMissionCommand(c.previewMissionCommand(PlayerCommand.CANCEL, id));
        before = c.captureState();
        assertFalse(c.previewPilotAction("REPORT_DISCOVERY", id, "", 0).allowed());
        assertEquals(before, c.captureState());
    }

    private static Stage228CampaignAuthority fixture() {
        return fixture(false, NpcAvailability.AVAILABLE);
    }

    @Test void deliveryOnInclusiveDeadlinePaysWhileRemoteOrUnavailableRecipientRejects() {
        var c = fixture();
        String id = c.coordinator().npcMissions().missions().get(0).missionId();
        c.submitMissionCommand(c.previewMissionCommand(PlayerCommand.ACCEPT, id));
        visit(c);
        long deadline = c.coordinator().npcMissions().missions().get(0).deadlineTick();
        while (c.coordinator().runtime().world().getAuthoritativeWorldTick() < deadline)
            c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var preview = c.previewPilotAction("REPORT_DISCOVERY", id, "", 0);
        assertTrue(preview.allowed()); c.submitPilotAction(preview);
        assertEquals(MissionStatus.COMPLETED, c.coordinator().npcMissions().missions().get(0).status());
        assertEquals(deadline, c.coordinator().npcMissions().missions().get(0).statusUpdatedTick());
        for (boolean remote : List.of(true, false)) {
            c = fixture(remote, NpcAvailability.AVAILABLE);
            id = c.coordinator().npcMissions().missions().get(0).missionId();
            // Explicit accepted-contract fixture isolates the recipient-location validator.
            c.coordinator().npcMissionService().acceptMission(id, c.coordinator().runtime().world().getAuthoritativeWorldTick());
            visit(c);
            if (!remote) {
                var old = c.coordinator().npcMissions();
                var npc = old.npcs().get(0);
                var unavailable = new NpcState(npc.npcId(), npc.nameKey(), npc.role(), npc.factionContentId(),
                        npc.locationSystemId(), NpcAvailability.UNAVAILABLE, npc.knowledge());
                var state = new com.spacesim.world.Stage21HNpcMissionState(old.schemaVersion(), old.simulationTick(), old.nextMissionSequence(),
                        List.of(unavailable), old.missions(), old.reputations(), old.storyChains());
                var checkpoint = c.captureState(); var coordinator = c.coordinator();
                var stage21 = GeneratedCampaignAuthorityCheckpoint.capture(coordinator.session(), coordinator.actors(), coordinator.strategicIntents(),
                        coordinator.diplomacy(), coordinator.warfare(), coordinator.commands(), coordinator.operations(), coordinator.transitions(), coordinator.recovery(), state);
                c = Stage228CampaignAuthority.restore(com.spacesim.persistence.Stage228GeneratedCampaignPersistentState.compose(stage21,
                        checkpoint.smallCraft(), checkpoint.hangars(), checkpoint.flightDeck(), checkpoint.operations(), checkpoint.playerState()));
            }
            var before = c.captureState();
            assertFalse(c.previewPilotAction("REPORT_DISCOVERY", id, "", 0).allowed());
            assertEquals(before, c.captureState());
        }
    }

    private static void visit(Stage228CampaignAuthority c) {
        var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoint(c.coordinator().npcMissions().missions().get(0).objective().subjectId());
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
    }

    private static Stage228CampaignAuthority fixture(boolean remote, NpcAvailability availability) {
        var c = FoundedCampaignFixture.restore(); var coordinator = c.coordinator(); var r = coordinator.runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())).findFirst().orElseThrow();
        String faction = coordinator.actors().capture().get(0).factionContentId();
        long tick = r.world().getAuthoritativeWorldTick();
        // Explicit NPC/causal posting fixture, not a claim of authored generated opportunities.
        var fact = new NpcKnowledgeFact("fact.test.recon", endpoint.stationId(), KnowledgeKind.DISCOVERY,
                "DISCOVERY.STATIC_OBJECT", 1, "test-posting", tick, -1);
        var npc = new NpcState("npc.test.recon", "Разведчик", NpcRole.EXPLORATION_INTELLIGENCE,
                faction, remote ? r.world().getTopology().systems().stream().map(s -> s.id())
                        .filter(s -> !s.equals(fleet.systemId())).findFirst().orElseThrow() : fleet.systemId(), availability, List.of(fact));
        var service = coordinator.npcMissionService(); service.installNpcRoster(List.of(npc));
        var economy = r.world().findFactionEconomicState(faction).orElseThrow();
        long missing = Math.max(0, 1000 - Math.max(0, economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits()));
        if (missing > 0) assertTrue(r.world().transferToFactionTreasury(faction, new WalletComponent(missing),
                "test-fixture-funding", missing, "test-fixture-funding"));
        var saved = r.captureState();
        service.offerMission(r.world(), saved.freight(), saved.campaign().industrialState(), r.discoveryState().knowledgeFor(faction),
                coordinator.operations(), npc.npcId(), MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE,
                new MissionObjective(ObjectiveAuthority.DISCOVERY, ObjectiveKind.DISCOVERY_AT_LEAST, endpoint.stationId(),
                        fleet.systemId().value(), 0, "INFRASTRUCTURE:KNOWN_STATIC_LOCATION"), List.of(fact.factId()), tick + 100, 1000);
        return c;
    }
}

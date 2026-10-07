package com.spacesim.campaign;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignNpcPlacementTest {
    @Test void ordinaryTickReviewsRefreshOnlyExistingAffiliatedDispatchers() {
        var campaign = Stage228CampaignAuthority.create(1);
        var initial = campaign.coordinator().npcMissions();
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        long tick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        assertTrue(tick > 0);
        var reviewed = campaign.coordinator().npcMissions();
        assertEquals(initial.npcs().stream().map(n -> n.npcId()).toList(), reviewed.npcs().stream().map(n -> n.npcId()).toList());
        assertTrue(reviewed.npcs().stream().flatMap(n -> n.knowledge().stream())
                .anyMatch(k -> k.factId().contains(":review:") && k.receivedTick() == tick));
        assertTrue(reviewed.missions().isEmpty());
        assertEquals(reviewed, Stage228CampaignAuthority.restore(campaign.captureState()).coordinator().npcMissions());
    }

    @Test void knowingThePostingSystemDoesNotGrantRemoteDispatcherContact() {
        var campaign = Stage228CampaignAuthority.create(1);
        for (var npc : campaign.coordinator().npcMissions().npcs()) assertFalse(campaign.canContactNpc(npc.npcId()));
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(campaign).isEmpty());
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        assertTrue(campaign.coordinator().npcMissions().npcs().stream().anyMatch(n ->
                campaign.playerState().orElseThrow().discoveredSystemIds().contains(n.locationSystemId())));
        for (var npc : campaign.coordinator().npcMissions().npcs()) {
            assertEquals(1, npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).count());
            assertFalse(campaign.canContactNpc(npc.npcId()), "Station briefing is not a physical contact");
        }
        var restored = Stage228CampaignAuthority.restore(campaign.captureState());
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(restored).isEmpty());
        for (var npc : restored.coordinator().npcMissions().npcs()) assertFalse(restored.canContactNpc(npc.npcId()));
        var npc = campaign.coordinator().npcMissions().npcs().stream().filter(n ->
                campaign.playerState().orElseThrow().discoveredSystemIds().contains(n.locationSystemId())).findFirst().orElseThrow();
        var posting = npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).findFirst().orElseThrow();
        var runtime = campaign.coordinator().runtime();
        var station = runtime.industry().industrial().stations().stream().filter(s -> s.stationId().equals(posting.subjectId())).findFirst().orElseThrow();
        var fleet = runtime.world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit berth geometry tests contact authorization, not the ordinary travel journey.
        if (!fleet.systemId().equals(station.systemId())) {
            runtime.world().beginFleetTransfer(fleet.id(), station.systemId());
            runtime.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = runtime.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = runtime.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(),
                com.spacesim.world.LocalPhysicalKinematics.stationary(station.position()));
        local.updatePhysicalState(fleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(station.position()));
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        campaign.submitPilotAction(campaign.previewPilotAction("DOCK", station.stationId(), "", 0));
        assertTrue(campaign.canContactNpc(npc.npcId()));
        assertTrue(Stage228CampaignAuthority.restore(campaign.captureState()).canContactNpc(npc.npcId()));
        var contacted = campaign.captureState();
        var conversations = com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(campaign);
        assertEquals(1, conversations.size());
        assertEquals("dispatcher-contact|" + npc.npcId(), conversations.get(0).selection().stableId());
        assertNull(conversations.get(0).focusSystem(), "Conversation must not reveal navigation coordinates");
        var worldUi = new com.spacesim.ui.GeneratedWorldUiModel(1, runtime, campaign.coordinator().content());
        var projected = new com.spacesim.ui.ProductionUiProjector(npc.factionContentId()).capture(campaign, worldUi.capture());
        assertTrue(projected.rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.CONTACTS).contains(conversations.get(0)));
        assertEquals(contacted, campaign.captureState(), "Conversation must not transfer knowledge or money");
        assertEquals(conversations, com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(
                Stage228CampaignAuthority.restore(contacted)));
        campaign.submitPilotAction(campaign.previewPilotAction("UNDOCK", "", "", 0));
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(campaign).isEmpty());
    }

    @Test void postingsUseExistingArchivesWithoutChangingPhysicalOrFinancialState() {
        var coordinator = GeneratedCampaignCoordinator.create(1);
        var before = coordinator.runtime().captureState();
        GeneratedCampaignNpcPlacement.install(coordinator);
        assertEquals(before, coordinator.runtime().captureState());
        var npcs = coordinator.npcMissions();
        assertFalse(npcs.npcs().isEmpty());
        assertTrue(npcs.missions().isEmpty());
        assertEquals(npcs.npcs().size(), npcs.npcs().stream().map(n -> n.factionContentId()).distinct().count());
        for (var npc : npcs.npcs()) {
            assertTrue(npc.knowledge().stream().anyMatch(k -> k.claimCode().startsWith("DISCOVERY.INFRASTRUCTURE.")));
            var actual = GeneratedCampaignFactionObservationPublisher.publish(before, npc.factionContentId(), 0);
            for (var fact : npc.knowledge().stream().filter(k -> k.claimCode().startsWith("ECONOMIC.")).toList())
                assertTrue(actual.currentObservations().stream().anyMatch(o -> o.targetId().equals(fact.subjectId())
                        && o.evidence().provenanceId().equals(fact.provenanceId())));
        }
        assertEquals(npcs, GeneratedCampaignCoordinator.restore(coordinator.captureState()).npcMissions());
        var legacy = GeneratedCampaignCoordinator.create(1);
        assertTrue(GeneratedCampaignCoordinator.restore(legacy.captureState()).npcMissions().npcs().isEmpty(),
                "Restoring an empty historical roster must not create postings");
    }
}


package com.spacesim.campaign;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignNpcPlacementTest {
    @Test void existingUnionLogisticsHubGetsANewGameDispatcherWithActualBerthAndLegalOperatorChecks() {
        var c = Stage228CampaignAuthority.create(1);
        var npc = c.coordinator().npcMissions().npcs().stream().filter(n -> n.factionContentId().equals("faction.beta")).findFirst().orElseThrow();
        var posting = npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).findFirst().orElseThrow();
        assertEquals("hub.14", posting.subjectId());
        var r = c.coordinator().runtime();
        assertTrue(r.industry().industrial().stations().stream().noneMatch(s -> s.stationId().equals(posting.subjectId())),
                "Logistics dispatchers must not require an industrial-station instance");
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        assertFalse(c.canContactNpc(npc.npcId()));
        var endpoint = r.infrastructure().endpoint(posting.subjectId());
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit berth fixture verifies the logistics contact boundary, not ordinary travel.
        r.world().beginFleetTransfer(fleet.id(), endpoint.systemId()); r.world().completeFleetTransfer(fleet.id(), 0, 0);
        fleet = r.world().findFleet(fleet.id()).orElseThrow();
        var local = r.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(),
                com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(fleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        assertTrue(c.canContactNpc(npc.npcId()));
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(c).stream().anyMatch(row ->
                row.selection().stableId().equals("dispatcher-contact|" + npc.npcId())));
        var saved = c.captureState();
        assertTrue(Stage228CampaignAuthority.restore(saved).canContactNpc(npc.npcId()));
        var legal = r.world().findSession(endpoint.systemId()).orElseThrow().getEntityRegistry()
                .require(c.pilotMarketReference(endpoint.stationId()).orElseThrow().entityId())
                .getComponent(com.spacesim.components.FactionComponent.class);
        legal.factionId = r.world().findFactionRuntimeId("faction.alpha").orElseThrow();
        assertFalse(c.canContactNpc(npc.npcId()), "Territorial presence and an old posting cannot substitute for the actual legal operator");
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(c).isEmpty());
    }

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
        assertEquals(npcs.npcs().size(), npcs.npcs().stream().flatMap(n -> n.knowledge().stream()
                .filter(k -> k.factId().equals(n.npcId() + ":posting")).map(k -> k.subjectId())).distinct().count());
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

    @Test void actualStarterDockHasALocalDispatcherWithoutTravelOrGeometryFixtures() {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var runtime = c.coordinator().runtime();
        var fleet = runtime.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        String station = "hub." + fleet.systemId().value();
        var local = c.coordinator().npcMissions().npcs().stream().filter(n -> n.knowledge().stream()
                .anyMatch(k -> k.factId().equals(n.npcId() + ":posting") && k.subjectId().equals(station))).findFirst().orElseThrow();
        assertFalse(c.canContactNpc(local.npcId()));
        assertTrue(com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(c).isEmpty());
        c.submitPilotAction(c.previewPilotAction("DOCK", station, "", 0));
        assertTrue(c.canContactNpc(local.npcId()));
        var before = c.captureState();
        var rows = com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(c);
        assertEquals(1, rows.size());
        assertEquals("dispatcher-contact|" + local.npcId(), rows.get(0).selection().stableId());
        assertEquals(before, c.captureState());
        var loaded = Stage228CampaignAuthority.restore(com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.decode(
                com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.encode(before)));
        assertEquals(before, loaded.captureState()); assertTrue(loaded.canContactNpc(local.npcId()));
        assertEquals(rows, com.spacesim.ui.GeneratedCampaignDispatcherContactUi.rows(loaded));
    }
}


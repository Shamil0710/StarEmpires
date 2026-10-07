package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignStationMapUi;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignStationMapPurchaseTest {
    @Test void paidStationCopyRequiresActualContactPreservesAgeAndCannotGrantTradeOrOwnership() throws Exception {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var r = c.coordinator().runtime();
        var npc = c.coordinator().npcMissions().npcs().stream()
                .filter(n -> n.npcId().startsWith("npc.stage23b.dispatcher:")).findFirst().orElseThrow();
        var posting = npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).findFirst().orElseThrow();
        var source = r.discoveryState().knowledgeFor(npc.factionContentId()).entries().stream()
                .filter(k -> k.object().kind() == StaticObjectKind.INFRASTRUCTURE && k.knownLocation().isPresent())
                .filter(k -> !k.object().objectId().equals(posting.subjectId()))
                .filter(k -> r.infrastructure().endpoints().stream().anyMatch(s -> s.stationId().equals(k.object().objectId())
                        && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0)).findFirst().orElseThrow();
        var personal = r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertTrue(personal.knowledge(source.object()).isEmpty(), "No personal navigation knowledge may be supplied by this fixture");
        assertFalse(c.canPurchaseStationMap(npc.npcId(), source.object().objectId()));
        assertTrue(GeneratedCampaignStationMapUi.rows(c).isEmpty());
        var endpoint = r.infrastructure().endpoint(posting.subjectId());
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit berth geometry isolates contact/payment, rather than claiming ordinary arrival.
        if (!fleet.systemId().equals(endpoint.systemId())) {
            r.world().beginFleetTransfer(fleet.id(), endpoint.systemId()); r.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = r.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = r.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        c.submitPilotAction(c.previewPilotAction("DOCK", posting.subjectId(), "", 0));
        assertTrue(c.canPurchaseStationMap(npc.npcId(), source.object().objectId()));
        var before = c.captureState();
        var row = GeneratedCampaignStationMapUi.rows(c).stream().filter(v -> v.selection().stableId()
                .equals("pilot-station-map|" + npc.npcId() + '|' + source.object().objectId())).findFirst().orElseThrow();
        assertNull(row.focusSystem()); assertEquals(0, row.focusFleet());
        assertEquals(before, c.captureState());
        assertFalse(c.previewPilotAction("BUY_STATION_MAP", npc.npcId(), "station.missing", 0).allowed());
        assertFalse(c.previewPilotAction("BUY_RESOURCE_MAP", npc.npcId(), source.object().objectId(), 0).allowed());
        var buy = c.previewPilotAction("BUY_STATION_MAP", npc.npcId(), source.object().objectId(), 0);
        assertTrue(buy.allowed()); assertEquals(-100_000, buy.walletChangeMilliCredits());
        assertEquals(before, c.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(buy));
        assertEquals(before, foreign.captureState());
        long treasury = r.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits();
        c.submitPilotAction(buy);
        var received = r.discoveryState().knowledgeFor(personal.ownerId()).knowledge(source.object()).orElseThrow();
        assertEquals(source.knownLocation(), received.knownLocation());
        assertEquals(source.classificationId(), received.classificationId());
        assertEquals(source.lastUpdatedSeconds(), received.lastUpdatedSeconds());
        assertEquals(ResourceKnowledge.none(), received.resourceKnowledge());
        assertEquals(source, r.discoveryState().knowledgeFor(npc.factionContentId()).knowledge(source.object()).orElseThrow());
        assertEquals(before.playerState().walletMilliCredits() - 100_000, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + 100_000, r.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits());
        assertEquals(before.playerState().ownedStations(), c.playerState().orElseThrow().ownedStations());
        assertEquals(before.playerState().ownedFleetIds(), c.playerState().orElseThrow().ownedFleetIds());
        assertTrue(c.playerState().orElseThrow().discoveredSystemIds().contains(source.object().systemId()));
        assertFalse(c.previewPilotAction("BUY", source.object().objectId(), "commodity.material.purified_water", 1).allowed());
        assertFalse(c.canPurchaseStationMap(npc.npcId(), source.object().objectId()));
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(buy));
        var entry = c.playerJournal().entries().get(c.playerJournal().entries().size() - 1);
        assertEquals("BUY_STATION_MAP", entry.action()); assertEquals(1, entry.quantity());
        assertEquals(-100_000, entry.walletDeltaMilliCredits());
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertEquals(GeneratedCampaignStationMapUi.rows(c), GeneratedCampaignStationMapUi.rows(restored));
        var stale = foreign.previewPilotAction("BUY_STATION_MAP", npc.npcId(), source.object().objectId(), 0);
        foreign.submitPilotAction(foreign.previewPilotAction("UNDOCK", "", "", 0));
        var undocked = foreign.captureState();
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(stale));
        assertFalse(foreign.previewPilotAction("BUY_STATION_MAP", npc.npcId(), source.object().objectId(), 0).allowed());
        assertEquals(undocked, foreign.captureState());
    }
}

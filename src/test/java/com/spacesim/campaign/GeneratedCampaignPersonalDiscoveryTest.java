package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalDiscoveryTest {
    @Test void actualDockingAddsOnlyOwnEvidenceAndRoundtripsWithoutInventingOtherKnowledge() {
        var c = FoundedCampaignFixture.restore();
        var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())).findFirst().orElseThrow();
        var object = new StaticObjectRef(endpoint.systemId(), StaticObjectKind.INFRASTRUCTURE, endpoint.stationId());
        var beforeKnowledge = r.discoveryState();
        assertEquals(DiscoveryState.UNKNOWN, beforeKnowledge.knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).discoveryState(object));
        // Explicit berth geometry; actual ownership/docking/knowledge authorities remain unchanged.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        var before = c.captureState();
        var preview = c.previewPilotAction("DOCK", endpoint.stationId(), "", 0);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        var knowledge = r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertEquals(1, knowledge.entries().size());
        var station = knowledge.knowledge(object).orElseThrow();
        assertEquals(DiscoveryState.KNOWN_STATIC_LOCATION, station.state());
        assertEquals(endpoint.position(), station.knownLocation().orElseThrow());
        assertEquals(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, station.evidence().get(0).source());
        assertEquals(ResourceKnowledge.none(), station.resourceKnowledge());
        for (var owner : beforeKnowledge.knowledgeStates()) assertEquals(owner, r.discoveryState().knowledgeFor(owner.ownerId()));
        var saved = c.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, loaded.captureState());
        assertEquals(knowledge, loaded.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID));
        var model = new GeneratedWorldUiModel(1, r, c.coordinator().content());
        for (String viewer : java.util.List.of("faction.alpha", "faction.beta")) {
            assertTrue(new ProductionUiProjector(viewer).capture(c, model.capture()).rows(Tab.INTELLIGENCE).stream()
                    .anyMatch(row -> row.selection().stableId().equals("station:" + endpoint.stationId())
                            && row.category().equals("Личные открытия")));
        }
        assertEquals(saved, c.captureState());
    }

    @Test void absentPhysicalDockingCannotRecordAVisitAndRepeatedVisitsAreIdempotent() {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())).findFirst().orElseThrow();
        var before = c.captureState();
        assertThrows(IllegalStateException.class, () -> r.recordPersonalStationVisit(c.playerState().orElseThrow(), endpoint.stationId()));
        assertEquals(before, c.captureState());
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        var knowledge = r.discoveryState();
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        assertEquals(knowledge, r.discoveryState());
        var docked = c.captureState();
        var elsewhere = r.infrastructure().endpoints().stream().filter(e -> !e.systemId().equals(fleet.systemId())).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> r.recordPersonalStationVisit(c.playerState().orElseThrow(), elsewhere.stationId()));
        assertEquals(docked, c.captureState());
    }
}

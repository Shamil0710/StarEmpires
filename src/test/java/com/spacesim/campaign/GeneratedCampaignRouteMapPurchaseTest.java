package com.spacesim.campaign;

import com.spacesim.persistence.Stage20DiscoveryPersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignRouteMapUi;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.Stage23FreightRouteReport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignRouteMapPurchaseTest {
    @Test void paidHistoricalChartCopiesOnlyItsPathAndAgeWithConservedMoneyAndExactNativeRestore() throws Exception {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var r = c.coordinator().runtime();
        var npc = c.coordinator().npcMissions().npcs().stream().filter(n -> n.npcId().startsWith("npc.stage23b.dispatcher:")).findFirst().orElseThrow();
        var posting = npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).findFirst().orElseThrow();
        var order = r.freight().capture().orders().stream().filter(o -> o.stableFactionId().equals(npc.factionContentId())
                && o.destinationEndpointId().equals(posting.subjectId())).findFirst().orElseThrow();
        var destination = r.infrastructure().endpoint(order.destinationEndpointId());
        var ref = new StaticObjectRef(destination.systemId(), StaticObjectKind.INFRASTRUCTURE, destination.stationId());
        var report = new Stage23FreightRouteReport.Report(order.orderId(), order.orderedSystems());
        String provenance = Stage23FreightRouteReport.provenance(report);
        String key = Stage23FreightRouteReport.offerKey(ref, provenance);
        assertFalse(c.canPurchaseRouteMap(npc.npcId(), key));
        assertTrue(GeneratedCampaignRouteMapUi.offers(c).isEmpty());
        // Explicit berth fixture isolates contact/payment. Actual delivery publication is tested separately.
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        if (!fleet.systemId().equals(destination.systemId())) {
            r.world().beginFleetTransfer(fleet.id(), destination.systemId()); r.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = r.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = r.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(destination.position()));
        local.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(destination.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        c.submitPilotAction(c.previewPilotAction("DOCK", destination.stationId(), "", 0));
        assertTrue(c.canContactNpc(npc.npcId()));
        assertFalse(c.canPurchaseRouteMap(npc.npcId(), key), "An assigned/planned order is not a completed route report");
        // Explicit historical-observation fixture; no delivery counter, cargo, money or personal facts are supplied.
        // Producer authenticity/failed unloading/repeat age are covered by the freight acceptance test.
        var baselineArchive = r.discoveryState();
        var observed = new Stage20DiscoveryKnowledgeRuntime().observe(baselineArchive.knowledgeFor(npc.factionContentId()),
                new Stage20DiscoveryKnowledgeRuntime.StaticObservation(ref, DiscoveryState.KNOWN_STATIC_LOCATION,
                        Optional.of(destination.stationArchetypeId()), Optional.of(destination.position()), ResourceKnowledge.none(),
                        new DiscoveryEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, provenance, 0, OptionalDouble.empty())));
        var owners = new java.util.ArrayList<>(baselineArchive.knowledgeStates()); owners.removeIf(k -> k.ownerId().equals(observed.ownerId())); owners.add(observed);
        var field = r.getClass().getDeclaredField("discovery"); field.setAccessible(true);
        field.set(r, new Stage20DiscoveryPersistentState(baselineArchive.envelopeVersion(), baselineArchive.rootSeed(),
                baselineArchive.worldGenerationVersion(), baselineArchive.worldFingerprint(), owners));
        var before = c.captureState();
        var offers = GeneratedCampaignRouteMapUi.offers(c);
        var offer = offers.stream().filter(row -> row.selection().stableId().equals("pilot-route-map|" + npc.npcId() + '|' + key)).findFirst().orElseThrow();
        assertNull(offer.focusSystem()); assertEquals(before, c.captureState());
        if (Boolean.getBoolean("stage23b.writeRouteFixture")) Stage228GeneratedCampaignPersistenceCodec.write(
                java.nio.file.Path.of("target/stage23b-route-map-fixture.s25"), before);
        long treasury = r.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits();
        var buy = c.previewPilotAction("BUY_ROUTE_MAP", npc.npcId(), key, 0);
        assertTrue(buy.allowed()); assertEquals(-100_000, buy.walletChangeMilliCredits()); assertEquals(before, c.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(buy)); assertEquals(before, foreign.captureState());
        c.submitPilotAction(buy);
        var expectedSystems = new java.util.TreeSet<>(before.playerState().discoveredSystemIds()); expectedSystems.addAll(report.systems());
        assertEquals(expectedSystems, new java.util.TreeSet<>(c.playerState().orElseThrow().discoveredSystemIds()));
        assertEquals(before.playerState().walletMilliCredits() - 100_000, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + 100_000, r.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits());
        assertEquals(before.playerState().ownedFleetIds(), c.playerState().orElseThrow().ownedFleetIds());
        assertEquals(before.playerState().ownedStations(), c.playerState().orElseThrow().ownedStations());
        assertEquals(before.playerState().threatIntel(), c.playerState().orElseThrow().threatIntel());
        assertEquals(observed, r.discoveryState().knowledgeFor(npc.factionContentId()));
        var received = r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).knowledge(ref).orElseThrow();
        var copied = received.evidence().stream().filter(e -> Stage23FreightRouteReport.decodeShared(ref, e).isPresent()).findFirst().orElseThrow();
        assertEquals(report, Stage23FreightRouteReport.decodeShared(ref, copied).orElseThrow()); assertEquals(0, copied.observedAtSeconds());
        assertFalse(c.canPurchaseRouteMap(npc.npcId(), key)); assertThrows(IllegalStateException.class, () -> c.submitPilotAction(buy));
        assertEquals("BUY_ROUTE_MAP", c.playerJournal().entries().get(c.playerJournal().entries().size() - 1).action());
        assertEquals(1, GeneratedCampaignRouteMapUi.personal(c).size());
        assertTrue(c.previewPilotRoute(report.systems().get(0)).isPresent(), "Paid systems must make the actual connected route available to the personal planner");
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState()); assertEquals(GeneratedCampaignRouteMapUi.personal(c), GeneratedCampaignRouteMapUi.personal(restored));
        var stale = foreign.previewPilotAction("BUY_ROUTE_MAP", npc.npcId(), key, 0);
        foreign.submitPilotAction(foreign.previewPilotAction("UNDOCK", "", "", 0)); var undocked = foreign.captureState();
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(stale));
        assertFalse(foreign.previewPilotAction("BUY_ROUTE_MAP", npc.npcId(), key, 0).allowed()); assertEquals(undocked, foreign.captureState());
    }
}

package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignResourceMapUi;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignResourceMapPurchaseTest {
    private record Fixture(Stage228CampaignAuthority campaign, String npc, com.spacesim.world.FleetId fleet,
                           String site, StaticObjectRef object, String station, String seller) { }

    private static Fixture fixture() {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        var r = c.coordinator().runtime();
        for (var npc : c.coordinator().npcMissions().npcs()) {
            for (var outpost : r.industry().sourceOutposts().outposts()) {
                for (var fleet : r.freight().capture().freighters()) {
                    if (fleet.phase() != FreightPhase.AT_SOURCE || !fleet.stableFactionId().equals(npc.factionContentId())) continue;
                    var order = r.freight().findOrder(fleet.activeOrderId()).orElseThrow();
                    if (!order.orderedSystems().get(0).equals(outpost.site().systemId())
                            || !order.commodityId().equals(outpost.source().sourceState().outputCommodityId())) continue;
                    String station = npc.knowledge().stream().filter(k -> k.factId().equals(npc.npcId() + ":posting")).findFirst().orElseThrow().subjectId();
                    return new Fixture(c, npc.npcId(), fleet.fleetId(), outpost.site().siteId(),
                            new StaticObjectRef(outpost.site().systemId(), StaticObjectKind.RESOURCE_OCCURRENCE, outpost.source().sourceId()),
                            station, npc.factionContentId());
                }
            }
        }
        throw new AssertionError("Seed 1 must contain a real local dispatcher and an assigned collection order");
    }

    private static void sample(Fixture f) {
        var r = f.campaign.coordinator().runtime();
        var outpost = r.industry().sourceOutposts().outpost(f.site);
        double reserve = outpost.source().sourceState().remainingAccessibleMassKg();
        f.campaign.coordinator().setPaused(false);
        for (int i = 0; i < 8 && r.discoveryState().knowledgeFor(f.seller).knowledge(f.object).isEmpty(); i++)
            f.campaign.advanceFrame(f.campaign.coordinator().session().fixedStepSeconds());
        f.campaign.coordinator().setPaused(true);
        assertTrue(r.discoveryState().knowledgeFor(f.seller).knowledge(f.object).isPresent(),
                "Ordinary autonomous collection must publish an actual source report");
        assertTrue(outpost.source().sourceState().remainingAccessibleMassKg() < reserve);
        assertTrue(r.freight().findFreighter(f.fleet).orElseThrow().cargoStorage().commodityMassByIdKg()
                .getOrDefault(outpost.source().sourceState().outputCommodityId(), 0d) > 0,
                "The ordinary collector must actually carry the extracted commodity");
    }

    private static void contact(Fixture f) {
        var c = f.campaign; var r = c.coordinator().runtime();
        var active = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit berth fixture isolates communication/payment; no resources, samples or archive entries are supplied.
        var endpoint = r.infrastructure().endpoint(f.station);
        if (!active.systemId().equals(endpoint.systemId())) {
            r.world().beginFleetTransfer(active.id(), endpoint.systemId()); r.world().completeFleetTransfer(active.id(), 0, 0);
            active = r.world().findFleet(active.id()).orElseThrow();
        }
        if (r.arrival().materialization(active.systemId()).physicalState(active.localEntityId()).isEmpty())
            r.arrival().materialization(active.systemId()).registerPhysicalState(active.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        r.arrival().materialization(active.systemId()).updatePhysicalState(active.localEntityId(),
                LocalPhysicalKinematics.stationary(endpoint.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        var dock = c.previewPilotAction("DOCK", f.station, "", 0); assertTrue(dock.allowed()); c.submitPilotAction(dock);
        assertTrue(c.canContactNpc(f.npc));
    }

    @Test void actualSampleRemainsPrivateUntilConservedPaidContactAndSurvivesNativeReload() {
        var f = fixture(); var c = f.campaign; var r = c.coordinator().runtime();
        var before = r.discoveryState();
        assertTrue(before.knowledgeFor(f.seller).knowledge(f.object).isEmpty());
        sample(f);
        var source = r.discoveryState().knowledgeFor(f.seller).knowledge(f.object).orElseThrow();
        assertEquals(ResourceKnowledgeLevel.RESOURCE_INDICATION, source.resourceKnowledge().level());
        assertTrue(source.resourceKnowledge().estimate().isEmpty());
        assertEquals(before.knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                "Other factions' actual collection cannot grant personal discoveries");
        assertFalse(c.canPurchaseResourceMap(f.npc, f.object.objectId()));
        assertTrue(GeneratedCampaignResourceMapUi.rows(c).isEmpty());
        contact(f);
        var baseline = c.captureState();
        var offers = GeneratedCampaignResourceMapUi.rows(c);
        var row = offers.stream().filter(v -> v.selection().stableId().endsWith("|" + f.object.objectId())).findFirst().orElseThrow();
        assertNull(row.focusSystem()); assertEquals(0, row.focusFleet());
        var advertised = r.discoveryState().knowledgeFor(f.seller).knowledge(f.object).orElseThrow();
        var commodity = advertised.classificationId().map(com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault()::findCommodity).orElseThrow();
        assertTrue(row.sections().stream().flatMap(section -> section.lines().stream()).anyMatch(line ->
                line.label().equals("Вид сырья в предложении") && line.value().equals(commodity.displayName())));
        assertEquals(baseline, c.captureState());
        if (Boolean.getBoolean("stage23b.writeMapFixture")) {
            try { Stage228GeneratedCampaignPersistenceCodec.write(java.nio.file.Path.of("target/stage23b-resource-map-fixture.s25"), baseline); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
        long money = c.playerState().orElseThrow().walletMilliCredits();
        long treasury = r.world().findFactionEconomicState(f.seller).orElseThrow().treasuryMilliCredits();
        var buy = c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, f.object.objectId(), 0);
        assertTrue(buy.allowed()); assertEquals(-Stage228CampaignAuthority.RESOURCE_MAP_PRICE_MILLI_CREDITS, buy.walletChangeMilliCredits());
        assertEquals(baseline, c.captureState()); c.submitPilotAction(buy);
        assertEquals(money - Stage228CampaignAuthority.RESOURCE_MAP_PRICE_MILLI_CREDITS, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + Stage228CampaignAuthority.RESOURCE_MAP_PRICE_MILLI_CREDITS,
                r.world().findFactionEconomicState(f.seller).orElseThrow().treasuryMilliCredits());
        var received = r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).knowledge(f.object).orElseThrow();
        assertEquals(source.knownLocation(), received.knownLocation());
        assertEquals(source.resourceKnowledge(), received.resourceKnowledge());
        assertTrue(c.playerState().orElseThrow().discoveredSystemIds().contains(f.object.systemId()));
        assertEquals(source.lastUpdatedSeconds(), received.lastUpdatedSeconds());
        assertEquals(source, r.discoveryState().knowledgeFor(f.seller).knowledge(f.object).orElseThrow());
        assertFalse(c.canPurchaseResourceMap(f.npc, f.object.objectId()));
        assertFalse(c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, f.object.objectId(), 0).allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(buy));
        assertEquals("BUY_RESOURCE_MAP", c.playerJournal().entries().get(c.playerJournal().entries().size() - 1).action());
        assertEquals(1, c.playerJournal().entries().get(c.playerJournal().entries().size() - 1).quantity());
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertEquals(GeneratedCampaignResourceMapUi.rows(c), GeneratedCampaignResourceMapUi.rows(restored));
    }

    @Test void rejectedExtractionAndUnknownRemoteOrStaleReportsCannotCreateKnowledgeOrPayments() {
        var f = fixture(); var c = f.campaign; var r = c.coordinator().runtime();
        var before = r.captureState();
        var denied = r.extractForFreighter(f.fleet, f.site, Double.MAX_VALUE, c.coordinator().session().fixedStepSeconds());
        assertFalse(denied.committed()); assertEquals(before, r.captureState());
        assertThrows(IllegalStateException.class, () -> r.extractForFreighter(c.playerState().orElseThrow().activeFleetId(), f.site, 1, .1));
        assertEquals(before, r.captureState());
        sample(f); contact(f);
        assertFalse(c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, "source.missing", 0).allowed());
        var preview = c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, f.object.objectId(), 0); assertTrue(preview.allowed());
        var other = Stage228CampaignAuthority.restore(c.captureState()); var otherState = other.captureState();
        assertThrows(IllegalStateException.class, () -> other.submitPilotAction(preview)); assertEquals(otherState, other.captureState());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0)); var undocked = c.captureState();
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview)); assertEquals(undocked, c.captureState());
        assertFalse(c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, f.object.objectId(), 0).allowed());
        assertEquals(undocked, c.captureState());
    }

    @Test void spendingPersonalFundsThroughTheOrdinaryTreasuryPathBlocksMapPurchaseWithoutDisclosure() {
        var f = fixture(); sample(f); contact(f);
        var c = f.campaign.submitPlayerFactionFoundation(f.campaign.previewPlayerFactionFoundation("faction.player", "Исследователи"));
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var capitalize = c.previewPilotAction("CAPITALIZE", Long.toString(wallet), "", 0);
        assertTrue(capitalize.allowed()); c.submitPilotAction(capitalize);
        assertEquals(0, c.playerState().orElseThrow().walletMilliCredits());
        assertTrue(c.canPurchaseResourceMap(f.npc, f.object.objectId()), "An offer is available even when the buyer cannot pay");
        var baseline = c.captureState();
        var buy = c.previewPilotAction("BUY_RESOURCE_MAP", f.npc, f.object.objectId(), 0);
        assertFalse(buy.allowed());
        assertEquals(baseline, c.captureState());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(buy));
        assertEquals(baseline, c.captureState());
    }
}

package com.spacesim.campaign;

import com.spacesim.player.PlayerRuntime;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPilotStartTest {
    @Test void startPurchasesExistingReserveWithConservedPaymentAndNoFactionControl() {
        var campaign = Stage228CampaignAuthority.create(1);
        var before = campaign.captureState();
        var offer = campaign.previewIndependentPilotStart();
        assertTrue(offer.allowed()); assertEquals(before, campaign.captureState());
        var fleet = campaign.coordinator().runtime().freight().findFreighter(offer.fleetId()).orElseThrow();
        long treasury = campaign.coordinator().runtime().world().findFactionEconomicState(fleet.stableFactionId()).orElseThrow().treasuryMilliCredits();
        var player = campaign.submitIndependentPilotStart(offer);
        assertNull(player.factionContentId()); assertEquals(java.util.List.of(offer.fleetId()), player.ownedFleetIds());
        assertEquals(75_000_000L, player.walletMilliCredits());
        assertEquals(treasury + Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS,
                campaign.coordinator().runtime().world().findFactionEconomicState(fleet.stableFactionId()).orElseThrow().treasuryMilliCredits());
        assertEquals(before.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime().freight(),
                campaign.coordinator().runtime().freight().capture());
        assertFalse(campaign.canStartIndependentPilot());
        assertThrows(IllegalStateException.class, () -> campaign.submitIndependentPilotStart(offer));
    }

    @Test void staleAndForeignPreviewRejectBeforeAnyMutation() {
        var campaign = Stage228CampaignAuthority.create(1);
        var offer = campaign.previewIndependentPilotStart();
        var other = Stage228CampaignAuthority.restore(campaign.captureState());
        var baseline = other.captureState();
        assertThrows(IllegalStateException.class, () -> other.submitIndependentPilotStart(offer));
        assertEquals(baseline, other.captureState());
        campaign.coordinator().setPaused(true);
        var paused = campaign.captureState();
        assertThrows(IllegalStateException.class, () -> campaign.submitIndependentPilotStart(offer));
        assertEquals(paused, campaign.captureState());
    }

    @Test void paidSaleCopiesOnlyArchivedSellerCivilianStationCoordinatesAndPersistsItsReceipt() {
        var campaign = Stage228CampaignAuthority.create(1);
        var runtime = campaign.coordinator().runtime();
        var baseline = campaign.captureState();
        var offer = campaign.previewIndependentPilotStart();
        assertTrue(offer.allowed(), "Real archive transfer must leave the conserved starter purchase available");
        assertEquals(baseline, campaign.captureState(), "Preview must not deliver intelligence");
        var seller = runtime.freight().findFreighter(offer.fleetId()).orElseThrow().stableFactionId();
        var archive = runtime.discoveryState().knowledgeFor(seller);
        var industrial = runtime.captureState().campaign().industrialState();
        var expected = archive.entries().stream().filter(e -> e.knownLocation().isPresent()
                && runtime.industry().industrial().stations().stream().anyMatch(s ->
                    s.stationId().equals(e.object().objectId()) && s.systemId().equals(e.object().systemId())
                    && s.stableFactionId().equals(seller)
                    && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0))
                .toList();
        assertFalse(expected.isEmpty(), "Ordinary start must have a real seller archive to disclose");
        campaign.submitIndependentPilotStart(offer);
        assertEquals(archive, runtime.discoveryState().knowledgeFor(seller));
        assertEquals(industrial, runtime.captureState().campaign().industrialState(), "Briefing cannot issue stock, facilities or work");
        var received = runtime.discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        var briefing = received.entries().stream().filter(e -> e.evidence().stream().anyMatch(v ->
                v.source() == com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.FACTION_INTELLIGENCE
                && v.provenanceId().startsWith("paid-ship-briefing:"))).toList();
        assertEquals(expected.size(), briefing.size());
        assertEquals(expected.size(), received.entries().size(), "No other faction or resource knowledge may be copied");
        for (var entry : briefing) {
            var source = expected.stream().filter(e -> e.object().equals(entry.object())).findFirst().orElseThrow();
            assertEquals(source.knownLocation(), entry.knownLocation());
            assertEquals(source.classificationId(), entry.classificationId());
            assertEquals(com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(), entry.resourceKnowledge());
            assertTrue(campaign.playerState().orElseThrow().discoveredSystemIds().contains(entry.object().systemId()));
        }
        assertEquals("PILOT_START", campaign.playerJournal().entries().get(0).action());
        var receipt = campaign.playerJournal().entries().get(1);
        assertEquals("SELLER_STATION_BRIEFING", receipt.action());
        assertEquals(expected.size(), receipt.quantity());
        var model = new GeneratedWorldUiModel(1, runtime, campaign.coordinator().content());
        var rows = new ProductionUiProjector("faction.beta").capture(campaign, model.capture()).rows(Tab.INTELLIGENCE);
        assertTrue(rows.stream().filter(row -> row.selection().stableId().startsWith("station:"))
                .anyMatch(row -> row.summary().equals("Известная станция")));
        assertFalse(rows.stream().anyMatch(row -> row.summary().equals("Посещённая станция")));
        var saved = campaign.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }

    @Test void saveLoadNeverIssuesMoneyOrShipAndComposedAdapterCannotAdvanceClock() {
        var campaign = Stage228CampaignAuthority.create(1);
        var historical = Stage228CampaignAuthority.restore(campaign.captureState());
        assertEquals(campaign.captureState(), historical.captureState(), "Resume must preserve the accepted archive without issuing a briefing");
        assertFalse(historical.canStartIndependentPilot());
        assertFalse(historical.previewIndependentPilotStart().allowed());
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var checkpoint = campaign.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(checkpoint)));
        assertEquals(checkpoint, restored.captureState()); assertFalse(restored.canStartIndependentPilot());
        var adapter = PlayerRuntime.attachToCampaign(restored.coordinator().runtime().world(),
                restored.coordinator().content(), restored.playerState().orElseThrow());
        assertThrows(IllegalStateException.class, () -> adapter.advanceFrame(1));
        assertFalse(adapter.setMovementIntent(1, 0));
        assertEquals(checkpoint, restored.captureState());
    }

    @Test void productionProjectionExposesStartConditionsAndPersonalOwnership() {
        var campaign = Stage228CampaignAuthority.create(1);
        var model = new GeneratedWorldUiModel(1, campaign.coordinator().runtime(), campaign.coordinator().content());
        var projector = new ProductionUiProjector("faction.beta");
        assertTrue(projector.capture(campaign, model.capture(), true).rows(Tab.SETTINGS).stream().anyMatch(r -> r.selection().stableId().equals("pilot-start")));
        assertFalse(projector.capture(campaign, model.capture()).rows(Tab.SETTINGS).stream().anyMatch(r -> r.selection().stableId().equals("pilot-start")));
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var before = campaign.captureState();
        var rows = projector.capture(campaign, model.capture()).rows(Tab.SHIPS);
        assertEquals(before, campaign.captureState());
        assertTrue(rows.stream().filter(r -> r.selection().stableId().startsWith("personal-ship:")).flatMap(r -> r.sections().stream()).anyMatch(s -> s.title().equals("Инженерное состояние")));
        assertTrue(rows.stream().anyMatch(r -> r.selection().stableId().startsWith("personal-ship:")));
    }
}

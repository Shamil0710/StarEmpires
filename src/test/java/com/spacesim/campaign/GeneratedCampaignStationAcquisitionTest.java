package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignStationAcquisitionUi;
import com.spacesim.world.LocalPhysicalKinematics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignStationAcquisitionTest {
    @Test void newGameCannotAcquireADistantStationWithoutPhysicalTravelOrRevealUnreceivedMarkets() {
        var c = Stage228CampaignAuthority.create(1);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var runtime = c.coordinator().runtime();
        var station = runtime.industry().industrial().stations().get(0);
        var fleet = runtime.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var actual = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        var dock = c.previewPilotAction("DOCK", station.stationId(), "", 0);
        var before = c.captureState();
        assertTrue(actual.position().distanceTo(station.position()) > 1000);
        assertFalse(dock.allowed());
        var buy = c.previewPilotAction("PURCHASE_STATION", station.stationId(), "", 0);
        assertFalse(buy.allowed()); assertFalse(c.ownsProductionStation(station.stationId()));
        if (!c.playerState().orElseThrow().discoveredObjects().contains(c.pilotMarketReference(station.stationId()).orElseThrow()))
            assertEquals(0, c.stationSalePriceMilliCredits(station.stationId()));
        assertEquals(75_000_000L, c.playerState().orElseThrow().walletMilliCredits());
        var model = new com.spacesim.ui.GeneratedWorldUiModel(1L, runtime, c.coordinator().content());
        var observer = c.coordinator().actors().capture().get(0).factionContentId();
        var projected = new com.spacesim.ui.ProductionUiProjector(observer).capture(c, model.capture());
        var personal = runtime.discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        for (var row : projected.rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.LOGISTICS)) {
            String key = row.selection().stableId();
            assertFalse(key.startsWith("pilot-market|"), "Remote station knowledge cannot disclose dock-only market quotes");
            if (key.startsWith("pilot-station|")) {
                String id = key.substring("pilot-station|".length());
                assertTrue(personal.entries().stream().anyMatch(e -> e.object().objectId().equals(id)
                        && e.knownLocation().isPresent()), "Every personal station row requires received coordinates");
            }
        }
        assertEquals(before, c.captureState());
    }

    @Test void disclosedPurchaseConservesPaymentAndAcquiresExistingPhysicalStationWithoutStockGrants() {
        var c = FoundedCampaignFixture.restore(); var runtime = c.coordinator().runtime();
        var station = runtime.industry().industrial().stations().stream()
                .filter(s -> s.stationArchetypeId().equals("station.infrastructure.industrial_station"))
                .findFirst().orElseThrow();
        String id = station.stationId();
        assertFalse(c.previewPilotAction("PURCHASE_STATION", id, "", 0).allowed(), "Known coordinates cannot bypass actual docking");
        var fleet = runtime.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        // Explicit travel geometry isolates the purchase. Ownership, funding, stock and work are ordinary owners.
        if (!fleet.systemId().equals(station.systemId())) {
            runtime.world().beginFleetTransfer(fleet.id(), station.systemId());
            runtime.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = runtime.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = runtime.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty())
            local.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        local.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
        c.submitPilotAction(c.previewPilotAction("DOCK", id, "", 0));
        var personal = runtime.discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertTrue(personal.entries().stream().filter(e -> e.object().objectId().equals(id)
                && e.object().systemId().equals(station.systemId())).flatMap(e -> e.evidence().stream())
                .anyMatch(e -> e.source() == com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY),
                "Actual docking must retain visit evidence even when the position was already known");
        long price = c.stationSalePriceMilliCredits(id); assertEquals(70_000_000L, price);
        assertTrue(GeneratedCampaignStationAcquisitionUi.rows(c).stream()
                .anyMatch(r -> r.selection().stableId().equals("pilot-station-purchase|" + id)));
        var before = c.captureState(); var industrial = runtime.captureState().campaign().industrialState();
        var seller = runtime.world().findFactionEconomicState(station.stableFactionId()).orElseThrow();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var preview = c.previewPilotAction("PURCHASE_STATION", id, "", 0);
        assertTrue(preview.allowed()); assertEquals(-price, preview.walletChangeMilliCredits()); assertEquals(before, c.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(preview)); assertEquals(before, foreign.captureState());
        var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(runtime.world(), c.coordinator().content(), c.playerState().orElseThrow());
        var ownership = new com.spacesim.player.PlayerOwnershipService(adapter);
        var ref = c.pilotMarketReference(id).orElseThrow();
        var ownedRef = new com.spacesim.player.OwnedStationRef(ref.systemId(), ref.entityId());
        assertFalse(ownership.purchaseFactionStation(ownedRef, "faction.player", price));
        assertFalse(ownership.purchaseFactionStation(ownedRef, station.stableFactionId(), wallet + 1));
        assertFalse(ownership.purchaseFactionStation(ownedRef, station.stableFactionId(), 0));
        assertEquals(before, c.captureState(), "Rejected ownership requests must not debit player or credit any seller");
        c.submitPilotAction(preview);
        assertTrue(c.ownsProductionStation(id));
        assertEquals(wallet - price, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(seller.treasuryMilliCredits() + price,
                runtime.world().findFactionEconomicState(station.stableFactionId()).orElseThrow().treasuryMilliCredits());
        assertEquals(industrial, runtime.captureState().campaign().industrialState(), "Purchase cannot alter stock, capability, facilities or yard state");
        assertEquals(0, c.stationSalePriceMilliCredits(id));
        var bought = c.captureState(); assertFalse(c.previewPilotAction("PURCHASE_STATION", id, "", 0).allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview)); assertEquals(bought, c.captureState());
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(bought)));
        assertEquals(bought, restored.captureState()); assertTrue(restored.ownsProductionStation(id));
        assertEquals("PURCHASE_STATION", c.playerJournal().entries().get(c.playerJournal().entries().size() - 1).action());
    }
}

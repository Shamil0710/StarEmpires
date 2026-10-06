package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignOrdinaryDockContactTest {
    @Test void actualStarterBerthIsUsableBeforeArchiveKnowledgeAndOnlyDockRecordsTheVisit() {
        var campaign = Stage228CampaignAuthority.create(1);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var runtime = campaign.coordinator().runtime();
        var fleet = runtime.world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
        String station = "hub." + fleet.systemId().value();
        var archive = runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertTrue(archive.entries().stream().noneMatch(e -> e.object().objectId().equals(station)));
        var before = campaign.captureState();
        assertTrue(campaign.canDockAtPersonalStation(station), "The real starter is already within the existing berth envelope");
        assertFalse(campaign.canDockAtPersonalStation("missing"));
        var model = new GeneratedWorldUiModel(1, runtime, campaign.coordinator().content());
        var projector = new ProductionUiProjector("faction.beta");
        var rows = projector.capture(campaign, model.capture()).rows(Tab.LOGISTICS);
        assertTrue(projector.capture(campaign, model.capture()).rows(Tab.SHIPS).stream()
                .noneMatch(r -> r.selection().stableId().startsWith("pilot-reserve|")),
                "An undocked pilot cannot read another faction's reserve manifest");
        assertTrue(runtime.freight().capture().freighters().stream()
                .noneMatch(f -> campaign.canViewPersonalReserveOffer(f.fleetId())));
        var contact = rows.stream().filter(r -> r.selection().stableId().equals("pilot-station|" + station)).findFirst().orElseThrow();
        assertEquals("Местный док", contact.name());
        assertEquals("В пределах стыковки", contact.summary());
        assertNull(contact.focusSystem(), "No saved coordinate/navigation grant before the visit");
        assertTrue(rows.stream().noneMatch(r -> r.selection().stableId().startsWith("pilot-market|")), "Remote contact cannot disclose quotes or stock");
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (archive.entries().stream().anyMatch(e -> e.object().objectId().equals(endpoint.stationId()))
                    || campaign.canDockAtPersonalStation(endpoint.stationId())) continue;
            assertTrue(rows.stream().noneMatch(r -> r.selection().stableId().equals("pilot-station|" + endpoint.stationId())));
        }
        assertEquals(before, campaign.captureState(), "Pure contact projection must not add knowledge, money or time");
        var dock = campaign.previewPilotAction("DOCK", station, "", 0);
        assertTrue(dock.allowed()); assertEquals(before, campaign.captureState());
        campaign.submitPilotAction(dock);
        var reserveRows = projector.capture(campaign, model.capture()).rows(Tab.SHIPS).stream()
                .filter(r -> r.selection().stableId().startsWith("pilot-reserve|")).toList();
        var seller = runtime.freight().findFreighter(fleet.id()).orElseThrow().stableFactionId();
        var expectedOffers = runtime.freight().capture().freighters().stream()
                .filter(f -> !f.fleetId().equals(fleet.id()) && f.stableFactionId().equals(seller)
                        && f.phase() == com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE
                        && f.cargoMassKg() == 0d)
                .filter(f -> runtime.world().findFleet(f.fleetId()).orElseThrow().systemId().equals(fleet.systemId()))
                .toList();
        assertFalse(expectedOffers.isEmpty(), "The ordinary seller has another real reserve offer");
        assertEquals(expectedOffers.size(), reserveRows.size());
        for (var offered : expectedOffers) assertTrue(campaign.canViewPersonalReserveOffer(offered.fleetId()));
        for (var row : reserveRows) {
            assertNull(row.focusSystem(), "Sale disclosure is not physical detection of the fleet");
            assertEquals(0, row.focusFleet());
        }
        assertFalse(campaign.canViewPersonalReserveOffer(new com.spacesim.world.FleetId(Long.MAX_VALUE)));
        assertFalse(campaign.canDockAtPersonalStation(station), "An already docked ship cannot initiate another docking");
        var personal = runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertTrue(personal.entries().stream().anyMatch(e -> e.object().objectId().equals(station)
                && e.evidence().stream().anyMatch(v -> v.provenanceId().startsWith("personal-station-visit:"))));
        assertTrue(projector.capture(campaign, model.capture()).rows(Tab.LOGISTICS).stream()
                .anyMatch(r -> r.selection().stableId().equals("pilot-market|" + station + "|commodity.material.purified_water")));
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        long money = campaign.playerState().orElseThrow().walletMilliCredits();
        double stock = runtime.infrastructure().endpoint(station).storage().commodityMassKg("commodity.material.purified_water");
        var buy = campaign.previewPilotAction("BUY", station, "commodity.material.purified_water", 1);
        assertTrue(buy.allowed()); campaign.submitPilotAction(buy);
        assertEquals(money + buy.walletChangeMilliCredits(), campaign.playerState().orElseThrow().walletMilliCredits());
        assertEquals(stock - 1, runtime.infrastructure().endpoint(station).storage().commodityMassKg("commodity.material.purified_water"));
        assertEquals(1, runtime.freight().findFreighter(fleet.id()).orElseThrow().cargoMassKg());
        var saved = campaign.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }
}

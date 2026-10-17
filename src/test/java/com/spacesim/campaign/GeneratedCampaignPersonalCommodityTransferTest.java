package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerState;
import com.spacesim.world.LocalPhysicalKinematics;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit ownership/berth fixtures; stock and handling remain actual runtime quantities. */
@Tag("slow")
class GeneratedCampaignPersonalCommodityTransferTest {
    private static final String WATER = "commodity.material.purified_water";

    @Test void ownedWarehouseTransferConservesMoneyMassAndNativeHandlingWatermark() {
        var c = fixture(true);
        String station = station(c);
        var r = c.coordinator().runtime();
        var fleet = c.playerState().orElseThrow().activeFleetId();
        double beforeStock = r.infrastructure().endpoint(station).storage().commodityMassKg(WATER);
        long beforeMoney = c.playerState().orElseThrow().walletMilliCredits();
        var baseline = c.captureState();
        var load = c.previewPilotAction("LOAD_COMMODITY", station, WATER, 1);
        assertTrue(load.allowed()); assertEquals(0, load.walletChangeMilliCredits());
        assertEquals(baseline, c.captureState());
        c.submitPilotAction(load);
        var loadOwner = c;
        assertThrows(IllegalStateException.class, () -> loadOwner.submitPilotAction(load));
        assertEquals(beforeStock - 1, r.infrastructure().endpoint(station).storage().commodityMassKg(WATER));
        assertEquals(1, r.freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().get(WATER));
        assertEquals(beforeMoney, c.playerState().orElseThrow().walletMilliCredits());
        assertFalse(c.previewPilotAction("UNLOAD_COMMODITY", station, WATER, 1).allowed());
        assertFalse(c.previewPilotAction("BUY", station, WATER, 1).allowed());
        var loaded = c.captureState();
        c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(loaded)));
        assertEquals(loaded, c.captureState());
        assertFalse(c.previewPilotAction("UNLOAD_COMMODITY", station, WATER, 1).allowed());
        tick(c);
        var unload = c.previewPilotAction("UNLOAD_COMMODITY", station, WATER, 1);
        assertTrue(unload.allowed()); c.submitPilotAction(unload);
        assertEquals(beforeStock, c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(WATER));
        assertEquals(0, c.coordinator().runtime().freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(WATER, 0d));
        assertEquals(beforeMoney, c.playerState().orElseThrow().walletMilliCredits());
        assertTrue(c.playerJournal().entries().stream().anyMatch(e -> e.action().equals("UNLOAD_COMMODITY")
                && e.quantity() == 1 && e.walletDeltaMilliCredits() == 0));
        var complete = c.captureState();
        assertEquals(complete, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(complete))).captureState());
    }

    @Test void foreignWarehouseInvalidMassMissingCargoAndOverBudgetRejectPurely() {
        var c = fixture(false); String station = station(c); var before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_COMMODITY", station, WATER, 1).allowed());
        assertFalse(c.previewPilotAction("UNLOAD_COMMODITY", station, WATER, 1).allowed());
        assertEquals(before, c.captureState());
        c = fixture(true); station = station(c); before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_COMMODITY", station, WATER, 0).allowed());
        assertFalse(c.previewPilotAction("LOAD_COMMODITY", station, WATER, Integer.MAX_VALUE).allowed());
        assertFalse(c.previewPilotAction("UNLOAD_COMMODITY", station, WATER, 1).allowed());
        assertEquals(before, c.captureState());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0)); before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_COMMODITY", station, WATER, 1).allowed());
        assertEquals(before, c.captureState());
    }

    private static String station(Stage228CampaignAuthority c) {
        var dock = c.playerState().orElseThrow().dockedAt();
        return c.coordinator().runtime().infrastructure().endpoints().stream()
                .filter(e -> c.pilotMarketReference(e.stationId()).filter(dock::equals).isPresent())
                .findFirst().orElseThrow().stationId();
    }

    private static void tick(Stage228CampaignAuthority c) {
        c.coordinator().setPaused(false);
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
    }

    private static Stage228CampaignAuthority fixture(boolean own) {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        var initial = c;
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())
                && initial.pilotMarketReference(e.stationId()).isPresent() && e.storage().commodityMassKg(WATER) > 1)
                .findFirst().orElseThrow();
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        if (own) {
            var state = c.captureState(); var p = state.playerState(); var ref = p.dockedAt();
            var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                    p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                    p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
            c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(state.stage21Runtime(),
                    state.smallCraft(), state.hangars(), state.flightDeck(), state.operations(), owner));
        }
        tick(c); return c;
    }
}

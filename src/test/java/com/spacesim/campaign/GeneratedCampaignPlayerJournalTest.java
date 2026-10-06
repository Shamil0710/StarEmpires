package com.spacesim.campaign;

import com.spacesim.persistence.*;
import com.spacesim.player.PlayerJournalState;
import com.spacesim.ui.GeneratedCampaignJournalUi;
import com.spacesim.world.LocalPhysicalKinematics;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPlayerJournalTest {
    @Test void committedTradeHasActualTimeMoneyAndDurableNotificationsWithoutPreviewOrReplayEvents() {
        var c = FoundedCampaignFixture.restore();
        var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        String water = "commodity.material.purified_water";
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())
                && e.storage().commodityMassKg(water) >= 1 && c.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
        // Explicit geometry fixture only; actual docking and purchase use ordinary authority.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        var before = c.captureState();
        assertEquals("PILOT_START", before.playerJournal().entries().get(0).action());
        assertEquals("FOUND_FACTION", before.playerJournal().entries().get(before.playerJournal().entries().size() - 1).action());
        var dock = c.previewPilotAction("DOCK", endpoint.stationId(), "", 0);
        assertTrue(dock.allowed()); assertTrue(before.equals(c.captureState()), "Dock preview must be pure");
        assertFalse(c.previewPilotAction("BUY", endpoint.stationId(), water, 1).allowed());
        assertTrue(before.equals(c.captureState()), "Rejected purchase must create no history");
        c.submitPilotAction(dock);
        assertEquals(before.playerJournal().nextSequence() + 1, c.playerJournal().nextSequence());
        var docked = c.captureState();
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(dock));
        assertTrue(docked.equals(c.captureState()), "Replay must be pure");
        c.coordinator().setPaused(false);
        assertEquals(1, c.advanceFrame(c.coordinator().session().fixedStepSeconds()).fixedTicks());
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var ready = c.captureState();
        var buy = c.previewPilotAction("BUY", endpoint.stationId(), water, 1);
        assertTrue(buy.allowed()); assertTrue(ready.equals(c.captureState()), "Trade preview must be pure");
        c.submitPilotAction(buy);
        var journal = c.playerJournal();
        var receipt = journal.entries().get(journal.entries().size() - 1);
        assertEquals("BUY", receipt.action()); assertEquals(1, receipt.quantity());
        assertEquals(fleet.fleetId().value(), receipt.fleetId());
        assertEquals(r.world().getAuthoritativeWorldTick(), receipt.tick());
        assertEquals(c.playerState().orElseThrow().walletMilliCredits() - wallet, receipt.walletDeltaMilliCredits());
        assertEquals(endpoint.stationId(), receipt.endpoint()); assertEquals(water, receipt.subject());
        var physicalBeforeRead = c.captureState();
        var read = c.previewPilotAction("ACKNOWLEDGE_JOURNAL", Long.toString(journal.nextSequence() - 1), "", 0);
        assertTrue(read.allowed()); assertEquals(journal, c.playerJournal());
        c.submitPilotAction(read);
        assertEquals(0, c.playerJournal().unreadCount()); assertEquals(journal.entries(), c.playerJournal().entries());
        assertEquals(journal.nextSequence(), c.playerJournal().nextSequence());
        assertEquals(physicalBeforeRead.playerState(), c.playerState().orElseThrow());
        assertTrue(physicalBeforeRead.stage21Runtime().equals(c.captureState().stage21Runtime()), "Acknowledgement cannot alter physical or strategic state");
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(read));
        var saved = c.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertTrue(saved.equals(loaded.captureState()), "History and read watermark must survive composed save/load");
        var stale = loaded.previewPilotAction("ACKNOWLEDGE_JOURNAL", Long.toString(journal.nextSequence() - 1), "", 0);
        loaded.submitPilotAction(loaded.previewPilotAction("UNDOCK", "", "", 0));
        assertThrows(IllegalStateException.class, () -> loaded.submitPilotAction(stale));
        assertEquals(1, loaded.playerJournal().unreadCount());
        assertTrue(GeneratedCampaignJournalUi.rows(loaded).stream().anyMatch(row -> row.name().equals("Груз куплен")));
        assertEquals("1 непрочитанных", GeneratedCampaignJournalUi.rows(loaded).get(0).summary());
    }

    @Test void nativeV5MigrationPreservesPlayerAndPhysicalStateWithoutInventingHistoryAndRejectsFutureEvents() throws Exception {
        var saved = FoundedCampaignFixture.restore().captureState();
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x53323843); out.writeInt(5); out.writeInt(5); out.writeUTF("m22.8.generated-campaign.v5");
            payload(out, Stage21IGeneratedWorldRuntimePersistenceCodec.encode(saved.stage21Runtime()));
            payload(out, Stage228SmallCraftPersistenceCodec.encode(saved.smallCraft()));
            payload(out, Stage228HangarPersistenceCodec.encode(saved.hangars()));
            payload(out, Stage228FlightDeckPersistenceCodec.encode(saved.flightDeck()));
            payload(out, Stage228OperationsPersistenceCodec.encode(saved.operations()));
            payload(out, GeneratedCampaignPlayerStateCodec.encode(saved.playerState()));
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(bytes.toByteArray());
        assertEquals(Stage228GeneratedCampaignPersistentState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(PlayerJournalState.empty(), migrated.playerJournal());
        assertEquals(saved.playerState(), migrated.playerState());
        assertTrue(saved.stage21Runtime().equals(migrated.stage21Runtime()), "Migration must not alter accepted authorities");
        assertEquals(saved.operations(), migrated.operations()); assertEquals(saved.smallCraft(), migrated.smallCraft());
        assertTrue(migrated.equals(Stage228CampaignAuthority.restore(migrated).captureState()), "Loading cannot reconstruct fictional old receipts");
        var future = PlayerJournalState.empty().append(Long.MAX_VALUE, PlayerJournalState.Kind.COMMAND, "BUY", "", "", 1, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),
                saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), saved.playerState(), future));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),
                saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), null, saved.playerJournal()));
    }

    private static void payload(DataOutputStream out, byte[] bytes) throws Exception {
        out.writeInt(bytes.length); out.write(bytes);
    }
}

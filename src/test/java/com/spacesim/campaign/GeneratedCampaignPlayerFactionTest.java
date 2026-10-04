package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPlayerFactionTest {
    @Test void foundationIsPureSingleUseAndPreservesEveryPhysicalOwnerAndClock() {
        var c = started(); var before = c.captureState(); var world = c.coordinator().runtime().captureState().worldState();
        var preview = c.previewPlayerFactionFoundation("faction.player", "Содружество пилота");
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        var founded = c.submitPlayerFactionFoundation(preview);
        assertThrows(IllegalStateException.class, () -> c.submitPlayerFactionFoundation(preview));
        assertEquals(before, c.captureState());
        var player = founded.playerState().orElseThrow(); var updated = founded.coordinator().runtime().captureState().worldState();
        assertEquals("faction.player", player.factionContentId()); assertEquals(75_000_000L, player.walletMilliCredits());
        assertEquals(0L, founded.coordinator().runtime().world().findFactionEconomicState("faction.player").orElseThrow().treasuryMilliCredits());
        assertEquals(world.systems(), updated.systems()); assertEquals(world.fleets(), updated.fleets());
        assertEquals(world.fleetJumps(), updated.fleetJumps()); assertEquals(world.constructionProjects(), updated.constructionProjects());
        assertEquals(world.factions().size() + 1, updated.factions().size());
        assertEquals(before.playerState().ownedFleetIds(), player.ownedFleetIds());
        assertEquals(c.coordinator().runtime().captureState().freight(), founded.coordinator().runtime().captureState().freight());
        assertEquals(c.coordinator().actors().capture(), founded.coordinator().actors().capture());
        assertEquals(before.smallCraft(), founded.captureState().smallCraft()); assertEquals(before.hangars(), founded.captureState().hangars());
        assertEquals(before.flightDeck(), founded.captureState().flightDeck()); assertEquals(before.operations(), founded.captureState().operations());
        roundtrip(founded);
        assertFalse(founded.previewPlayerFactionFoundation("faction.second", "Вторая").allowed());
    }

    @Test void uninitializedCollisionForeignAndStaleFoundationRequestsRejectUnchanged() {
        var empty = Stage228CampaignAuthority.create(1); var pristine = empty.captureState();
        assertFalse(empty.previewPlayerFactionFoundation("faction.player", "Содружество").allowed());
        assertEquals(pristine, empty.captureState());
        empty.submitIndependentPilotStart(empty.previewIndependentPilotStart()); var before = empty.captureState();
        assertFalse(empty.previewPlayerFactionFoundation("faction.alpha", "Дубликат").allowed());
        var preview = empty.previewPlayerFactionFoundation("faction.player", "Содружество");
        var other = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> other.submitPlayerFactionFoundation(preview)); assertEquals(before, other.captureState());
        empty.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> empty.submitPlayerFactionFoundation(preview));
    }

    @Test void realPersonalCapitalAndTreasuryReturnConserveFundsWithPureAdmission() {
        var independent = started();
        assertFalse(independent.previewPilotAction("CAPITALIZE", "1000000", "", 0).allowed());
        var c = independent.submitPlayerFactionFoundation(independent.previewPlayerFactionFoundation("faction.player", "Содружество"));
        var before = c.captureState();
        assertFalse(c.previewPilotAction("WITHDRAW", "1000000", "", 0).allowed());
        assertFalse(c.previewPilotAction("CAPITALIZE", "-1", "", 0).allowed());
        assertFalse(c.previewPilotAction("CAPITALIZE", Long.toString(Long.MAX_VALUE), "", 0).allowed());
        assertEquals(before, c.captureState());
        var deposit = c.previewPilotAction("CAPITALIZE", "1000000", "", 0);
        assertTrue(deposit.allowed()); assertEquals(-1_000_000L, deposit.walletChangeMilliCredits()); assertEquals(before, c.captureState());
        c.submitPilotAction(deposit);
        assertEquals(74_000_000L, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1_000_000L, c.coordinator().runtime().world().findFactionEconomicState("faction.player").orElseThrow().treasuryMilliCredits());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(deposit));
        var returned = c.previewPilotAction("WITHDRAW", "1000000", "", 0);
        assertTrue(returned.allowed()); assertEquals(1_000_000L, returned.walletChangeMilliCredits()); c.submitPilotAction(returned);
        assertEquals(75_000_000L, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(0L, c.coordinator().runtime().world().findFactionEconomicState("faction.player").orElseThrow().treasuryMilliCredits());
        roundtrip(c);
    }
    private static Stage228CampaignAuthority started() { var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart()); return c; }
    private static void roundtrip(Stage228CampaignAuthority c) {
        var before = c.captureState(); var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(before)));
        assertEquals(before, restored.captureState());
    }
}

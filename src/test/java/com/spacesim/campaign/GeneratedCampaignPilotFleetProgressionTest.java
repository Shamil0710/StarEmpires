package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPilotFleetProgressionTest {
    @Test void localReservePurchasePaysRealSellerWithoutCreatingAnotherHull() {
        var c = started(); var r = c.coordinator().runtime(); var world = r.world();
        var target = reserve(c); dock(c);
        var baseline = c.captureState(); long fleetCount = world.getFleetPlacements().size();
        long treasury = world.findFactionEconomicState(target.stableFactionId()).orElseThrow().treasuryMilliCredits();
        var preview = c.previewPilotAction("PURCHASE", Long.toString(target.fleetId().value()), "", 0);
        assertTrue(preview.allowed()); assertEquals(-25_000_000L, preview.walletChangeMilliCredits());
        assertEquals(baseline, c.captureState());
        var active = c.playerState().orElseThrow().activeFleetId();
        c.submitPilotAction(preview);
        assertEquals(50_000_000L, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + 25_000_000L, world.findFactionEconomicState(target.stableFactionId()).orElseThrow().treasuryMilliCredits());
        assertEquals(fleetCount, world.getFleetPlacements().size());
        assertEquals(active, c.playerState().orElseThrow().activeFleetId());
        assertEquals(2, c.playerState().orElseThrow().ownedFleetIds().size());
        assertEquals(target, r.freight().findFreighter(target.fleetId()).orElseThrow());
        var receipt = c.playerJournal().entries().get(c.playerJournal().entries().size() - 1);
        assertEquals("PURCHASE", receipt.action()); assertEquals(target.fleetId().value(), receipt.fleetId());
        assertEquals(-25_000_000L, receipt.walletDeltaMilliCredits()); assertEquals(1, receipt.quantity());
        assertFalse(c.previewPilotAction("PURCHASE", Long.toString(target.fleetId().value()), "", 0).allowed());
        roundtrip(c);
    }

    @Test void handoverRequiresOwnedLocalTargetAndStoppedUndockedCurrentShip() {
        var c = started(); var target = reserve(c); dock(c);
        c.submitPilotAction(c.previewPilotAction("PURCHASE", Long.toString(target.fleetId().value()), "", 0));
        assertFalse(c.previewPilotAction("SWITCH", Long.toString(target.fleetId().value()), "", 0).allowed());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        var r = c.coordinator().runtime(); var current = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var material = r.arrival().materialization(current.systemId());
        var exact = material.physicalState(current.localEntityId()).orElseThrow();
        material.updatePhysicalState(current.localEntityId(), new LocalPhysicalKinematics(exact.position(), 5, 0));
        assertFalse(c.previewPilotAction("SWITCH", Long.toString(target.fleetId().value()), "", 0).allowed());
        material.updatePhysicalState(current.localEntityId(), LocalPhysicalKinematics.stationary(exact.position()));
        var unowned = reserve(c);
        assertFalse(c.previewPilotAction("SWITCH", Long.toString(unowned.fleetId().value()), "", 0).allowed());
        var baseline = c.captureState(); var preview = c.previewPilotAction("SWITCH", Long.toString(target.fleetId().value()), "", 0);
        assertTrue(preview.allowed()); assertEquals(baseline, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(target.fleetId(), c.playerState().orElseThrow().activeFleetId());
        var handover = c.playerJournal().entries().get(c.playerJournal().entries().size() - 1);
        assertEquals("SWITCH", handover.action()); assertEquals(target.fleetId().value(), handover.fleetId());
        assertEquals(0, handover.walletDeltaMilliCredits());
        assertEquals(50_000_000L, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(exact.position(), material.physicalState(current.localEntityId()).orElseThrow().position());
        assertTrue(c.setPilotThrust(0, 0, false));
        assertTrue(r.world().findSession(current.systemId()).orElseThrow().getEngine()
                .getSystem(com.spacesim.systems.PlayerDirectControlSystem.class) == null);
        roundtrip(c);
    }

    @Test void foreignRemoteAndStaleOffersRejectWithoutTakingFundsOrOwnership() {
        var c = started(); var target = reserve(c);
        assertFalse(c.previewPilotAction("PURCHASE", Long.toString(target.fleetId().value()), "", 0).allowed());
        dock(c); var baseline = c.captureState();
        var preview = c.previewPilotAction("PURCHASE", Long.toString(target.fleetId().value()), "", 0);
        var other = Stage228CampaignAuthority.restore(baseline);
        assertThrows(IllegalStateException.class, () -> other.submitPilotAction(preview));
        assertEquals(baseline, other.captureState());
        var remote = c.coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> !f.currentSystemId().equals(target.currentSystemId())).findFirst().orElseThrow();
        assertFalse(c.previewPilotAction("PURCHASE", Long.toString(remote.fleetId().value()), "", 0).allowed());
        assertFalse(c.previewPilotAction("PURCHASE", "9223372036854775807", "", 0).allowed());
        assertEquals(baseline, c.captureState());
        c.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
    }

    private static Stage228CampaignAuthority started() { var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart()); return c; }
    private static com.spacesim.persistence.Stage20FreightPersistentState.FreighterState reserve(Stage228CampaignAuthority c) {
        var player = c.playerState().orElseThrow(); var current = c.coordinator().runtime().world().findFleet(player.activeFleetId()).orElseThrow();
        return c.coordinator().runtime().freight().capture().freighters().stream().filter(f -> f.phase() == FreightPhase.IDLE
                && f.currentSystemId().equals(current.systemId()) && !player.ownedFleetIds().contains(f.fleetId())).findFirst().orElseThrow();
    }
    private static void dock(Stage228CampaignAuthority c) {
        var r = c.coordinator().runtime(); var p = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(p.systemId()) && c.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
        // Explicit range fixture; ownership, seller payment and commands remain ordinary production paths.
        r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
    }
    private static void roundtrip(Stage228CampaignAuthority c) {
        var before = c.captureState(); var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(before)));
        assertEquals(before, restored.captureState());
    }
}

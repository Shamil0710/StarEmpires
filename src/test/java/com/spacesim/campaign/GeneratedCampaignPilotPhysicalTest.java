package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage20FreightPersistenceCodec;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPilotPhysicalTest {
    private static final String WATER = "commodity.material.purified_water";

    @Test void exactMovementCrossesCellWithFiniteFuelAndCoastsWithoutInput() {
        var c = started(); var r = c.coordinator().runtime();
        var placement = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var material = r.arrival().materialization(placement.systemId());
        var position = new LocalPhysicalPosition(123456789L, 0, LocalPhysicalPosition.HALF_CELL_SIZE_M - 0.01, 0);
        material.updatePhysicalState(placement.localEntityId(), new LocalPhysicalKinematics(position, 100d, 0d));
        var fitted = r.world().findSession(placement.systemId()).orElseThrow().getEntityRegistry().require(placement.localEntityId()).getComponent(EngineeringComponent.class);
        double fuel = fitted.runtimeState.consumables().interfaceLoads().stream().mapToDouble(v -> v.amount()).sum();
        c.setPilotThrust(1, 0, false);
        c.advanceFrame(0.001f);
        assertEquals(new LocalPhysicalKinematics(position, 100d, 0d), material.physicalState(placement.localEntityId()).orElseThrow());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var moved = material.physicalState(placement.localEntityId()).orElseThrow();
        assertEquals(position.cellX() + 1, moved.position().cellX());
        assertTrue(moved.velocityXMps() > 100d);
        assertTrue(fitted.runtimeState.consumables().interfaceLoads().stream().mapToDouble(v -> v.amount()).sum() < fuel);
        c.setPilotThrust(0, 0, false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var coasted = material.physicalState(placement.localEntityId()).orElseThrow();
        assertEquals(moved.velocityXMps(), coasted.velocityXMps());
        c.setPilotThrust(0, 0, true); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(material.physicalState(placement.localEntityId()).orElseThrow().velocityXMps() < coasted.velocityXMps());
        roundtrip(c);
    }

    @Test void dockRequiresExactRangeAndLowSpeedThenPhysicalTradeConservesCargoAndMoney() {
        var c = legacyStarted(); var endpoint = market(c);
        position(c, new LocalPhysicalKinematics(endpoint.position().translated(2000, 0), 0, 0));
        assertFalse(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0).allowed());
        position(c, new LocalPhysicalKinematics(endpoint.position().translated(500, 0), 5, 0));
        assertFalse(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0).allowed());
        position(c, LocalPhysicalKinematics.stationary(endpoint.position().translated(500, 0)));
        var before = c.captureState(); var dock = c.previewPilotAction("DOCK", endpoint.stationId(), "", 0);
        assertTrue(dock.allowed()); assertEquals(before, c.captureState()); c.submitPilotAction(dock);
        assertTrue(c.playerState().orElseThrow().docked());
        assertFalse(c.setPilotThrust(1, 0, false));
        assertFalse(c.previewPilotAction("BUY", endpoint.stationId(), WATER, 1).allowed()); // No elapsed handling tick.
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var fleet = c.playerState().orElseThrow().activeFleetId();
        var ref = c.pilotMarketReference(endpoint.stationId()).orElseThrow();
        var wallet = c.coordinator().runtime().world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId()).getComponent(WalletComponent.class);
        double stock = endpoint.storage().commodityMassKg(WATER); long personal = c.playerState().orElseThrow().walletMilliCredits(); long station = wallet.getBalanceMilliCredits();
        var buy = c.previewPilotAction("BUY", endpoint.stationId(), WATER, 1);
        assertTrue(buy.allowed()); assertEquals(-5000, buy.walletChangeMilliCredits()); c.submitPilotAction(buy);
        assertEquals(stock - 1, endpoint.storage().commodityMassKg(WATER));
        assertEquals(1d, c.coordinator().runtime().freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().get(WATER));
        assertEquals(personal - 5000, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(station + 5000, wallet.getBalanceMilliCredits());
        assertFalse(c.previewPilotAction("SELL", endpoint.stationId(), WATER, 1).allowed());
        var restored = roundtrip(c);
        assertFalse(restored.previewPilotAction("SELL", endpoint.stationId(), WATER, 1).allowed());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var sell = c.previewPilotAction("SELL", endpoint.stationId(), WATER, 1);
        assertTrue(sell.allowed()); c.submitPilotAction(sell);
        assertEquals(0d, c.coordinator().runtime().freight().findFreighter(fleet).orElseThrow().cargoMassKg());
        assertEquals(personal - 500, c.playerState().orElseThrow().walletMilliCredits());
        roundtrip(c);
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        assertFalse(c.playerState().orElseThrow().docked());
    }

    @Test void previewRejectsInsufficientOrOverBudgetCargoWithoutMutationAndCannotCrossCampaigns() {
        var c = started(); var endpoint = market(c);
        position(c, LocalPhysicalKinematics.stationary(endpoint.position().translated(500, 0)));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0)); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        for (int amount : new int[]{0, -1, Integer.MAX_VALUE}) {
            var before = c.captureState(); assertFalse(c.previewPilotAction("BUY", endpoint.stationId(), WATER, amount).allowed()); assertEquals(before, c.captureState());
        }
        assertFalse(c.previewPilotAction("SELL", endpoint.stationId(), WATER, 1).allowed());
        var buy = c.previewPilotAction("BUY", endpoint.stationId(), WATER, 1);
        var restored = roundtrip(c); var before = restored.captureState();
        assertThrows(IllegalStateException.class, () -> restored.submitPilotAction(buy)); assertEquals(before, restored.captureState());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds()); before = c.captureState();
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(buy)); assertEquals(before, c.captureState());
    }

    @Test void historicalFreightSchemaMigratesWithoutCargoAndManualCargoCannotLosePersonalOwner() throws Exception {
        var c = Stage228CampaignAuthority.create(1);
        var freight = c.coordinator().runtime().freight().capture();
        byte[] old;
        try (var in = new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/campaign/stage23b-freight-v2.s20f.gz"))) { old = in.readAllBytes(); }
        // Explicit synthetic v1 header over genuine v2's identical historical layout, without current v3 fields.
        java.nio.ByteBuffer.wrap(old).putInt(8, 1);
        var original = old.clone();
        assertEquals(freight, Stage20FreightPersistenceCodec.decode(old));
        assertEquals(3, Stage20FreightPersistenceCodec.decode(old).schemaVersion());
        assertArrayEquals(original, old);
        byte[] future = Stage20FreightPersistenceCodec.encode(freight);
        java.nio.ByteBuffer.wrap(future).putInt(8, 4);
        assertThrows(IllegalArgumentException.class, () -> Stage20FreightPersistenceCodec.decode(future));
        c.submitIndependentPilotStart(c.previewIndependentPilotStart()); var endpoint = market(c);
        position(c, LocalPhysicalKinematics.stationary(endpoint.position().translated(500, 0)));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0)); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.submitPilotAction(c.previewPilotAction("BUY", endpoint.stationId(), WATER, 1));
        var save = c.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(save.stage21Runtime(), save.smallCraft(), save.hangars(), save.flightDeck(), save.operations(), null));
        var cargo = c.coordinator().runtime().freight().capture(); old = Stage20FreightPersistenceCodec.encode(cargo);
        java.nio.ByteBuffer.wrap(old).putInt(8, 1); final byte[] invalidOld = old;
        assertThrows(IllegalArgumentException.class, () -> Stage20FreightPersistenceCodec.decode(invalidOld));
    }

    // Genuine accepted v1 checkpoint retains all original numeric settlement assertions.
    private static Stage228CampaignAuthority legacyStarted() {
        try (var input = new java.util.zip.GZIPInputStream(GeneratedCampaignPilotPhysicalTest.class
                .getResourceAsStream("/campaign/stage23b-pilot-opening-v1.s25.gz"))) {
            return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(input.readAllBytes()));
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    private static Stage228CampaignAuthority started() { var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart()); return c; }
    private static com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint market(Stage228CampaignAuthority c) {
        var p = c.coordinator().runtime().world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return c.coordinator().runtime().infrastructure().endpoints().stream().filter(e -> e.systemId().equals(p.systemId()) && e.storage().commodityMassKg(WATER) > 1 && c.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
    }
    private static void position(Stage228CampaignAuthority c, LocalPhysicalKinematics physical) {
        var p = c.coordinator().runtime().world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        c.coordinator().runtime().arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), physical);
    }
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) {
        var checkpoint = c.captureState(); var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(checkpoint)));
        assertEquals(checkpoint, restored.captureState()); return restored;
    }
}

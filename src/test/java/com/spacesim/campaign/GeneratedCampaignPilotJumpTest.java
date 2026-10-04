package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPilotJumpTest {
    @Test void departurePreviewIsPureForeignAndStaleTokensRejectAndOnlyNeighborsAreAdmitted() {
        var c = started(); var r = c.coordinator().runtime(); var p = placement(c);
        var destination = r.world().getTopology().neighbors(p.systemId()).get(0);
        var baseline = c.captureState();
        var preview = c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0);
        assertEquals(baseline, c.captureState());
        assertTrue(preview.allowed());
        var other = Stage228CampaignAuthority.restore(baseline);
        assertThrows(IllegalStateException.class, () -> other.submitPilotAction(preview));
        assertEquals(baseline, other.captureState());
        assertFalse(c.previewPilotAction("JUMP", Long.toString(p.systemId().value()), "", 0).allowed());
        assertFalse(c.previewPilotAction("JUMP", "9223372036854775807", "", 0).allowed());
        c.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var model = new GeneratedWorldUiModel(1, r, c.coordinator().content());
        var beforeProjection = c.captureState();
        assertTrue(new ProductionUiProjector("faction.beta").capture(c, model.capture()).rows(Tab.SHIPS).stream()
                .anyMatch(row -> row.selection().stableId().equals("pilot-jump|" + destination.value())));
        assertEquals(beforeProjection, c.captureState());
    }

    @Test void dockedOrFuelEmptyShipCannotDepartAndPreviewDoesNotRefuelFromStation() {
        var c = started(); var r = c.coordinator().runtime(); var p = placement(c);
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(p.systemId())
                && c.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
        r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        var destination = r.world().getTopology().neighbors(p.systemId()).get(0);
        assertFalse(c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0).allowed());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        var fitted = r.world().findSession(p.systemId()).orElseThrow().getEntityRegistry().require(p.localEntityId()).getComponent(EngineeringComponent.class);
        var old = fitted.runtimeState; var stores = old.consumables();
        var loads = stores.interfaceLoads().stream().map(v -> v.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS
                ? new com.spacesim.ship.ShipEngineeringState.ConsumableLoad(v.mountId(), v.interfaceId(), v.kind(), 0, 0, v.itemCount()) : v).toList();
        fitted.setRuntimeState(new com.spacesim.ship.ShipEngineeringRuntime.RuntimeState(
                new com.spacesim.ship.ShipEngineeringState.ConsumableState(stores.cargoMassKg(), stores.storesMassKg(), stores.missionPayloadMassKg(), stores.missionIntegrationVolumeM3(), loads),
                old.sharedBusEnergyJ(), old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(), old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
        var baseline = c.captureState();
        assertFalse(c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0).allowed());
        assertEquals(baseline, c.captureState());
    }

    @Test void realHopKeepsPersonalCargoAndExactStateAcrossTransitAndArrivalReload() {
        var c = started(); final var initial = c; var r = c.coordinator().runtime(); var p = placement(c);
        String water = "commodity.material.purified_water";
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(p.systemId())
                && e.storage().commodityMassKg(water) > 1 && initial.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
        r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        long paidQuote = c.pilotCommodityPrice(endpoint.stationId(), water, true);
        c.submitPilotAction(c.previewPilotAction("BUY", endpoint.stationId(), water, 1));
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        var destination = r.world().getTopology().neighbors(p.systemId()).get(0);
        // Explicit geometry fixture avoids testing human navigation; all jump phases use ordinary ticks.
        var departure = r.arrival().resolve(destination, p.systemId());
        r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), LocalPhysicalKinematics.stationary(departure.physicalState().position()));
        c.submitPilotAction(c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0));
        assertFalse(c.setPilotThrust(1, 0, false));
        boolean sawTransit = false;
        for (int i = 0; i < 2000 && c.coordinator().runtime().world().findFleetJump(p.id()).isPresent(); i++) {
            c.advanceFrame(0.25f);
            var jump = c.coordinator().runtime().world().findFleetJump(p.id()).orElse(null);
            if (!sawTransit && jump != null && jump.phase() == FleetJumpPhase.IN_TRANSIT) {
                assertEquals(FleetLocationKind.IN_TRANSIT, placement(c).locationKind());
                assertFalse(c.playerState().orElseThrow().discoveredSystemIds().contains(destination));
                c = roundtrip(c); sawTransit = true;
            }
        }
        assertTrue(sawTransit); assertTrue(c.coordinator().runtime().world().findFleetJump(p.id()).isEmpty());
        var arrived = placement(c); assertEquals(destination, arrived.systemId());
        assertTrue(c.playerState().orElseThrow().discoveredSystemIds().contains(destination));
        var freight = c.coordinator().runtime().freight().findFreighter(p.id()).orElseThrow();
        assertEquals(destination, freight.currentSystemId());
        assertEquals(1d, freight.cargoStorage().commodityMassByIdKg().get(water));
        assertEquals(1, c.coordinator().runtime().freight().capture().cargoLots().stream().filter(l -> l.fleetId().equals(p.id())).count());
        var loaded = roundtrip(c); assertFalse(loaded.canStartIndependentPilot());
        assertEquals(75_000_000L - paidQuote, loaded.playerState().orElseThrow().walletMilliCredits());
    }
    private static Stage228CampaignAuthority started() { var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart()); return c; }
    private static com.spacesim.world.FleetPlacementState placement(Stage228CampaignAuthority c) { return c.coordinator().runtime().world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow(); }
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) {
        var before = c.captureState(); var result = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(before)));
        assertEquals(before, result.captureState()); return result;
    }
}

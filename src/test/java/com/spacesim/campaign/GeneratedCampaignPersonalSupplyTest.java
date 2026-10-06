package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.world.LocalPhysicalKinematics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalSupplyTest {
    @Test void purchasedCargoLoadsTankWithConservedMassMoneyAndPersistentProvenance() {
        var c = dockedFixture(true);
        var fleet = c.playerState().orElseThrow().activeFleetId();
        var r = c.coordinator().runtime();
        var fitted = fitted(c);
        var binding = binding(fitted);
        var mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var before = c.captureState();
        var old = fitted.runtimeState;
        double mass = new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted).totalMassKg();
        var preview = c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), mount, 1);
        assertTrue(preview.allowed());
        assertEquals(0, preview.walletChangeMilliCredits());
        assertEquals(before, c.captureState());
        var rows = com.spacesim.ui.GeneratedCampaignSupplyUi.rows(c);
        assertTrue(rows.stream().anyMatch(row -> row.selection().stableId().equals("pilot-supply|" + binding.id() + "|" + mount)));
        assertEquals(before, c.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(preview));
        c.submitPilotAction(preview);
        assertEquals(0d, r.freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(binding.commodityId(), 0d));
        assertTrue(r.freight().capture().cargoLots().stream().noneMatch(l -> l.fleetId().equals(fleet)));
        assertEquals(mass, new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted).totalMassKg(), 1e-6);
        assertEquals(old.sharedBusEnergyJ(), fitted.runtimeState.sharedBusEnergyJ());
        assertEquals(old.shipHeatStoredJ(), fitted.runtimeState.shipHeatStoredJ());
        assertEquals(old.localHeatJByMount(), fitted.runtimeState.localHeatJByMount());
        assertEquals(before.playerState(), c.playerState().orElseThrow());
        assertEquals(before.stage21Runtime().stage21HRuntime().npcMissionState(), c.coordinator().npcMissions());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var saved = c.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }

    @Test void emptyHoldUnknownInterfaceOverfillAndUndockedServiceRejectWithoutMutation() {
        var c = dockedFixture(false);
        var binding = binding(fitted(c));
        var mount = fitted(c).fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), mount, 1).allowed());
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", "unknown", mount, 1).allowed());
        assertEquals(before, c.captureState());
        c = dockedFixture(true);
        before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), "foreign-mount", 1).allowed());
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), mount, 0).allowed());
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), mount, 10000).allowed());
        assertEquals(before, c.captureState());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_CONSUMABLE", binding.id(), mount, 1).allowed());
        assertEquals(before, c.captureState());
    }

    private static Stage228CampaignAuthority dockedFixture(boolean buy) {
        var c = FoundedCampaignFixture.restore();
        var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())
                && e.storage().commodityMassKg("commodity.material.purified_water") > 1).findFirst().orElseThrow();
        // Explicit berth geometry and previously spent tank mass; no station or hold stock is granted.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        var component = fitted(c);
        var old = component.runtimeState;
        var stores = old.consumables();
        var loads = stores.interfaceLoads().stream().map(l -> l.kind() == InterfaceKind.REACTION_MASS
                ? new ConsumableLoad(l.mountId(), l.interfaceId(), l.kind(), l.amount() - 2, l.massKg() - 2, l.itemCount()) : l).toList();
        component.setRuntimeState(new RuntimeState(new ConsumableState(stores.cargoMassKg(), stores.storesMassKg(),
                stores.missionPayloadMassKg(), stores.missionIntegrationVolumeM3(), loads), old.sharedBusEnergyJ(),
                old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(), old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        if (buy) {
            c.advanceFrame(c.coordinator().session().fixedStepSeconds());
            c.submitPilotAction(c.previewPilotAction("BUY", endpoint.stationId(), "commodity.material.purified_water", 1));
        }
        return c;
    }

    private static EngineeringComponent fitted(Stage228CampaignAuthority c) {
        var world = c.coordinator().runtime().world();
        var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).getComponent(EngineeringComponent.class);
    }

    private static com.spacesim.content.Stage18ShipConsumableCatalog.ShipConsumableBinding binding(EngineeringComponent c) {
        return Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream().filter(b -> c.fit.installedModules().stream()
                .anyMatch(m -> m.moduleId().equals(b.moduleId()))).findFirst().orElseThrow();
    }
}

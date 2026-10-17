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
    @Test void paidStationSupplyConservesStockMoneyAndTankWithoutCreatingCargoAndSharesHandlingAfterRestore() {
        var c = dockedFixture(false);
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
        var r = c.coordinator().runtime(); var world = r.world(); var player = c.playerState().orElseThrow();
        var fitted = fitted(c); var binding = binding(fitted);
        var mount = fitted.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> c.pilotMarketReference(e.stationId())
                .filter(player.dockedAt()::equals).isPresent()).findFirst().orElseThrow();
        var stationWallet = world.findSession(player.dockedAt().systemId()).orElseThrow().getEntityRegistry()
                .require(player.dockedAt().entityId()).getComponent(com.spacesim.components.WalletComponent.class);
        long stationMoney = stationWallet.getBalanceMilliCredits();
        long quotedPrice = c.pilotCommodityPrice(endpoint.stationId(), binding.commodityId(), true);
        double stock = endpoint.storage().commodityMassKg(binding.commodityId());
        var freight = r.freight().capture(); var old = fitted.runtimeState;
        double mass = new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted).reactionMassKg();
        var before = c.captureState();
        var preview = c.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, 1);
        assertTrue(preview.allowed()); assertTrue(preview.walletChangeMilliCredits() < 0);
        assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(stock - 1, endpoint.storage().commodityMassKg(binding.commodityId()));
        assertEquals(mass + 1, new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted).reactionMassKg(), 1e-7);
        assertEquals(player.walletMilliCredits() + preview.walletChangeMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(stationMoney + quotedPrice, stationWallet.getBalanceMilliCredits());
        assertEquals(freight.cargoLots(), r.freight().capture().cargoLots());
        assertEquals(freight.nextCargoLotOrdinal(), r.freight().capture().nextCargoLotOrdinal());
        assertEquals(old.sharedBusEnergyJ(), fitted.runtimeState.sharedBusEnergyJ());
        assertEquals(old.shipHeatStoredJ(), fitted.runtimeState.shipHeatStoredJ());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertFalse(restored.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, 1).allowed());
        assertFalse(restored.previewPilotAction("BUY", endpoint.stationId(), binding.commodityId(), 1).allowed());
        assertEquals(saved, restored.captureState());
        restored.coordinator().setPaused(false); restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        restored.coordinator().setPaused(true);
        assertTrue(restored.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, 1).allowed());
    }

    @Test void rejectedPaidStationServiceCannotSpendStockMoneyOrFillAnUnavailableInterface() {
        var c = dockedFixture(false);
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
        var binding = binding(fitted(c));
        var mount = fitted(c).fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var before = c.captureState();
        assertFalse(c.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, 3).allowed());
        assertFalse(c.previewPilotAction("BUY_CONSUMABLE", binding.id(), "missing-mount", 1).allowed());
        assertFalse(c.previewPilotAction("BUY_CONSUMABLE", "unknown-binding", mount, 1).allowed());
        assertEquals(before, c.captureState());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        before = c.captureState();
        assertFalse(c.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, 1).allowed());
        assertEquals(before, c.captureState());
    }

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

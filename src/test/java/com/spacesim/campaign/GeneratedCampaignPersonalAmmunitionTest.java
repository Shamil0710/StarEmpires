package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.PlayerState;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ui.GeneratedCampaignAmmunitionUi;
import com.spacesim.ui.GeneratedCampaignJournalUi;
import com.spacesim.world.LocalPhysicalKinematics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit weapon, own-station, stock and berth fixtures; does not prove ordinary acquisition. */
@Tag("slow")
class GeneratedCampaignPersonalAmmunitionTest {
    @Test void loadingConservesPhysicalRoundsMassAndIdentityAndSharesHandlingAfterNativeRestore() {
        var c = fixture(4); var service = new ShipAmmunitionSupplyService();
        var feed = service.feeds(ship(c)).get(0); var station = c.dockedAmmunitionStationId().orElseThrow();
        var storage = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        var old = ship(c).runtimeState; var instance = ship(c).instanceState;
        var before = c.captureState();
        double mass = new ProductionEngineeringRuntimeResolver().derive(ship(c)).totalMassKg();
        var preview = c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1);
        assertTrue(preview.allowed()); assertEquals(0, preview.walletChangeMilliCredits());
        var rows = GeneratedCampaignAmmunitionUi.rows(c);
        assertTrue(rows.stream().anyMatch(row -> row.selection().stableId().equals("pilot-ammunition|" + station + '|' + feed.mountId())));
        assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(3, storage.productCount(feed.productId()));
        assertEquals(1, ship(c).runtimeState.consumables().ammunitionCount());
        assertEquals(mass + feed.unitMassKg(), new ProductionEngineeringRuntimeResolver().derive(ship(c)).totalMassKg(), 1e-6);
        assertEquals(instance, ship(c).instanceState);
        assertEquals(old.sharedBusEnergyJ(), ship(c).runtimeState.sharedBusEnergyJ());
        assertEquals(old.shipHeatStoredJ(), ship(c).runtimeState.shipHeatStoredJ());
        assertEquals(before.playerState(), c.playerState().orElseThrow());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var saved = c.captureState(); var restored = roundTrip(c);
        assertEquals(saved, restored.captureState());
        assertEquals(rows.get(0).selection(), GeneratedCampaignAmmunitionUi.rows(restored).get(0).selection());
        assertFalse(restored.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        String commodity = storage.snapshotCommodityMassByIdKg().entrySet().stream()
                .filter(e -> e.getValue() >= 1).findFirst().orElseThrow().getKey();
        assertFalse(restored.previewPilotAction("LOAD_COMMODITY", station, commodity, 1).allowed());
        assertEquals(saved, restored.captureState());
        assertEquals(1, restored.playerJournal().entries().stream().filter(e -> e.action().equals("LOAD_AMMUNITION")).count());
        assertTrue(GeneratedCampaignJournalUi.rows(restored).stream().flatMap(row -> row.sections().stream())
                .flatMap(section -> section.lines().stream()).anyMatch(line -> line.label().equals("Количество") && line.value().equals("1 шт.")));
        restored.coordinator().setPaused(false); restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        restored.coordinator().setPaused(true);
        assertTrue(restored.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
    }

    @Test void emptyStockUnknownMountRemoteStationAndOverlargeCountRejectWithoutMutation() {
        var c = fixture(0); var station = c.dockedAmmunitionStationId().orElseThrow();
        var feed = new ShipAmmunitionSupplyService().feeds(ship(c)).get(0); var baseline = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, "missing", 1).allowed());
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", "remote", feed.mountId(), 1).allowed());
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 0).allowed());
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), Integer.MAX_VALUE).allowed());
        assertEquals(baseline, c.captureState());
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        baseline = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        assertTrue(GeneratedCampaignAmmunitionUi.rows(c).isEmpty());
        assertEquals(baseline, c.captureState());
    }

    @Test void foreignStationAndAnonymousOrIncompatibleFeedCannotGrantServiceOrRevealStock() {
        var c = fixture(4); var feed = new ShipAmmunitionSupplyService().feeds(ship(c)).get(0);
        var station = c.dockedAmmunitionStationId().orElseThrow(); var p = c.playerState().orElseThrow();
        var foreign = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), List.of());
        var other = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                c.captureState(), c.coordinator().runtime().captureState(), foreign));
        var before = other.captureState();
        assertFalse(other.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        assertTrue(GeneratedCampaignAmmunitionUi.rows(other).isEmpty()); assertEquals(before, other.captureState());
        var component = ship(c); var state = component.instanceState;
        component.setInstanceState(new ShipInstanceRuntimeState(state.damage(), state.shieldStatesByMount(), state.maintenance(),
                new WeaponLoadoutState(List.of()), state.weaponMountRuntime()));
        assertTrue(new ShipAmmunitionSupplyService().feeds(component).isEmpty());
        assertTrue(GeneratedCampaignAmmunitionUi.rows(c).isEmpty());
        component.setInstanceState(new ShipInstanceRuntimeState(state.damage(), state.shieldStatesByMount(), state.maintenance(),
                new WeaponLoadoutState(List.of(new WeaponLoadoutState.FeedBinding(feed.mountId(), feed.port().id(), "ammo.missing"))),
                state.weaponMountRuntime()));
        before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        assertEquals(before, c.captureState());
    }

    @Test void reloadedRoundsRemainConsumableByTheCommonPhysicalFiringAuthority() {
        var c = fixture(4); var service = new ShipAmmunitionSupplyService(); var feed = service.feeds(ship(c)).get(0);
        var station = c.dockedAmmunitionStationId().orElseThrow();
        c.submitPilotAction(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1));
        var fired = new AmmunitionRuntime().consumeOne(ship(c).runtimeState.consumables(), feed.mountId(), feed.launcher(), feed.unitMassKg());
        assertEquals(0, fired.consumables().ammunitionCount()); assertEquals(feed.unitMassKg(), fired.consumedMassKg());
        assertEquals(1, ship(c).runtimeState.consumables().ammunitionCount(), "Planning firing must not mutate the authoritative ship");
        assertEquals(c.captureState(), roundTrip(c).captureState());
    }

    @Test void adequateStockCannotBypassMagazineCapacityOrHullMassAndDestroyedWeaponsDoNotReceiveRounds() {
        var c = fixture(4); var component = ship(c); var service = new ShipAmmunitionSupplyService();
        var feed = service.feeds(component).get(0);
        int excess = (int) Math.floor(feed.freeAmount() / feed.launcher().ammunitionAmountPerShot()) + 1;
        var product = Stage22CivilianMiningProductionPath.loadProducts().findProduct(feed.productId());
        var donor = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                "fixture.overfill", Map.of(product.storageClassId(), feed.unitMassKg() * excess), Map.of(), Map.of(feed.productId(), excess));
        var before = c.captureState(); var stock = donor.snapshot();
        assertThrows(IllegalStateException.class, () -> service.load(component, feed.mountId(), excess, donor));
        assertEquals(stock, donor.snapshot()); assertEquals(before, c.captureState());
        var old = component.runtimeState; var stores = old.consumables();
        var hull = com.spacesim.content.ship.Stage23RuntimeEngineeringCatalogLoader.loadDefault().findHull(component.fit.hullId());
        double spare = hull.maxOperationalMassKg() - new ProductionEngineeringRuntimeResolver().derive(component).totalMassKg();
        component.setRuntimeState(new ShipEngineeringRuntime.RuntimeState(new ConsumableState(stores.cargoMassKg(), stores.storesMassKg(),
                stores.missionPayloadMassKg() + spare + 1, stores.missionIntegrationVolumeM3(), stores.interfaceLoads()),
                old.sharedBusEnergyJ(), old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(),
                old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
        assertThrows(DerivedShipCalculator.InvalidShipFitException.class, () -> service.load(component, feed.mountId(), 1, donor));
        assertEquals(stock, donor.snapshot());
        component.setRuntimeState(old);
        var instance = component.instanceState;
        component.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(),
                new ShipEngineeringState.DamageState(Map.of(feed.mountId(), 0d))), instance.shieldStatesByMount(), instance.maintenance(),
                instance.weaponLoadout(), instance.weaponMountRuntime()));
        assertTrue(service.feeds(component).isEmpty()); assertEquals(stock, donor.snapshot());
    }

    @Test void stationCommodityHandlingAlsoPreventsASecondAmmunitionTransferInTheSameTick() {
        var c = fixture(4); var feed = new ShipAmmunitionSupplyService().feeds(ship(c)).get(0);
        var station = c.dockedAmmunitionStationId().orElseThrow();
        var storage = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        String commodity = storage.snapshotCommodityMassByIdKg().entrySet().stream()
                .filter(e -> e.getValue() >= 1 && c.previewPilotAction("LOAD_COMMODITY", station, e.getKey(), 1).allowed())
                .findFirst().orElseThrow().getKey();
        c.submitPilotAction(c.previewPilotAction("LOAD_COMMODITY", station, commodity, 1));
        var baseline = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1).allowed());
        assertEquals(baseline, c.captureState());
    }

    static Stage228CampaignAuthority fixture(int count) {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime(); var world = r.world();
        var actor = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT).weapons().battleState().combatants().get(0);
        var sourceShip = actor.engineering(); var old = sourceShip.runtimeState; var stores = old.consumables();
        var loads = stores.interfaceLoads().stream().map(l -> l.kind() == InterfaceKind.AMMUNITION
                ? new ConsumableLoad(l.mountId(), l.interfaceId(), l.kind(), 0, 0, 0) : l).toList();
        var fitted = new EngineeringComponent(sourceShip.fit, new ShipEngineeringRuntime.RuntimeState(new ConsumableState(
                stores.cargoMassKg(), stores.storesMassKg(), stores.missionPayloadMassKg(), stores.missionIntegrationVolumeM3(), loads),
                old.sharedBusEnergyJ(), old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(),
                old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()), sourceShip.instanceState);
        var feed = new ShipAmmunitionSupplyService().feeds(fitted).get(0);
        var products = Stage22CivilianMiningProductionPath.loadProducts(); var product = products.findProduct(feed.productId());
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> c.pilotMarketReference(e.stationId()).isPresent()
                && r.industry().industrial().stations().stream().anyMatch(s -> s.stationId().equals(e.stationId()))
                && e.handlingCapability().supportedStorageClassIds().contains(product.storageClassId())
                && e.handlingCapability().maxUnitMassKg() >= feed.unitMassKg()
                && e.handlingCapability().openInterval(c.coordinator().session().fixedStepSeconds()).remainingMassKg() >= feed.unitMassKg())
                .findFirst().orElseThrow();
        var original = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        boolean moved = !original.systemId().equals(endpoint.systemId());
        if (moved) { world.beginFleetTransfer(original.id(), endpoint.systemId()); world.completeFleetTransfer(original.id(), 0, 0); }
        var fleet = world.findFleet(original.id()).orElseThrow();
        var physical = r.arrival().materialization(fleet.systemId());
        if (moved) physical.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        physical.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).add(fitted);
        r.synchronizeFreightEngineeringCargo(fleet.id());
        if (count > 0) {
            var ontology = Stage18ResourceOntologyLoader.loadDefault();
            var donor = new Stage18StationStorage(ontology, products, "fixture.ammunition",
                    Map.of(product.storageClassId(), feed.unitMassKg() * count), Map.of(), Map.of(feed.productId(), count));
            var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.ammunition", Set.of(product.storageClassId()), 100000, 100000);
            assertTrue(new Stage18LogisticsRuntime(ontology, products).transferProduct(donor, endpoint.storage(), feed.productId(),
                    count, handling, handling.openInterval(1)).transferred());
        }
        var p = c.playerState().orElseThrow(); var ref = c.pilotMarketReference(endpoint.stationId()).orElseThrow();
        var systems = new ArrayList<>(p.discoveredSystemIds()); if (!systems.contains(ref.systemId())) systems.add(ref.systemId());
        var objects = new ArrayList<>(p.discoveredObjects()); if (!objects.contains(ref)) objects.add(ref);
        var own = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                systems, objects, p.homeSystemId(), ref, p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(),
                List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var restored = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(c.captureState(), r.captureState(), own));
        restored.coordinator().setPaused(false); restored.advanceFrame(restored.coordinator().session().fixedStepSeconds()); restored.coordinator().setPaused(true);
        return restored;
    }

    private static EngineeringComponent ship(Stage228CampaignAuthority c) {
        var world = c.coordinator().runtime().world(); var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).getComponent(EngineeringComponent.class);
    }

    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
}

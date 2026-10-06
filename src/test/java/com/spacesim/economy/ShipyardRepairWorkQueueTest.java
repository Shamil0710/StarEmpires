package com.spacesim.economy;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.content.ship.*;
import com.spacesim.persistence.*;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardRepairWorkQueueTest {
    private final varHolder fixture = new varHolder();
    private static final class varHolder {
        final ShipEngineeringCatalog engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
        final Stage18ShipyardRuntime.YardCapabilitySnapshot yard;
        varHolder() {
            var d = Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findYard(Stage22IndustrialUnionProductionCatalogs.YARD_ID);
            var c = new ShipyardEngineeringService.ShipyardCapability("fixture.yard", d.berthDimensionsM(), d.maxServiceMassKg(),
                    d.stage175FabricationCapabilities(), d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(),
                    d.ratedEngineeringWorkRate(), d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW());
            yard = new Stage18ShipyardRuntime.YardCapabilitySnapshot("fixture.yard", d.id(), Stage18ShipyardRuntime.YardStatus.ACTIVE,
                    c, d.handledStorageClassIds(), d.maxHandledUnitMassKg());
        }
        EngineeringComponent ship() {
            var fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
            var ship = new EngineeringComponent(fit, new ShipEngineeringRuntime(engineering).initialize(fit, ConsumableState.empty()));
            var before = ship.instanceState;
            ship.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("utility_sensor", .5))),
                    before.shieldStatesByMount(), new ShipyardEngineeringService.MaintenanceState(Map.of("utility_sensor", 920d)),
                    before.weaponLoadout(), before.weaponMountRuntime()));
            return ship;
        }
        Map<String, Double> inputs(EngineeringComponent ship) {
            return new Stage18ShipyardRuntime(Stage22CivilianMiningProductionPath.loadRuntimeShipyards(), ontology, products)
                    .repairMaterialRequirements(ShipyardRepairWorkQueue.plan(new EntityId(31), ship, yard), ship.instanceState.damage());
        }
        Stage18StationStorage store(EngineeringComponent ship, int batches) {
            var raw = new TreeMap<String, Double>(); var caps = new TreeMap<String, Double>();
            inputs(ship).forEach((id, mass) -> { raw.put(id, mass * batches); caps.merge(ontology.findCommodity(id).storageClassId(), mass * batches * 2, Double::sum); });
            return new Stage18StationStorage(ontology, products, "fixture.station", caps, raw, Map.of());
        }
    }

    @Test void reservedRepairContinuesExactlyAfterNativeProgressSaveWithoutInstantHealingOrWorkReplay() {
        var ship = fixture.ship(); var store = fixture.store(ship, 1); var initial = store.snapshot();
        var queue = new ShipyardRepairWorkQueue(ShipyardRepairQueueState.empty());
        var instance = ship.instanceState; var runtime = ship.runtimeState;
        var work = ShipyardRepairWorkQueue.plan(new EntityId(31), ship, fixture.yard).requirements().totalWorkSeconds();
        double duration = work / fixture.yard.plannerCapability().workRate() / 4;
        queue.start("fixture.repair.31", 31, new EntityId(31), ship, store, fixture.yard, 0);
        assertTrue(store.snapshotCommodityMassByIdKg().isEmpty()); assertSame(instance, ship.instanceState);
        for (var capacity : initial.capacityByStorageClassKg().keySet())
            assertEquals(initial.commodityMassByIdKg().entrySet().stream().filter(e -> fixture.ontology.findCommodity(e.getKey()).storageClassId().equals(capacity))
                    .mapToDouble(Map.Entry::getValue).sum(), store.usedCapacityKg(capacity), 1e-9);
        var context = new ShipyardRepairWorkQueue.RepairContext(ship, fixture.yard);
        queue.advance(1, ignored -> store, ignored -> context, ignored -> fixture.yard.openInterval(duration));
        assertEquals(work / 4, queue.capture().orders().get(0).completedWorkSeconds(), 1e-9); assertSame(instance, ship.instanceState);
        var saved = ShipyardRepairQueuePersistenceCodec.decode(ShipyardRepairQueuePersistenceCodec.encode(queue.capture()));
        var restored = new ShipyardRepairWorkQueue(saved);
        var restoredStore = Stage18StationStorage.restore(fixture.ontology, fixture.products, store.snapshot());
        restored.restoreReservations(ignored -> restoredStore);
        var restoredShip = new EngineeringComponent(ship.fit, ship.runtimeState, ship.instanceState);
        var restoredContext = new ShipyardRepairWorkQueue.RepairContext(restoredShip, fixture.yard);
        var duplicateBudget = fixture.yard.openInterval(duration);
        queue.advance(1, ignored -> store, ignored -> context, ignored -> duplicateBudget);
        assertEquals(saved, queue.capture()); assertEquals(work / 4, duplicateBudget.remainingWorkSeconds(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> queue.advance(3, ignored -> store, ignored -> context, ignored -> duplicateBudget));
        for (int tick = 2; tick <= 4; tick++) {
            queue.advance(tick, ignored -> store, ignored -> context, ignored -> fixture.yard.openInterval(duration));
            restored.advance(tick, ignored -> restoredStore, ignored -> restoredContext, ignored -> fixture.yard.openInterval(duration));
            assertEquals(queue.capture(), restored.capture()); assertEquals(store.snapshot(), restoredStore.snapshot());
            assertEquals(ship.instanceState, restoredShip.instanceState);
        }
        assertTrue(queue.capture().orders().isEmpty()); assertTrue(ship.instanceState.damage().moduleDamage().moduleIntegrityByMount().isEmpty());
        assertSame(runtime, ship.runtimeState); assertEquals(instance.maintenance(), ship.instanceState.maintenance());
        assertEquals(instance.weaponLoadout(), ship.instanceState.weaponLoadout());
        for (var capacity : initial.capacityByStorageClassKg().keySet()) assertEquals(0, store.usedCapacityKg(capacity));
    }

    @Test void lostContactOrChangedDamagePausesAndCancellationReturnsExactlyReservedMaterial() {
        var ship = fixture.ship(); var store = fixture.store(ship, 1); var initial = store.snapshot();
        var queue = new ShipyardRepairWorkQueue(ShipyardRepairQueueState.empty());
        queue.start("repair", 31, new EntityId(31), ship, store, fixture.yard, 0);
        var budget = fixture.yard.openInterval(1); var before = ship.instanceState;
        queue.advance(1, ignored -> store, ignored -> null, ignored -> budget);
        assertEquals(0, queue.capture().orders().get(0).completedWorkSeconds());
        var changed = new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("utility_sensor", .3))),
                before.shieldStatesByMount(), before.maintenance(), before.weaponLoadout(), before.weaponMountRuntime());
        ship.setInstanceState(changed);
        queue.advance(2, ignored -> store, ignored -> new ShipyardRepairWorkQueue.RepairContext(ship, fixture.yard), ignored -> budget);
        assertEquals(0, queue.capture().orders().get(0).completedWorkSeconds()); assertEquals(28, budget.remainingWorkSeconds());
        queue.cancel("repair", store); assertEquals(initial, store.snapshot()); assertSame(changed, ship.instanceState);
        assertTrue(queue.capture().orders().isEmpty());
    }

    @Test void paidReservationSurvivesPartialWorkAndCannotRefundAfterWorkOrDecodeAnAlteredFee() {
        var ship = fixture.ship(); var store = fixture.store(ship, 1);
        var queue = new ShipyardRepairWorkQueue(ShipyardRepairQueueState.empty());
        long quote = queue.serviceQuote(new EntityId(31), ship, fixture.yard);
        var payment = new ShipyardRepairServicePayment("faction.industrial_union", quote);
        queue.start("paid", 31, new EntityId(31), ship, store, fixture.yard, 0, payment);
        var initial = queue.capture(); var bytes = ShipyardRepairQueuePersistenceCodec.encode(initial);
        assertEquals(2, java.nio.ByteBuffer.wrap(bytes).getInt());
        assertEquals(initial, ShipyardRepairQueuePersistenceCodec.decode(bytes));
        var changedFee = bytes.clone(); java.nio.ByteBuffer.wrap(changedFee).putLong(changedFee.length - 8, quote + 1);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairQueuePersistenceCodec.decode(changedFee));
        var oldFrame = bytes.clone(); java.nio.ByteBuffer.wrap(oldFrame).putInt(0, 1);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairQueuePersistenceCodec.decode(oldFrame));
        double required = initial.orders().get(0).requiredWorkSeconds();
        queue.advance(1, ignored -> store, ignored -> new ShipyardRepairWorkQueue.RepairContext(ship, fixture.yard),
                ignored -> fixture.yard.openInterval(required / fixture.yard.plannerCapability().workRate() / 4));
        assertTrue(queue.capture().orders().get(0).completedWorkSeconds() > 0);
        assertEquals(payment, queue.capture().orders().get(0).servicePayment());
        var beforeCancel = queue.capture(); var stock = store.snapshot();
        assertThrows(IllegalStateException.class, () -> queue.cancel("paid", store));
        assertEquals(beforeCancel, queue.capture()); assertEquals(stock, store.snapshot());
        assertEquals(beforeCancel, new ShipyardRepairWorkQueue(ShipyardRepairQueuePersistenceCodec.decode(
                ShipyardRepairQueuePersistenceCodec.encode(beforeCancel))).capture());
    }

    @Test void tariffRoundsOnceAndRejectsMissingPhysicalBillAndCurrencyOverflow() {
        assertEquals(6500, ShipyardRepairServicePayment.quote(1.5, Map.of("material", .1)));
        assertEquals(1, ShipyardRepairServicePayment.quote(1e-9, Map.of("material", 1e-9)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairServicePayment.quote(Double.MAX_VALUE, Map.of("material", 1d)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairServicePayment.quote(1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairServicePayment.quote(1, Map.of("material", Double.NaN)));
    }

    @Test void competingJobsCannotReuseARealYardIntervalAndMalformedEscrowCannotRestore() {
        var first = fixture.ship(); var second = fixture.ship(); var store = fixture.store(first, 2);
        var queue = new ShipyardRepairWorkQueue(ShipyardRepairQueueState.empty());
        queue.start("a", 31, new EntityId(31), first, store, fixture.yard, 0);
        queue.start("b", 32, new EntityId(32), second, store, fixture.yard, 0);
        double required = queue.capture().orders().get(0).requiredWorkSeconds();
        var shared = fixture.yard.openInterval(required / fixture.yard.plannerCapability().workRate() / 2);
        queue.advance(1, ignored -> store, o -> new ShipyardRepairWorkQueue.RepairContext(o.fleetId() == 31 ? first : second, fixture.yard), ignored -> shared);
        assertEquals(required / 2, queue.capture().orders().stream().mapToDouble(o -> o.completedWorkSeconds()).sum(), 1e-9);
        assertEquals(0, shared.remainingWorkSeconds());
        var order = queue.capture().orders().get(0);
        var corrupt = new ShipyardRepairQueueState.RepairOrder(order.orderId(), order.fleetId(), order.assetId(), order.stationId(),
                order.yardInstanceId(), order.yardDefinitionId(), order.startedAtTick(), order.sourceFit(), order.sourceDamage(),
                order.requiredWorkSeconds() * 2, order.completedWorkSeconds(), order.reservedCommodityMassByIdKg());
        assertThrows(IllegalArgumentException.class, () -> new ShipyardRepairWorkQueue(new ShipyardRepairQueueState(1, List.of(corrupt))));
        byte[] bytes = ShipyardRepairQueuePersistenceCodec.encode(queue.capture());
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        java.nio.ByteBuffer.wrap(bytes).putInt(12, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRepairQueuePersistenceCodec.decode(bytes));
    }
}

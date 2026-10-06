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

class ShipyardRefitWorkQueueTest {
    @Test void standalonePaidRefitRetainsMoneyAndRejectsAlteredTariffAndUnfundedExecution() {
        var ship = ship(); var store = store(1, reservedMass()); var queue = queue();
        start(queue, "paid-format", 31, new EntityId(31), ship, store, yard, 0);
        var o = queue.capture().orders().get(0);
        var payment = new ShipyardRefitServicePayment("faction.industrial_union",
                ShipyardRefitServicePayment.quote(o.requiredWorkSeconds(), o.reservedProductCounts()));
        var paid = new ShipyardRefitQueueState.Order(o.orderId(), o.fleetId(), o.assetId(), o.stationId(),
                o.yardInstanceId(), o.yardDefinitionId(), o.startedAtTick(), o.sourceFit(), o.targetFit(),
                o.sourceDamage(), o.requiredWorkSeconds(), 0, o.reservedProductCounts(), o.reservedUsedModulesByTargetMount(), payment);
        var state = new ShipyardRefitQueueState(1, List.of(paid.withWork(paid.requiredWorkSeconds() / 2)));
        assertEquals(payment, state.orders().get(0).servicePayment());
        var bytes = ShipyardRefitQueuePersistenceCodec.encode(state);
        assertEquals(3, java.nio.ByteBuffer.wrap(bytes).getInt());
        assertEquals(state, ShipyardRefitQueuePersistenceCodec.decode(bytes));
        var bad = bytes.clone(); java.nio.ByteBuffer.wrap(bad).putLong(bad.length - 8, payment.reservedMilliCredits() + 1);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(bad));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var legacy = bytes.clone(); java.nio.ByteBuffer.wrap(legacy).putInt(0, 2);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(legacy));
        var restored = new ShipyardRefitWorkQueue(state); var stock = store.snapshot(); var original = ship.fit;
        var result = restored.advance(2, ignored -> store, ignored -> context(ship),
                ignored -> yard.openInterval(paid.requiredWorkSeconds() / yard.plannerCapability().workRate()), ShipyardModuleCustodyState.empty());
        assertTrue(result.completed().isEmpty()); assertEquals(state.orders(), restored.capture().orders());
        assertEquals(stock, store.snapshot()); assertSame(original, ship.fit);
    }

    @Test void paidRefitSettlesOnlyAfterPhysicalPreflightAndRetainsEquipmentOwnershipAcrossResume() {
        var ship = ship(); var originalFit = ship.fit; var store = store(1, reservedMass()); var queue = queue();
        var target = target(ship);
        double work = ShipyardRefitWorkQueue.plan(new EntityId(31), ship, target, yard).requirements().totalWorkSeconds();
        var payment = new ShipyardRefitServicePayment("faction.industrial_union",
                ShipyardRefitServicePayment.quote(work, Map.of(incoming, 1)));
        var held = new com.spacesim.components.WalletComponent(payment.reservedMilliCredits());
        var operator = new com.spacesim.components.WalletComponent(0);
        queue.start("paid", 31, new EntityId(31), ship, target, store, yard, 0, Map.of(), ShipyardModuleCustodyState.empty(), payment);
        var stock = store.snapshot(); var reserved = queue.capture().orders();
        var denied = new ShipyardRefitWorkQueue.Context(ship, yard, completion -> true, "actor.player", order -> true, order -> false);
        var finalBudget = yard.openInterval(work / yard.plannerCapability().workRate() * 1.001);
        double available = finalBudget.remainingWorkSeconds();
        var attempt = queue.advance(1, ignored -> store, ignored -> denied, ignored -> finalBudget, ShipyardModuleCustodyState.empty());
        assertTrue(attempt.completed().isEmpty()); assertEquals(reserved, queue.capture().orders());
        assertEquals(stock, store.snapshot()); assertSame(originalFit, ship.fit);
        assertEquals(available, finalBudget.remainingWorkSeconds()); assertEquals(0, operator.getBalanceMilliCredits());
        int[] settlements = {0};
        var funded = new ShipyardRefitWorkQueue.Context(ship, yard, completion -> true, "actor.player",
                order -> held.canDebit(order.servicePayment().reservedMilliCredits()), order -> {
                    boolean moved = held.transferTo(operator, order.servicePayment().reservedMilliCredits());
                    if (moved) settlements[0]++;
                    return moved;
                });
        queue.advance(2, ignored -> store, ignored -> funded,
                ignored -> yard.openInterval(work / yard.plannerCapability().workRate() / 2), ShipyardModuleCustodyState.empty());
        assertTrue(queue.capture().orders().get(0).completedWorkSeconds() > 0); assertEquals(0, settlements[0]);
        assertThrows(IllegalStateException.class, () -> queue.cancel("paid", store));
        var restored = new ShipyardRefitWorkQueue(ShipyardRefitQueuePersistenceCodec.decode(ShipyardRefitQueuePersistenceCodec.encode(queue.capture())));
        var completed = restored.advance(3, ignored -> store, ignored -> funded,
                ignored -> yard.openInterval(work / yard.plannerCapability().workRate() * 1.001), ShipyardModuleCustodyState.empty());
        assertEquals(1, completed.completed().size()); assertEquals(1, settlements[0]);
        assertEquals(0, held.getBalanceMilliCredits()); assertEquals(payment.reservedMilliCredits(), operator.getBalanceMilliCredits());
        assertEquals(target, ship.fit); assertTrue(restored.capture().orders().isEmpty());
        var removed = completed.custody().modules().get(0);
        assertEquals("actor.player", removed.ownerActorId()); assertEquals(.35, removed.condition().integrity());
        assertEquals(920d, removed.condition().secondsSinceService());
        assertEquals(completed.custody(), ShipyardModuleCustodyPersistenceCodec.decode(ShipyardModuleCustodyPersistenceCodec.encode(completed.custody())));
        restored.advance(4, ignored -> store, ignored -> funded, ignored -> yard.openInterval(1000), completed.custody());
        assertEquals(1, settlements[0]); assertEquals(payment.reservedMilliCredits(), operator.getBalanceMilliCredits());
    }

    @Test void existingUsedEquipmentHasOnlyWorkFeeAndUnknownProductsCannotBeQuoted() {
        assertEquals(1500, ShipyardRefitServicePayment.quote(1.5, Map.of()));
        assertEquals(1, ShipyardRefitServicePayment.quote(1e-9, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitServicePayment.quote(1, Map.of("unknown", 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitServicePayment.quote(1, Map.of(incoming, 0)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitServicePayment.quote(Double.MAX_VALUE, Map.of()));
    }

    @Test void exactUsedModuleCanBeCancelledSavedAndReinstalledWithoutRestoringItsCondition() {
        var ship = ship(); var originalFit = ship.fit;
        var store = store(1, reservedMass()); var queue = queue();
        start(queue, "remove", 31, new EntityId(31), ship, store, yard, 0);
        double duration = queue.capture().orders().get(0).requiredWorkSeconds() / yard.plannerCapability().workRate() * 1.001;
        var custody = queue.advance(1, ignored -> store, ignored -> context(ship), ignored -> yard.openInterval(duration),
                ShipyardModuleCustodyState.empty()).custody();
        var used = custody.modules().get(0); var before = store.snapshot();
        queue.start("return", 31, new EntityId(31), ship, originalFit, store, yard, 1, Map.of("mission_primary", used), custody);
        assertTrue(queue.capture().orders().get(0).reservedProductCounts().isEmpty());
        assertEquals(reservedMass(), store.usedCapacityKg(storageClass));
        var second = new EngineeringComponent(ship.fit, ship.runtimeState, ship.instanceState);
        assertThrows(IllegalArgumentException.class, () -> queue.start("duplicate", 32, new EntityId(32), second,
                originalFit, store, yard, 1, Map.of("mission_primary", used), custody));
        assertThrows(IllegalArgumentException.class, () -> queue.validateUsedReservations(ShipyardModuleCustodyState.empty()));
        queue.cancel("return", store); assertEquals(before, store.snapshot());
        assertEquals(0, store.productCount(outgoing)); assertEquals(used, custody.modules().get(0));
        queue.start("return-again", 31, new EntityId(31), ship, originalFit, store, yard, 1, Map.of("mission_primary", used), custody);
        var saved = queue.capture();
        var restored = new ShipyardRefitWorkQueue(ShipyardRefitQueuePersistenceCodec.decode(ShipyardRefitQueuePersistenceCodec.encode(saved)));
        assertEquals(saved, restored.capture()); restored.validateUsedReservations(custody);
        var restoredStore = Stage18StationStorage.restore(ontology, products, store.snapshot());
        ShipyardModuleCustodyStorage.bind(custody, products, ignored -> restoredStore);
        restored.restoreReservations(ignored -> restoredStore);
        var result = restored.advance(2, ignored -> restoredStore, ignored -> context(ship),
                ignored -> yard.openInterval(saved.orders().get(0).requiredWorkSeconds() / yard.plannerCapability().workRate() * 1.001), custody);
        assertEquals(originalFit, ship.fit); assertTrue(restored.capture().orders().isEmpty());
        assertEquals(.35, ship.instanceState.damage().moduleDamage().moduleIntegrityByMount().get("mission_primary"));
        assertEquals(920d, ship.instanceState.maintenance().secondsSinceServiceByMount().get("mission_primary"));
        assertEquals(1, result.custody().modules().size());
        assertEquals(incoming, result.custody().modules().get(0).condition().assignment().moduleId());
        assertFalse(result.custody().modules().contains(used)); assertEquals(0, restoredStore.productCount(outgoing));
        assertEquals(products.findProduct(incoming).unitMassKg(), restoredStore.usedCapacityKg(storageClass));
    }
    @Test void oldPristineOnlyRefitPayloadMigratesWithoutGrantingUsedInputs() {
        var ship = ship(); var store = store(1, reservedMass()); var queue = queue();
        start(queue, "old", 31, new EntityId(31), ship, store, yard, 0);
        var current = ShipyardRefitQueuePersistenceCodec.encode(queue.capture());
        // A single schema-1 row ends before schema-2's empty used-input count.
        var legacy = Arrays.copyOf(current, current.length - Integer.BYTES);
        java.nio.ByteBuffer.wrap(legacy).putInt(0, 1);
        assertEquals(queue.capture(), ShipyardRefitQueuePersistenceCodec.decode(legacy));
        assertTrue(ShipyardRefitQueuePersistenceCodec.decode(legacy).orders().get(0).reservedUsedModulesByTargetMount().isEmpty());
    }
    private final ShipEngineeringCatalog engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final String incoming = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final String outgoing = "module.industrial_union_cargo_section_v1";
    private final String storageClass = Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS;
    private final Stage18ShipyardRuntime.YardCapabilitySnapshot yard = yard();
    private Stage18ShipyardRuntime.YardCapabilitySnapshot yard() {
        var d = Stage22CivilianMiningProductionPath.loadRuntimeShipyards().findYard(Stage22IndustrialUnionProductionCatalogs.YARD_ID);
        var c = new ShipyardEngineeringService.ShipyardCapability("fixture.refit.yard", d.berthDimensionsM(), d.maxServiceMassKg(),
                d.stage175FabricationCapabilities(), d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(),
                d.ratedEngineeringWorkRate(), d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW());
        return new Stage18ShipyardRuntime.YardCapabilitySnapshot(c.yardId(), d.id(), Stage18ShipyardRuntime.YardStatus.ACTIVE, c,
                d.handledStorageClassIds(), d.maxHandledUnitMassKg());
    }
    private EngineeringComponent ship() {
        var fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        var ship = new EngineeringComponent(fit, new ShipEngineeringRuntime(engineering).initialize(fit, ConsumableState.empty()));
        var s = ship.instanceState;
        ship.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("mission_primary", .35, "utility_sensor", .8))),
                s.shieldStatesByMount(), new ShipyardEngineeringService.MaintenanceState(Map.of("mission_primary", 920d, "utility_sensor", 180d)), s.weaponLoadout(), s.weaponMountRuntime()));
        return ship;
    }
    private Stage18StationStorage store(int units, double capacity) {
        return new Stage18StationStorage(ontology, products, "fixture.refit.station", Map.of(storageClass, capacity), Map.of(), units == 0 ? Map.of() : Map.of(incoming, units));
    }
    private double reservedMass() { return Math.max(products.findProduct(incoming).unitMassKg(), products.findProduct(outgoing).unitMassKg()); }
    private ShipyardRefitWorkQueue queue() { return new ShipyardRefitWorkQueue(ShipyardRefitQueueState.empty()); }
    private static InstalledFit target(EngineeringComponent ship) { return Stage22CivilianMiningProductionPath.freightMiningProposal(ship.fit); }
    private void start(ShipyardRefitWorkQueue q, String id, long fleet, EntityId asset, EngineeringComponent ship, Stage18StationStorage store, Stage18ShipyardRuntime.YardCapabilitySnapshot y, long tick) {
        q.start(id, fleet, asset, ship, target(ship), store, y, tick);
    }
    private ShipyardRefitWorkQueue.Context context(EngineeringComponent ship) {
        // Domain fixture has no cargo. Player integration must supply its actual capacity authority.
        return new ShipyardRefitWorkQueue.Context(ship, yard, completion -> true);
    }
    @Test void partialRefitResumesExactlyAndPreservesRemovedConditionAtActualCompletion() {
        var ship = ship(); var source = ship.fit; var sourceState = ship.instanceState; var physical = ship.runtimeState;
        var store = store(1, reservedMass() * 2); var queue = queue(); start(queue, "refit", 31, new EntityId(31), ship, store, yard, 0);
        assertEquals(0, store.productCount(incoming)); assertSame(source, ship.fit); assertSame(sourceState, ship.instanceState);
        assertEquals(reservedMass(), store.usedCapacityKg(storageClass));
        double work = queue.capture().orders().get(0).requiredWorkSeconds(); double duration = work / yard.plannerCapability().workRate() / 4;
        var empty = ShipyardModuleCustodyState.empty();
        var first = queue.advance(1, ignored -> store, ignored -> context(ship), ignored -> yard.openInterval(duration), empty);
        assertEquals(empty, first.custody()); assertTrue(first.completed().isEmpty()); assertSame(source, ship.fit);
        var saved = queue.capture();
        var restored = new ShipyardRefitWorkQueue(ShipyardRefitQueuePersistenceCodec.decode(ShipyardRefitQueuePersistenceCodec.encode(saved)));
        var restoredStore = Stage18StationStorage.restore(ontology, products, store.snapshot()); restored.restoreReservations(ignored -> restoredStore);
        var restoredShip = new EngineeringComponent(ship.fit, ship.runtimeState, ship.instanceState);
        assertEquals(store.usedCapacityKg(storageClass), restoredStore.usedCapacityKg(storageClass));
        var duplicate = yard.openInterval(duration); queue.advance(1, ignored -> store, ignored -> context(ship), ignored -> duplicate, empty);
        assertEquals(saved, queue.capture()); assertEquals(work / 4, duplicate.remainingWorkSeconds(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> queue.advance(3, ignored -> store, ignored -> context(ship), ignored -> duplicate, empty));
        var custody = empty; var restoredCustody = empty;
        for (int tick = 2; tick <= 5; tick++) {
            custody = queue.advance(tick, ignored -> store, ignored -> context(ship), ignored -> yard.openInterval(duration), custody).custody();
            restoredCustody = restored.advance(tick, ignored -> restoredStore, ignored -> context(restoredShip), ignored -> yard.openInterval(duration), restoredCustody).custody();
            assertEquals(queue.capture(), restored.capture()); assertEquals(store.snapshot(), restoredStore.snapshot());
            assertEquals(ship.fit, restoredShip.fit); assertEquals(ship.instanceState, restoredShip.instanceState); assertEquals(custody, restoredCustody);
        }
        assertTrue(queue.capture().orders().isEmpty()); assertEquals(Stage22CivilianMiningProductionPath.freightMiningProposal(source), ship.fit);
        assertEquals(1, custody.modules().size()); var removed = custody.modules().get(0);
        assertEquals(.35, removed.condition().integrity()); assertEquals(920, removed.condition().secondsSinceService());
        assertTrue(removed.removedAtTick() >= 4 && removed.removedAtTick() <= 5); assertEquals(31, removed.sourceAssetId());
        assertEquals(0, store.productCount(outgoing)); assertEquals(products.findProduct(outgoing).unitMassKg(), store.usedCapacityKg(storageClass));
        assertEquals(.8, ship.instanceState.damage().moduleDamage().moduleIntegrityByMount().get("utility_sensor"));
        assertEquals(180, ship.instanceState.maintenance().secondsSinceServiceByMount().get("utility_sensor"));
        assertEquals(physical.consumables(), ship.runtimeState.consumables()); assertEquals(physical.shipHeatStoredJ(), ship.runtimeState.shipHeatStoredJ());
        assertTrue(new ShipMiningEngineeringAdapter().derive(new ProductionEngineeringRuntimeResolver().derive(ship)).isPresent());
    }
    @Test void cargoGuardAndLostContactPauseWithoutSpendingThenCancellationReturnsExactEquipment() {
        var ship = ship(); var store = store(1, reservedMass() * 2); var before = store.snapshot(); var state = ship.instanceState; var queue = queue();
        start(queue, "refit", 31, new EntityId(31), ship, store, yard, 0); var budget = yard.openInterval(1);
        queue.advance(1, ignored -> store, ignored -> new ShipyardRefitWorkQueue.Context(ship, yard, completion -> false), ignored -> budget, ShipyardModuleCustodyState.empty());
        queue.advance(2, ignored -> store, ignored -> null, ignored -> budget, ShipyardModuleCustodyState.empty());
        assertEquals(0, queue.capture().orders().get(0).completedWorkSeconds()); assertEquals(yard.plannerCapability().workRate(), budget.remainingWorkSeconds());
        queue.cancel("refit", store); assertEquals(before, store.snapshot()); assertSame(state, ship.instanceState); assertTrue(queue.capture().orders().isEmpty());
        assertEquals(products.findProduct(incoming).unitMassKg(), store.usedCapacityKg(storageClass));
    }
    @Test void sharedBudgetCannotBeReusedAndRefitEscrowBlocksOtherStorageLayers() {
        var first = ship(); var second = ship(); var store = store(2, reservedMass() * 2); var queue = queue();
        start(queue, "a", 31, new EntityId(31), first, store, yard, 0); start(queue, "b", 32, new EntityId(32), second, store, yard, 0);
        double work = queue.capture().orders().get(0).requiredWorkSeconds(); var budget = yard.openInterval(work / yard.plannerCapability().workRate() / 2);
        queue.advance(1, ignored -> store, o -> context(o.fleetId() == 31 ? first : second), ignored -> budget, ShipyardModuleCustodyState.empty());
        assertEquals(work / 2, queue.capture().orders().stream().mapToDouble(o -> o.completedWorkSeconds()).sum(), 1e-9); assertEquals(0, budget.remainingWorkSeconds());
        assertEquals(0, store.manufacturingLayerCapacityByStorageClassKg().get(storageClass));
        assertEquals(0, store.commodityLayerCapacityByStorageClassKg().get(storageClass));
        assertThrows(IllegalStateException.class, () -> store.addProduct(incoming, 1));
        assertThrows(IllegalArgumentException.class, () -> queue.cancel("a", new Stage18StationStorage(ontology, products, "wrong", Map.of(storageClass, 1e9), Map.of(), Map.of())));
        assertEquals(2, queue.capture().orders().size());
    }
    @Test void invalidRecipeWorkAndMalformedNativePayloadCannotRestore() {
        var ship = ship(); var store = store(1, reservedMass() * 2); var queue = queue(); start(queue, "refit", 31, new EntityId(31), ship, store, yard, 0);
        var o = queue.capture().orders().get(0);
        var corrupt = new ShipyardRefitQueueState.Order(o.orderId(), o.fleetId(), o.assetId(), o.stationId(), o.yardInstanceId(), o.yardDefinitionId(), o.startedAtTick(),
                o.sourceFit(), o.targetFit(), o.sourceDamage(), o.requiredWorkSeconds() * 2, 0, o.reservedProductCounts());
        assertThrows(IllegalArgumentException.class, () -> new ShipyardRefitWorkQueue(new ShipyardRefitQueueState(0, List.of(corrupt))));
        var bytes = ShipyardRefitQueuePersistenceCodec.encode(queue.capture());
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var count = bytes.clone(); java.nio.ByteBuffer.wrap(count).putInt(12, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(count));
        var schema = bytes.clone(); java.nio.ByteBuffer.wrap(schema).putInt(0, 3);
        assertThrows(IllegalArgumentException.class, () -> ShipyardRefitQueuePersistenceCodec.decode(schema));
    }
    @Test void missingStockAndInsufficientReservationRoomRejectBeforeAnyMutation() {
        var ship = ship(); var queue = queue(); var missing = store(0, reservedMass() * 2); var before = missing.snapshot(); var fit = ship.fit;
        assertThrows(IllegalArgumentException.class, () -> start(queue, "missing", 31, new EntityId(31), ship, missing, yard, 0));
        assertEquals(before, missing.snapshot()); assertTrue(queue.capture().orders().isEmpty()); assertSame(fit, ship.fit);
        var supplied = store(1, reservedMass() * 2); var suppliedBefore = supplied.snapshot();
        assertThrows(IllegalArgumentException.class, () -> start(queue, "x".repeat(512), 31, new EntityId(31), ship, supplied, yard, 0));
        assertEquals(suppliedBefore, supplied.snapshot()); assertTrue(queue.capture().orders().isEmpty());
        // Select the direction removing the physically larger section: room for the smaller
        // incoming unit alone must not permit the larger removed hardware to disappear.
        var cargoFit = ship.fit; var miningFit = target(ship);
        boolean removeMining = products.findProduct(incoming).unitMassKg() > products.findProduct(outgoing).unitMassKg();
        var source = removeMining ? miningFit : cargoFit; var target = removeMining ? cargoFit : miningFit;
        String replacement = removeMining ? outgoing : incoming;
        var actual = new EngineeringComponent(source, new ShipEngineeringRuntime(engineering).initialize(source, ConsumableState.empty()));
        assertTrue(ShipyardRefitWorkQueue.plan(new EntityId(32), actual, target, yard).feasibility().feasible());
        var crowded = new Stage18StationStorage(ontology, products, "fixture.refit.station",
                Map.of(storageClass, products.findProduct(replacement).unitMassKg()), Map.of(), Map.of(replacement, 1));
        var stockBefore = crowded.snapshot(); var usedBefore = crowded.usedCapacityKg(storageClass);
        assertThrows(IllegalArgumentException.class, () -> queue.start("crowded", 32, new EntityId(32), actual, target, crowded, yard, 0));
        assertTrue(queue.capture().orders().isEmpty()); assertEquals(stockBefore, crowded.snapshot());
        assertEquals(usedBefore, crowded.usedCapacityKg(storageClass)); assertSame(source, actual.fit);
    }
    @Test void changedDamageAndReducedActualHandlingPauseWithoutLosingEquipmentOrWork() {
        var ship = ship(); var store = store(1, reservedMass() * 2); var queue = queue();
        start(queue, "refit", 31, new EntityId(31), ship, store, yard, 0);
        var reduced = new Stage18ShipyardRuntime.YardCapabilitySnapshot(yard.yardInstanceId(), yard.yardDefinitionId(), yard.status(),
                yard.plannerCapability(), yard.handledStorageClassIds(), 1);
        var budget = yard.openInterval(1);
        queue.advance(1, ignored -> store, ignored -> new ShipyardRefitWorkQueue.Context(ship, reduced, completion -> true), ignored -> budget, ShipyardModuleCustodyState.empty());
        var s = ship.instanceState;
        var changed = new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("mission_primary", .2))),
                s.shieldStatesByMount(), s.maintenance(), s.weaponLoadout(), s.weaponMountRuntime());
        ship.setInstanceState(changed);
        queue.advance(2, ignored -> store, ignored -> context(ship), ignored -> budget, ShipyardModuleCustodyState.empty());
        assertEquals(0, queue.capture().orders().get(0).completedWorkSeconds()); assertEquals(yard.plannerCapability().workRate(), budget.remainingWorkSeconds());
        queue.cancel("refit", store); assertEquals(1, store.productCount(incoming)); assertSame(changed, ship.instanceState);
    }
    @Test void fullIndividualCustodyPausesCompletionBeforeSpendingFinalWorkOrChangingShip() {
        var ship = ship(); var modules = new ArrayList<ShipyardModuleCustodyState.StoredModule>();
        for (int i = 0; i < ShipyardModuleCustodyState.CAPACITY; i++) modules.add(new ShipyardModuleCustodyState.StoredModule("existing/" + i,
                "fixture.refit.station", 7, 0, new ShipyardRefitContinuity.RemovedModuleState(new ShipEngineeringCatalog.InstalledModuleDefinition("old", incoming), .5, 20)));
        var custody = new ShipyardModuleCustodyState(modules);
        var store = store(1, ShipyardModuleCustodyState.CAPACITY * products.findProduct(incoming).unitMassKg() + reservedMass() * 2);
        ShipyardModuleCustodyStorage.bind(custody, products, ignored -> store);
        var queue = queue(); start(queue, "refit", 31, new EntityId(31), ship, store, yard, 0);
        var pending = queue.capture(); var stock = store.snapshot(); var instance = ship.instanceState; var fitting = ship.fit;
        var budget = yard.openInterval(pending.orders().get(0).requiredWorkSeconds() / yard.plannerCapability().workRate() * 1.001);
        double work = budget.remainingWorkSeconds();
        var result = queue.advance(1, ignored -> store, ignored -> context(ship), ignored -> budget, custody);
        assertEquals(custody, result.custody()); assertTrue(result.completed().isEmpty()); assertEquals(pending.orders(), queue.capture().orders());
        assertEquals(1, queue.capture().lastProcessedTick()); assertEquals(work, budget.remainingWorkSeconds()); assertEquals(stock, store.snapshot());
        assertSame(fitting, ship.fit); assertSame(instance, ship.instanceState);
    }
}

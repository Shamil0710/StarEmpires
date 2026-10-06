package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18ManufacturingWorkQueueTest {
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final Stage18ManufacturingCatalog catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
    private final String module = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final String id = Stage18ManufacturingWorkQueue.PREFIX + "fixture";
    private Stage18ManufacturingCatalog.ProductProfileDefinition profile() {
        return catalog.findProductProfile(catalog.findProductBinding(module).profileId());
    }
    private Stage18ManufacturingWorkQueue queue() {
        return new Stage18ManufacturingWorkQueue(ontology, catalog, products, List.of(), 0);
    }
    private Stage18StationStorage storage(int batches) {
        double mass = products.findProduct(module).unitMassKg();
        var inputs = new TreeMap<String, Double>();
        var capacity = new TreeMap<String, Double>();
        profile().inputs().forEach(i -> {
            inputs.put(i.commodityId(), mass * i.fractionOfOutputMass() * batches);
            capacity.put(ontology.findCommodity(i.commodityId()).storageClassId(), mass * 4);
        });
        capacity.put(products.findProduct(module).storageClassId(), mass * 4);
        return new Stage18StationStorage(ontology, products, "fixture", capacity, inputs, Map.of());
    }
    private Stage18ManufacturingRuntime.ManufacturingCapability line() {
        double mass = products.findProduct(module).unitMassKg();
        return new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line", profile().requiredCapabilityTags(),
                mass * profile().energyJPerOutputKg() / 4,
                mass * profile().workSecondsPerOutputKg() / 4,
                mass * profile().maintenanceWorkSecondsPerOutputKg() / 4);
    }

    @Test void reservedMassAndPartialWorkSurviveNativeSaveAndContinueWithoutReusingTicks() {
        var store = storage(1); var q = queue(); var initial = store.snapshot();
        q.start(id, module, 1, store, line(), 0);
        assertEquals(0, store.productCount(module));
        assertTrue(store.snapshotCommodityMassByIdKg().isEmpty());
        q.advance(1, ignored -> store, ignored -> line().openInterval(1));
        assertEquals(.25, q.capture().get(0).completedFraction(), 1e-12);
        var partial = q.capture();
        q.advance(1, ignored -> store, ignored -> line().openInterval(1));
        assertEquals(partial, q.capture());
        var state = new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), q.lastProcessedTick(),
                List.of(), List.of(store.snapshot()), List.of(), List.of(), List.of(), q.capture());
        var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(state));
        assertEquals(state, decoded);
        var restored = new Stage18ManufacturingWorkQueue(ontology, catalog, products, decoded.processOrders(), decoded.simulationTick());
        var restoredStore = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
        restored.restoreReservations(ignored -> restoredStore);
        for (var storageClass : store.snapshotCapacityByStorageClassKg().keySet())
            assertEquals(store.usedCapacityKg(storageClass), restoredStore.usedCapacityKg(storageClass));
        for (int tick = 2; tick <= 4; tick++) {
            q.advance(tick, ignored -> store, ignored -> line().openInterval(1));
            restored.advance(tick, ignored -> restoredStore, ignored -> line().openInterval(1));
            assertEquals(q.capture(), restored.capture());
            assertEquals(store.snapshot(), restoredStore.snapshot());
        }
        assertTrue(q.capture().isEmpty()); assertEquals(1, store.productCount(module));
        double inputMass = initial.commodityMassByIdKg().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(products.findProduct(module).unitMassKg(), inputMass, inputMass * 1e-12);
        q.advance(4, ignored -> store, ignored -> line().openInterval(1));
        assertEquals(1, store.productCount(module));
    }

    @Test void competingJobsShareWorkAndMissingLinePausesRatherThanCompletes() {
        var store = storage(2); var q = queue();
        q.start(id + "a", module, 1, store, line(), 0);
        q.start(id + "b", module, 1, store, line(), 0);
        var shared = line().openInterval(1);
        q.advance(1, ignored -> store, ignored -> shared);
        assertEquals(.25, q.capture().get(0).completedFraction(), 1e-12);
        assertEquals(0, q.capture().get(1).completedFraction());
        var before = q.capture();
        q.advance(2, ignored -> store, ignored -> null);
        assertEquals(before, q.capture());
        assertEquals(0, store.productCount(module));
        assertThrows(IllegalArgumentException.class, () -> q.advance(4, ignored -> store, ignored -> line().openInterval(100)));
    }

    @Test void cancellationReturnsFiniteCustodyAndRefusalsAreAtomic() {
        var store = storage(1); var q = queue(); var before = store.snapshot();
        q.start(id, module, 1, store, line(), 0);
        var reserved = store.snapshot(); var orders = q.capture();
        assertThrows(IllegalStateException.class, () -> q.start(id + "other", module, 1, store, line(), 0));
        assertEquals(reserved, store.snapshot()); assertEquals(orders, q.capture());
        q.advance(1, ignored -> store, ignored -> line().openInterval(1));
        q.cancel(id, store);
        assertEquals(before, store.snapshot()); assertTrue(q.capture().isEmpty());
        assertThrows(IllegalStateException.class, () -> q.cancel(id, store));
        var forged = new Stage18IndustrialState.ProcessOrderSnapshot(id, Stage18IndustrialState.ProcessKind.PRODUCT_MANUFACTURING,
                module, store.stationId(), "", 0, 1, .5, Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new Stage18ManufacturingWorkQueue(ontology, catalog, products, List.of(forged), 1));
    }

    @Test void fullStoragePausesWorkAndCannotDisplaceReservedMaterials() {
        var store = storage(1); var q = queue();
        q.start(id, module, 1, store, line(), 0);
        String commodity = profile().inputs().stream().filter(i -> ontology.findCommodity(i.commodityId()).storageClassId()
                .equals(products.findProduct(module).storageClassId())).findFirst().orElseThrow().commodityId();
        String storageClass = ontology.findCommodity(commodity).storageClassId();
        double fill = store.remainingCapacityKg(storageClass);
        store.addCommodity(commodity, fill);
        var full = store.snapshot(); var reserved = q.capture();
        assertThrows(IllegalStateException.class, () -> store.addCommodity(commodity, 1));
        assertEquals(full, store.snapshot()); assertEquals(reserved, q.capture());
        var supplied = storage(1); var suppliedBefore = supplied.snapshot();
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", Set.of(storageClass), 100, 100);
        var transferBudget = handling.openInterval(1);
        assertEquals(Stage18LogisticsRuntime.Status.DESTINATION_FULL,
                new Stage18LogisticsRuntime(ontology, products).transferCommodity(supplied, store, commodity, 1,
                        handling, transferBudget).status());
        assertEquals(suppliedBefore, supplied.snapshot()); assertEquals(full, store.snapshot());
        assertEquals(100, transferBudget.remainingMassKg());
        var budget = line().openInterval(10);
        double energy = budget.remainingEnergyJ(); double work = budget.remainingWorkSeconds();
        double maintenance = budget.remainingMaintenanceWorkSeconds();
        q.advance(1, ignored -> store, ignored -> budget);
        assertEquals(reserved, q.capture()); assertEquals(0, store.productCount(module));
        assertEquals(energy, budget.remainingEnergyJ()); assertEquals(work, budget.remainingWorkSeconds());
        assertEquals(maintenance, budget.remainingMaintenanceWorkSeconds());
        q.cancel(id, store); assertTrue(q.capture().isEmpty());
        assertEquals(0, store.productCount(module));
        assertEquals(products.findProduct(module).unitMassKg() + fill,
                store.snapshotCommodityMassByIdKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
    }

    @Test void restoreRejectsCustodyThatExceedsActualStationCapacity() {
        var store = storage(1); var q = queue();
        q.start(id, module, 1, store, line(), 0);
        String commodity = profile().inputs().get(0).commodityId();
        var forgedContents = new TreeMap<String, Double>();
        forgedContents.put(commodity, store.snapshotCapacityByStorageClassKg().get(ontology.findCommodity(commodity).storageClassId()));
        var overfilled = new Stage18StationStorage(ontology, products, store.stationId(), store.snapshotCapacityByStorageClassKg(),
                forgedContents, Map.of());
        var restored = new Stage18ManufacturingWorkQueue(ontology, catalog, products, q.capture(), 0);
        assertThrows(IllegalArgumentException.class, () -> restored.restoreReservations(ignored -> overfilled));
    }

    @Test void addingNewWorkCannotSkipTheExistingOrdersUnprocessedInterval() {
        var store = storage(2); var q = queue();
        q.start(id, module, 1, store, line(), 0);
        var before = store.snapshot(); var orders = q.capture();
        assertThrows(IllegalStateException.class, () -> q.start(id + "next", module, 1, store, line(), 2));
        assertEquals(before, store.snapshot()); assertEquals(orders, q.capture()); assertEquals(0, q.lastProcessedTick());
    }
}

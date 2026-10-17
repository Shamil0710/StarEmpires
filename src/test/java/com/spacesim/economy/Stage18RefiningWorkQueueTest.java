package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18RefiningWorkQueueTest {
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private static final String RECIPE = "refining.conductor_metal";
    private static final String INPUT = "commodity.feedstock.conductor_ore";
    private static final String OUTPUT = "commodity.material.conductor_metal";
    private static final String ID = Stage18ManufacturingWorkQueue.REFINING_PREFIX + "fixture";
    private Stage18ManufacturingWorkQueue queue(List<Stage18IndustrialState.ProcessOrderSnapshot> rows, long tick) {
        return new Stage18ManufacturingWorkQueue(ontology, Stage22CivilianMiningProductionPath.loadManufacturing(), products, rows, tick);
    }
    private Stage18StationStorage store(double input) {
        var capacities = new TreeMap<String, Double>();
        capacities.put(ontology.findCommodity(INPUT).storageClassId(), 1000d);
        capacities.put(ontology.findCommodity(OUTPUT).storageClassId(), 1000d);
        return new Stage18StationStorage(ontology, products, "fixture.refining", capacities, Map.of(INPUT, input), Map.of());
    }
    private Stage18ManufacturingRuntime.ManufacturingCapability line() {
        return new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line",
                Set.of("capability.process.bulk_refining"), 250_000_000, 55, 7);
    }

    @Test void partialNativeCustodyResumesToMassClosedOutputAndCannotRepeatCompletedWork() {
        var store = store(100); var q = queue(List.of(), 0);
        q.startRefining(ID, RECIPE, 100, store, line(), 0);
        assertEquals(0, store.commodityMassKg(INPUT));
        assertEquals(0, store.commodityMassKg(OUTPUT));
        q.advance(1, ignored -> store, ignored -> line().openInterval(1));
        assertEquals(.5, q.capture().get(0).completedFraction(), 1e-12);
        var persistent = new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), 1,
                List.of(), List.of(store.snapshot()), List.of(), List.of(), List.of(), q.capture());
        var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(persistent));
        var resumed = queue(decoded.processOrders(), 1);
        var resumedStore = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
        resumed.restoreReservations(ignored -> resumedStore);
        assertEquals(store.usedCapacityKg(ontology.findCommodity(INPUT).storageClassId()),
                resumedStore.usedCapacityKg(ontology.findCommodity(INPUT).storageClassId()));
        for (int tick = 2; tick <= 4; tick++) {
            q.advance(tick, ignored -> store, ignored -> line().openInterval(1));
            resumed.advance(tick, ignored -> resumedStore, ignored -> line().openInterval(1));
        }
        assertTrue(q.capture().isEmpty()); assertEquals(store.snapshot(), resumedStore.snapshot());
        assertEquals(63, store.commodityMassKg(OUTPUT), 1e-12);
        assertEquals(37, 100 - store.commodityMassKg(OUTPUT), 1e-12, "Authored tailings are not retained as useful material");
        resumed.advance(4, ignored -> resumedStore, ignored -> line().openInterval(1));
        assertEquals(store.snapshot(), resumedStore.snapshot());
    }

    @Test void cancellationReturnsRealInputAndCompetingOrdersShareOneInterval() {
        var store = store(200); var q = queue(List.of(), 0);
        q.startRefining(ID, RECIPE, 100, store, line(), 0);
        q.startRefining(ID + "2", RECIPE, 100, store, line(), 0);
        var shared = line().openInterval(1);
        q.advance(1, ignored -> store, ignored -> shared);
        assertEquals(.5, q.capture().stream().mapToDouble(o -> o.completedFraction()).sum(), 1e-12);
        assertEquals(0, shared.remainingEnergyJ(), 1e-6);
        q.cancel(ID, store); q.cancel(ID + "2", store);
        assertEquals(200, store.commodityMassKg(INPUT), 1e-12);
        assertEquals(0, store.commodityMassKg(OUTPUT));
        assertTrue(q.capture().isEmpty());
    }

    @Test void missingInputAndCorruptOrOverflowingReservationsFailWithoutMaterialChanges() {
        var store = store(99); var q = queue(List.of(), 0); var before = store.snapshot();
        assertThrows(IllegalStateException.class, () -> q.startRefining(ID, RECIPE, 100, store, line(), 0));
        assertThrows(IllegalArgumentException.class, () -> q.startRefining(ID, RECIPE, Double.MAX_VALUE, store, line(), 0));
        assertEquals(before, store.snapshot()); assertTrue(q.capture().isEmpty());
        var forged = new Stage18IndustrialState.ProcessOrderSnapshot(ID, Stage18IndustrialState.ProcessKind.REFINING,
                RECIPE, store.stationId(), "", 100, 0, .5, Map.of(INPUT, 99d), Map.of());
        assertThrows(IllegalArgumentException.class, () -> queue(List.of(forged), 1));
    }
}

package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionCapability;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23YardConstructionWorkQueueTest {
    private static final String YARD = "yard.orbital_escort_v1";
    private static final String LOCATION = "location.orbital_station";

    @Test void sharesActualWorkWithFacilityConstructionAndRetainsIndependentCapacityCustody() {
        var catalog = Stage23YardConstructionCatalog.loadDefault(); var spec = catalog.find(YARD);
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var facilityCatalog = Stage18FacilityConstructionCatalogLoader.loadDefault();
        String facility = "facility.fabrication.assembly";
        var raw = new TreeMap<>(spec.requiredMassByCommodityKg());
        facilityCatalog.requiredMassByCommodityKg(facility).forEach((id, kg) -> raw.merge(id, kg, Double::sum));
        var store = store(raw); double initial = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        var facilities = new Stage18FacilityConstructionWorkQueue(new Stage18FacilityConstructionRuntime(facilityCatalog,
                Stage18FacilityCatalogLoader.loadDefault(), ontology), ontology, List.of(), 7);
        var yards = new Stage23YardConstructionWorkQueue(catalog, ontology, Stage23YardConstructionWorkQueue.State.empty());
        facilities.start(Stage18FacilityConstructionWorkQueue.PREFIX + "facility", "facility-instance", facility, LOCATION, store, 7);
        yards.start("yard-order", "yard-instance", YARD, LOCATION, store, 7);
        assertTrue(store.snapshotCommodityMassByIdKg().isEmpty()); assertEquals(initial, occupied(store), 1e-6);
        double facilityWork = facilityCatalog.totalWorkSeconds(facility);
        var budget = new ConstructionCapability("shared-work", spec.requiredCapabilityTags(), facilityWork + spec.requiredWorkSeconds() / 2).openInterval(1);
        assertEquals(1, facilities.advance(8, id -> store, order -> budget).size());
        assertTrue(yards.advance(8, id -> store, order -> budget).isEmpty());
        assertEquals(0, budget.remainingWorkSeconds());
        assertEquals(spec.requiredWorkSeconds() / 2, yards.capture().orders().get(0).completedWorkSeconds());
        assertEquals(spec.installedMassKg(), occupied(store), 1e-6);
        var repeatBudget = new ConstructionCapability("repeat", spec.requiredCapabilityTags(), spec.requiredWorkSeconds()).openInterval(1);
        assertTrue(yards.advance(8, id -> store, order -> repeatBudget).isEmpty());
        assertEquals(spec.requiredWorkSeconds(), repeatBudget.remainingWorkSeconds());
        var currentYards = yards;
        assertThrows(IllegalArgumentException.class, () -> currentYards.advance(10, id -> store, order -> repeatBudget));
        assertThrows(IllegalStateException.class, () -> currentYards.cancel("yard-order", store));
        var saved = yards.capture(); var restoredStore = Stage18StationStorage.restore(ontology,
                Stage22CivilianMiningProductionPath.loadProducts(), store.snapshot());
        yards = new Stage23YardConstructionWorkQueue(catalog, ontology, saved); yards.bindReservations(id -> restoredStore);
        assertEquals(saved, yards.capture()); assertEquals(spec.installedMassKg(), occupied(restoredStore), 1e-6);
        var remaining = new ConstructionCapability("resume", spec.requiredCapabilityTags(), spec.requiredWorkSeconds() / 2).openInterval(1);
        assertEquals(1, yards.advance(9, id -> restoredStore, order -> remaining).size());
        assertEquals(0, occupied(restoredStore)); assertTrue(restoredStore.snapshotCommodityMassByIdKg().isEmpty());
        assertEquals(spec.requiredWorkSeconds(), yards.capture().orders().get(0).completedWorkSeconds());
        assertEquals(0, remaining.remainingWorkSeconds());
        assertThrows(IllegalArgumentException.class, () -> repeatBudget.consume(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> repeatBudget.consume(repeatBudget.remainingWorkSeconds() + 1));
    }

    @Test void cancellationReturnsOnlyRealUnworkedInputsAndInvalidContractsRejectBeforeMutation() {
        var catalog = Stage23YardConstructionCatalog.loadDefault(); var spec = catalog.find(YARD);
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var storage = store(spec.requiredMassByCommodityKg());
        var queue = new Stage23YardConstructionWorkQueue(catalog, ontology, Stage23YardConstructionWorkQueue.State.empty());
        var before = storage.snapshot();
        assertThrows(IllegalArgumentException.class, () -> queue.start("bad", "yard", YARD, "location.free_body", storage, 1));
        assertEquals(before, storage.snapshot()); assertTrue(queue.capture().orders().isEmpty());
        queue.start("order", "yard", YARD, LOCATION, storage, 1);
        var held = storage.snapshot(); double occupied = occupied(storage);
        assertThrows(IllegalStateException.class, () -> queue.start("second", "other-yard", YARD, LOCATION, storage, 1));
        assertEquals(held, storage.snapshot()); assertEquals(occupied, occupied(storage));
        queue.cancel("order", storage); assertEquals(before, storage.snapshot()); assertTrue(queue.capture().orders().isEmpty());
        var order = new Stage23YardConstructionWorkQueue.Order("order", "yard", YARD, storage.stationId(), LOCATION, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new Stage23YardConstructionWorkQueue(catalog, ontology,
                new Stage23YardConstructionWorkQueue.State("0".repeat(64), 1, List.of(order))));
        assertThrows(IllegalArgumentException.class, () -> new Stage23YardConstructionWorkQueue.State(catalog.fingerprint(), 0, List.of(order)));
        var future = new Stage23YardConstructionWorkQueue.Order("future", "future-yard", YARD, storage.stationId(), LOCATION, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new Stage23YardConstructionWorkQueue.State(catalog.fingerprint(), 1, List.of(future)));
    }

    @Test void stagedDeliveryBuildsTheFullStructureThroughAStoreSmallerThanItsBillAndResumesExactly() {
        var catalog = Stage23YardConstructionCatalog.loadDefault(); var spec = catalog.find(YARD);
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage22CivilianMiningProductionPath.loadProducts();
        var capacities = new TreeMap<String, Double>();
        spec.requiredMassByCommodityKg().keySet().forEach(id -> capacities.put(ontology.findCommodity(id).storageClassId(), 1_000_000d));
        var station = new Stage18StationStorage(ontology, products, "station", capacities, Map.of(), Map.of());
        // Explicit finite input fixture; this proves settlement, not ordinary market acquisition.
        var supply = store("finite-input-supply", spec.requiredMassByCommodityKg());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("physical-handling", capacities.keySet(), 1_000_000d, 1_000_000d);
        var logistics = new Stage18LogisticsRuntime(ontology, products);
        var queue = new Stage23YardConstructionWorkQueue(catalog, ontology, Stage23YardConstructionWorkQueue.State.empty());
        queue.plan("staged-order", "staged-yard", YARD, LOCATION, station, 0);
        assertTrue(queue.capture().orders().get(0).deliveredMassByCommodityKg().isEmpty());
        assertTrue(station.snapshotCommodityMassByIdKg().isEmpty());
        boolean resumed = false, completed = false;
        for (long tick = 1; tick <= 200 && !completed; tick++) {
            var incoming = handling.openInterval(1);
            for (String id : spec.requiredMassByCommodityKg().keySet()) {
                double kg = Math.min(supply.commodityMassKg(id), Math.min(incoming.remainingMassKg(),
                        station.remainingCapacityKg(ontology.findCommodity(id).storageClassId())));
                if (kg > 0) assertTrue(logistics.transferCommodity(supply, station, id, kg, handling, incoming).transferred());
            }
            var delivered = handling.openInterval(1);
            var work = new ConstructionCapability("actual-work", spec.requiredCapabilityTags(), spec.requiredWorkSeconds() / 2).openInterval(1);
            completed = !queue.advance(tick, id -> station, order -> work,
                    order -> new Stage23YardConstructionWorkQueue.MaterialDeliveryContext(handling, delivered)).isEmpty();
            var order = queue.capture().orders().get(0);
            double atSite = order.deliveredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(spec.installedMassKg(), atSite + occupied(station) + occupied(supply), 1e-6);
            assertTrue(occupied(station) <= capacities.values().stream().mapToDouble(Double::doubleValue).sum());
            if (!order.deliveredMassByCommodityKg().equals(spec.requiredMassByCommodityKg())) assertEquals(0, order.completedWorkSeconds());
            if (!resumed && atSite >= spec.installedMassKg() / 2) {
                var state = queue.capture();
                queue = new Stage23YardConstructionWorkQueue(catalog, ontology,
                        com.spacesim.persistence.Stage23YardConstructionPersistenceCodec.decode(
                                com.spacesim.persistence.Stage23YardConstructionPersistenceCodec.encode(state)));
                queue.bindReservations(id -> station); assertEquals(state, queue.capture()); resumed = true;
            }
        }
        assertTrue(resumed, "No half-bill delivery: " + queue.capture());
        assertTrue(completed, "Unfinished bill: " + queue.capture() + "; source=" + supply.snapshotCommodityMassByIdKg());
        assertTrue(supply.snapshotCommodityMassByIdKg().isEmpty()); assertTrue(station.snapshotCommodityMassByIdKg().isEmpty());
        var order = queue.capture().orders().get(0);
        assertEquals(spec.requiredMassByCommodityKg(), order.deliveredMassByCommodityKg());
        assertEquals(spec.requiredWorkSeconds(), order.completedWorkSeconds());
        assertFalse(queue.completedYard(order).enabled());
    }

    @Test void siteDeliverySharesActualHandlingAndCancellationCannotOverflowTheSourceWarehouse() {
        var catalog = Stage23YardConstructionCatalog.loadDefault(); var ontology = Stage18ResourceOntologyLoader.loadDefault();
        String commodity = catalog.find(YARD).requiredMassByCommodityKg().keySet().iterator().next();
        var station = store(Map.of(commodity, 1000d)); var other = store("other", Map.of(commodity, 1000d));
        other.removeCommodity(commodity, 1000d);
        var handling = new Stage18LogisticsRuntime.HandlingCapability("shared", station.snapshotCapacityByStorageClassKg().keySet(), 1000d, 1000d);
        var budget = handling.openInterval(1);
        assertTrue(new Stage18LogisticsRuntime(ontology, Stage22CivilianMiningProductionPath.loadProducts())
                .transferCommodity(station, other, commodity, 250, handling, budget).transferred());
        var queue = new Stage23YardConstructionWorkQueue(catalog, ontology, Stage23YardConstructionWorkQueue.State.empty());
        queue.plan("site", "yard", YARD, LOCATION, station, 0);
        queue.advance(1, id -> station, order -> null, order -> new Stage23YardConstructionWorkQueue.MaterialDeliveryContext(handling, budget));
        assertEquals(0, budget.remainingMassKg()); assertEquals(750d, queue.capture().orders().get(0).deliveredMassByCommodityKg().get(commodity));
        assertEquals(250d, other.commodityMassKg(commodity)); assertEquals(0, occupied(station));
        var saved = queue.capture(); var replay = handling.openInterval(1);
        queue.advance(1, id -> station, order -> null, order -> new Stage23YardConstructionWorkQueue.MaterialDeliveryContext(handling, replay));
        assertEquals(saved, queue.capture()); assertEquals(1000d, replay.remainingMassKg());
        station.addCommodity(commodity, 1000d);
        var full = station.snapshot(); assertThrows(IllegalArgumentException.class, () -> queue.cancel("site", station));
        assertEquals(saved, queue.capture()); assertEquals(full, station.snapshot());
        station.removeCommodity(commodity, 1000d); queue.cancel("site", station);
        assertEquals(750d, station.commodityMassKg(commodity)); assertTrue(queue.capture().orders().isEmpty());
    }

    private static double occupied(Stage18StationStorage store) {
        return store.snapshotCapacityByStorageClassKg().keySet().stream().mapToDouble(store::usedCapacityKg).sum();
    }
    private static Stage18StationStorage store(Map<String, Double> raw) {
        return store("station", raw);
    }
    private static Stage18StationStorage store(String stationId, Map<String, Double> raw) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var capacities = new TreeMap<String, Double>();
        raw.forEach((id, kg) -> capacities.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
        return new Stage18StationStorage(ontology, Stage22CivilianMiningProductionPath.loadProducts(), stationId, capacities, raw, Map.of());
    }
}

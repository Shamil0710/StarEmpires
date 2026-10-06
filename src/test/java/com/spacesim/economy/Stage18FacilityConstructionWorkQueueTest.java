package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionCapability;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.persistence.Stage18IndustrialContentFingerprint;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialStateCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18FacilityConstructionWorkQueueTest {
    private static final String DEFINITION = "facility.processing.recycling";
    private static final String LOCATION = "location.orbital_station";
    private static final String FIRST = Stage18FacilityConstructionWorkQueue.PREFIX + "one";
    private static final String SECOND = Stage18FacilityConstructionWorkQueue.PREFIX + "two";
    private final com.spacesim.content.Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18FacilityConstructionRuntime construction = new Stage18FacilityConstructionRuntime(
            Stage18FacilityConstructionCatalogLoader.loadDefault(), Stage18FacilityCatalogLoader.loadDefault(), ontology);

    @Test void reservedMaterialsStillOccupySpaceAndCancellationReturnsExactlyTheOriginalBill() {
        var storage = storage(1);
        var before = storage.snapshot();
        var queue = queue(List.of(), 0);
        var order = queue.start(FIRST, "facility.one", DEFINITION, LOCATION, storage, 0);
        assertTrue(storage.snapshotCommodityMassByIdKg().isEmpty());
        for (var capacity : before.capacityByStorageClassKg().entrySet()) {
            assertEquals(capacity.getValue(), storage.usedCapacityKg(capacity.getKey()), 1e-6);
            assertEquals(0d, storage.remainingCapacityKg(capacity.getKey()), 1e-6);
        }
        var commodity = order.requiredMassByCommodityKg().keySet().iterator().next();
        assertFalse(storage.canAddCommodity(commodity, 1));
        queue.cancel(FIRST, storage);
        assertEquals(before, storage.snapshot());
        assertTrue(queue.capture().isEmpty());
    }

    @Test void sharedWorkCannotBeDoubledByCompetingOrdersRepeatedTicksOrSaveLoad() {
        var storage = storage(2);
        var queue = queue(List.of(), 0);
        var first = queue.start(FIRST, "facility.one", DEFINITION, LOCATION, storage, 0);
        queue.start(SECOND, "facility.two", DEFINITION, LOCATION, storage, 0);
        var capability = new ConstructionCapability("actual-line", Set.of("capability.fabrication.heavy",
                "capability.fabrication.assembly"), 1);
        var shared = capability.openInterval(first.requiredWorkSeconds() + 10);
        var completed = queue.advance(1, id -> storage, order -> shared);
        assertEquals(1, completed.size());
        assertEquals(0d, shared.remainingWorkSeconds(), 1e-6);
        assertEquals(first.requiredWorkSeconds() + 10,
                queue.capture().stream().mapToDouble(ConstructionOrderSnapshot::completedWorkSeconds).sum(), 1e-6);
        var checkpoint = new Stage18IndustrialState(Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(), 1, List.of(), List.of(storage.snapshot()),
                List.of(), List.of(), queue.capture(), List.of());
        var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(checkpoint));
        assertEquals(checkpoint, decoded);
        var restoredStorage = Stage18StationStorage.restore(ontology, Stage18ManufacturingProductRegistry.loadDefault(),
                decoded.stationStorages().get(0));
        var restored = queue(decoded.constructionOrders(), decoded.simulationTick());
        restored.bindReservations(id -> restoredStorage);
        for (String storageClass : storage.snapshotCapacityByStorageClassKg().keySet())
            assertEquals(storage.usedCapacityKg(storageClass), restoredStorage.usedCapacityKg(storageClass), 1e-6);
        var saved = restored.capture();
        var duplicate = capability.openInterval(100);
        assertTrue(restored.advance(1, id -> restoredStorage, order -> duplicate).isEmpty());
        assertEquals(100d, duplicate.remainingWorkSeconds());
        assertThrows(IllegalArgumentException.class, () -> restored.advance(3, id -> restoredStorage, order -> duplicate));
        assertEquals(saved, restored.capture());
        assertEquals(100d, duplicate.remainingWorkSeconds());
        assertThrows(IllegalStateException.class, () -> restored.cancel(SECOND, restoredStorage));
        assertEquals(saved, restored.capture());
        var remaining = saved.stream().filter(o -> o.orderId().equals(SECOND)).findFirst().orElseThrow().remainingWorkSeconds();
        var finish = capability.openInterval(remaining);
        assertEquals(1, restored.advance(2, id -> restoredStorage, order -> finish).size());
        assertTrue(restored.capture().stream().allMatch(o -> o.status() == Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE));
        for (String storageClass : restoredStorage.snapshotCapacityByStorageClassKg().keySet())
            assertEquals(0d, restoredStorage.usedCapacityKg(storageClass), 1e-6);
    }

    @Test void rejectedStartCannotConsumeStockOrReplaceAnExistingReservation() {
        var storage = storage(1);
        var queue = queue(List.of(), 0);
        var before = storage.snapshot();
        assertThrows(IllegalArgumentException.class, () -> queue.start(FIRST, "facility.one", DEFINITION,
                "location.invalid", storage, 0));
        assertEquals(before, storage.snapshot());
        queue.start(FIRST, "facility.one", DEFINITION, LOCATION, storage, 0);
        var reserved = queue.capture();
        assertThrows(IllegalArgumentException.class, () -> queue.start(SECOND, "facility.one", DEFINITION, LOCATION, storage, 0));
        assertThrows(IllegalStateException.class, () -> queue.start(SECOND, "facility.two", DEFINITION, LOCATION, storage, 0));
        assertEquals(reserved, queue.capture());
        queue.cancel(FIRST, storage);
        assertEquals(before, storage.snapshot());
    }

    @Test void unavailableOrIncompatiblePhysicalWorkPausesWithoutInventingProgress() {
        var storage = storage(1);
        var queue = queue(List.of(), 0);
        var original = queue.start(FIRST, "facility.one", DEFINITION, LOCATION, storage, 0);
        assertTrue(queue.advance(1, id -> storage, order -> null).isEmpty());
        var incompatible = new ConstructionCapability("wrong-line", Set.of(), 1000).openInterval(1);
        assertTrue(queue.advance(2, id -> storage, order -> incompatible).isEmpty());
        assertEquals(List.of(original), queue.capture());
        assertEquals(1000d, incompatible.remainingWorkSeconds());
    }

    @Test void invalidRestoreAndStorageResolverCannotMutateEarlierOrdersOrTheirWorkBudget() {
        var storage = storage(2);
        var queue = queue(List.of(), 0);
        var first = queue.start(FIRST, "facility.one", DEFINITION, LOCATION, storage, 0);
        var second = queue.start(SECOND, "facility.two", DEFINITION, LOCATION, storage, 0);
        var foreign = new ConstructionOrderSnapshot(second.orderId(), second.facilityInstanceId(),
                second.facilityDefinitionId(), "other-station", second.locationTag(), second.requiredMassByCommodityKg(),
                second.deliveredMassByCommodityKg(), second.requiredWorkSeconds(), 0, second.status());
        var composed = queue(List.of(first, foreign), 0);
        var budget = new ConstructionCapability("actual-line", Set.of("capability.fabrication.heavy",
                "capability.fabrication.assembly"), 1000).openInterval(1);
        var before = composed.capture();
        assertThrows(IllegalArgumentException.class, () -> composed.advance(1, id -> storage, order -> budget));
        assertEquals(before, composed.capture());
        assertEquals(1000d, budget.remainingWorkSeconds());
        var changedBill = new ConstructionOrderSnapshot(first.orderId(), first.facilityInstanceId(),
                first.facilityDefinitionId(), first.stationId(), first.locationTag(), first.requiredMassByCommodityKg(),
                first.deliveredMassByCommodityKg(), first.requiredWorkSeconds() + 1, 0, first.status());
        assertThrows(IllegalArgumentException.class, () -> queue(List.of(changedBill), 0));
        var fakeProgress = new ConstructionOrderSnapshot(first.orderId(), first.facilityInstanceId(),
                first.facilityDefinitionId(), first.stationId(), first.locationTag(), first.requiredMassByCommodityKg(),
                first.deliveredMassByCommodityKg(), first.requiredWorkSeconds(), 1, first.status());
        assertThrows(IllegalArgumentException.class, () -> queue(List.of(fakeProgress), 0));
    }

    private Stage18FacilityConstructionWorkQueue queue(List<ConstructionOrderSnapshot> orders, long tick) {
        return new Stage18FacilityConstructionWorkQueue(construction, ontology, orders, tick);
    }

    private Stage18StationStorage storage(int copies) {
        var bill = construction.createOrder(FIRST, "template", DEFINITION, "station", LOCATION);
        var masses = new TreeMap<String, Double>();
        var capacities = new TreeMap<String, Double>();
        bill.requiredMassByCommodityKg().forEach((commodity, kg) -> {
            masses.put(commodity, kg * copies);
            capacities.merge(ontology.findCommodity(commodity).storageClassId(), kg * copies, Double::sum);
        });
        return new Stage18StationStorage(ontology, Stage18ManufacturingProductRegistry.loadDefault(),
                "station", capacities, masses, Map.of());
    }
}

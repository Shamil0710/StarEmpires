package com.spacesim.economy;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18FinishedProductHandlingReservationTest {
    private static final String PRODUCT = com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).unitMassKg();
    private final String storageClass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).storageClassId();

    @Test void reservedUnitsStayInSourceCapacityButCannotBeTransferredOrConsumedByAnotherOwner() {
        var source = storage("source", 3, mass * 3);
        var destination = storage("destination", 0, mass * 3);
        source.bindProductHandlingReservations(Map.of(PRODUCT, 2));
        assertEquals(1, source.productCount(PRODUCT));
        assertEquals(3, source.snapshotProductCountById().get(PRODUCT));
        assertEquals(mass * 3, source.usedCapacityKg(storageClass));
        var logistics = new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("actual-handling", Set.of(storageClass), mass * 3, mass);
        var budget = handling.openInterval(1);
        assertTrue(logistics.transferProduct(source, destination, PRODUCT, 1, handling, budget).transferred());
        assertEquals(2, source.snapshotProductCountById().get(PRODUCT));
        assertEquals(0, source.productCount(PRODUCT));
        var saved = source.snapshot();
        assertFalse(logistics.transferProduct(source, destination, PRODUCT, 1, handling, budget).transferred());
        assertThrows(IllegalArgumentException.class, () -> source.replaceContents(Map.of(), Map.of(PRODUCT, 1)));
        assertEquals(saved, source.snapshot());
        source.bindProductHandlingReservations(Map.of());
        assertEquals(2, source.productCount(PRODUCT));
        assertEquals(mass * 2, source.usedCapacityKg(storageClass));
    }

    @Test void invalidReservationAndCountOverflowRejectBeforeEitherPhysicalStorageChanges() {
        var source = storage("source", 1, mass);
        var before = source.snapshot();
        assertThrows(IllegalArgumentException.class, () -> source.bindProductHandlingReservations(Map.of(PRODUCT, 2)));
        assertThrows(IllegalArgumentException.class, () -> source.bindProductHandlingReservations(Map.of(PRODUCT, 0)));
        assertEquals(before, source.snapshot());
        assertEquals(1, source.productCount(PRODUCT));
        var destination = storage("destination", Integer.MAX_VALUE, mass * (Integer.MAX_VALUE + 2d));
        var oldDestination = destination.snapshot();
        var handling = new Stage18LogisticsRuntime.HandlingCapability("actual-handling", Set.of(storageClass), mass, mass);
        var budget = handling.openInterval(1);
        assertFalse(new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts())
                .transferProduct(source, destination, PRODUCT, 1, handling, budget).transferred());
        assertEquals(before, source.snapshot());
        assertEquals(oldDestination, destination.snapshot());
        assertEquals(mass, budget.remainingMassKg());
    }

    private Stage18StationStorage storage(String id, int count, double capacity) {
        return new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                id, Map.of(storageClass, capacity), Map.of(), count == 0 ? Map.of() : Map.of(PRODUCT, count));
    }
}

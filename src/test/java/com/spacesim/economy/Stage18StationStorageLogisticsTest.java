package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18LogisticsRuntime.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18StationStorageLogisticsTest {
    private static final double TOLERANCE = 1e-8d;

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ManufacturingProductRegistry products;
    private Stage18LogisticsRuntime logistics;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        products = Stage18ManufacturingProductRegistry.loadDefault();
        logistics = new Stage18LogisticsRuntime(ontology, products);
    }

    @Test
    void commoditiesAndFinishedProductsShareOnePhysicalStorageClassCapacity() {
        Stage18StationStorage storage = storage(
                "station.test.shared",
                Map.of("storage.hazardous_controlled", 1_500d),
                Map.of("commodity.material.industrial_chemicals", 600d),
                Map.of());

        assertEquals(600d, storage.usedCapacityKg("storage.hazardous_controlled"), TOLERANCE);
        assertEquals(900d, storage.remainingCapacityKg("storage.hazardous_controlled"), TOLERANCE);
        assertFalse(storage.canAddProduct("ammo.interceptor_1t_v1", 1));

        storage.removeCommodity("commodity.material.industrial_chemicals", 100d);
        assertTrue(storage.canAddProduct("ammo.interceptor_1t_v1", 1));
        storage.addProduct("ammo.interceptor_1t_v1", 1);
        assertEquals(1_500d, storage.usedCapacityKg("storage.hazardous_controlled"), TOLERANCE);
    }

    @Test
    void storageSnapshotRoundTripsExactPhysicalInventory() {
        Stage18StationStorage source = storage(
                "station.test.snapshot",
                Map.of("storage.dry_bulk", 10_000d, "storage.hazardous_controlled", 10_000d),
                Map.of("commodity.material.structural_alloy", 4_000d),
                Map.of("ammo.interceptor_1t_v1", 2));

        Stage18StationStorage restored = Stage18StationStorage.restore(ontology, products, source.snapshot());

        assertEquals(source.stationId(), restored.stationId());
        assertEquals(source.snapshotCapacityByStorageClassKg(), restored.snapshotCapacityByStorageClassKg());
        assertEquals(source.snapshotCommodityMassByIdKg(), restored.snapshotCommodityMassByIdKg());
        assertEquals(source.snapshotProductCountById(), restored.snapshotProductCountById());
    }

    @Test
    void commodityTransferConsumesFiniteMassBudgetAndMutatesBothEndpointsAtomically() {
        Stage18StationStorage source = storage(
                "station.test.source",
                Map.of("storage.dry_bulk", 10_000d),
                Map.of("commodity.material.structural_alloy", 1_000d),
                Map.of());
        Stage18StationStorage destination = storage(
                "station.test.destination",
                Map.of("storage.dry_bulk", 10_000d),
                Map.of(),
                Map.of());
        HandlingCapability handling = new HandlingCapability(
                "handling.test.bulk", Set.of("storage.dry_bulk"), 100d, 10_000d);
        var budget = handling.openInterval(5d);

        var first = logistics.transferCommodity(
                source, destination, "commodity.material.structural_alloy", 500d, handling, budget);
        var second = logistics.transferCommodity(
                source, destination, "commodity.material.structural_alloy", 1d, handling, budget);

        assertEquals(Status.TRANSFERRED, first.status());
        assertTrue(first.transferred());
        assertEquals(500d, source.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
        assertEquals(500d, destination.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
        assertEquals(0d, budget.remainingMassKg(), TOLERANCE);
        assertEquals(Status.THROUGHPUT_LIMIT, second.status());
        assertEquals(500d, source.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    @Test
    void incompatibleCargoClassIsRejectedWithoutMutation() {
        Stage18StationStorage source = storage(
                "station.test.source",
                Map.of("storage.hazardous_controlled", 10_000d),
                Map.of("commodity.material.industrial_chemicals", 1_000d),
                Map.of());
        Stage18StationStorage destination = storage(
                "station.test.destination",
                Map.of("storage.hazardous_controlled", 10_000d),
                Map.of(),
                Map.of());
        HandlingCapability handling = new HandlingCapability(
                "handling.test.dry", Set.of("storage.dry_bulk"), 1_000d, 10_000d);
        var budget = handling.openInterval(10d);

        var result = logistics.transferCommodity(
                source, destination, "commodity.material.industrial_chemicals", 100d, handling, budget);

        assertEquals(Status.STORAGE_CLASS_INCOMPATIBLE, result.status());
        assertEquals(1_000d, source.commodityMassKg("commodity.material.industrial_chemicals"), TOLERANCE);
        assertEquals(0d, destination.commodityMassKg("commodity.material.industrial_chemicals"), TOLERANCE);
        assertEquals(10_000d, budget.remainingMassKg(), TOLERANCE);
    }

    @Test
    void productTransferRespectsSingleUnitHandlingEnvelope() {
        Stage18StationStorage source = storage(
                "station.test.source",
                Map.of("storage.oversized", 5_000_000d),
                Map.of(),
                Map.of("module.reactor_5gw_v1", 1));
        Stage18StationStorage destination = storage(
                "station.test.destination",
                Map.of("storage.oversized", 5_000_000d),
                Map.of(),
                Map.of());
        HandlingCapability handling = new HandlingCapability(
                "handling.test.small", Set.of("storage.oversized"), 5_000_000d, 1_000_000d);
        var budget = handling.openInterval(1d);

        var result = logistics.transferProduct(
                source, destination, "module.reactor_5gw_v1", 1, handling, budget);

        assertEquals(Status.UNIT_HANDLING_LIMIT, result.status());
        assertEquals(1, source.productCount("module.reactor_5gw_v1"));
        assertEquals(0, destination.productCount("module.reactor_5gw_v1"));
        assertEquals(5_000_000d, budget.remainingMassKg(), TOLERANCE);
    }

    @Test
    void destinationFullRejectsProductTransferWithoutRemovingSourceCargo() {
        Stage18StationStorage source = storage(
                "station.test.source",
                Map.of("storage.hazardous_controlled", 5_000d),
                Map.of(),
                Map.of("ammo.interceptor_1t_v1", 1));
        Stage18StationStorage destination = storage(
                "station.test.destination",
                Map.of("storage.hazardous_controlled", 1_500d),
                Map.of("commodity.material.industrial_chemicals", 600d),
                Map.of());
        HandlingCapability handling = new HandlingCapability(
                "handling.test.hazard", Set.of("storage.hazardous_controlled"), 5_000d, 5_000d);
        var budget = handling.openInterval(1d);

        var result = logistics.transferProduct(
                source, destination, "ammo.interceptor_1t_v1", 1, handling, budget);

        assertEquals(Status.DESTINATION_FULL, result.status());
        assertEquals(1, source.productCount("ammo.interceptor_1t_v1"));
        assertEquals(0, destination.productCount("ammo.interceptor_1t_v1"));
    }

    private Stage18StationStorage storage(
            String stationId,
            Map<String, Double> capacities,
            Map<String, Double> commodities,
            Map<String, Integer> productCounts) {
        return new Stage18StationStorage(
                ontology, products, stationId, capacities, commodities, productCounts);
    }
}

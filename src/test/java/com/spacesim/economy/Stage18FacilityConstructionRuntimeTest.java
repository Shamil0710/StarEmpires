package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalog;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18FacilityConstructionRuntimeTest {
    private Stage18ResourceOntologyCatalog ontology;
    private Stage18FacilityCatalog facilities;
    private Stage18FacilityConstructionCatalog construction;
    private Stage18FacilityConstructionRuntime runtime;
    private Stage18StationStorage storage;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        facilities = Stage18FacilityCatalogLoader.loadDefault();
        construction = Stage18FacilityConstructionCatalogLoader.loadDefault();
        runtime = new Stage18FacilityConstructionRuntime(construction, facilities, ontology);
        storage = emptyStorage("station.test.construction");
    }

    @Test
    void physicalDeliveryAndFiniteWorkInstallOrdinaryStage18eFacility() {
        var order = runtime.createOrder(
                "construction.order.recycling",
                "station.test.construction.facility.recycling",
                "facility.processing.recycling",
                storage.stationId(),
                "location.orbital_station");
        assertEquals(18_000_000d, order.installedMassKg(), 1e-6d);
        loadExactBill(order, storage);

        for (Map.Entry<String, Double> required : order.requiredMassByCommodityKg().entrySet()) {
            var delivery = runtime.deliver(order, storage, required.getKey(), required.getValue());
            assertEquals(Stage18FacilityConstructionRuntime.DeliveryStatus.DELIVERED, delivery.status());
            order = delivery.order();
        }
        assertEquals(Stage18FacilityConstructionRuntime.OrderStatus.READY_FOR_WORK, order.status());
        assertTrue(order.materialsFulfilled());
        assertEquals(0d,
                order.requiredMassByCommodityKg().keySet().stream()
                        .mapToDouble(storage::commodityMassKg).sum(),
                1e-6d);

        var capability = new Stage18FacilityConstructionRuntime.ConstructionCapability(
                "construction.capability.test",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"),
                1000d);
        var budget = capability.openInterval(order.requiredWorkSeconds() / 1000d + 1d);
        var result = runtime.advanceWork(order, budget);

        assertEquals(Stage18FacilityConstructionRuntime.WorkStatus.COMPLETED, result.status());
        assertEquals(Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE, result.order().status());
        assertNotNull(result.installedFacility());
        assertEquals("facility.processing.recycling", result.installedFacility().facilityDefinitionId());
        assertEquals("station.test.construction.facility.recycling", result.installedFacility().facilityInstanceId());
    }

    @Test
    void engineeringWorkCannotBeginBeforePhysicalMaterialsArrive() {
        var order = runtime.createOrder(
                "construction.order.empty",
                "station.test.construction.facility.empty",
                "facility.processing.recycling",
                storage.stationId(),
                "location.orbital_station");
        var capability = new Stage18FacilityConstructionRuntime.ConstructionCapability(
                "construction.capability.test",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"),
                1000d);
        var budget = capability.openInterval(1000d);
        double before = budget.remainingWorkSeconds();

        var result = runtime.advanceWork(order, budget);

        assertEquals(Stage18FacilityConstructionRuntime.WorkStatus.MATERIALS_INCOMPLETE, result.status());
        assertEquals(0d, result.order().completedWorkSeconds(), 0d);
        assertEquals(before, budget.remainingWorkSeconds(), 0d);
    }

    @Test
    void missingConstructionCapabilityCannotBeReplacedByTimeOrCredits() {
        var order = runtime.createOrder(
                "construction.order.hightech",
                "station.test.construction.facility.precision",
                "facility.fabrication.precision",
                storage.stationId(),
                "location.orbital_station");
        loadExactBill(order, storage);
        for (Map.Entry<String, Double> required : order.requiredMassByCommodityKg().entrySet()) {
            order = runtime.deliver(order, storage, required.getKey(), required.getValue()).order();
        }
        var inadequate = new Stage18FacilityConstructionRuntime.ConstructionCapability(
                "construction.capability.heavy_only",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"),
                1_000_000d);
        var budget = inadequate.openInterval(1000d);

        var result = runtime.advanceWork(order, budget);

        assertEquals(Stage18FacilityConstructionRuntime.WorkStatus.MISSING_CAPABILITY, result.status());
        assertEquals(0d, result.order().completedWorkSeconds(), 0d);
    }

    @Test
    void materialMustFirstBeAtTheConstructionStation() {
        var order = runtime.createOrder(
                "construction.order.locality",
                "station.test.construction.facility.locality",
                "facility.processing.recycling",
                storage.stationId(),
                "location.orbital_station");
        Stage18StationStorage remote = emptyStorage("station.test.remote");
        String commodity = order.requiredMassByCommodityKg().keySet().iterator().next();
        double amount = order.requiredMassByCommodityKg().get(commodity);
        remote.addCommodity(commodity, amount);

        var result = runtime.deliver(order, remote, commodity, amount);

        assertEquals(Stage18FacilityConstructionRuntime.DeliveryStatus.INVALID_REQUEST, result.status());
        assertEquals(amount, remote.commodityMassKg(commodity), 1e-9d);
        assertEquals(0d, result.order().deliveredMassByCommodityKg().getOrDefault(commodity, 0d), 0d);
    }

    @Test
    void cancellationReturnsDeliveredMassInsteadOfDeletingIt() {
        var order = runtime.createOrder(
                "construction.order.cancel",
                "station.test.construction.facility.cancel",
                "facility.processing.recycling",
                storage.stationId(),
                "location.orbital_station");
        String commodity = order.requiredMassByCommodityKg().keySet().iterator().next();
        double required = order.requiredMassByCommodityKg().get(commodity);
        storage.addCommodity(commodity, required);
        var delivery = runtime.deliver(order, storage, commodity, required / 2d);
        assertEquals(required / 2d, delivery.acceptedMassKg(), 1e-9d);
        assertEquals(required / 2d, storage.commodityMassKg(commodity), 1e-9d);

        var cancelled = runtime.cancelAndReturn(delivery.order(), storage);

        assertEquals(Stage18FacilityConstructionRuntime.OrderStatus.CANCELLED, cancelled.status());
        assertEquals(required, storage.commodityMassKg(commodity), 1e-9d);
        assertTrue(cancelled.deliveredMassByCommodityKg().isEmpty());
    }

    @Test
    void incompatibleFacilityLocationIsRejectedBeforeAnyMaterialCanBeReserved() {
        assertThrows(IllegalArgumentException.class, () -> runtime.createOrder(
                "construction.order.badlocation",
                "facility.instance.badlocation",
                "facility.extraction.asteroid",
                storage.stationId(),
                "location.orbital_station"));
    }

    private void loadExactBill(
            Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot order,
            Stage18StationStorage target) {
        order.requiredMassByCommodityKg().forEach(target::addCommodity);
    }

    private Stage18StationStorage emptyStorage(String stationId) {
        return new Stage18StationStorage(
                ontology,
                Stage18ManufacturingProductRegistry.loadDefault(),
                stationId,
                Map.of(
                        "storage.dry_bulk", 200_000_000d,
                        "storage.liquid_tank", 200_000_000d,
                        "storage.pressurized_gas", 200_000_000d,
                        "storage.general_container", 200_000_000d,
                        "storage.hazardous_controlled", 200_000_000d,
                        "storage.high_value_controlled", 200_000_000d,
                        "storage.oversized", 200_000_000d),
                Map.of(),
                Map.of());
    }
}

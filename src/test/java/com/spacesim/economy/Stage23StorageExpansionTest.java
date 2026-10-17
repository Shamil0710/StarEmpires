package com.spacesim.economy;

import com.spacesim.content.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23StorageExpansionTest {
    @Test void monotonicExpansionRetainsEveryExistingCustodyAndHandlingReservation() {
        var store = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage18ManufacturingProductRegistry.loadDefault(),
                "test", Map.of("storage.dry_bulk", 1000d, "storage.hazardous_controlled", 10000d),
                Map.of("commodity.material.structural_alloy", 100d), Map.of("ammo.interceptor_1t_v1", 1));
        var raw = store.snapshotCommodityMassByIdKg(); var products = store.snapshotProductCountById();
        var reserved = Map.of("storage.dry_bulk", 10d);
        store.replaceContentsWithProcessReservation(raw, products, reserved);
        store.replaceContentsWithRepairReservation(raw, products, reserved);
        store.replaceContentsWithConstructionReservation(raw, products, reserved);
        store.replaceContentsWithYardConstructionReservation(raw, products, reserved);
        store.replaceContentsWithRefitReservation(raw, products, reserved, reserved);
        store.replaceContentsWithCraftBuildReservation(raw, products, reserved);
        store.bindProductHandlingReservations(Map.of("ammo.interceptor_1t_v1", 1));
        double occupied = store.usedCapacityKg("storage.hazardous_controlled");
        assertEquals(170d, store.usedCapacityKg("storage.dry_bulk"));
        assertEquals(930d, store.commodityLayerCapacityByStorageClassKg().get("storage.dry_bulk"));
        assertEquals(930d, store.manufacturingLayerCapacityByStorageClassKg().get("storage.dry_bulk"));
        assertThrows(IllegalArgumentException.class, () -> store.preflightRefitReservation(raw, products,
                Map.of("storage.dry_bulk", 845d), reserved));
        assertThrows(IllegalArgumentException.class, () -> store.replaceContentsWithCraftBuildReservation(raw, products,
                Map.of("storage.dry_bulk", 900d)));
        assertEquals(170d, store.usedCapacityKg("storage.dry_bulk"));
        var expanded = Map.of("storage.dry_bulk", 1000d, "storage.hazardous_controlled", 10000d, "storage.liquid_tank", 10000d);
        store.expandProvedCapacity(expanded); store.expandProvedCapacity(expanded);
        assertEquals(raw, store.snapshotCommodityMassByIdKg()); assertEquals(products, store.snapshotProductCountById());
        assertEquals(170d, store.usedCapacityKg("storage.dry_bulk"));
        assertEquals(occupied, store.usedCapacityKg("storage.hazardous_controlled")); assertEquals(0, store.productCount("ammo.interceptor_1t_v1"));
        assertThrows(IllegalArgumentException.class, () -> store.expandProvedCapacity(Map.of("storage.dry_bulk", 2000d)));
        assertThrows(IllegalArgumentException.class, () -> store.expandProvedCapacity(Map.of("unknown.class", 1000d)));
        assertThrows(IllegalArgumentException.class, () -> store.expandProvedCapacity(Map.of("storage.liquid_tank", Double.POSITIVE_INFINITY)));
        assertEquals(expanded, store.snapshotCapacityByStorageClassKg());
    }

    @Test void passiveContainmentCannotProduceOrReceiveOperatingResources() {
        var catalog = Stage23StationStorageCatalog.facilities();
        var state = new Stage18FacilityRuntime.InstalledFacilityState("tank", Stage23StationStorageCatalog.FACILITY_ID,
                1, 0, 0, 0, 0, "location.orbital_station", false);
        var capability = new Stage18FacilityRuntime(catalog).project(state);
        assertEquals(Stage18FacilityRuntime.Status.PASSIVE_STORAGE, capability.status());
        assertTrue(capability.capabilityTags().isEmpty()); assertEquals(0d, capability.effectiveProcessPowerW());
        assertEquals(0d, capability.effectiveEngineeringWorkRate()); assertEquals(0d, capability.effectiveMaintenanceWorkRate());
        var allocator = new Stage18FacilityResourceAllocator(catalog);
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(List.of(state), "tank"));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateFraction(List.of(state), "tank", .5));
        var forged = new Stage18FacilityRuntime.InstalledFacilityState("tank", state.definitionId(), 1, 1, 0, 0, 0, state.locationTag(), false);
        assertThrows(IllegalArgumentException.class, () -> new Stage18FacilityRuntime(catalog).project(forged));
    }
}

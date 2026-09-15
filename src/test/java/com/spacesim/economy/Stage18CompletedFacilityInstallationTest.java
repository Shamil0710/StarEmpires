package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A completed build is projected into the existing station roster without granting resources. */
class Stage18CompletedFacilityInstallationTest {
    private final Stage18FacilityConstructionRuntime construction = new Stage18FacilityConstructionRuntime(
            Stage18FacilityConstructionCatalogLoader.loadDefault(), Stage18FacilityCatalogLoader.loadDefault(),
            Stage18ResourceOntologyLoader.loadDefault());

    @Test
    void incompleteCorruptAndWrongStationOrdersCannotAddAWorkingFacility() {
        var node = node();
        var initial = construction.createOrder("order.install", "facility.install", "facility.fabrication.precision",
                node.stationId(), node.locationTag());
        assertThrows(IllegalArgumentException.class, () -> node.withCompletedConstruction(initial, construction));
        var unpaid = copy(initial, initial.requiredMassByCommodityKg(), Map.of(), initial.requiredWorkSeconds(),
                initial.requiredWorkSeconds(), OrderStatus.COMPLETE, initial.stationId());
        assertThrows(IllegalArgumentException.class, () -> node.withCompletedConstruction(unpaid, construction));
        var corruptBill = copy(initial, Map.of("commodity.material.structural_steel", 1d),
                Map.of("commodity.material.structural_steel", 1d), 1d, 1d, OrderStatus.COMPLETE, initial.stationId());
        assertThrows(IllegalArgumentException.class, () -> node.withCompletedConstruction(corruptBill, construction));
        var foreign = copy(initial, initial.requiredMassByCommodityKg(), initial.requiredMassByCommodityKg(),
                initial.requiredWorkSeconds(), initial.requiredWorkSeconds(), OrderStatus.COMPLETE, "foreign.station");
        assertThrows(IllegalArgumentException.class, () -> node.withCompletedConstruction(foreign, construction));
        assertEquals(4, node.installedFacilities().size());
    }

    @Test
    void paidCompletionKeepsStableIdentityAndExistingStorageAndRejectsIdCollision() {
        var node = node();
        var initial = construction.createOrder("order.install", "facility.install", "facility.fabrication.precision",
                node.stationId(), node.locationTag());
        var kit = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(), node.stationId(),
                node.storage().snapshotCapacityByStorageClassKg(), initial.requiredMassByCommodityKg(), Map.of());
        var paid = initial;
        for (var entry : initial.requiredMassByCommodityKg().entrySet()) {
            paid = construction.deliver(paid, kit, entry.getKey(), entry.getValue()).order();
        }
        var capability = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.constructors",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly",
                        "capability.fabrication.electrical", "capability.fabrication.precision"), 1d);
        var completed = construction.advanceWork(paid, capability.openInterval(paid.requiredWorkSeconds() + 1d));
        var expanded = node.withCompletedConstruction(completed.order(), construction);
        assertEquals(5, expanded.installedFacilities().size());
        assertEquals(4, node.installedFacilities().size());
        assertSame(node.storage(), expanded.storage());
        assertSame(node.handlingCapability(), expanded.handlingCapability());
        assertSame(expanded, expanded.withCompletedConstruction(completed.order(), construction));
        var other = construction.createOrder("order.collision", "facility.install", "facility.fabrication.heavy",
                node.stationId(), node.locationTag());
        var conflict = copy(other, other.requiredMassByCommodityKg(), other.requiredMassByCommodityKg(),
                other.requiredWorkSeconds(), other.requiredWorkSeconds(), OrderStatus.COMPLETE, other.stationId());
        assertThrows(IllegalArgumentException.class, () -> expanded.withCompletedConstruction(conflict, construction));
        assertTrue(kit.snapshotCommodityMassByIdKg().isEmpty());
    }

    private static Stage18StationIndustrialNode node() {
        return Stage18StationIndustrialNode.instantiate("station.install", "location.orbital_station",
                Stage18StationInfrastructureCatalogLoader.loadDefault().findArchetype("station.infrastructure.industrial_station"),
                Stage18ResourceOntologyLoader.loadDefault(), Stage18ManufacturingProductRegistry.loadDefault());
    }

    private static ConstructionOrderSnapshot copy(ConstructionOrderSnapshot source, Map<String, Double> required,
            Map<String, Double> delivered, double work, double done, OrderStatus status, String station) {
        return new ConstructionOrderSnapshot(source.orderId(), source.facilityInstanceId(), source.facilityDefinitionId(),
                station, source.locationTag(), required, delivered, work, done, status);
    }
}

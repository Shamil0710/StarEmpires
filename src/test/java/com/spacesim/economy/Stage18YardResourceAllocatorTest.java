package com.spacesim.economy;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18YardResourceAllocatorTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"yard.empire_capital_service_v1", "yard.industrial_union.series_complex_v1"})
    void availableResourcesEnablePartialOperationWithoutCreatingCapacity(String design) {
        var catalog = com.spacesim.content.Stage23SmallCraftProductionCatalogs.shipyards();
        var allocator = new Stage18YardResourceAllocator(catalog);
        var donor = new InstalledYardState("donor", "yard.orbital_escort_v1", .6, 1.2e9, 12, 500, 500, true);
        var target = new InstalledYardState("target", design, 1, 0, 0, 0, 0, false);
        var original = List.of(target, donor);
        assertThrows(IllegalStateException.class, () -> allocator.allocateRated(original, "target"));
        var result = allocator.allocateAvailable(original, "target");
        var active = result.stream().filter(y -> y.yardInstanceId().equals("target")).findFirst().orElseThrow();
        assertTrue(active.enabled()); assertEquals(1.2e9, active.allocatedIntegrationPowerW(), 1e-6);
        assertTrue(active.availableIntegrationWorkRate() > 7);
        assertTrue(active.availableLaborCapacity() >= 400); assertTrue(active.availableAutomationCapacity() >= 200);
        assertEquals(1.2e9, result.stream().mapToDouble(InstalledYardState::allocatedIntegrationPowerW).sum(), 1e-6);
        assertEquals(12, result.stream().mapToDouble(InstalledYardState::availableIntegrationWorkRate).sum(), 1e-12);
        assertEquals(500, result.stream().mapToInt(InstalledYardState::availableLaborCapacity).sum());
        assertEquals(500, result.stream().mapToInt(InstalledYardState::availableAutomationCapacity).sum());
        assertEquals(result, allocator.allocateAvailable(result, "target"));
        assertEquals(.6, result.stream().filter(y -> y.yardInstanceId().equals("donor")).findFirst().orElseThrow().conditionFraction());
        var noLabor = new InstalledYardState("empty", donor.yardDefinitionId(), 1, 1.2e9, 12, 0, 500, true);
        assertThrows(IllegalStateException.class, () -> allocator.allocateAvailable(List.of(target, noLabor), "target"));
        assertFalse(target.enabled());
    }
    @Test void integerLimitedAvailableAllocationNeverRoundsStaffOutOfNothing() {
        var allocator = new Stage18YardResourceAllocator(com.spacesim.content.Stage23SmallCraftProductionCatalogs.shipyards());
        var donor = new InstalledYardState("donor", "yard.orbital_escort_v1", 1, 1.2e9, 12, 1, 2, true);
        var target = new InstalledYardState("target", "yard.empire_capital_service_v1", 1, 0, 0, 0, 0, false);
        var result = allocator.allocateAvailable(List.of(target, donor), "target");
        var supplied = result.stream().filter(y -> y.yardInstanceId().equals("target")).findFirst().orElseThrow();
        assertEquals(1, supplied.availableLaborCapacity()); assertEquals(1, supplied.availableAutomationCapacity());
        assertEquals(1, result.stream().mapToInt(InstalledYardState::availableLaborCapacity).sum());
        assertEquals(2, result.stream().mapToInt(InstalledYardState::availableAutomationCapacity).sum());
        assertEquals(result, allocator.allocateAvailable(result, "target"));
        assertEquals(12, result.stream().mapToDouble(InstalledYardState::availableIntegrationWorkRate).sum(), 1e-12);
    }
    @Test void redistributionConservesAllAllocationsAndPreservesPhysicalCondition() {
        var catalog = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var design = catalog.findYard("yard.orbital_escort_v1");
        var donor = new InstalledYardState("donor", design.id(), .6, design.ratedIntegrationPowerW(),
                design.ratedEngineeringWorkRate(), design.laborCapacity(), design.automationCapacity(), true);
        var target = new InstalledYardState("target", design.id(), 1, 0, 0, 0, 0, false);
        var original = List.of(target, donor);
        var allocator = new Stage18YardResourceAllocator(catalog);
        var result = allocator.allocateRated(original, "target");
        var working = result.stream().filter(y -> y.yardInstanceId().equals("target")).findFirst().orElseThrow();
        var depleted = result.stream().filter(y -> y.yardInstanceId().equals("donor")).findFirst().orElseThrow();
        assertTrue(working.enabled()); assertEquals(design.ratedIntegrationPowerW(), working.allocatedIntegrationPowerW());
        assertEquals(design.ratedEngineeringWorkRate(), working.availableIntegrationWorkRate());
        assertEquals(design.laborCapacity(), working.availableLaborCapacity()); assertEquals(design.automationCapacity(), working.availableAutomationCapacity());
        assertEquals(0, depleted.allocatedIntegrationPowerW()); assertEquals(0, depleted.availableIntegrationWorkRate());
        assertEquals(0, depleted.availableLaborCapacity()); assertEquals(0, depleted.availableAutomationCapacity());
        assertEquals(.6, depleted.conditionFraction()); assertTrue(depleted.enabled());
        assertEquals(result, allocator.allocateRated(result, "target")); assertFalse(target.enabled());
    }

    @Test void missingResourcesOrDuplicateIdentitiesRejectWithoutPartialAllocation() {
        var catalog = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var target = new InstalledYardState("target", "yard.orbital_escort_v1", 1, 0, 0, 0, 0, false);
        var allocator = new Stage18YardResourceAllocator(catalog);
        assertThrows(IllegalStateException.class, () -> allocator.allocateRated(List.of(target), "target"));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(List.of(target, target), "target"));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(List.of(target), "absent"));
        assertFalse(target.enabled()); assertEquals(0, target.allocatedIntegrationPowerW());
    }
}

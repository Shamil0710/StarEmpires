package com.spacesim.economy;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18YardResourceAllocatorTest {
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

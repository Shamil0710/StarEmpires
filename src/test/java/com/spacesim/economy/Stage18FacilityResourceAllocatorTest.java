package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18FacilityResourceAllocatorTest {
    private final Stage18FacilityResourceAllocator allocator = new Stage18FacilityResourceAllocator(Stage18FacilityCatalogLoader.loadDefault());

    @Test void ratedAllocationConservesAllFourResourcesAndExistingConditions() {
        var d = Stage18FacilityCatalogLoader.loadDefault().findFacility("facility.fabrication.heavy");
        var donor = new InstalledFacilityState("donor", d.id(), .7, d.ratedProcessPowerW(),
                d.ratedProcessPowerW() * d.heatRejectionWPerProcessW(), d.requiredLaborUnitsAtFullRate(), d.maintenanceWorkRate(),
                "location.orbital_station", true);
        var target = new InstalledFacilityState("target", d.id(), 1, 0, 0, 0, 0, "location.orbital_station", false);
        var before = List.of(target, donor);
        var result = allocator.allocateRated(before, "target");
        assertEquals(2, result.size());
        assertEquals(0d, result.get(0).allocatedProcessPowerW());
        assertEquals(.7, result.get(0).conditionFraction());
        assertTrue(result.get(0).enabled());
        assertTrue(result.get(1).enabled());
        assertEquals(d.ratedProcessPowerW(), result.get(1).allocatedProcessPowerW());
        assertEquals(d.ratedProcessPowerW() * d.heatRejectionWPerProcessW(), result.get(1).availableHeatRejectionW());
        assertEquals(d.requiredLaborUnitsAtFullRate(), result.get(1).availableLaborUnits());
        assertEquals(d.maintenanceWorkRate(), result.get(1).availableMaintenanceWorkRate());
        var projection = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        assertEquals(0d, projection.project(result.get(0)).effectiveEngineeringWorkRate());
        assertEquals(d.engineeringWorkRate(), projection.project(result.get(1)).effectiveEngineeringWorkRate());
        assertEquals(result, allocator.allocateRated(result, "target"));
        assertEquals(target, before.get(0), "Allocation is a pure transaction");
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test void absentBrokenDuplicateAndUnderfundedInstallationsRejectWithoutChangingInputs() {
        var target = new InstalledFacilityState("target", "facility.fabrication.heavy", 1, 0, 0, 0, 0,
                "location.orbital_station", false);
        var before = List.of(target);
        assertThrows(IllegalStateException.class, () -> allocator.allocateRated(before, "target"));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(before, "missing"));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(List.of(target, target), "target"));
        var broken = new InstalledFacilityState("target", target.definitionId(), 0, 0, 0, 0, 0, target.locationTag(), false);
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateRated(List.of(broken), "target"));
        assertEquals(List.of(target), before);
    }
}

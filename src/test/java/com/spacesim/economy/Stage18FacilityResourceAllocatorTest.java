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

    @Test void halfRatedOperationCanStartBelowFullResourcesAndReturnsSurplusExactly() {
        var d = Stage18FacilityCatalogLoader.loadDefault().findFacility("facility.fabrication.heavy");
        var donor = new InstalledFacilityState("donor", d.id(), .7, .5 * d.ratedProcessPowerW(),
                .5 * d.ratedProcessPowerW() * d.heatRejectionWPerProcessW(), .5 * d.requiredLaborUnitsAtFullRate(),
                .5 * d.maintenanceWorkRate(), "location.orbital_station", true);
        var target = new InstalledFacilityState("target", d.id(), 1, 0, 0, 0, 0, "location.orbital_station", false);
        var before = List.of(target, donor);
        assertThrows(IllegalStateException.class, () -> allocator.allocateRated(before, "target"));
        var half = allocator.allocateFraction(before, "target", .5);
        assertEquals(.5, new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault()).project(half.get(1)).effectiveThroughputFraction(), 1e-12);
        assertEquals(.7, half.get(0).conditionFraction());
        assertEquals(half, allocator.allocateFraction(half, "target", .5));
        var quarter = allocator.allocateFraction(half, "target", .25);
        assertEquals(.25 * d.ratedProcessPowerW(), quarter.get(0).allocatedProcessPowerW());
        assertEquals(.25 * d.ratedProcessPowerW(), quarter.get(1).allocatedProcessPowerW());
        assertEquals(half, allocator.allocateFraction(quarter, "target", .5));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateFraction(before, "target", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocateFraction(before, "target", 0));
        assertThrows(IllegalStateException.class, () -> allocator.allocateFraction(before, "target", .75));
        for (var result : List.of(half, quarter)) {
            assertEquals(before.stream().mapToDouble(InstalledFacilityState::allocatedProcessPowerW).sum(), result.stream().mapToDouble(InstalledFacilityState::allocatedProcessPowerW).sum(), 1e-8);
            assertEquals(before.stream().mapToDouble(InstalledFacilityState::availableHeatRejectionW).sum(), result.stream().mapToDouble(InstalledFacilityState::availableHeatRejectionW).sum(), 1e-8);
            assertEquals(before.stream().mapToDouble(InstalledFacilityState::availableLaborUnits).sum(), result.stream().mapToDouble(InstalledFacilityState::availableLaborUnits).sum(), 1e-8);
            assertEquals(before.stream().mapToDouble(InstalledFacilityState::availableMaintenanceWorkRate).sum(), result.stream().mapToDouble(InstalledFacilityState::availableMaintenanceWorkRate).sum(), 1e-8);
        }
    }
}

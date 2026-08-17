package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18FacilityCapabilityNetworkTest {
    @Test
    void activePhysicalLinesComposeRecipeCapabilitiesWithoutStationClassBonus() {
        Stage18FacilityRuntime runtime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        var bulk = runtime.project(fullRate(
                "facility.network.bulk", "facility.processing.bulk_refinery", 180_000_000d, 135_000_000d, 90d, 3.5d));
        var advanced = runtime.project(fullRate(
                "facility.network.advanced", "facility.processing.advanced_materials", 130_000_000d, 105_000_000d, 70d, 4.5d));

        assertFalse(advanced.capabilityTags().contains("capability.process.beneficiation"));
        var network = Stage18FacilityCapabilityNetwork.refining(
                "network.refining.hightech", List.of(advanced, bulk));

        assertTrue(network.capabilityTags().contains("capability.process.beneficiation"));
        assertTrue(network.capabilityTags().contains("capability.process.advanced_materials"));
        assertTrue(network.availablePowerW() > advanced.effectiveProcessPowerW());
        assertTrue(network.workRate() > advanced.effectiveEngineeringWorkRate());
        assertTrue(network.maintenanceWorkRate() > advanced.effectiveMaintenanceWorkRate());
    }

    private static Stage18FacilityRuntime.InstalledFacilityState fullRate(
            String instanceId,
            String definitionId,
            double powerW,
            double heatRejectionW,
            double labor,
            double maintenanceRate) {
        return new Stage18FacilityRuntime.InstalledFacilityState(
                instanceId,
                definitionId,
                1d,
                powerW,
                heatRejectionW,
                labor,
                maintenanceRate,
                "location.orbital_station",
                true);
    }
}

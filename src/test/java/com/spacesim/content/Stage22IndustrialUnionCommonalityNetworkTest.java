package com.spacesim.content;

import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22IndustrialUnionCommonalityNetworkTest {
    @Test
    void correlatedSharedNetworkDisruptionIsMateriallyWorseThanIsolatedLocalLoss() {
        YardSeriesState yard = steadyLogisticsYard();
        var healthy = Stage22IndustrialUnionCommonalityNetwork.observe(
                yard,
                "ship_family.industrial_union.freight",
                Stage22IndustrialUnionCommonalityNetwork.healthy());

        Map<String, Double> localAssemblies = new LinkedHashMap<>(
                Stage22IndustrialUnionCommonalityNetwork.healthy().sharedAssemblyAvailability());
        localAssemblies.put("module.industrial_union_sensor_block_v1", 0.75d);
        var isolated = Stage22IndustrialUnionCommonalityNetwork.observe(
                yard,
                "ship_family.industrial_union.freight",
                new Stage22IndustrialUnionCommonalityNetwork.Availability(localAssemblies, 1d, 1d));

        Map<String, Double> correlatedAssemblies = new LinkedHashMap<>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> correlatedAssemblies.put(id, 0.75d));
        var correlated = Stage22IndustrialUnionCommonalityNetwork.observe(
                yard,
                "ship_family.industrial_union.freight",
                new Stage22IndustrialUnionCommonalityNetwork.Availability(correlatedAssemblies, 0.75d, 0.75d));

        assertEquals(1d, healthy.workBurdenMultiplier(), 1e-12d);
        assertEquals(0d, healthy.throughputDegradation(), 1e-12d);
        assertFalse(healthy.correlatedDisruption());
        assertTrue(isolated.throughputDegradation() < 0.10d);
        assertFalse(isolated.correlatedDisruption());
        assertTrue(correlated.correlatedDisruption());
        assertTrue(correlated.throughputDegradation() >= 0.25d);
        assertTrue(correlated.throughputDegradation() > isolated.throughputDegradation());
        assertTrue(correlated.workBurdenMultiplier() > isolated.workBurdenMultiplier());
    }

    @Test
    void networkBurdenProjectsIntoStage18WorkAndEnergyWithoutChangingMaterialInputs() {
        YardSeriesState yard = steadyLogisticsYard();
        var base = Stage22CommonManufacturingProfiles.definitions().get(0);
        var healthy = Stage22IndustrialUnionCommonalityNetwork.deriveProfile(
                base,
                "manufacturing.profile.industrial_union_network_healthy",
                yard,
                "ship_family.industrial_union.freight",
                Stage22IndustrialUnionCommonalityNetwork.healthy());

        Map<String, Double> assemblies = new LinkedHashMap<>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> assemblies.put(id, 0.75d));
        var disruptedAvailability = new Stage22IndustrialUnionCommonalityNetwork.Availability(
                assemblies, 0.75d, 0.75d);
        var disrupted = Stage22IndustrialUnionCommonalityNetwork.deriveProfile(
                base,
                "manufacturing.profile.industrial_union_network_disrupted",
                yard,
                "ship_family.industrial_union.freight",
                disruptedAvailability);

        assertEquals(base.inputs(), disrupted.inputs());
        assertEquals(base.requiredCapabilityTags(), disrupted.requiredCapabilityTags());
        assertEquals(base.maintenanceWorkSecondsPerOutputKg(), disrupted.maintenanceWorkSecondsPerOutputKg());
        assertTrue(disrupted.workSecondsPerOutputKg() > healthy.workSecondsPerOutputKg());
        assertTrue(disrupted.energyJPerOutputKg() > healthy.energyJPerOutputKg());
        assertTrue(disrupted.workSecondsPerOutputKg() > base.workSecondsPerOutputKg());
    }

    @Test
    void availabilityFailsClosedOnMissingExtraOrOutOfRangeDependencies() {
        Map<String, Double> missing = new LinkedHashMap<>();
        missing.put("module.industrial_union_reactor_bank_v1", 1d);
        assertThrows(IllegalArgumentException.class,
                () -> new Stage22IndustrialUnionCommonalityNetwork.Availability(missing, 1d, 1d));

        Map<String, Double> exact = new LinkedHashMap<>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> exact.put(id, 1d));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage22IndustrialUnionCommonalityNetwork.Availability(exact, -0.01d, 1d));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage22IndustrialUnionCommonalityNetwork.Availability(exact, 1d, 1.01d));
    }

    private static YardSeriesState steadyLogisticsYard() {
        YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.freight");
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
        yard = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        for (int i = 0; i < 3; i++) {
            yard = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    yard, "ship_family.industrial_union.freight");
        }
        return yard;
    }
}

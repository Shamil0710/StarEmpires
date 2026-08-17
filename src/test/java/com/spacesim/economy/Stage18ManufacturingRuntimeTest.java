package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ManufacturingRuntime.IntervalBudget;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingResult;
import com.spacesim.economy.Stage18ManufacturingRuntime.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ManufacturingRuntimeTest {
    private static final double TOLERANCE = 1e-7d;

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ManufacturingProductRegistry products;
    private Stage18ManufacturingRuntime runtime;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        products = Stage18ManufacturingProductRegistry.loadDefault();
        Stage18ManufacturingCatalog catalog = Stage18ManufacturingCatalogLoader.loadDefault();
        runtime = new Stage18ManufacturingRuntime(ontology, catalog, products);
    }

    @Test
    void heavyComponentsConsumeExactlyTheirPhysicalOutputMass() {
        ManufacturingInventory inventory = inventory(Map.of(
                "commodity.material.structural_alloy", 1000d,
                "commodity.material.light_alloy", 1000d,
                "commodity.material.refractory_alloy", 1000d,
                "commodity.material.conductor_metal", 1000d,
                "commodity.material.industrial_chemicals", 1000d,
                "commodity.material.carbon_material", 1000d));
        IntervalBudget budget = capability(Set.of("capability.fabrication.heavy")).openInterval(10d);

        ManufacturingResult result = runtime.manufactureComponent(
                "manufacturing.component.heavy", 100d, inventory, budget);

        assertEquals(Status.MANUFACTURED, result.status());
        assertTrue(result.accepted());
        assertEquals(100d, result.outputMassKg(), TOLERANCE);
        assertEquals(100d, result.consumedInputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum(), TOLERANCE);
        assertEquals(100d, inventory.commodityMassKg("commodity.component.heavy_components"), TOLERANCE);
        assertEquals(945d, inventory.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    @Test
    void productionReactorUsesAuthoritativeTwoPointTwoMillionKgMass() {
        ManufacturingInventory inventory = inventory(Map.of(
                "commodity.component.heavy_components", 1_500_000d,
                "commodity.component.electrical_components", 1_000_000d,
                "commodity.component.precision_components", 1_000_000d,
                "commodity.material.structural_alloy", 1_000_000d,
                "commodity.material.refractory_alloy", 1_000_000d,
                "commodity.material.ceramic_glass", 1_000_000d));
        Set<String> tags = Set.of(
                "capability.fabrication.assembly",
                "capability.fabrication.heavy",
                "capability.fabrication.electrical",
                "capability.fabrication.precision");

        ManufacturingResult result = runtime.manufactureProduct(
                "module.reactor_5gw_v1", 1, inventory, capability(tags).openInterval(10d));

        assertEquals(Status.MANUFACTURED, result.status());
        assertEquals(2_200_000d, result.outputMassKg(), TOLERANCE);
        assertEquals(1, result.outputUnitCount());
        assertEquals(1, inventory.productCount("module.reactor_5gw_v1"));
        assertEquals(2_200_000d, result.consumedInputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum(), TOLERANCE);
        assertEquals(510_000d, inventory.commodityMassKg("commodity.component.heavy_components"), TOLERANCE);
    }

    @Test
    void guidedAmmunitionManufacturesFinitePhysicalInventoryByCount() {
        ManufacturingInventory inventory = inventory(Map.of(
                "commodity.material.light_alloy", 10_000d,
                "commodity.material.structural_alloy", 10_000d,
                "commodity.material.industrial_chemicals", 10_000d,
                "commodity.component.electrical_components", 10_000d,
                "commodity.component.precision_components", 10_000d,
                "commodity.material.conductor_metal", 10_000d,
                "commodity.material.carbon_material", 10_000d));
        Set<String> tags = Set.of(
                "capability.fabrication.ordnance",
                "capability.fabrication.electrical",
                "capability.fabrication.precision");

        ManufacturingResult result = runtime.manufactureProduct(
                "ammo.interceptor_1t_v1", 2, inventory, capability(tags).openInterval(10d));

        assertEquals(Status.MANUFACTURED, result.status());
        assertEquals(2_000d, result.outputMassKg(), TOLERANCE);
        assertEquals(2, inventory.productCount("ammo.interceptor_1t_v1"));
        assertEquals(2_000d, result.consumedInputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum(), TOLERANCE);
    }

    @Test
    void rejectedManufacturingDoesNotConsumeInputsOrBudget() {
        ManufacturingInventory inventory = inventory(Map.of(
                "commodity.material.structural_alloy", 10d,
                "commodity.material.light_alloy", 100d,
                "commodity.material.refractory_alloy", 100d,
                "commodity.material.conductor_metal", 100d,
                "commodity.material.industrial_chemicals", 100d,
                "commodity.material.carbon_material", 100d));
        IntervalBudget budget = capability(Set.of("capability.fabrication.heavy")).openInterval(10d);
        Map<String, Double> before = inventory.snapshotCommodityMassByIdKg();
        double energyBefore = budget.remainingEnergyJ();

        ManufacturingResult result = runtime.manufactureComponent(
                "manufacturing.component.heavy", 100d, inventory, budget);

        assertEquals(Status.INSUFFICIENT_INPUT, result.status());
        assertFalse(result.accepted());
        assertEquals(before, inventory.snapshotCommodityMassByIdKg());
        assertEquals(energyBefore, budget.remainingEnergyJ(), TOLERANCE);
    }

    @Test
    void insufficientFinishedProductStorageRejectsWithoutConsumingInputs() {
        Map<String, Double> capacities = capacities();
        capacities.put("storage.hazardous_controlled", 10_000d);
        ManufacturingInventory inventory = new ManufacturingInventory(
                ontology,
                products,
                capacities,
                Map.of(
                        "commodity.material.light_alloy", 10_000d,
                        "commodity.material.structural_alloy", 10_000d,
                        "commodity.material.industrial_chemicals", 10_000d,
                        "commodity.component.electrical_components", 10_000d,
                        "commodity.component.precision_components", 10_000d,
                        "commodity.material.conductor_metal", 10_000d,
                        "commodity.material.carbon_material", 10_000d),
                Map.of());
        Map<String, Double> before = inventory.snapshotCommodityMassByIdKg();
        Set<String> tags = Set.of(
                "capability.fabrication.ordnance",
                "capability.fabrication.electrical",
                "capability.fabrication.precision");

        ManufacturingResult result = runtime.manufactureProduct(
                "ammo.interceptor_1t_v1", 1, inventory, capability(tags).openInterval(10d));

        assertEquals(Status.STORAGE_FULL, result.status());
        assertEquals(before, inventory.snapshotCommodityMassByIdKg());
        assertEquals(0, inventory.productCount("ammo.interceptor_1t_v1"));
    }

    @Test
    void refiningMaterialStoreCanHandOffIntoManufacturingWithoutLegacyItemConversion() {
        Stage18RefiningRuntime.PhysicalMaterialStore refined = new Stage18RefiningRuntime.PhysicalMaterialStore(
                ontology,
                Map.of("storage.dry_bulk", 1000d),
                Map.of("commodity.material.structural_alloy", 500d));

        ManufacturingInventory inventory = ManufacturingInventory.fromRefinedMaterialStore(
                ontology, products, capacities(), refined);

        assertEquals(500d, inventory.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
        assertTrue(inventory.snapshotProductCountById().isEmpty());
    }

    private ManufacturingInventory inventory(Map<String, Double> masses) {
        return new ManufacturingInventory(ontology, products, capacities(), masses, Map.of());
    }

    private static ManufacturingCapability capability(Set<String> tags) {
        return new ManufacturingCapability("facility.test_manufacturing", tags, 5_000_000_000_000d, 50_000d, 5_000d);
    }

    private static java.util.HashMap<String, Double> capacities() {
        java.util.HashMap<String, Double> capacities = new java.util.HashMap<>();
        capacities.put("storage.dry_bulk", 100_000_000d);
        capacities.put("storage.liquid_tank", 100_000_000d);
        capacities.put("storage.pressurized_gas", 100_000_000d);
        capacities.put("storage.general_container", 100_000_000d);
        capacities.put("storage.hazardous_controlled", 100_000_000d);
        capacities.put("storage.high_value_controlled", 100_000_000d);
        capacities.put("storage.oversized", 100_000_000d);
        return capacities;
    }
}

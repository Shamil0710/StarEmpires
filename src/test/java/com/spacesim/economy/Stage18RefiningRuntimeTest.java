package com.spacesim.economy;

import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18RefiningRuntime.IntervalBudget;
import com.spacesim.economy.Stage18RefiningRuntime.PhysicalMaterialStore;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningCapability;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningResult;
import com.spacesim.economy.Stage18RefiningRuntime.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18RefiningRuntimeTest {
    private static final double TOLERANCE = 1e-8d;
    private Stage18ResourceOntologyCatalog ontology;
    private Stage18RefiningRuntime runtime;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18RefiningCatalog catalog = Stage18RefiningCatalogLoader.loadDefault();
        runtime = new Stage18RefiningRuntime(ontology, catalog);
    }

    @Test
    void structuralAlloyRefiningConsumesMassAndFiniteProcessBudgetAtomically() {
        PhysicalMaterialStore store = store(Map.of("commodity.feedstock.metallic_ore", 100d));
        RefiningCapability capability = capability(Set.of("capability.process.bulk_refining"),
                10_000_000d, 5d, 1d);
        IntervalBudget budget = capability.openInterval(10d);

        RefiningResult result = runtime.refine("refining.structural_alloy", 50d, store, budget);

        assertEquals(Status.REFINED, result.status());
        assertTrue(result.accepted());
        assertEquals(50d, result.consumedInputMassByCommodityKg()
                .get("commodity.feedstock.metallic_ore"), TOLERANCE);
        assertEquals(34d, result.outputMassStoredKg(), TOLERANCE);
        assertEquals(16d, result.discardedMassKg(), TOLERANCE);
        assertEquals(50d, result.outputMassStoredKg() + result.discardedMassKg(), TOLERANCE);
        assertEquals(50d, store.massKg("commodity.feedstock.metallic_ore"), TOLERANCE);
        assertEquals(34d, store.massKg("commodity.material.structural_alloy"), TOLERANCE);
        assertEquals(50_000_000d, budget.remainingEnergyJ(), TOLERANCE);
        assertEquals(0d, budget.remainingWorkSeconds(), TOLERANCE);
        assertEquals(4d, budget.remainingMaintenanceWorkSeconds(), TOLERANCE);
    }

    @Test
    void mixedChemicalRecipeConsumesEachInputFraction() {
        PhysicalMaterialStore store = store(Map.of(
                "commodity.feedstock.carbonaceous_feedstock", 100d,
                "commodity.feedstock.volatile_feedstock", 100d));
        RefiningCapability capability = capability(Set.of("capability.process.chemical_processing"),
                20_000_000d, 10d, 2d);

        RefiningResult result = runtime.refine(
                "refining.industrial_chemicals", 100d, store, capability.openInterval(10d));

        assertEquals(Status.REFINED, result.status());
        assertEquals(45d, store.massKg("commodity.feedstock.carbonaceous_feedstock"), TOLERANCE);
        assertEquals(55d, store.massKg("commodity.feedstock.volatile_feedstock"), TOLERANCE);
        assertEquals(66d, store.massKg("commodity.material.industrial_chemicals"), TOLERANCE);
    }

    @Test
    void rejectedBatchDoesNotMutateInputsOrBudget() {
        PhysicalMaterialStore store = store(Map.of("commodity.feedstock.metallic_ore", 100d));
        RefiningCapability capability = capability(Set.of("capability.process.bulk_refining"),
                1_000_000d, 5d, 1d);
        IntervalBudget budget = capability.openInterval(10d);
        Map<String, Double> before = store.snapshotMassByCommodityKg();
        double energyBefore = budget.remainingEnergyJ();
        double workBefore = budget.remainingWorkSeconds();

        RefiningResult result = runtime.refine("refining.structural_alloy", 50d, store, budget);

        assertEquals(Status.INSUFFICIENT_POWER, result.status());
        assertFalse(result.accepted());
        assertEquals(before, store.snapshotMassByCommodityKg());
        assertEquals(energyBefore, budget.remainingEnergyJ(), TOLERANCE);
        assertEquals(workBefore, budget.remainingWorkSeconds(), TOLERANCE);
    }

    @Test
    void missingCapabilityAndOutputStorageAreRejectedWithoutMutation() {
        PhysicalMaterialStore missingCapabilityStore = store(Map.of("commodity.feedstock.metallic_ore", 100d));
        RefiningCapability wrongCapability = capability(Set.of("capability.process.chemical_processing"),
                20_000_000d, 10d, 2d);
        RefiningResult capabilityResult = runtime.refine(
                "refining.structural_alloy", 10d, missingCapabilityStore, wrongCapability.openInterval(10d));
        assertEquals(Status.MISSING_CAPABILITY, capabilityResult.status());
        assertEquals(100d, missingCapabilityStore.massKg("commodity.feedstock.metallic_ore"), TOLERANCE);

        PhysicalMaterialStore noDryBulkOutputSpace = new PhysicalMaterialStore(
                ontology,
                Map.of("storage.dry_bulk", 100d),
                Map.of("commodity.feedstock.metallic_ore", 100d));
        RefiningCapability proper = capability(Set.of("capability.process.bulk_refining"),
                20_000_000d, 10d, 2d);
        RefiningResult storageResult = runtime.refine(
                "refining.structural_alloy", 10d, noDryBulkOutputSpace, proper.openInterval(10d));
        // Consuming 10 kg frees the same dry-bulk class, and the 6.8 kg output fits.
        assertEquals(Status.REFINED, storageResult.status());
        assertEquals(96.8d,
                noDryBulkOutputSpace.massKg("commodity.feedstock.metallic_ore")
                        + noDryBulkOutputSpace.massKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    private PhysicalMaterialStore store(Map<String, Double> masses) {
        return new PhysicalMaterialStore(
                ontology,
                Map.of(
                        "storage.dry_bulk", 1000d,
                        "storage.liquid_tank", 1000d,
                        "storage.pressurized_gas", 1000d,
                        "storage.hazardous_controlled", 1000d,
                        "storage.high_value_controlled", 1000d),
                masses);
    }

    private static RefiningCapability capability(
            Set<String> tags, double powerW, double workRate, double maintenanceRate) {
        return new RefiningCapability("facility.test_refinery", tags, powerW, workRate, maintenanceRate);
    }
}

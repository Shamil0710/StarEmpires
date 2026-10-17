package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23BootstrapMaterialsTest {
    private static final String DESIGN = Stage23BootstrapMaterialsCatalog.FACILITY_ID;
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final Stage18FacilityConstructionRuntime construction = new Stage18FacilityConstructionRuntime(
            Stage23BootstrapMaterialsCatalog.construction(), Stage23BootstrapMaterialsCatalog.facilities(), ontology);

    @Test void additiveDesignPreservesEveryHistoricalDefinitionAndBreaksTheMaterialDependencyCycle() {
        var baseline = Stage18FacilityCatalogLoader.loadDefault();
        for (var definition : baseline.getFacilities()) assertEquals(definition, Stage23BootstrapMaterialsCatalog.facilities().findFacility(definition.id()));
        var historical = Stage18FacilityConstructionCatalogLoader.loadDefault();
        for (var binding : historical.getFacilities()) {
            assertEquals(binding, Stage23BootstrapMaterialsCatalog.construction().findFacility(binding.facilityDefinitionId()));
            assertEquals(historical.requiredMassByCommodityKg(binding.facilityDefinitionId()),
                    Stage23BootstrapMaterialsCatalog.construction().requiredMassByCommodityKg(binding.facilityDefinitionId()));
        }
        var bill = Stage23BootstrapMaterialsCatalog.construction().requiredMassByCommodityKg(DESIGN);
        assertEquals(3, bill.size());
        assertEquals(Stage23BootstrapMaterialsCatalog.INSTALLED_MASS_KG, bill.values().stream().mapToDouble(Double::doubleValue).sum());
        for (var material : bill.keySet()) {
            var recipe = Stage18RefiningCatalogLoader.loadDefault().getRecipes().stream()
                    .filter(r -> r.outputCommodityId().equals(material)).findFirst().orElseThrow();
            assertEquals(Set.of("capability.process.bulk_refining"), recipe.requiredCapabilityTags());
            assertTrue(recipe.inputs().stream().allMatch(i -> i.commodityId().startsWith("commodity.feedstock.")));
        }
        var batch = Stage23BootstrapMaterialsCatalog.facilities().findFacility(DESIGN);
        assertEquals(Stage18FacilityCatalog.FacilityFamily.PROCESSING, batch.family());
        for (var recipe : Stage18RefiningCatalogLoader.loadDefault().getRecipes())
            if (Set.of("refining.carbon_material", "refining.industrial_chemicals", "refining.ceramic_glass",
                    "refining.refractory_alloy", "refining.electronic_grade_material").contains(recipe.id()))
                assertTrue(batch.capabilityTags().containsAll(recipe.requiredCapabilityTags()), recipe.id());
        assertFalse(batch.capabilityTags().stream().anyMatch(t -> t.contains("fabrication") || t.contains("extraction")));
    }

    @Test void fullFiniteConstructionResumesNativeCustodyThenNeedsExistingOperatingResources() {
        var bill = Stage23BootstrapMaterialsCatalog.construction().requiredMassByCommodityKg(DESIGN);
        var capacities = new TreeMap<String, Double>();
        bill.forEach((id, mass) -> capacities.merge(ontology.findCommodity(id).storageClassId(), mass, Double::sum));
        // Explicit complete basic-alloy fixture and finite construction line, without seeded work.
        var store = new Stage18StationStorage(ontology, products, "fixture.bootstrap", capacities, bill, Map.of());
        var queue = new Stage18FacilityConstructionWorkQueue(construction, ontology, List.of(), 0);
        String id = Stage18FacilityConstructionWorkQueue.PREFIX + "bootstrap";
        var order = queue.start(id, "fixture.bootstrap.batch", DESIGN, "location.orbital_station", store, 0);
        var line = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.assembly",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), 60);
        queue.advance(1, ignored -> store, ignored -> line.openInterval(1));
        assertEquals(60, queue.capture().get(0).completedWorkSeconds());
        var partial = new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), 1,
                List.of(), List.of(store.snapshot()), List.of(), List.of(), queue.capture(), List.of());
        var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(partial));
        var restoredStore = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
        queue = new Stage18FacilityConstructionWorkQueue(construction, ontology, decoded.constructionOrders(), 1);
        queue.bindReservations(ignored -> restoredStore);
        for (long tick = 2; queue.capture().get(0).status() != Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE; tick++) {
            assertTrue(tick < 100_000);
            queue.advance(tick, ignored -> restoredStore, ignored -> line.openInterval(1));
        }
        var completed = queue.capture().get(0);
        assertEquals(order.requiredWorkSeconds(), completed.completedWorkSeconds());
        assertTrue(restoredStore.snapshotCommodityMassByIdKg().isEmpty());
        var reference = construction.completedFacility(completed);
        var built = new Stage18FacilityRuntime.InstalledFacilityState(reference.facilityInstanceId(), DESIGN,
                1, 0, 0, 0, 0, "location.orbital_station", false);
        var facilities = Stage23BootstrapMaterialsCatalog.facilities();
        assertEquals(0, new Stage18FacilityRuntime(facilities).project(built).effectiveProcessPowerW());
        var allocator = new Stage18FacilityResourceAllocator(facilities);
        assertThrows(IllegalStateException.class, () -> allocator.allocateRated(List.of(built), built.facilityInstanceId()));
        var donor = new Stage18FacilityRuntime.InstalledFacilityState("fixture.donor", "facility.processing.bulk_refinery",
                1, 100_000_000, 75_000_000, 80, 5, "location.orbital_station", true);
        var active = allocator.allocateRated(List.of(built, donor), built.facilityInstanceId()).stream()
                .filter(f -> f.facilityInstanceId().equals(built.facilityInstanceId())).findFirst().orElseThrow();
        var capability = new Stage18FacilityRuntime(facilities).project(active);
        assertEquals(10_000_000, capability.effectiveProcessPowerW());
        assertEquals(1, capability.effectiveEngineeringWorkRate());
        assertEquals(.1, capability.effectiveMaintenanceWorkRate());
    }

    @Test void allFiveMissingProcessesUseTheBuiltWorkshopsFiniteEnergyWorkAndMaintenance() {
        var definition = Stage23BootstrapMaterialsCatalog.facilities().findFacility(DESIGN);
        var state = new Stage18FacilityRuntime.InstalledFacilityState("fixture.batch", DESIGN, 1,
                definition.ratedProcessPowerW(), definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(), definition.maintenanceWorkRate(), "location.orbital_station", true);
        var projected = new Stage18FacilityRuntime(Stage23BootstrapMaterialsCatalog.facilities()).project(state);
        var line = new Stage18ManufacturingRuntime.ManufacturingCapability(projected.facilityInstanceId(), projected.capabilityTags(),
                projected.effectiveProcessPowerW(), projected.effectiveEngineeringWorkRate(), projected.effectiveMaintenanceWorkRate());
        for (String recipeId : List.of("refining.carbon_material", "refining.industrial_chemicals", "refining.ceramic_glass",
                "refining.refractory_alloy", "refining.electronic_grade_material")) {
            var recipe = Stage18RefiningCatalogLoader.loadDefault().findRecipe(recipeId);
            var inputs = new TreeMap<String, Double>(); var capacities = new TreeMap<String, Double>();
            recipe.inputs().forEach(i -> {
                inputs.put(i.commodityId(), 100 * i.fractionOfInputMass());
                assertTrue(definition.storageClassInterfaces().contains(ontology.findCommodity(i.commodityId()).storageClassId()));
                capacities.put(ontology.findCommodity(i.commodityId()).storageClassId(), 1000d);
            });
            assertTrue(definition.storageClassInterfaces().contains(ontology.findCommodity(recipe.outputCommodityId()).storageClassId()));
            capacities.put(ontology.findCommodity(recipe.outputCommodityId()).storageClassId(), 1000d);
            var store = new Stage18StationStorage(ontology, products, "fixture.batch", capacities, inputs, Map.of());
            var queue = new Stage18ManufacturingWorkQueue(ontology, Stage22CivilianMiningProductionPath.loadManufacturing(), products, List.of(), 0);
            queue.startRefining(Stage18ManufacturingWorkQueue.REFINING_PREFIX + recipeId, recipeId, 100, store, line, 0);
            queue.advance(1, ignored -> store, ignored -> line.openInterval(1));
            var partial = new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), 1,
                    List.of(), List.of(store.snapshot()), List.of(), List.of(), List.of(), queue.capture());
            var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(partial));
            var restoredStore = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
            queue = new Stage18ManufacturingWorkQueue(ontology, Stage22CivilianMiningProductionPath.loadManufacturing(), products, decoded.processOrders(), 1);
            queue.restoreReservations(ignored -> restoredStore);
            long tick = 1;
            while (!queue.capture().isEmpty()) {
                assertTrue(tick < 10_000);
                queue.advance(++tick, ignored -> restoredStore, ignored -> line.openInterval(1));
            }
            double minimumSeconds = Math.max(100 * recipe.energyJPerInputKg() / projected.effectiveProcessPowerW(),
                    Math.max(100 * recipe.workSecondsPerInputKg() / projected.effectiveEngineeringWorkRate(),
                            100 * recipe.maintenanceWorkSecondsPerInputKg() / projected.effectiveMaintenanceWorkRate()));
            assertTrue(tick >= Math.ceil(minimumSeconds), recipeId);
            assertEquals(100 * recipe.outputMassFraction(), restoredStore.commodityMassKg(recipe.outputCommodityId()), 1e-9);
            assertEquals(1, restoredStore.snapshotCommodityMassByIdKg().size());
        }
    }
}

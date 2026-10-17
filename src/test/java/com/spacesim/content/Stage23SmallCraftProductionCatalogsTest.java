package com.spacesim.content;

import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class Stage23SmallCraftProductionCatalogsTest {
    @Test
    void extendsOnlyAuthoredCraftContentAndPreservesHistoricalDefinitions() {
        var oldProducts = Stage22CivilianMiningProductionPath.loadProducts();
        var products = Stage23SmallCraftProductionCatalogs.products();
        var oldManufacturing = Stage22CivilianMiningProductionPath.loadManufacturing();
        var manufacturing = Stage23SmallCraftProductionCatalogs.manufacturing();
        var oldYards = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var yards = Stage23SmallCraftProductionCatalogs.shipyards();
        assertEquals(oldProducts.getProducts().size() + 7, products.getProducts().size());
        oldProducts.getProducts().forEach(p -> assertEquals(p, products.findProduct(p.contentId())));
        oldManufacturing.getProductBindings().forEach(b ->
                assertEquals(b, manufacturing.findProductBinding(b.productContentId())));
        assertEquals(oldManufacturing.getComponentRecipes(), manufacturing.getComponentRecipes());
        assertEquals(oldManufacturing.getProductProfiles(), manufacturing.getProductProfiles());
        oldYards.getYards().forEach(y -> assertEquals(y, yards.findYard(y.id())));
        oldYards.getHullProfiles().forEach(h -> assertEquals(h, yards.findHullProfile(h.hullId())));
        oldYards.getModuleProfiles().forEach(m -> assertEquals(m, yards.findModuleProfile(m.moduleId())));
        assertEquals(oldYards.getHullProfiles().size() + 2, yards.getHullProfiles().size());
        assertEquals(oldYards.getModuleProfiles().size() + 7, yards.getModuleProfiles().size());
        assertNotNull(yards.findHullProfile(Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID));
        assertNotNull(yards.findHullProfile(Stage228SmallCraftProductionProjection.UNION_HULL_ID));
        var engineering = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        for (var id : Stage23SmallCraftProductionCatalogs.moduleIds()) {
            assertNull(oldProducts.findProduct(id));
            assertEquals(engineering.findModule(id).massKg(), products.findProduct(id).unitMassKg());
            assertEquals(Stage18ManufacturingProductRegistry.Provenance.STAGE22_AUTHORED,
                    products.findProduct(id).provenance());
            assertNotNull(manufacturing.findProductProfile(manufacturing.findProductBinding(id).profileId()));
            assertNotNull(yards.findModuleProfile(id));
        }
    }

    @Test
    void everyCraftModuleConsumesItsFullBillAndFiniteWorkThroughOrdinaryManufacturing() {
        var products = Stage23SmallCraftProductionCatalogs.products();
        var catalog = Stage23SmallCraftProductionCatalogs.manufacturing();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var runtime = new com.spacesim.economy.Stage18ManufacturingRuntime(ontology, catalog, products);
        for (var id : Stage23SmallCraftProductionCatalogs.moduleIds()) {
            var mass = products.findProduct(id).unitMassKg();
            var profile = catalog.findProductProfile(catalog.findProductBinding(id).profileId());
            var bill = new java.util.HashMap<String, Double>();
            for (var input : profile.inputs()) bill.merge(input.commodityId(),
                    mass * input.fractionOfOutputMass(), Double::sum);
            var capacities = new java.util.HashMap<String, Double>();
            ontology.getStorageClasses().forEach(s -> capacities.put(s.id(), 1e9));
            var inventory = new com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory(
                    ontology, products, capacities, bill, java.util.Map.of());
            var deniedBudget = new com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability(
                    "fixture.no-work", profile.requiredCapabilityTags(), 0, 0, 0).openInterval(1);
            assertFalse(runtime.manufactureProduct(id, 1, inventory, deniedBudget).accepted());
            assertEquals(bill, inventory.snapshotCommodityMassByIdKg());
            assertEquals(0, inventory.productCount(id));
            var budget = new com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability(
                    "fixture.paid-work", profile.requiredCapabilityTags(), mass * profile.energyJPerOutputKg(),
                    mass * profile.workSecondsPerOutputKg(),
                    mass * profile.maintenanceWorkSecondsPerOutputKg()).openInterval(1);
            var result = runtime.manufactureProduct(id, 1, inventory, budget);
            assertTrue(result.accepted(), id + ": " + result.status());
            assertEquals(mass, result.outputMassKg());
            assertEquals(mass, result.consumedInputMassByCommodityKg().values().stream()
                    .mapToDouble(Double::doubleValue).sum(), 1e-8);
            assertEquals(1, inventory.productCount(id));
            assertFalse(runtime.manufactureProduct(id, 1, inventory, budget).accepted());
            assertEquals(1, inventory.productCount(id));
        }
    }

    @Test
    void boundedRegistrationRejectsUnknownRepeatedAndExistingIdsAtomically() {
        var base = Stage22CivilianMiningProductionPath.loadProducts();
        var before = base.getProducts();
        var engineering = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var provenance = Stage18ManufacturingProductRegistry.Provenance.STAGE22_AUTHORED;
        var id = Stage228SmallCraftProductionProjection.REACTOR_ID;
        assertThrows(IllegalArgumentException.class, () -> base.withEngineeringModules(
                engineering, List.of(id, "module.missing"), provenance));
        assertThrows(IllegalArgumentException.class, () -> base.withEngineeringModules(
                engineering, List.of(id, id), provenance));
        var extended = base.withEngineeringModules(engineering, List.of(id), provenance);
        assertThrows(IllegalArgumentException.class, () -> extended.withEngineeringModules(
                engineering, List.of(id), provenance));
        assertEquals(before, base.getProducts());
        assertNull(base.findProduct(id));
    }
}

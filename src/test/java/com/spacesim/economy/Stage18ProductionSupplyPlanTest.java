package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage18ProductionSupplyPlanTest {
    private static final String PRODUCT = "module.industrial_union_cargo_section_v1";
    private final Stage18ManufacturingCatalog catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18RefiningCatalog refining = Stage18RefiningCatalogLoader.loadDefault();

    private Stage18ProductionSupplyPlan.Plan plan(Map<String, Double> stock) {
        return Stage18ProductionSupplyPlan.forProduct(PRODUCT, stock, products, catalog, refining);
    }

    @Test void existingSharedMaterialIsCountedOnceAfterAllComponentAndDirectDemand() {
        var empty = plan(Map.of());
        assertEquals(11, empty.batches().size());
        String metal = "commodity.material.conductor_metal";
        var supplied = plan(Map.of(metal, 63d));
        double required = empty.materialDemand().get(metal);
        var recipe = refining.findRecipe("refining.conductor_metal");
        var batch = supplied.batches().stream().filter(b -> b.recipeId().equals(recipe.id())).findFirst().orElseThrow();
        assertEquals((int) Math.ceil((required - 63) / recipe.outputMassFraction()), batch.kilograms());
        var full = plan(empty.directBill());
        assertTrue(full.batches().isEmpty()); assertTrue(full.feedstockDemand().isEmpty());
        var raw = plan(empty.feedstockDemand());
        assertTrue(raw.missingFeedstock().isEmpty());
        assertEquals(empty.batches(), raw.batches());
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of(metal, Double.NaN)));
        assertThrows(UnsupportedOperationException.class, () -> empty.directBill().clear());
        var roundedComponents = new TreeMap<>(empty.directBill());
        roundedComponents.put("commodity.component.heavy_components", 510000d);
        assertTrue(plan(roundedComponents).batches().isEmpty(), "Binary recipe rounding must not order another kilogram");
    }

    @Test void fullAuthoredSectionChainConsumesFiniteRawMassAndRealWorkThroughEveryNativeReload() {
        var plan = plan(Map.of());
        var capacity = new TreeMap<String, Double>();
        for (var commodity : ontology.getCommodities()) capacity.put(commodity.storageClassId(), 8_000_000d);
        capacity.put(products.findProduct(PRODUCT).storageClassId(), 8_000_000d);
        // Explicit finite feedstock/line fixtures validate the full bill, never an ordinary acquisition claim.
        var store = new Stage18StationStorage(ontology, products, "fixture.full-chain", capacity, plan.feedstockDemand(), Map.of());
        var q = new Stage18ManufacturingWorkQueue(ontology, catalog, products, List.of(), 0);
        var tags = new TreeSet<String>();
        refining.getRecipes().forEach(r -> tags.addAll(r.requiredCapabilityTags()));
        catalog.getComponentRecipes().forEach(r -> tags.addAll(r.requiredCapabilityTags()));
        var profile = catalog.findProductProfile(catalog.findProductBinding(PRODUCT).profileId());
        tags.addAll(profile.requiredCapabilityTags());
        var line = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.full-chain-line", tags, 1_000_000_000d, 100d, 100d);
        long tick = 0; int ordinal = 0; double waste = 0;
        for (var batch : plan.batches()) {
            String id = (batch.process() == Stage18ProductionSupplyPlan.Process.REFINING
                    ? Stage18ManufacturingWorkQueue.REFINING_PREFIX : Stage18ManufacturingWorkQueue.COMPONENT_PREFIX) + ordinal++;
            if (batch.process() == Stage18ProductionSupplyPlan.Process.REFINING) {
                q.startRefining(id, batch.recipeId(), batch.kilograms(), store, line, tick);
                waste += batch.kilograms() * refining.findRecipe(batch.recipeId()).discardedMassFraction();
            } else q.startComponents(id, batch.recipeId(), batch.kilograms(), store, line, tick);
            var currentStore = store;
            q.advance(++tick, ignored -> currentStore, ignored -> line.openInterval(1));
            assertEquals(0, store.productCount(PRODUCT));
            assertTrue(q.capture().get(0).completedFraction() > 0 && q.capture().get(0).completedFraction() < 1);
            var partial = state(store, q, tick);
            var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(partial));
            assertEquals(partial, decoded);
            store = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
            q = new Stage18ManufacturingWorkQueue(ontology, catalog, products, decoded.processOrders(), tick);
            var resumedStore = store; q.restoreReservations(ignored -> resumedStore);
            while (!q.capture().isEmpty()) {
                assertTrue(tick < 1_000_000, "Full finite work must complete within its actual line budget");
                q.advance(++tick, ignored -> resumedStore, ignored -> line.openInterval(1));
            }
            assertTrue(store.commodityMassKg(batch.outputCommodityId()) >= batch.outputMassKg());
        }
        for (var input : plan.directBill().entrySet()) assertTrue(store.commodityMassKg(input.getKey()) + 1e-9 >= input.getValue(), input.getKey());
        q.start(Stage18ManufacturingWorkQueue.PREFIX + "section", PRODUCT, 1, store, line, tick);
        var finalStore = store;
        q.advance(++tick, ignored -> finalStore, ignored -> line.openInterval(1));
        var partial = state(store, q, tick);
        var restored = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(partial));
        store = Stage18StationStorage.restore(ontology, products, restored.stationStorages().get(0));
        q = new Stage18ManufacturingWorkQueue(ontology, catalog, products, restored.processOrders(), tick);
        var resumed = store; q.restoreReservations(ignored -> resumed);
        while (!q.capture().isEmpty()) {
            assertTrue(tick < 1_000_000);
            q.advance(++tick, ignored -> resumed, ignored -> line.openInterval(1));
        }
        assertEquals(1, store.productCount(PRODUCT));
        double initial = plan.feedstockDemand().values().stream().mapToDouble(Double::doubleValue).sum();
        double retained = store.snapshotCommodityMassByIdKg().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(initial, retained + products.findProduct(PRODUCT).unitMassKg() + waste, 1e-6);
        var complete = state(store, q, tick);
        assertEquals(complete, Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(complete)));
        q.advance(tick, ignored -> resumed, ignored -> line.openInterval(1));
        assertEquals(1, store.productCount(PRODUCT));
    }

    private Stage18IndustrialState state(Stage18StationStorage store, Stage18ManufacturingWorkQueue queue, long tick) {
        return new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), tick,
                List.of(), List.of(store.snapshot()), List.of(), List.of(), List.of(), queue.capture());
    }
}

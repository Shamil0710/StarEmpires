package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Stage18ComponentWorkQueueTest {
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final Stage18ManufacturingCatalog catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
    private Stage18ManufacturingWorkQueue queue(List<Stage18IndustrialState.ProcessOrderSnapshot> rows, long tick) {
        return new Stage18ManufacturingWorkQueue(ontology, catalog, products, rows, tick);
    }
    private Stage18StationStorage store(Stage18ManufacturingCatalog.ComponentRecipeDefinition recipe, double kilograms) {
        var inputs = new TreeMap<String, Double>(); var capacities = new TreeMap<String, Double>();
        recipe.inputs().forEach(i -> {
            inputs.put(i.commodityId(), kilograms * i.fractionOfOutputMass());
            capacities.put(ontology.findCommodity(i.commodityId()).storageClassId(), kilograms * 4);
        });
        capacities.put(ontology.findCommodity(recipe.outputCommodityId()).storageClassId(), kilograms * 4);
        return new Stage18StationStorage(ontology, products, "fixture.components", capacities, inputs, Map.of());
    }
    private Stage18ManufacturingRuntime.ManufacturingCapability halfBatchLine(Stage18ManufacturingCatalog.ComponentRecipeDefinition recipe) {
        return new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line", recipe.requiredCapabilityTags(),
                50 * recipe.energyJPerOutputKg(), 50 * recipe.workSecondsPerOutputKg(), 50 * recipe.maintenanceWorkSecondsPerOutputKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {"manufacturing.component.heavy", "manufacturing.component.electrical", "manufacturing.component.precision"})
    void eachAuthoredFamilyReservesExactMassAndResumesPartialWorkToOneOutput(String recipeId) {
        var recipe = catalog.findComponentRecipe(recipeId); var store = store(recipe, 100); var q = queue(List.of(), 0);
        String id = Stage18ManufacturingWorkQueue.COMPONENT_PREFIX + "fixture";
        q.startComponents(id, recipeId, 100, store, halfBatchLine(recipe), 0);
        assertTrue(store.snapshotCommodityMassByIdKg().isEmpty());
        q.advance(1, ignored -> store, ignored -> halfBatchLine(recipe).openInterval(1));
        assertEquals(.5, q.capture().get(0).completedFraction(), 1e-12);
        var state = new Stage18IndustrialState(1, Stage18IndustrialContentFingerprint.current(), 1,
                List.of(), List.of(store.snapshot()), List.of(), List.of(), List.of(), q.capture());
        var decoded = Stage18IndustrialStateCodec.decode(Stage18IndustrialStateCodec.encode(state));
        var resumed = queue(decoded.processOrders(), 1);
        var restoredStore = Stage18StationStorage.restore(ontology, products, decoded.stationStorages().get(0));
        resumed.restoreReservations(ignored -> restoredStore);
        for (int tick = 2; tick <= 4; tick++) {
            q.advance(tick, ignored -> store, ignored -> halfBatchLine(recipe).openInterval(1));
            resumed.advance(tick, ignored -> restoredStore, ignored -> halfBatchLine(recipe).openInterval(1));
        }
        assertTrue(q.capture().isEmpty()); assertEquals(store.snapshot(), restoredStore.snapshot());
        assertEquals(Map.of(recipe.outputCommodityId(), 100d), store.snapshotCommodityMassByIdKg());
        resumed.advance(4, ignored -> restoredStore, ignored -> halfBatchLine(recipe).openInterval(1));
        assertEquals(store.snapshot(), restoredStore.snapshot());
    }

    @Test void competingBatchesShareTheirIntervalAndCancellationRestoresTheirActualMaterials() {
        var recipe = catalog.findComponentRecipe("manufacturing.component.heavy");
        var store = store(recipe, 200); var before = store.snapshot(); var q = queue(List.of(), 0);
        String id = Stage18ManufacturingWorkQueue.COMPONENT_PREFIX + "fixture";
        q.startComponents(id, recipe.id(), 100, store, halfBatchLine(recipe), 0);
        q.startComponents(id + "2", recipe.id(), 100, store, halfBatchLine(recipe), 0);
        var interval = halfBatchLine(recipe).openInterval(1);
        q.advance(1, ignored -> store, ignored -> interval);
        assertEquals(.5, q.capture().stream().mapToDouble(o -> o.completedFraction()).sum(), 1e-12);
        q.cancel(id, store); q.cancel(id + "2", store);
        assertEquals(before, store.snapshot()); assertTrue(q.capture().isEmpty());
    }

    @Test void insufficientOrForgedMaterialsCannotProduceComponents() {
        var recipe = catalog.findComponentRecipe("manufacturing.component.heavy");
        var store = store(recipe, 99); var before = store.snapshot(); var q = queue(List.of(), 0);
        String id = Stage18ManufacturingWorkQueue.COMPONENT_PREFIX + "fixture";
        assertThrows(IllegalStateException.class, () -> q.startComponents(id, recipe.id(), 100, store, halfBatchLine(recipe), 0));
        assertThrows(IllegalArgumentException.class, () -> q.startComponents(id, recipe.id(), Double.MAX_VALUE, store, halfBatchLine(recipe), 0));
        assertEquals(before, store.snapshot()); assertTrue(q.capture().isEmpty());
        var forged = new Stage18IndustrialState.ProcessOrderSnapshot(id, Stage18IndustrialState.ProcessKind.COMPONENT_MANUFACTURING,
                recipe.id(), store.stationId(), "", 100, 0, .5, before.commodityMassByIdKg(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> queue(List.of(forged), 1));
    }
}

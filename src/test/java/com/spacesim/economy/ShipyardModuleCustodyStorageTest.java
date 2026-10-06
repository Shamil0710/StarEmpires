package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardModuleCustodyStorageTest {
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final String module = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final double mass = products.findProduct(module).unitMassKg();
    private ShipyardModuleCustodyState custody(String station) {
        return new ShipyardModuleCustodyState(List.of(new StoredModule("fixture.refit/mount", station, 31, 0,
                new RemovedModuleState(new InstalledModuleDefinition("mount", module), .4, 100))));
    }

    @Test void reservationsShareCapacityWithManufacturingAndAreReboundWithoutDuplicatingMass() {
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        var profile = catalog.findProductProfile(catalog.findProductBinding(module).profileId());
        var capacities = new TreeMap<String, Double>();
        var inputs = new TreeMap<String, Double>();
        profile.inputs().forEach(i -> {
            inputs.put(i.commodityId(), mass * i.fractionOfOutputMass());
            capacities.put(ontology.findCommodity(i.commodityId()).storageClassId(), mass * 4);
        });
        capacities.put(products.findProduct(module).storageClassId(), mass * 4);
        var storage = new Stage18StationStorage(ontology, products, "fixture", capacities, inputs, Map.of());
        var custody = custody("fixture");
        ShipyardModuleCustodyStorage.bind(custody, products, ignored -> storage);
        var line = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line", profile.requiredCapabilityTags(),
                mass * profile.energyJPerOutputKg(), mass * profile.workSecondsPerOutputKg(),
                mass * profile.maintenanceWorkSecondsPerOutputKg());
        var queue = new Stage18ManufacturingWorkQueue(ontology, catalog, products, List.of(), 0);
        double occupiedBefore = storage.usedCapacityKg(products.findProduct(module).storageClassId());
        queue.start(Stage18ManufacturingWorkQueue.PREFIX + "fixture", module, 1, storage, line, 0);
        assertEquals(occupiedBefore, storage.usedCapacityKg(products.findProduct(module).storageClassId()));
        var restored = Stage18StationStorage.restore(ontology, products, storage.snapshot());
        var restoredQueue = new Stage18ManufacturingWorkQueue(ontology, catalog, products, queue.capture(), 0);
        restoredQueue.restoreReservations(ignored -> restored);
        ShipyardModuleCustodyStorage.bind(custody, products, ignored -> restored);
        ShipyardModuleCustodyStorage.bind(custody, products, ignored -> restored);
        for (String storageClass : capacities.keySet())
            assertEquals(storage.usedCapacityKg(storageClass), restored.usedCapacityKg(storageClass));
        restoredQueue.advance(1, ignored -> restored, ignored -> line.openInterval(1.001));
        assertEquals(1, restored.productCount(module));
        assertEquals(mass * 2, restored.usedCapacityKg(products.findProduct(module).storageClassId()), 1e-8);
        assertEquals(custody, custody("fixture"));
    }

    @Test void overfilledOrWrongStationFailsBeforeChangingAnyReservation() {
        var storage = new Stage18StationStorage(ontology, products, "fixture",
                Map.of(products.findProduct(module).storageClassId(), mass / 2), Map.of(), Map.of());
        var before = storage.snapshot();
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyStorage.bind(custody("fixture"), products, ignored -> storage));
        assertEquals(0, storage.usedCapacityKg(products.findProduct(module).storageClassId()));
        assertEquals(before, storage.snapshot());
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyStorage.bind(custody("other"), products, ignored -> storage));
        assertEquals(before, storage.snapshot());
    }

    @Test void logisticsCannotFillSpaceOccupiedByUsedEquipmentAndMultiStationBindIsAtomic() {
        String storageClass = products.findProduct(module).storageClassId();
        var source = new Stage18StationStorage(ontology, products, "source", Map.of(storageClass, mass), Map.of(), Map.of(module, 1));
        var destination = new Stage18StationStorage(ontology, products, "destination", Map.of(storageClass, mass), Map.of(), Map.of());
        ShipyardModuleCustodyStorage.bind(custody("destination"), products, ignored -> destination);
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", Set.of(storageClass), mass, mass);
        var budget = handling.openInterval(1);
        var sourceBefore = source.snapshot(); var destinationBefore = destination.snapshot();
        var result = new Stage18LogisticsRuntime(ontology, products).transferProduct(source, destination, module, 1, handling, budget);
        assertFalse(result.transferred());
        assertEquals(sourceBefore, source.snapshot()); assertEquals(destinationBefore, destination.snapshot());
        assertEquals(mass, budget.remainingMassKg()); assertEquals(mass, destination.usedCapacityKg(storageClass));
        var sufficient = new Stage18StationStorage(ontology, products, "a", Map.of(storageClass, mass * 2), Map.of(), Map.of());
        var insufficient = new Stage18StationStorage(ontology, products, "b", Map.of(storageClass, mass / 2), Map.of(), Map.of());
        var first = custody("a").modules().get(0);
        var second = new StoredModule("other.identity", "b", 32, 0, first.condition());
        var both = new ShipyardModuleCustodyState(List.of(first, second));
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyStorage.bind(both, products,
                id -> id.equals("a") ? sufficient : insufficient));
        assertEquals(0, sufficient.usedCapacityKg(storageClass)); assertEquals(0, insufficient.usedCapacityKg(storageClass));
    }
}

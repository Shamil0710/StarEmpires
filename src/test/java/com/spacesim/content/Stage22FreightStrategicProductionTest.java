package com.spacesim.content;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.*;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.ShipyardRepairQueuePersistenceCodec;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Stage22FreightStrategicProductionTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void longHaulDriveRequiresItsFullPhysicalMassAndFiniteManufacturingBudget(boolean empire) {
        String module = empire ? Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_LONG_HAUL_DRIVE
                : Stage22FreightStrategicEngineeringCatalogLoader.UNION_LONG_HAUL_DRIVE;
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        var profile = catalog.findProductProfile(catalog.findProductBinding(module).profileId());
        double mass = products.findProduct(module).unitMassKg();
        var raw = new TreeMap<String, Double>(); var capacities = new TreeMap<String, Double>();
        profile.inputs().forEach(i -> {
            raw.put(i.commodityId(), mass * i.fractionOfOutputMass());
            capacities.merge(ontology.findCommodity(i.commodityId()).storageClassId(), mass * 2, Double::sum);
        });
        capacities.put(Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS, mass * 2);
        var inventory = new Stage18ManufacturingRuntime.ManufacturingInventory(ontology, products, capacities, raw, Map.of());
        var runtime = new Stage18ManufacturingRuntime(ontology, catalog, products);
        var line = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.longhaul.line", profile.requiredCapabilityTags(), 1e12, 1e6, 1e6);
        assertFalse(runtime.manufactureProduct(module, 1, inventory, line.openInterval(1e-6)).accepted());
        assertEquals(raw, inventory.snapshotCommodityMassByIdKg()); assertEquals(0, inventory.productCount(module));
        double duration = Math.max(mass * profile.energyJPerOutputKg() / line.availablePowerW(),
                Math.max(mass * profile.workSecondsPerOutputKg() / line.workRate(), mass * profile.maintenanceWorkSecondsPerOutputKg() / line.maintenanceWorkRate()));
        var budget = line.openInterval(duration * 1.001);
        var made = runtime.manufactureProduct(module, 1, inventory, budget);
        assertTrue(made.accepted()); assertEquals(1, inventory.productCount(module));
        assertEquals(mass, made.consumedInputMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum(), mass * 1e-12);
        assertFalse(runtime.manufactureProduct(module, 1, inventory, budget).accepted());
        assertEquals(1, inventory.productCount(module));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void actualDamagedFreightDriveRepairConsumesEscrowAndContinuesAfterNativeSave(boolean empire) {
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(empire
                ? Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_FREIGHT_STRATEGIC_FIT
                : Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        var ship = new EngineeringComponent(fit, new ShipEngineeringRuntime(engineering).initialize(fit, ConsumableState.empty()));
        var instance = ship.instanceState;
        ship.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("core_drive", .5))),
                instance.shieldStatesByMount(), new ShipyardEngineeringService.MaintenanceState(Map.of("core_drive", 920d)), instance.weaponLoadout(), instance.weaponMountRuntime()));
        var initial = ship.instanceState; var physical = ship.runtimeState;
        var catalog = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var d = catalog.findYard(empire ? Stage22EmpireProductionCatalogs.YARD_ID : Stage22IndustrialUnionProductionCatalogs.YARD_ID);
        var capability = new ShipyardEngineeringService.ShipyardCapability("fixture.longhaul.yard", d.berthDimensionsM(), d.maxServiceMassKg(),
                d.stage175FabricationCapabilities(), d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(),
                d.ratedEngineeringWorkRate(), d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW());
        var yard = new Stage18ShipyardRuntime.YardCapabilitySnapshot(capability.yardId(), d.id(), Stage18ShipyardRuntime.YardStatus.ACTIVE,
                capability, d.handledStorageClassIds(), d.maxHandledUnitMassKg());
        var asset = new EntityId(31); var plan = ShipyardRepairWorkQueue.plan(asset, ship, yard);
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage22CivilianMiningProductionPath.loadProducts();
        var inputs = new Stage18ShipyardRuntime(catalog, ontology, products).repairMaterialRequirements(plan, initial.damage());
        assertTrue(inputs.values().stream().mapToDouble(Double::doubleValue).sum() > 0);
        var capacities = new TreeMap<String, Double>();
        inputs.forEach((id, mass) -> capacities.merge(ontology.findCommodity(id).storageClassId(), mass * 2, Double::sum));
        var store = new Stage18StationStorage(ontology, products, "fixture.longhaul.station", capacities, inputs, Map.of());
        var queue = new ShipyardRepairWorkQueue(ShipyardRepairQueueState.empty());
        queue.start("longhaul", 31, asset, ship, store, yard, 0); assertSame(initial, ship.instanceState);
        assertTrue(store.snapshotCommodityMassByIdKg().isEmpty());
        double interval = plan.requirements().totalWorkSeconds() / capability.workRate() / 4;
        queue.advance(1, ignored -> store, ignored -> new ShipyardRepairWorkQueue.RepairContext(ship, yard), ignored -> yard.openInterval(interval));
        assertSame(initial, ship.instanceState); assertTrue(queue.capture().orders().get(0).completedWorkSeconds() > 0);
        var restored = new ShipyardRepairWorkQueue(ShipyardRepairQueuePersistenceCodec.decode(ShipyardRepairQueuePersistenceCodec.encode(queue.capture())));
        var restoredStore = Stage18StationStorage.restore(ontology, products, store.snapshot()); restored.restoreReservations(ignored -> restoredStore);
        var restoredShip = new EngineeringComponent(ship.fit, ship.runtimeState, ship.instanceState);
        for (int tick = 2; tick <= 5; tick++) {
            queue.advance(tick, ignored -> store, ignored -> new ShipyardRepairWorkQueue.RepairContext(ship, yard), ignored -> yard.openInterval(interval));
            restored.advance(tick, ignored -> restoredStore, ignored -> new ShipyardRepairWorkQueue.RepairContext(restoredShip, yard), ignored -> yard.openInterval(interval));
            assertEquals(queue.capture(), restored.capture()); assertEquals(store.snapshot(), restoredStore.snapshot());
            assertEquals(ship.instanceState, restoredShip.instanceState);
        }
        assertTrue(queue.capture().orders().isEmpty()); assertTrue(ship.instanceState.damage().moduleDamage().moduleIntegrityByMount().isEmpty());
        assertSame(physical, ship.runtimeState); assertEquals(initial.maintenance(), ship.instanceState.maintenance());
        assertEquals(initial.weaponLoadout(), ship.instanceState.weaponLoadout());
        capacities.keySet().forEach(id -> assertEquals(0, store.usedCapacityKg(id)));
    }
}

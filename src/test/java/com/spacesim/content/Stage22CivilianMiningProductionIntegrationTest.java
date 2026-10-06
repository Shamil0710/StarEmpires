package com.spacesim.content;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.*;
import com.spacesim.economy.Stage18ManufacturingRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardModuleCustodyStorage;
import com.spacesim.persistence.ShipyardModuleCustodyPersistenceCodec;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class Stage22CivilianMiningProductionIntegrationTest {
    private static final String MODULE = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;

    @Test void competingOrdersCannotReuseOneLineIntervalAgainstCanonicalStock() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        var profile = catalog.findProductProfile(catalog.findProductBinding(MODULE).profileId());
        double mass = products.findProduct(MODULE).unitMassKg();
        var inputs = new TreeMap<String, Double>();
        var capacities = new TreeMap<String, Double>();
        profile.inputs().forEach(i -> {
            inputs.put(i.commodityId(), i.fractionOfOutputMass() * mass * 2);
            capacities.put(ontology.findCommodity(i.commodityId()).storageClassId(), mass * 4);
        });
        capacities.put(Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS, mass * 4);
        var storage = new Stage18StationStorage(ontology, products, "fixture.shared.line", capacities, inputs, Map.of());
        var bridge = new com.spacesim.economy.Stage18StationProductionBridge(ontology, products,
                new com.spacesim.economy.Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault()),
                new com.spacesim.economy.Stage18ExtractionRuntime(ontology, Stage18ExtractionCatalogLoader.loadDefault()),
                new com.spacesim.economy.Stage18RefiningRuntime(ontology, Stage18RefiningCatalogLoader.loadDefault()),
                new Stage18ManufacturingRuntime(ontology, catalog, products));
        var capability = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.shared.line",
                profile.requiredCapabilityTags(), 1e12, 1e6, 1e6);
        double duration = Math.max(mass * profile.energyJPerOutputKg() / capability.availablePowerW(),
                Math.max(mass * profile.workSecondsPerOutputKg() / capability.workRate(),
                        mass * profile.maintenanceWorkSecondsPerOutputKg() / capability.maintenanceWorkRate())) * 1.001;
        var budget = capability.openInterval(duration);
        assertTrue(bridge.manufactureProductAtStorage(MODULE, 1, storage, budget).accepted());
        var afterFirst = storage.snapshot();
        double energy = budget.remainingEnergyJ();
        double work = budget.remainingWorkSeconds();
        double maintenance = budget.remainingMaintenanceWorkSeconds();
        assertFalse(bridge.manufactureProductAtStorage(MODULE, 1, storage, budget).accepted());
        assertEquals(afterFirst, storage.snapshot());
        assertEquals(energy, budget.remainingEnergyJ());
        assertEquals(work, budget.remainingWorkSeconds());
        assertEquals(maintenance, budget.remainingMaintenanceWorkSeconds());
        assertTrue(bridge.manufactureProductAtStorage(MODULE, 1, storage, capability.openInterval(duration)).accepted());
        assertEquals(2, storage.productCount(MODULE));
        assertTrue(storage.snapshotCommodityMassByIdKg().values().stream().allMatch(v -> v < mass * 1e-9));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID,
            Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID})
    void finiteManufacturingOutputIsConsumedBySameHullRefitBeforeMiningBecomesAvailable(String module) {
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var manufacturing = Stage22CivilianMiningProductionPath.loadManufacturing();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var profile = manufacturing.findProductProfile(manufacturing.findProductBinding(module).profileId());
        double mass = products.findProduct(module).unitMassKg();
        var inputs = new TreeMap<String, Double>();
        profile.inputs().forEach(i -> inputs.put(i.commodityId(), i.fractionOfOutputMass() * mass));
        var capacities = new TreeMap<String, Double>();
        inputs.keySet().forEach(id -> capacities.put(ontology.findCommodity(id).storageClassId(), mass * 2));
        capacities.put(Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS, mass * 2);
        // Explicit finite material and installed-line fixtures; no generated stock/asset grant.
        var inventory = new Stage18ManufacturingRuntime.ManufacturingInventory(ontology, products, capacities, inputs, Map.of());
        var maker = new Stage18ManufacturingRuntime(ontology, manufacturing, products);
        var capability = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line", profile.requiredCapabilityTags(),
                1e12, 1e6, 1e6);
        double duration = Math.max(mass * profile.energyJPerOutputKg() / capability.availablePowerW(),
                Math.max(mass * profile.workSecondsPerOutputKg() / capability.workRate(),
                        mass * profile.maintenanceWorkSecondsPerOutputKg() / capability.maintenanceWorkRate()));
        var budget = capability.openInterval(duration * 1.001);
        var made = maker.manufactureProduct(module, 1, inventory, budget); assertTrue(made.accepted());
        assertEquals(mass, made.consumedInputMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum(), mass * 1e-12);
        assertEquals(1, inventory.productCount(module));
        var station = new Stage18StationStorage(ontology, products, "station.fixture", capacities,
                inventory.snapshotCommodityMassByIdKg(), inventory.snapshotProductCountById());
        var source = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        var target = module.equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)
                ? InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                : Stage22CivilianMiningProductionPath.freightMiningProposal(source);
        assertEquals(source.hullId(), target.hullId());
        assertEquals(source.installedModules().size(), target.installedModules().size());
        var component = new EngineeringComponent(source, new ShipEngineeringRuntime(engineering).initialize(source, ConsumableState.empty()));
        var damage = new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("utility_sensor", .8, "mission_primary", .5)));
        var ages = new ShipyardEngineeringService.MaintenanceState(Map.of("utility_sensor", 100d, "mission_primary", 200d));
        var neutral = component.instanceState;
        component.instanceState = new ShipInstanceRuntimeState(damage, neutral.shieldStatesByMount(), ages,
                neutral.weaponLoadout(), neutral.weaponMountRuntime());
        var adapter = new ShipMiningEngineeringAdapter(); assertTrue(adapter.derive(new ProductionEngineeringRuntimeResolver().derive(component)).isEmpty());
        var shipyards = Stage22CivilianMiningProductionPath.loadShipyards(); var yard = yard(shipyards);
        var planner = new ShipyardEngineeringService(engineering, Stage22CivilianMiningIndustrialCatalogLoader.loadDefault());
        var id = new EntityId(31);
        var plan = planner.planRefit(id, source, target, component.runtimeState.consumables(), component.instanceState.damage(), yard.plannerCapability());
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertTrue(plan.requirements().totalWorkSeconds() > 0);
        var runtime = new Stage18ShipyardRuntime(shipyards, ontology, products);
        var insufficientWork = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() / 2);
        var stockBefore = station.snapshot(); double workBefore = insufficientWork.remainingWorkSeconds();
        assertEquals(Stage18ShipyardRuntime.SettlementStatus.INSUFFICIENT_WORK,
                runtime.settleRefit(plan, station, yard, insufficientWork).status());
        assertEquals(stockBefore, station.snapshot()); assertEquals(workBefore, insufficientWork.remainingWorkSeconds());
        assertEquals(source, component.fit);
        var work = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() * 1.001);
        var handoff = runtime.settleRefitWithCustody(plan, damage, ages, planner, station, yard, work,
                ShipyardModuleCustodyState.empty(), "fixture.refit.31", 0);
        var settled = handoff.settlement(); assertTrue(settled.settled());
        assertEquals(0, station.productCount(module)); assertEquals(Map.of(module, 1), settled.consumedProductCount());
        var completed = handoff.completion();
        assertEquals(id, completed.assetId());
        assertEquals(.5, completed.removedModules().get(0).integrity());
        assertEquals(200, completed.removedModules().get(0).secondsSinceService());
        var custody = handoff.custody();
        assertEquals(1, custody.modules().size());
        String removedModule = completed.removedModules().get(0).assignment().moduleId();
        assertEquals(0, station.productCount(removedModule), "Damaged equipment must not become pristine stock");
        double removedMass = products.findProduct(removedModule).unitMassKg();
        assertEquals(removedMass, station.usedCapacityKg(Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS));
        var restoredCustody = ShipyardModuleCustodyPersistenceCodec.decode(ShipyardModuleCustodyPersistenceCodec.encode(custody));
        var restoredStorage = Stage18StationStorage.restore(ontology, products, station.snapshot());
        ShipyardModuleCustodyStorage.bind(restoredCustody, products, idValue -> restoredStorage);
        assertEquals(custody, restoredCustody);
        assertEquals(removedMass, restoredStorage.usedCapacityKg(Stage18ManufacturingProductRegistry.MODULE_STORAGE_CLASS));
        double workAfter = work.remainingWorkSeconds();
        var stockAfter = station.snapshot();
        assertThrows(IllegalArgumentException.class, () -> runtime.settleRefitWithCustody(plan, damage, ages,
                planner, station, yard, work, custody, "fixture.refit.31", 0));
        assertEquals(stockAfter, station.snapshot()); assertEquals(workAfter, work.remainingWorkSeconds());
        new ShipRefitApplicationService(engineering).apply(id, component, completed);
        assertEquals(target, component.fit);
        assertEquals(.8, component.instanceState.damage().moduleDamage().moduleIntegrityByMount().get("utility_sensor"));
        assertEquals(100, component.instanceState.maintenance().secondsSinceServiceByMount().get("utility_sensor"));
        assertEquals(25, adapter.derive(new ProductionEngineeringRuntimeResolver().derive(component)).orElseThrow().maximumSourceKgPerSecond());
        var before = station.snapshot();
        var nextWork = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() * 1.001);
        double nextBefore = nextWork.remainingWorkSeconds();
        assertEquals(Stage18ShipyardRuntime.SettlementStatus.INSUFFICIENT_PRODUCT,
                runtime.settleRefit(plan, station, yard, nextWork).status());
        assertEquals(before, station.snapshot()); assertEquals(nextBefore, nextWork.remainingWorkSeconds());
    }

    @Test void missingMaterialsCannotManufactureASectionAndForeignHullCannotBecomeMiningFreight() {
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var manufacturing = Stage22CivilianMiningProductionPath.loadManufacturing(); var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var profile = manufacturing.findProductProfile(manufacturing.findProductBinding(MODULE).profileId());
        var empty = new Stage18ManufacturingRuntime.ManufacturingInventory(ontology, products,
                Map.of("storage.dry_bulk", 1e9, "storage.oversized", 1e9), Map.of(), Map.of());
        var capability = new Stage18ManufacturingRuntime.ManufacturingCapability("fixture.line", profile.requiredCapabilityTags(), 1e12, 1e6, 1e6);
        var result = new Stage18ManufacturingRuntime(ontology, manufacturing, products).manufactureProduct(MODULE, 1, empty, capability.openInterval(1000));
        assertFalse(result.accepted()); assertEquals(0, empty.productCount(MODULE));
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var empire = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_FREIGHT_STRATEGIC_FIT));
        assertThrows(IllegalArgumentException.class, () -> Stage22CivilianMiningProductionPath.freightMiningProposal(empire));
        var cargo = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        assertThrows(IllegalArgumentException.class, () -> Stage22CivilianMiningProductionPath.freightMiningProposal(
                Stage22CivilianMiningProductionPath.freightMiningProposal(cargo)));
    }

    @Test void removingEquipmentIntoAnUndersizedStoreSpendsNeitherWorkNorStock() {
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var base = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(
                Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        var source = Stage22CivilianMiningProductionPath.freightMiningProposal(base);
        var target = new InstalledFit(source.hullId(), source.installedModules().stream()
                .filter(m -> !m.mountId().equals("mission_primary")).toList());
        var shipyards = Stage22CivilianMiningProductionPath.loadShipyards();
        var yard = yard(shipyards);
        var planner = new ShipyardEngineeringService(engineering, Stage22CivilianMiningIndustrialCatalogLoader.loadDefault());
        var damage = new ShipDamageRuntime.Snapshot(Map.of(), DamageState.pristine());
        var plan = planner.planRefit(new EntityId(32), source, target, ConsumableState.empty(), damage, yard.plannerCapability());
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var storage = new Stage18StationStorage(ontology, products, "fixture.small",
                Map.of(products.findProduct(MODULE).storageClassId(), products.findProduct(MODULE).unitMassKg() / 2), Map.of(), Map.of());
        var work = yard.openInterval(plan.requirements().durationSeconds(yard.plannerCapability()) * 1.001);
        var before = storage.snapshot(); double workBefore = work.remainingWorkSeconds();
        var outcome = new Stage18ShipyardRuntime(shipyards, ontology, products).settleRefitWithCustody(plan, damage,
                ShipyardEngineeringService.MaintenanceState.initial(), planner, storage, yard, work,
                ShipyardModuleCustodyState.empty(), "fixture.remove.32", 0);
        assertEquals(Stage18ShipyardRuntime.SettlementStatus.INSUFFICIENT_STORAGE, outcome.settlement().status());
        assertEquals(ShipyardModuleCustodyState.empty(), outcome.custody()); assertNull(outcome.completion());
        assertEquals(before, storage.snapshot()); assertEquals(workBefore, work.remainingWorkSeconds());
    }

    private static Stage18ShipyardRuntime.YardCapabilitySnapshot yard(Stage18ShipyardCatalog catalog) {
        var definition = catalog.findYard(Stage22IndustrialUnionProductionCatalogs.YARD_ID);
        // Explicit operational yard projection fixture; production must install and allocate its supports.
        var planner = new ShipyardEngineeringService.ShipyardCapability("fixture.yard", definition.berthDimensionsM(),
                definition.maxServiceMassKg(), definition.stage175FabricationCapabilities(), definition.stage175HandledRequirementIds(),
                definition.toolingTags(), definition.precisionCapability(), definition.ratedEngineeringWorkRate(),
                definition.laborCapacity(), definition.automationCapacity(), definition.ratedIntegrationPowerW());
        return new Stage18ShipyardRuntime.YardCapabilitySnapshot("fixture.yard", definition.id(), Stage18ShipyardRuntime.YardStatus.ACTIVE,
                planner, definition.handledStorageClassIds(), definition.maxHandledUnitMassKg());
    }
}

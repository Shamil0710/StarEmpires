package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardRefitContinuity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ShipyardRuntimeTest {
    private static final double TOLERANCE = 1e-6d;

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ManufacturingProductRegistry products;
    private Stage18ShipyardCatalog shipyards;
    private ShipEngineeringCatalog engineering;
    private ShipyardEngineeringService engineeringService;
    private Stage18FacilityRuntime facilityRuntime;
    private Stage18ShipyardRuntime runtime;
    private InstalledFit fullFit;
    private Stage18StationIndustrialNode station;
    private Stage18ShipyardRuntime.YardCapabilitySnapshot yard;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        products = Stage18ManufacturingProductRegistry.loadDefault();
        shipyards = Stage18ShipyardCatalogLoader.loadDefault();
        engineering = ShipEngineeringCatalogLoader.loadDefault();
        engineeringService = new ShipyardEngineeringService(
                engineering,
                ShipyardIndustrialCatalogLoader.loadDefault(engineering));
        facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        runtime = new Stage18ShipyardRuntime(shipyards, ontology, products);
        fullFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));

        var infrastructure = Stage18StationInfrastructureCatalogLoader.loadDefault()
                .findArchetype("station.infrastructure.industrial_station");
        station = Stage18StationIndustrialNode.instantiate(
                "station.test.shipyard",
                "location.orbital_station",
                infrastructure,
                ontology,
                products);
        yard = runtime.projectYard(
                installedYard(),
                station,
                activeSupportFacilities(station));
        assertTrue(yard.active());
    }

    @Test
    void yardCapabilityExistsOnlyWithInstalledActiveSupportFacilities() {
        var withoutAssembly = activeSupportFacilities(station).stream()
                .filter(value -> !value.definitionId().equals("facility.fabrication.assembly"))
                .toList();

        var rejected = runtime.projectYard(installedYard(), station, withoutAssembly);

        assertEquals(Stage18ShipyardRuntime.YardStatus.SUPPORT_FACILITY_MISSING, rejected.status());
        assertFalse(rejected.active());
        assertEquals(null, rejected.plannerCapability());
    }

    @Test
    void buildConsumesClosedBareHullMassAndFinishedModulesBeforeCompletion() {
        var plan = engineeringService.planBuild(fullFit, yard.plannerCapability());
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        loadBuildInputs(station.storage(), fullFit);
        double workBefore = plan.requirements().totalWorkSeconds() + 100d;
        var budget = yard.openInterval(workBefore / yard.plannerCapability().workRate());

        var settlement = runtime.settleBuild(plan, station.storage(), yard, budget);

        assertTrue(settlement.settled());
        assertEquals(12_000_000d,
                settlement.consumedCommodityMassKg().values().stream().mapToDouble(Double::doubleValue).sum(),
                TOLERANCE);
        assertEquals(fullFit.installedModules().size(),
                settlement.consumedProductCount().values().stream().mapToInt(Integer::intValue).sum());
        for (InstalledModuleDefinition assignment : fullFit.installedModules()) {
            assertEquals(0, station.storage().productCount(assignment.moduleId()));
        }
        assertEquals(100d, budget.remainingWorkSeconds(), TOLERANCE);

        var completion = engineeringService.completeBuild(
                new EntityId(18_001L), plan, settlement.compatibilitySettlement());
        assertEquals(fullFit, completion.fit());
    }

    @Test
    void failedBuildDoesNotConsumeAnyStorageOrWork() {
        var plan = engineeringService.planBuild(fullFit, yard.plannerCapability());
        loadBuildInputs(station.storage(), fullFit);
        station.storage().removeCommodity("commodity.material.structural_alloy", 1d);
        var before = station.storage().snapshot();
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);
        double workBefore = budget.remainingWorkSeconds();

        var settlement = runtime.settleBuild(plan, station.storage(), yard, budget);

        assertEquals(Stage18ShipyardRuntime.SettlementStatus.INSUFFICIENT_COMMODITY, settlement.status());
        assertEquals("commodity.material.structural_alloy", settlement.subject());
        assertEquals(before, station.storage().snapshot());
        assertEquals(workBefore, budget.remainingWorkSeconds(), TOLERANCE);
    }

    @Test
    void refitConsumesNewFinishedModuleAndPreservesIdentity() {
        InstalledFit sourceFit = withoutMount(fullFit, "utility_sensor");
        EntityId asset = new EntityId(18_002L);
        ShipDamageRuntime.Snapshot damage = pristineDamage();
        var plan = engineeringService.planRefit(
                asset,
                sourceFit,
                fullFit,
                ConsumableState.empty(),
                damage,
                yard.plannerCapability());
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        station.storage().addProduct("module.sensor_array_escort_v1", 1);
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);

        var settlement = runtime.settleRefit(plan, station.storage(), yard, budget);
        var completion = engineeringService.completeRefit(plan, settlement.compatibilitySettlement());

        assertTrue(settlement.settled());
        assertEquals(1, settlement.consumedProductCount().get("module.sensor_array_escort_v1"));
        assertEquals(0, station.storage().productCount("module.sensor_array_escort_v1"));
        assertEquals(asset, completion.assetId());
        assertEquals(fullFit, completion.fit());
    }

    @Test
    void refitReportsRemovedModuleWithoutPretendingItsConditionIsPristineInventory() {
        InstalledFit target = withoutMount(fullFit, "utility_sensor");
        EntityId asset = new EntityId(18_003L);
        ShipDamageRuntime.Snapshot damage = damagedSnapshot(Map.of("utility_sensor", 0.4d), 1d);
        MaintenanceState maintenance = new MaintenanceState(Map.of("utility_sensor", 50_000d));
        var plan = engineeringService.planRefit(
                asset,
                fullFit,
                target,
                ConsumableState.empty(),
                damage,
                yard.plannerCapability());
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);

        var settlement = runtime.settleRefit(plan, station.storage(), yard, budget);
        ShipyardRefitContinuity.Completion completion = ShipyardRefitContinuity.complete(
                engineeringService,
                plan,
                settlement.compatibilitySettlement(),
                damage,
                maintenance);

        assertTrue(settlement.settled());
        assertEquals(1, settlement.releasedProductCount().get("module.sensor_array_escort_v1"));
        assertEquals(0, station.storage().productCount("module.sensor_array_escort_v1"));
        assertEquals(1, completion.removedModules().size());
        assertEquals(0.4d, completion.removedModules().get(0).integrity(), 0d);
        assertEquals(50_000d, completion.removedModules().get(0).secondsSinceService(), 0d);
    }

    @Test
    void repairConsumesDamageScaledPhysicalInputsAndCannotBeFree() {
        EntityId asset = new EntityId(18_004L);
        ShipDamageRuntime.Snapshot damage = damagedSnapshot(Map.of("core_drive", 0.5d), 0.5d);
        var plan = engineeringService.planRepair(
                asset,
                fullFit,
                ConsumableState.empty(),
                damage,
                yard.plannerCapability());
        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        loadRepairInputs(station.storage(), fullFit, damage);
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);

        var settlement = runtime.settleRepair(plan, damage, station.storage(), yard, budget);
        var completion = engineeringService.completeRepair(plan, settlement.compatibilitySettlement());

        assertTrue(settlement.settled());
        assertEquals(300_000d,
                settlement.consumedCommodityMassKg().get("commodity.material.structural_alloy"),
                TOLERANCE);
        assertTrue(settlement.consumedCommodityMassKg().values().stream().mapToDouble(Double::doubleValue).sum() > 0d);
        assertTrue(completion.damage().moduleDamage().isPristine());
        assertTrue(completion.damage().compartmentIntegrityById().values().stream().allMatch(value -> value == 1d));
    }

    @Test
    void scheduledMaintenanceConsumesPhysicalSparesBeforeServiceAgeReset() {
        EntityId asset = new EntityId(18_005L);
        MaintenanceState aged = engineeringService.advanceMaintenance(
                fullFit,
                MaintenanceState.initial(),
                200_000d);
        var plan = engineeringService.planMaintenance(
                asset,
                fullFit,
                ConsumableState.empty(),
                aged,
                yard.plannerCapability());
        assertTrue(plan.affectedMounts().contains("utility_sensor"));
        assertTrue(plan.affectedMounts().contains("weapon_spinal"));
        loadMaintenanceInputs(station.storage(), fullFit, plan.affectedMounts());
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1d);

        var settlement = runtime.settleMaintenance(plan, station.storage(), yard, budget);
        var completion = engineeringService.completeMaintenance(
                plan,
                settlement.compatibilitySettlement(),
                aged);

        assertTrue(settlement.settled());
        assertTrue(settlement.consumedCommodityMassKg().values().stream().mapToDouble(Double::doubleValue).sum() > 0d);
        assertEquals(0d, completion.maintenance().secondsSinceServiceByMount().get("utility_sensor"), 0d);
        assertEquals(0d, completion.maintenance().secondsSinceServiceByMount().get("weapon_spinal"), 0d);
    }

    private Stage18ShipyardRuntime.InstalledYardState installedYard() {
        return new Stage18ShipyardRuntime.InstalledYardState(
                "yard.instance.test",
                "yard.orbital_escort_v1",
                1d,
                1_200_000_000d,
                12d,
                500,
                500,
                true);
    }

    private List<FacilityCapabilitySnapshot> activeSupportFacilities(Stage18StationIndustrialNode node) {
        List<FacilityCapabilitySnapshot> result = new ArrayList<>();
        for (var reference : node.installedFacilities()) {
            if (reference.facilityDefinitionId().equals("facility.fabrication.heavy")) {
                result.add(facilityRuntime.project(new InstalledFacilityState(
                        reference.facilityInstanceId(),
                        reference.facilityDefinitionId(),
                        1d,
                        80_000_000d,
                        44_000_000d,
                        80d,
                        4d,
                        node.locationTag(),
                        true)));
            } else if (reference.facilityDefinitionId().equals("facility.fabrication.assembly")) {
                result.add(facilityRuntime.project(new InstalledFacilityState(
                        reference.facilityInstanceId(),
                        reference.facilityDefinitionId(),
                        1d,
                        200_000_000d,
                        120_000_000d,
                        150d,
                        6d,
                        node.locationTag(),
                        true)));
            }
        }
        return result;
    }

    private void loadBuildInputs(Stage18StationStorage storage, InstalledFit fit) {
        Stage18ShipyardCatalog.HullPhysicalProfile hull = shipyards.findHullProfile(fit.hullId());
        hull.buildInputsKg().forEach(input -> storage.addCommodity(input.commodityId(), input.massKg()));
        fit.installedModules().forEach(assignment -> storage.addProduct(assignment.moduleId(), 1));
    }

    private void loadRepairInputs(
            Stage18StationStorage storage,
            InstalledFit fit,
            ShipDamageRuntime.Snapshot damage) {
        Map<String, Double> required = new LinkedHashMap<>();
        var hull = shipyards.findHullProfile(fit.hullId());
        for (Map.Entry<String, Double> entry : damage.compartmentIntegrityById().entrySet()) {
            double loss = 1d - entry.getValue();
            if (loss <= 0d) {
                continue;
            }
            var profile = hull.findCompartmentRepair(entry.getKey());
            profile.inputsAtFullLossKg().forEach(input ->
                    required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
        }
        Map<String, String> moduleByMount = new LinkedHashMap<>();
        fit.installedModules().forEach(value -> moduleByMount.put(value.mountId(), value.moduleId()));
        for (Map.Entry<String, Double> entry : damage.moduleDamage().moduleIntegrityByMount().entrySet()) {
            double loss = 1d - entry.getValue();
            String moduleId = moduleByMount.get(entry.getKey());
            if (loss <= 0d || moduleId == null) {
                continue;
            }
            shipyards.findModuleProfile(moduleId).repairInputsAtFullLossKg().forEach(input ->
                    required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
        }
        required.forEach(storage::addCommodity);
    }

    private void loadMaintenanceInputs(
            Stage18StationStorage storage,
            InstalledFit fit,
            List<String> dueMounts) {
        Map<String, String> moduleByMount = new LinkedHashMap<>();
        fit.installedModules().forEach(value -> moduleByMount.put(value.mountId(), value.moduleId()));
        Map<String, Double> required = new LinkedHashMap<>();
        for (String mount : dueMounts) {
            String moduleId = moduleByMount.get(mount);
            if (moduleId == null) {
                continue;
            }
            shipyards.findModuleProfile(moduleId).maintenanceInputsKg().forEach(input ->
                    required.merge(input.commodityId(), input.massKg(), Double::sum));
        }
        required.forEach(storage::addCommodity);
    }

    private static InstalledFit withoutMount(InstalledFit fit, String mountId) {
        return new InstalledFit(
                fit.hullId(),
                fit.installedModules().stream()
                        .filter(value -> !value.mountId().equals(mountId))
                        .toList());
    }

    private static ShipDamageRuntime.Snapshot pristineDamage() {
        return damagedSnapshot(Map.of(), 1d);
    }

    private static ShipDamageRuntime.Snapshot damagedSnapshot(
            Map<String, Double> moduleIntegrity,
            double engineeringIntegrity) {
        Map<String, Double> compartments = new LinkedHashMap<>();
        compartments.put("engineering", engineeringIntegrity);
        compartments.put("mission_core", 1d);
        compartments.put("weapons", 1d);
        return new ShipDamageRuntime.Snapshot(compartments, new DamageState(moduleIntegrity));
    }
}

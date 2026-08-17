package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.FeasibilityCode;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardEngineeringServiceTest {
    @Test
    void capableYardBuildRequiresPhysicalInputsAndWorkBeforeCompletion() {
        Fixture fixture = fixture();
        WorkPlan plan = fixture.service.planBuild(fixture.fit, capableYard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertTrue(plan.requirements().inputs().stream()
                .anyMatch(value -> value.contentId().equals("component.heavy") && value.amount() > 0d));
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);
        assertTrue(plan.requirements().durationSeconds(capableYard())
                < plan.requirements().totalWorkSeconds());

        EntityId newShip = new EntityId(9001L);
        assertThrows(IllegalStateException.class,
                () -> fixture.service.completeBuild(newShip, plan, WorkSettlement.empty()));
        ShipyardEngineeringService.BuildCompletion completed = fixture.service.completeBuild(
                newShip, plan, fullySettled(plan));
        assertEquals(newShip, completed.assetId());
        assertEquals(fixture.fit, completed.fit());
    }

    @Test
    void yardCapabilityIsPhysicalAndCannotBeReplacedByTierNumber() {
        Fixture fixture = fixture();
        ShipyardCapability incapable = new ShipyardCapability(
                "yard.small",
                new Dimensions3d(120d, 40d, 20d),
                10_000_000d,
                Set.of(),
                Set.of("component.heavy"),
                Set.of(),
                0.20d,
                1d,
                10,
                10,
                10_000_000d);

        WorkPlan plan = fixture.service.planBuild(fixture.fit, incapable);
        assertFalse(plan.feasibility().feasible());
        Set<FeasibilityCode> codes = plan.feasibility().issues().stream()
                .map(ShipyardEngineeringService.FeasibilityIssue::code)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.contains(FeasibilityCode.BERTH_ENVELOPE_EXCEEDED));
        assertTrue(codes.contains(FeasibilityCode.BERTH_MASS_EXCEEDED));
        assertTrue(codes.contains(FeasibilityCode.FABRICATION_CAPABILITY_MISSING));
        assertTrue(codes.contains(FeasibilityCode.MATERIAL_HANDLING_MISSING));
        assertTrue(codes.contains(FeasibilityCode.TOOLING_MISSING));
        assertTrue(codes.contains(FeasibilityCode.PRECISION_CAPABILITY_INSUFFICIENT));
        assertTrue(codes.contains(FeasibilityCode.INDUSTRIAL_POWER_INSUFFICIENT));
        assertTrue(codes.contains(FeasibilityCode.LABOR_CAPACITY_INSUFFICIENT));
        assertTrue(codes.contains(FeasibilityCode.AUTOMATION_CAPACITY_INSUFFICIENT));
    }

    @Test
    void repairConsumesDamageScaledInputsAndWorkThenPreservesAssetIdentity() {
        Fixture fixture = fixture();
        EntityId asset = new EntityId(77L);
        ShipDamageRuntime.Snapshot damaged = damagedSnapshot(Map.of("core_drive", 0.5d), 0.75d);

        WorkPlan plan = fixture.service.planRepair(
                asset, fixture.fit, ConsumableState.empty(), damaged, capableYard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertTrue(plan.requirements().inputs().stream().mapToDouble(value -> value.amount()).sum() > 0d);
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);
        assertTrue(plan.affectedMounts().contains("core_drive"));
        assertTrue(plan.affectedMounts().contains("compartment:engineering"));
        assertThrows(IllegalStateException.class,
                () -> fixture.service.completeRepair(plan, WorkSettlement.empty()));

        ShipyardEngineeringService.RepairCompletion completion = fixture.service.completeRepair(
                plan, fullySettled(plan));
        assertEquals(asset, completion.assetId());
        assertTrue(completion.damage().moduleDamage().isPristine());
        assertTrue(completion.damage().compartmentIntegrityById().values().stream()
                .allMatch(value -> value == 1d));
    }

    @Test
    void refitChangesSameAssetReturnsRemovedModuleAndDoesNotTransferItsDamageToReplacementState() {
        Fixture fixture = fixture();
        EntityId asset = new EntityId(88L);
        InstalledFit target = withoutMount(fixture.fit, "utility_sensor");
        ShipDamageRuntime.Snapshot damaged = damagedSnapshot(
                Map.of("utility_sensor", 0.4d, "core_drive", 0.6d), 1d);

        WorkPlan plan = fixture.service.planRefit(
                asset, fixture.fit, target, ConsumableState.empty(), damaged, capableYard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertEquals(1, plan.removedModules().size());
        assertEquals("utility_sensor", plan.removedModules().get(0).mountId());
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);

        ShipyardEngineeringService.RefitCompletion completion = fixture.service.completeRefit(
                plan, fullySettled(plan));
        assertEquals(asset, completion.assetId());
        assertEquals(target, completion.fit());
        assertEquals(1, completion.removedModules().size());
        assertFalse(completion.damage().moduleDamage().moduleIntegrityByMount().containsKey("utility_sensor"));
        assertEquals(0.6d,
                completion.damage().moduleDamage().moduleIntegrityByMount().get("core_drive"), 1e-12d);
    }

    @Test
    void refitChangesPerformanceOnlyThroughTheCentralDerivedCalculator() {
        Fixture fixture = fixture();
        InstalledFit target = withoutMount(fixture.fit, "utility_sensor");
        WorkPlan plan = fixture.service.planRefit(
                new EntityId(880L), fixture.fit, target, ConsumableState.empty(),
                damagedSnapshot(Map.of(), 1d), capableYard());
        ShipyardEngineeringService.RefitCompletion completion = fixture.service.completeRefit(
                plan, fullySettled(plan));

        DerivedShipCalculator calculator = new DerivedShipCalculator(fixture.engineering);
        ShipEngineeringCatalog.HullDefinition hull = fixture.engineering.findHull(fixture.fit.hullId());
        DerivedShipState before = calculator.derive(
                hull, fixture.fit, ConsumableState.empty(), DamageState.pristine());
        DerivedShipState after = calculator.derive(
                hull, completion.fit(), ConsumableState.empty(), completion.damage().moduleDamage());

        assertTrue(after.totalMassKg() < before.totalMassKg());
        assertTrue(after.continuousPowerDemandW() < before.continuousPowerDemandW());
    }

    @Test
    void refitRejectsTargetUntilConsumablesBoundToRemovedHardwareAreUnloaded() {
        Fixture fixture = fixture();
        InstalledFit target = withoutMount(fixture.fit, "weapon_spinal");
        ConsumableState loadedWeapon = new ConsumableState(
                0d, 0d, 0d, 0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal", "kinetic_magazine_feed", InterfaceKind.AMMUNITION,
                        150d, 150d, 1L)));

        WorkPlan plan = fixture.service.planRefit(
                new EntityId(881L), fixture.fit, target, loadedWeapon,
                damagedSnapshot(Map.of(), 1d), capableYard());

        assertFalse(plan.feasibility().feasible());
        assertTrue(plan.feasibility().issues().stream()
                .anyMatch(value -> value.code() == FeasibilityCode.INVALID_TARGET_FIT));
    }

    @Test
    void maintenanceAgeUsesAuthoredIntervalsAndOnlyCompletedServiceResetsDueMounts() {
        Fixture fixture = fixture();
        EntityId asset = new EntityId(99L);
        MaintenanceState aged = fixture.service.advanceMaintenance(
                fixture.fit, MaintenanceState.initial(), 200_000d);
        WorkPlan plan = fixture.service.planMaintenance(
                asset, fixture.fit, ConsumableState.empty(), aged, capableYard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertTrue(plan.affectedMounts().contains("utility_sensor"));
        assertTrue(plan.affectedMounts().contains("weapon_spinal"));
        assertFalse(plan.affectedMounts().contains("core_drive"));
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);

        ShipyardEngineeringService.MaintenanceCompletion completion = fixture.service.completeMaintenance(
                plan, fullySettled(plan), aged);
        assertEquals(asset, completion.assetId());
        assertEquals(0d, completion.maintenance().secondsSinceServiceByMount().get("utility_sensor"), 0d);
        assertEquals(0d, completion.maintenance().secondsSinceServiceByMount().get("weapon_spinal"), 0d);
        assertEquals(200_000d, completion.maintenance().secondsSinceServiceByMount().get("core_drive"), 0d);
    }

    @Test
    void identicalRequestsUseOneOwnershipNeutralPlanningBoundary() {
        Fixture fixture = fixture();
        WorkPlan first = fixture.service.planBuild(fixture.fit, capableYard());
        WorkPlan second = fixture.service.planBuild(fixture.fit, capableYard());

        assertEquals(first.requirements(), second.requirements());
        assertEquals(first.feasibility(), second.feasibility());
        assertEquals(first.targetFit(), second.targetFit());
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial = ShipyardIndustrialCatalogLoader.loadDefault(engineering);
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        return new Fixture(engineering, new ShipyardEngineeringService(engineering, industrial), fit);
    }

    private static ShipyardCapability capableYard() {
        return new ShipyardCapability(
                "yard.escort_demonstrator",
                new Dimensions3d(300d, 120d, 70d),
                30_000_000d,
                Set.of(
                        "heavy_structure", "pressure_hull", "armor_integration",
                        "heavy_machinery", "power_system_integration", "propulsion_integration",
                        "electronics_integration", "precision_alignment", "thermal_system_integration",
                        "light_structure", "weapon_integration"),
                Set.of("component.heavy", "component.electrical", "component.precision"),
                Set.of(
                        "escort_frame_fixture", "heavy_lift", "reactor_service_fixture",
                        "drive_alignment_fixture", "sensor_calibration_rig", "coolant_pressure_rig",
                        "weapon_bore_alignment_rig"),
                1d,
                8d,
                500,
                500,
                2_000_000_000d);
    }

    private static ShipDamageRuntime.Snapshot damagedSnapshot(
            Map<String, Double> moduleIntegrity, double engineeringIntegrity) {
        Map<String, Double> compartments = new LinkedHashMap<>();
        compartments.put("engineering", engineeringIntegrity);
        compartments.put("mission_core", 1d);
        compartments.put("weapons", 1d);
        return new ShipDamageRuntime.Snapshot(compartments, new DamageState(moduleIntegrity));
    }

    private static InstalledFit withoutMount(InstalledFit fit, String mountId) {
        List<InstalledModuleDefinition> modules = fit.installedModules().stream()
                .filter(value -> !value.mountId().equals(mountId))
                .toList();
        return new InstalledFit(fit.hullId(), modules);
    }

    private static WorkSettlement fullySettled(WorkPlan plan) {
        Map<String, Double> inputs = new LinkedHashMap<>();
        for (ShipyardEngineeringService.IndustrialInputRequirement input : plan.requirements().inputs()) {
            inputs.put(input.contentId(), input.amount());
        }
        return new WorkSettlement(inputs, plan.requirements().totalWorkSeconds());
    }

    private record Fixture(
            ShipEngineeringCatalog engineering,
            ShipyardEngineeringService service,
            InstalledFit fit) {
        private Fixture {
            assertNotNull(engineering);
            assertNotNull(service);
            assertNotNull(fit);
        }
    }
}

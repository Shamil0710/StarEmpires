package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardRefitContinuityTest {
    @Test
    void removedDamagedModuleRetainsIntegrityAndServiceAgeInsteadOfBecomingPristine() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardEngineeringService service = new ShipyardEngineeringService(
                engineering, ShipyardIndustrialCatalogLoader.loadDefault(engineering));
        InstalledFit source = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        InstalledFit target = new InstalledFit(source.hullId(), source.installedModules().stream()
                .filter(value -> !value.mountId().equals("utility_sensor"))
                .toList());
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                Map.of("engineering", 1d, "mission_core", 0.9d, "weapons", 1d),
                new DamageState(Map.of("utility_sensor", 0.35d, "core_drive", 0.7d)));
        MaintenanceState maintenance = new MaintenanceState(Map.of(
                "utility_sensor", 180_000d,
                "core_drive", 75_000d));
        WorkPlan plan = service.planRefit(
                new EntityId(701L), source, target, ConsumableState.empty(), damage, capableYard());

        ShipyardRefitContinuity.Completion completion = ShipyardRefitContinuity.complete(
                service, plan, fullySettled(plan), damage, maintenance);

        assertEquals(new EntityId(701L), completion.assetId());
        assertEquals(target, completion.fit());
        assertEquals(1, completion.removedModules().size());
        ShipyardRefitContinuity.RemovedModuleState removed = completion.removedModules().get(0);
        assertEquals("utility_sensor", removed.assignment().mountId());
        assertEquals(0.35d, removed.integrity(), 0d);
        assertEquals(180_000d, removed.secondsSinceService(), 0d);
        assertFalse(completion.installedDamage().moduleDamage().moduleIntegrityByMount()
                .containsKey("utility_sensor"));
        assertEquals(0.7d,
                completion.installedDamage().moduleDamage().moduleIntegrityByMount().get("core_drive"), 0d);
        assertEquals(75_000d,
                completion.installedMaintenance().secondsSinceServiceByMount().get("core_drive"), 0d);
        assertEquals(0.9d,
                completion.installedDamage().compartmentIntegrityById().get("mission_core"), 0d);
    }

    private static WorkSettlement fullySettled(WorkPlan plan) {
        Map<String, Double> delivered = new LinkedHashMap<>();
        for (ShipyardEngineeringService.IndustrialInputRequirement input : plan.requirements().inputs()) {
            delivered.put(input.contentId(), input.amount());
        }
        return new WorkSettlement(delivered, plan.requirements().totalWorkSeconds());
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
                1d, 8d, 500, 500, 2_000_000_000d);
    }
}

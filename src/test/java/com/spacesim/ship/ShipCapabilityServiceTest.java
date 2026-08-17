package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipCapabilityServiceTest {
    @Test
    void readOnlyCapabilityProjectionUsesPhysicalDamageAmmoAndMaintenanceState() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ConsumableState consumables = new ConsumableState(
                0d, 0d, 0d, 0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal", "kinetic_magazine_feed", InterfaceKind.AMMUNITION,
                        12d, 60d, 12L)));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        RuntimeState operating = runtime.initialize(fit, consumables);
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("compartment_mid", 0.8d),
                        new DamageState(Map.of("core_drive", 0.5d))),
                Map.of(),
                new MaintenanceState(Map.of("core_drive", 600_000d)),
                new WeaponLoadoutState(List.of(
                        new WeaponLoadoutState.FeedBinding(
                                "weapon_spinal", "kinetic_magazine_feed", "ammo.kinetic_slug_v1"))),
                WeaponMountRuntime.RuntimeState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, operating, instance);
        ShipCapabilityService service = new ShipCapabilityService(catalog);
        RuntimeState beforeRuntime = component.runtimeState;
        ShipInstanceRuntimeState beforeInstance = component.instanceState;

        ShipCapabilityService.Snapshot snapshot = service.snapshot(component);

        double ratedThrust = catalog.findModule("module.main_drive_escort_v1")
                .capabilityParameters().get(ShipEngineeringRuntime.THRUST_N);
        assertEquals(ratedThrust * 0.5d, snapshot.acceleration().maxThrustN(), 1e-6);
        assertEquals(ratedThrust * 0.5d, snapshot.derived().availableThrustN(), 1e-6);
        assertTrue(service.getRepairNeed(component).repairRequired());
        assertEquals(60d, snapshot.ammunition().ammunitionMassKg(), 0d);
        assertEquals(12L, snapshot.ammunition().ammunitionCount());
        assertEquals("ammo.kinetic_slug_v1", snapshot.ammunition().feeds().get(0).ammunitionContentId());
        assertTrue(snapshot.maintenance().overdueMounts().contains("core_drive"));
        assertEquals(beforeRuntime, component.runtimeState,
                "capability queries must not mutate authoritative operating state");
        assertEquals(beforeInstance, component.instanceState,
                "capability queries must not mutate local physical continuity state");
    }
}

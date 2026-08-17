package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipCapabilityService;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipEngineeringUiProjectionTest {
    @Test
    void projectionExposesPhysicalStateWithoutMutatingEngineeringComponent() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        var runtime = new ShipEngineeringRuntime(catalog).initialize(fit, ConsumableState.empty());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("compartment_mid", 0.8d),
                        new DamageState(Map.of("core_drive", 0.5d))),
                Map.of(),
                new MaintenanceState(Map.of("core_drive", 600_000d)),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, runtime, instance);
        Entity entity = new Entity().add(component);
        var beforeRuntime = component.runtimeState;
        var beforeInstance = component.instanceState;

        String text = ShipEngineeringUiProjection.describe(entity, new ShipCapabilityService(catalog));

        assertTrue(text.contains("Инженерное состояние"));
        assertTrue(text.contains("Масса:"));
        assertTrue(text.contains("Макс. ускорение:"));
        assertTrue(text.contains("Остаток Δv:"));
        assertTrue(text.contains("Запас мощности:"));
        assertTrue(text.contains("Боеприпасы:"));
        assertTrue(text.contains("Ремонт требуется: да"));
        assertTrue(text.contains("Просрочено обслуживание: core_drive"));
        assertEquals(beforeRuntime, component.runtimeState);
        assertEquals(beforeInstance, component.instanceState);
    }
}

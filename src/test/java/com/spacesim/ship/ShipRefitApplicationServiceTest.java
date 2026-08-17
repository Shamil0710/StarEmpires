package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardRefitContinuity.Completion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipRefitApplicationServiceTest {
    @Test
    void refitKeepsSameAssetAndRetainedModuleRuntimeButDropsRemovedWeaponContinuity() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit source = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        InstalledFit target = new InstalledFit(
                source.hullId(),
                source.installedModules().stream()
                        .filter(module -> !module.mountId().equals("weapon_1"))
                        .toList());
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        RuntimeState initial = runtime.initialize(source, ConsumableState.empty());
        RuntimeState hot = new RuntimeState(
                initial.consumables(), initial.sharedBusEnergyJ(), initial.shipHeatStoredJ(),
                Map.of("core_drive", 500d, "weapon_1", 250d),
                initial.thrustLimitNByMount(), initial.coolantBusCapacityW(),
                Map.of("core_ftl", 8d));
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                Map.of("compartment_mid", 0.8d), new DamageState(Map.of("core_drive", 0.7d)));
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                new MaintenanceState(Map.of("core_drive", 900d, "weapon_1", 300d)),
                new WeaponLoadoutState(List.of(
                        new WeaponLoadoutState.FeedBinding(
                                "weapon_1", "primary_feed", "ammo.kinetic_slug_v1"))),
                new WeaponMountRuntime.RuntimeState(Map.of("weapon_1", 3d)));
        EngineeringComponent component = new EngineeringComponent(source, hot, instance);
        EntityId id = new EntityId(712L);
        Completion completion = new Completion(
                id,
                target,
                damage,
                new MaintenanceState(Map.of("core_drive", 900d)),
                List.of(new ShipyardRefitContinuity.RemovedModuleState(
                        new InstalledModuleDefinition("weapon_1", "module.kinetic_cannon_escort_v1"),
                        1d, 300d)));

        EngineeringComponent returned = new ShipRefitApplicationService(catalog).apply(id, component, completion);

        assertSame(component, returned);
        assertEquals(target, component.fit);
        assertEquals(500d, component.runtimeState.localHeatJByMount().get("core_drive"), 0d);
        assertFalse(component.runtimeState.localHeatJByMount().containsKey("weapon_1"));
        assertEquals(8d, component.runtimeState.ftlCooldownSecondsByMount().get("core_ftl"), 0d);
        assertEquals(0.7d,
                component.instanceState.damage().moduleDamage().moduleIntegrityByMount().get("core_drive"), 0d);
        assertEquals(900d,
                component.instanceState.maintenance().secondsSinceServiceByMount().get("core_drive"), 0d);
        assertEquals(List.of(), component.instanceState.weaponLoadout().feeds());
        assertEquals(Map.of(), component.instanceState.weaponMountRuntime().cooldownSecondsByMount());
    }

    @Test
    void completionForDifferentAssetCannotMutateLiveShip() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        EngineeringComponent component = new EngineeringComponent(
                fit, new ShipEngineeringRuntime(catalog).initialize(fit, ConsumableState.empty()));
        Completion wrong = new Completion(
                new EntityId(2L), fit,
                new ShipDamageRuntime.Snapshot(Map.of(), DamageState.pristine()),
                MaintenanceState.initial(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new ShipRefitApplicationService(catalog).apply(new EntityId(1L), component, wrong));
        assertEquals(fit, component.fit);
    }
}

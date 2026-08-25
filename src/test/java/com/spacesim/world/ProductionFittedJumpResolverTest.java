package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.WeaponMountRuntime;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionFittedJumpResolverTest {
    @Test
    void productionResolverUsesStage21CatalogAndCommitsTheSamePhysicalPlan() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.all().get(0);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var state = runtime.initialize(fit, doctrine.initialConsumables(), DamageState.pristine());
        EngineeringComponent component = new EngineeringComponent(
                fit, state, ShipInstanceRuntimeState.legacyNeutral());
        ProductionFittedJumpResolver resolver = new ProductionFittedJumpResolver();

        var plan = resolver.plan(component);
        assertTrue(plan.allowed(), () -> "strategic fitted jump rejected: " + plan.failure());

        var committed = resolver.commit(component, plan);
        assertEquals(state.sharedBusEnergyJ() - plan.storedEnergyDrawJ(), committed.sharedBusEnergyJ(), 1e-6d);
        assertEquals(plan.cooldownSeconds(), committed.ftlCooldownSecondsByMount().get(plan.mountId()), 1e-9d);
        assertEquals(plan.jumpHeatJ(), committed.localHeatJByMount().get(plan.mountId()), 1e-6d);
    }

    @Test
    void destroyedPhysicalFtlMountFailsClosedInsteadOfUsingPristineCapability() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.all().get(0);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var state = runtime.initialize(fit, doctrine.initialConsumables(), DamageState.pristine());
        DamageState destroyedFtl = new DamageState(Map.of("utility_datalink", 0d));
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new Snapshot(Map.of(), destroyedFtl),
                Map.of(),
                new MaintenanceState(Map.of()),
                doctrine.weaponLoadout(),
                WeaponMountRuntime.RuntimeState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, state, instance);

        var plan = new ProductionFittedJumpResolver().plan(component);

        assertEquals(JumpFailure.NO_FTL_MODULE, plan.failure());
        assertTrue(!plan.allowed());
    }
}

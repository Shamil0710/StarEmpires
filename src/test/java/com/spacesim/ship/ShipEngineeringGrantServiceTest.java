package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipEngineeringGrantServiceTest {
    @Test
    void incrementalOperationUsesContinuousMarginThenPhysicalSharedStorageAndCommitsHeat() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        RuntimeState operating = runtime.initialize(fit, ConsumableState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, operating, ShipInstanceRuntimeState.legacyNeutral());
        ShipEngineeringGrantService grants = new ShipEngineeringGrantService(catalog);
        double continuousMarginW = runtime.derive(fit, operating, DamageState.pristine()).continuousPowerMarginW();
        double requestedPowerW = Math.max(0d, continuousMarginW) + 1_000d;
        double beforeEnergyJ = operating.sharedBusEnergyJ();
        double beforeHeatJ = operating.localHeatJByMount().getOrDefault("core_drive", 0d);

        ShipEngineeringGrantService.GrantResult result = grants.grantAndCommit(
                component, "core_drive", requestedPowerW, 100d, 2d);

        assertTrue(result.committed());
        assertEquals(requestedPowerW, result.grant().grantedPowerW(), 0d);
        assertEquals(2_000d, result.storageDrawJ(), 1e-6,
                "only demand above surviving continuous margin may leave shared storage");
        assertEquals(beforeEnergyJ - 2_000d, component.runtimeState.sharedBusEnergyJ(), 1e-6);
        assertEquals(beforeHeatJ + 200d,
                component.runtimeState.localHeatJByMount().get("core_drive"), 1e-9);
    }

    @Test
    void deniedGrantCannotMutateEnergyOrHeatAndDestroyedMountCannotOperate() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        RuntimeState operating = runtime.initialize(fit, ConsumableState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, operating, ShipInstanceRuntimeState.legacyNeutral());
        ShipEngineeringGrantService grants = new ShipEngineeringGrantService(catalog);

        ShipEngineeringGrantService.GrantResult impossible = grants.grantAndCommit(
                component, "core_drive", Double.MAX_VALUE / 4d, 0d, 1d);
        assertFalse(impossible.committed());
        assertEquals(operating, component.runtimeState);

        component.setInstanceState(new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of("core_drive", 0d))),
                Map.of(),
                component.instanceState.maintenance(),
                component.instanceState.weaponLoadout(),
                component.instanceState.weaponMountRuntime()));
        ShipEngineeringGrantService.GrantResult destroyed = grants.grantAndCommit(
                component, "core_drive", 1d, 1d, 1d);
        assertFalse(destroyed.committed());
        assertEquals(operating, component.runtimeState);
    }
}

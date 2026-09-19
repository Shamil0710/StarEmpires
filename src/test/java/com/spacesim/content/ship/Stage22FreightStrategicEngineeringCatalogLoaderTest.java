package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22FreightStrategicEngineeringCatalogLoaderTest {
    @Test
    void bothCoreFreightVariantsPayForFittedFtlAndCanPlanPhysicalJump() {
        ShipEngineeringCatalog catalog = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();

        for (String fitId : List.of(
                Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_FREIGHT_STRATEGIC_FIT,
                Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT)) {
            var definition = catalog.findDemonstratorFit(fitId);
            assertNotNull(definition);
            InstalledFit fit = InstalledFit.fromDemonstrator(definition);
            assertTrue(fit.installedModules().stream().anyMatch(value ->
                    value.mountId().equals("utility_defense")
                            && value.moduleId().equals(Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID)));

            ArrayList<ConsumableLoad> loads = new ArrayList<>();
            for (var installed : fit.installedModules()) {
                var module = catalog.findModule(installed.moduleId());
                for (var iface : module.interfaces()) {
                    if (iface.kind() == InterfaceKind.REACTION_MASS) {
                        loads.add(new ConsumableLoad(
                                installed.mountId(), iface.id(), iface.kind(),
                                iface.capacity(), iface.capacity(), 0L));
                    }
                }
            }
            var consumables = new ConsumableState(0d, 0d, 0d, 0d, loads);
            ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
            var state = runtime.initialize(fit, consumables, DamageState.pristine());
            var jump = runtime.planJump(fit, state, DamageState.pristine());
            assertTrue(jump.allowed(), () -> fitId + " must use fitted physical FTL: " + jump.failure());
        }
    }
}

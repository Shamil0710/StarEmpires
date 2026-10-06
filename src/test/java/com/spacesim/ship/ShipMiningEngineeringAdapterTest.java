package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipMiningEngineeringAdapterTest {
    @Test void commonRuntimeAdmitsAuthoredMiningFitButDoesNotGrantEquipmentToFreightersOrWorkshop() {
        var catalog = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var adapter = new ShipMiningEngineeringAdapter();
        var fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(Stage22CivilianMiningEngineeringCatalogLoader.MINING_FIT_ID));
        var component = new EngineeringComponent(fit, new ShipEngineeringRuntime(catalog).initialize(fit, ConsumableState.empty()));
        var before = component.runtimeState;
        var capability = adapter.derive(new ProductionEngineeringRuntimeResolver().derive(component)).orElseThrow();
        assertEquals(25, capability.maximumSourceKgPerSecond());
        assertEquals(4_000_000, capability.extraction().availablePowerW());
        assertSame(before, component.runtimeState);
        for (String id : java.util.List.of(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT,
                Stage22CivilianMiningEngineeringCatalogLoader.BASE_SUPPORT_FIT_ID)) {
            var other = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(id));
            assertTrue(adapter.derive(new DerivedShipCalculator(catalog).derive(catalog.findHull(other.hullId()), other, ConsumableState.empty(), DamageState.pristine())).isEmpty());
        }
    }

    @Test void damagedModuleReducesRatesAndDestroyedEquipmentOrReactorCannotMine() {
        var catalog = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(Stage22CivilianMiningEngineeringCatalogLoader.MINING_FIT_ID));
        String mount = fit.installedModules().stream().filter(m -> m.moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID))
                .findFirst().orElseThrow().mountId();
        var calculator = new DerivedShipCalculator(catalog); var adapter = new ShipMiningEngineeringAdapter();
        var damaged = adapter.derive(calculator.derive(catalog.findHull(fit.hullId()), fit, ConsumableState.empty(), new DamageState(Map.of(mount, .5)))).orElseThrow();
        assertEquals(12.5, damaged.maximumSourceKgPerSecond());
        assertEquals(2_000_000, damaged.extraction().availablePowerW());
        assertEquals(1.25, damaged.extraction().workRate());
        assertTrue(adapter.derive(calculator.derive(catalog.findHull(fit.hullId()), fit, ConsumableState.empty(), new DamageState(Map.of(mount, 0d)))).isEmpty());
        var reactors = new java.util.TreeMap<String, Double>();
        for (var installed : fit.installedModules()) if (catalog.findModule(installed.moduleId()).continuousPowerSupplyW() > 0)
            reactors.put(installed.mountId(), 0d);
        assertTrue(adapter.derive(calculator.derive(catalog.findHull(fit.hullId()), fit, ConsumableState.empty(), new DamageState(reactors))).isEmpty());
    }
}


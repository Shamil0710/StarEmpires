package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedShipSignatureGeometryTest {
    @Test
    void hullProjectedGeometrySeedsProductionRadarSignature() {
        var catalog = ShipEngineeringCatalogLoader.loadDefault();
        var hull = catalog.findHull("hull.escort_destroyer_v1");
        var derived = new DerivedShipCalculator(catalog).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", ConsumableState.empty(), DamageState.pristine());
        var suite = new ShipSensorEngineeringAdapter().derive(derived);

        assertEquals(
                hull.baseSignatureGeometryAreaM2(),
                suite.staticSignature().radarCrossSectionM2(),
                0d);
    }

    @Test
    void stage175iActiveRadarCanAcquireRealDerivedHullWithoutInjectedTargetSignature() {
        var catalog = com.spacesim.content.ship.Stage175ICombatTestContentPack.loadDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.get(Stage175IFleetDoctrineCatalog.DoctrineId.A_KINETIC_LINE);
        var hull = catalog.findHull("hull.test_doctrine_destroyer_v1");
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(doctrine.fitId()));
        var derived = new DerivedShipCalculator(catalog).derive(
                hull, fit, doctrine.initialConsumables(), DamageState.pristine());
        var suite = new ShipSensorEngineeringAdapter().derive(derived);
        FittedSensor radar = suite.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElseThrow();

        var observation = new ShipSensorRuntime().observe(
                1L,
                2L,
                radar.definition(),
                SensorRuntimeState.nominal(),
                new Position2d(0d, 0d),
                new Position2d(300_000d, 0d),
                suite.staticSignature(),
                ElectronicWarfareState.empty(),
                0d);

        assertTrue(observation.measurement().isPresent());
        assertTrue(observation.measurement().orElseThrow().hasRange());
    }
}

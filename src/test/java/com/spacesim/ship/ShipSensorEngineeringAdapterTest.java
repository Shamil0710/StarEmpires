package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensorSuite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSensorEngineeringAdapterTest {
    @Test
    void productionDemonstratorProjectsFittedModesWithoutStaticActiveRadarEmission() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipState derived = new DerivedShipCalculator(catalog).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                DamageState.pristine());

        FittedSensorSuite suite = new ShipSensorEngineeringAdapter().derive(derived);

        assertEquals(2, suite.sensors().size());
        FittedSensor passive = suite.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.PASSIVE_THERMAL)
                .findFirst().orElseThrow();
        FittedSensor radar = suite.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst().orElseThrow();

        assertEquals("utility_sensor", passive.mountId());
        assertEquals("utility_sensor", radar.mountId());
        assertEquals("module.sensor_array_escort_v1", radar.moduleId());
        assertEquals(240d, passive.definition().apertureAreaM2(), 0d);
        assertEquals(240d, radar.definition().apertureAreaM2(), 0d);
        assertEquals(45_000_000d, radar.definition().activeTransmitPowerW(), 0d);
        assertEquals(10d, radar.definition().transmitGainLinear(), 0d);
        assertEquals(60_000_000d, radar.definition().activeModePowerDemandW(), 0d);
        assertEquals(15_000_000d, radar.definition().activeModeWasteHeatW(), 0d);
        assertEquals(5_000_000d, radar.definition().eccmPowerDemandW(), 0d);
        assertEquals(2_500_000d, radar.definition().eccmWasteHeatW(), 0d);
        assertEquals(0d, suite.staticSignature().activeRadioEmissionPowerW(), 0d);
        assertEquals(0d, suite.staticSignature().jammerEmissionPowerW(), 0d);
        assertEquals(950_000_000_000d, suite.staticSignature().enginePlumeRadiantPowerW(), 0d);
        assertEquals(3_420_000_000d, suite.staticSignature().thermalRadiantPowerW(), 0d);
        assertTrue(derived.signatureContributions().containsKey("thermal_w"));
    }

    @Test
    void activeRadarDefinitionRejectsFreeRadiatedPower() {
        boolean rejected = false;
        try {
            new SensorDefinition(
                    "sensor.invalid_free_radar",
                    Mode.ACTIVE_RADAR,
                    SignatureState.Channel.RADAR,
                    10d, 1e-12d, 1d, 2d, 3d, 4d,
                    1e-4d, 1e-3d,
                    100d, 1d,
                    50d, 0d,
                    10d, 0d, 0d);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }
}

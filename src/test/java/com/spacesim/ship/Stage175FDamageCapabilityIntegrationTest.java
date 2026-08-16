package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175FDamageCapabilityIntegrationTest {
    @Test
    void driveReactorAndRadiatorDamageReduceRealCommonCapabilities() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        DerivedShipState pristine = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", ConsumableState.empty(), DamageState.pristine());

        DerivedShipState damagedDrive = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("core_drive", 0.5d)));
        assertEquals(pristine.availableThrustN() * 0.5d, damagedDrive.availableThrustN(), 1e-6d);
        assertEquals(pristine.accelerationMps2() * 0.5d, damagedDrive.accelerationMps2(), 1e-12d);
        assertEquals(pristine.totalMassKg(), damagedDrive.totalMassKg(), 1e-9d);

        DerivedShipState damagedReactor = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("core_reactor", 0.5d)));
        assertEquals(pristine.continuousPowerSupplyW() * 0.5d,
                damagedReactor.continuousPowerSupplyW(), 1e-6d);
        assertTrue(damagedReactor.continuousPowerMarginW() < pristine.continuousPowerMarginW());

        DerivedShipState damagedRadiator = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("utility_thermal", 0.5d)));
        assertEquals(pristine.heatRejectionW() * 0.5d, damagedRadiator.heatRejectionW(), 1e-6d);
        assertTrue(damagedRadiator.continuousHeatMarginW() < pristine.continuousHeatMarginW());
    }

    @Test
    void sensorDamageWorsensApertureNoiseAndMeasurementFloors() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        ShipSensorEngineeringAdapter adapter = new ShipSensorEngineeringAdapter();

        DerivedShipState pristine = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", ConsumableState.empty(), DamageState.pristine());
        DerivedShipState damaged = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("utility_sensor", 0.5d)));

        SensorDefinition pristinePassive = adapter.derive(pristine).sensors().stream()
                .map(ShipSensorEngineeringAdapter.FittedSensor::definition)
                .filter(value -> value.mode() == Mode.PASSIVE_THERMAL)
                .findFirst().orElseThrow();
        SensorDefinition damagedPassive = adapter.derive(damaged).sensors().stream()
                .map(ShipSensorEngineeringAdapter.FittedSensor::definition)
                .filter(value -> value.mode() == Mode.PASSIVE_THERMAL)
                .findFirst().orElseThrow();

        assertEquals(pristinePassive.apertureAreaM2() * 0.5d,
                damagedPassive.apertureAreaM2(), 1e-12d);
        assertEquals(pristinePassive.receiverNoisePowerW() * 2d,
                damagedPassive.receiverNoisePowerW(), 1e-18d);
        assertEquals(pristinePassive.bearingSigmaFloorRad() * 2d,
                damagedPassive.bearingSigmaFloorRad(), 1e-12d);
        assertEquals(pristinePassive.rangeSigmaFraction() * 2d,
                damagedPassive.rangeSigmaFraction(), 1e-12d);

        DerivedShipState destroyed = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("utility_sensor", 0d)));
        assertTrue(adapter.derive(destroyed).sensors().isEmpty());
    }
}

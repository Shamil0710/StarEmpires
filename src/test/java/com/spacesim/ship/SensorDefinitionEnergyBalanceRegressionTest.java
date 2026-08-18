package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensorDefinitionEnergyBalanceRegressionTest {
    @Test
    void fractionalSensorDamageCannotTurnClosedRadarEnergyBudgetIntoFalseViolation() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipState damaged = new DerivedShipCalculator(catalog).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                new DamageState(Map.of("utility_sensor", 0.2517d)));

        var suite = assertDoesNotThrow(() -> new ShipSensorEngineeringAdapter().derive(damaged));
        SensorDefinition radar = suite.sensors().stream()
                .map(ShipSensorEngineeringAdapter.FittedSensor::definition)
                .filter(value -> value.mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElseThrow();

        double accountedPowerW = radar.activeTransmitPowerW() + radar.activeModeWasteHeatW();
        double toleranceW = Math.ulp(Math.max(accountedPowerW, radar.activeModePowerDemandW())) * 8d;
        assertEquals(radar.activeModePowerDemandW(), accountedPowerW, toleranceW,
                "damage scaling must preserve the authored closed radar energy budget within floating-point roundoff");
    }

    @Test
    void materialRadarEnergyDeficitIsStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SensorDefinition(
                "sensor.invalid_energy_deficit",
                Mode.ACTIVE_RADAR,
                SignatureState.Channel.RADAR,
                10d,
                1e-12d,
                1d,
                2d,
                3d,
                4d,
                1e-4d,
                1e-3d,
                45d,
                1d,
                59d,
                15d,
                1d,
                0d,
                0d));
    }
}

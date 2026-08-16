package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipObservationService.EngineeringGrant;
import com.spacesim.ship.ShipObservationService.ExecutionResult;
import com.spacesim.ship.ShipObservationService.OperationPlan;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipObservationServiceTest {
    @Test
    void activeRadarCannotMeasureOrEmitWithoutCommonEngineeringGrant() {
        FittedSensor radar = productionRadar();
        ShipObservationService service = new ShipObservationService();
        SensorRuntimeState state = SensorRuntimeState.nominal();
        OperationPlan plan = service.planOperation(radar, state);

        assertEquals(60_000_000d, plan.requiredPowerW(), 0d);
        assertEquals(15_000_000d, plan.requiredHeatW(), 0d);

        ExecutionResult denied = service.execute(
                plan, EngineeringGrant.denied(), radar, state,
                1L, 2L,
                new Position2d(0d, 0d), new Position2d(500_000d, 0d),
                ShipSensorGeometryTest.radarTarget(), ElectronicWarfareState.empty(), 10d);

        assertFalse(denied.executed());
        assertTrue(denied.measurement().isEmpty());
        assertEquals(0d, denied.observerEmission().activeRadioEmissionPowerW(), 0d);
    }

    @Test
    void grantedActiveRadarCreatesMeasurementEmissionAndExplicitPhysicalLoad() {
        FittedSensor radar = productionRadar();
        ShipObservationService service = new ShipObservationService();
        SensorRuntimeState state = new SensorRuntimeState(true, true, 1d, 1d);
        OperationPlan plan = service.planOperation(radar, state);

        assertEquals(65_000_000d, plan.requiredPowerW(), 0d);
        assertEquals(17_500_000d, plan.requiredHeatW(), 0d);

        ExecutionResult executed = service.execute(
                plan,
                new EngineeringGrant(plan.requiredPowerW(), plan.requiredHeatW()),
                radar,
                state,
                1L,
                2L,
                new Position2d(0d, 0d),
                new Position2d(500_000d, 0d),
                ShipSensorGeometryTest.radarTarget(),
                ElectronicWarfareState.empty(),
                10d);

        assertTrue(executed.executed());
        assertTrue(executed.measurement().isPresent());
        assertTrue(executed.measurement().orElseThrow().hasRange());
        assertEquals(45_000_000d, executed.observerEmission().activeRadioEmissionPowerW(), 0d);
        assertEquals(65_000_000d, executed.consumedPowerW(), 0d);
        assertEquals(17_500_000d, executed.generatedHeatW(), 0d);
    }

    private static FittedSensor productionRadar() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        ShipEngineeringState.DerivedShipState derived = new DerivedShipCalculator(catalog).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ConsumableState.empty(),
                DamageState.pristine());
        return new ShipSensorEngineeringAdapter().derive(derived).sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst().orElseThrow();
    }
}

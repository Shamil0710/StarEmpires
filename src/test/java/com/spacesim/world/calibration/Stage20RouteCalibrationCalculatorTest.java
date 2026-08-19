package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator.RouteTravelSample;
import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator.TravelRegime;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RouteCalibrationCalculatorTest {
    @Test
    void shortRouteUsesPartialVariableMassAccelBrakeAndClosesDistance() {
        RepresentativeShipPropulsionEnvelope escort = productionEscort();
        double distanceM = escort.characteristicRestToRestDistanceM() * 0.5d;

        RouteTravelSample first = Stage20RouteCalibrationCalculator.derive("ESCORT_DESTROYER", escort, distanceM);
        RouteTravelSample second = Stage20RouteCalibrationCalculator.derive("ESCORT_DESTROYER", escort, distanceM);

        assertEquals(first, second);
        assertEquals(TravelRegime.ACCEL_BRAKE, first.regime());
        assertEquals(0d, first.coastTimeS(), 0d);
        assertTrue(first.reactionMassFractionConsumed() > 0d);
        assertTrue(first.reactionMassFractionConsumed() < 1d);
        assertTrue(first.reactionMassConsumedKg() < escort.reactionMassKg());
        assertTrue(first.requiredDeltaVMps() < escort.deltaVMps());
        assertTrue(first.peakSpeedMps() < escort.symmetricPeakSpeedMps());
        assertEquals(
                distanceM,
                first.accelerationDistanceM() + first.brakingDistanceM(),
                Math.max(1e-3d, distanceM * 1e-12d));
        assertEquals(
                first.totalTravelTimeS(),
                first.accelerationBurnDurationS() + first.brakingBurnDurationS(),
                1e-9d);
    }

    @Test
    void characteristicDistanceConsumesFullLoadWithoutCoast() {
        RepresentativeShipPropulsionEnvelope escort = productionEscort();

        RouteTravelSample sample = Stage20RouteCalibrationCalculator.derive(
                "ESCORT_DESTROYER", escort, escort.characteristicRestToRestDistanceM());

        assertEquals(TravelRegime.ACCEL_BRAKE, sample.regime());
        assertEquals(1d, sample.reactionMassFractionConsumed(), 0d);
        assertEquals(escort.reactionMassKg(), sample.reactionMassConsumedKg(), 0d);
        assertEquals(escort.deltaVMps(), sample.requiredDeltaVMps(), 0d);
        assertEquals(escort.fullBurnDurationS(), sample.totalTravelTimeS(), 0d);
        assertEquals(0d, sample.coastTimeS(), 0d);
    }

    @Test
    void longRouteUsesFullBurnAndAddsOnlyPhysicalCoastTime() {
        RepresentativeShipPropulsionEnvelope escort = productionEscort();
        double distanceM = escort.characteristicRestToRestDistanceM() * 3d;

        RouteTravelSample sample = Stage20RouteCalibrationCalculator.derive(
                "ESCORT_DESTROYER", escort, distanceM);

        double expectedCoastDistanceM = distanceM - escort.characteristicRestToRestDistanceM();
        double expectedCoastTimeS = expectedCoastDistanceM / escort.symmetricPeakSpeedMps();
        assertEquals(TravelRegime.ACCEL_COAST_BRAKE, sample.regime());
        assertEquals(1d, sample.reactionMassFractionConsumed(), 0d);
        assertEquals(expectedCoastTimeS, sample.coastTimeS(), 1e-9d);
        assertEquals(escort.fullBurnDurationS() + expectedCoastTimeS, sample.totalTravelTimeS(), 1e-9d);
        assertEquals(escort.deltaVMps(), sample.requiredDeltaVMps(), 0d);
        assertEquals(escort.accelerationDistanceM(), sample.accelerationDistanceM(), 0d);
        assertEquals(escort.brakingDistanceM(), sample.brakingDistanceM(), 0d);
    }

    @Test
    void routeCalculatorRejectsInvalidInputs() {
        RepresentativeShipPropulsionEnvelope escort = productionEscort();

        assertThrows(IllegalArgumentException.class,
                () -> Stage20RouteCalibrationCalculator.derive("", escort, 1_000d));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RouteCalibrationCalculator.derive("ESCORT_DESTROYER", escort, 0d));
        assertThrows(NullPointerException.class,
                () -> Stage20RouteCalibrationCalculator.derive("ESCORT_DESTROYER", null, 1_000d));
    }

    private static RepresentativeShipPropulsionEnvelope productionEscort() {
        return Stage20ScaleCalibrationProfile.deriveCurrent().representativeShips().stream()
                .filter(value -> "ESCORT_DESTROYER".equals(value.representativeId()))
                .findFirst()
                .orElseThrow();
    }
}

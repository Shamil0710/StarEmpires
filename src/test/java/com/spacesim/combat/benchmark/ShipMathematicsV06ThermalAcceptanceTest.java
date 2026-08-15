package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV06ThermalAcceptanceTest {
    @Test
    void radiatorReferenceRemainsSiAndMatchesThe1100KSeed() {
        assertEquals(74_717.9566817211,
                ShipMathematicsV06ThermalHarness.radiatorFluxWPerM2(),
                1.0e-6);
        assertEquals(16.666666666666668,
                ShipMathematicsV06ThermalHarness.pointDefenseTerminalWindowS(),
                1.0e-12);
        assertEquals(44.44444444444444,
                ShipMathematicsV06ThermalHarness.nonOverlappingWavePeriodS(),
                1.0e-12);
        assertEquals(0.375,
                ShipMathematicsV06ThermalHarness.requestedPointDefenseDutyFraction(),
                1.0e-12);
    }

    @Test
    void healthyPointDefenseCoolingSustainsTheCurrentWorstCaseEnvelope() {
        assertEquals(0.40,
                ShipMathematicsV06ThermalHarness.sustainablePointDefenseDutyFraction(
                        ShipMathematicsV06ThermalHarness.PD_LOCAL_COOLANT_TRANSFER_W),
                1.0e-12);
        assertEquals(20.0,
                ShipMathematicsV06ThermalHarness.coldPointDefenseBurstS(
                        ShipMathematicsV06ThermalHarness.PD_LOCAL_COOLANT_TRANSFER_W),
                1.0e-12);

        ShipMathematicsV06ThermalHarness.EmitterTrain train =
                ShipMathematicsV06ThermalHarness.runEmitterTrain(
                        ShipMathematicsV06ThermalHarness.PD_LOCAL_COOLANT_TRANSFER_W);
        assertEquals(13, train.waves().size());
        for (ShipMathematicsV06ThermalHarness.EmitterWave wave : train.waves()) {
            assertEquals(16.66, wave.deliveredBeamS(), 0.021);
            assertEquals(0.0, wave.throttledS(), 1.0e-12);
            assertTrue(wave.peakHeatJ() < ShipMathematicsV06ThermalHarness.PD_LOCAL_THERMAL_BUFFER_J);
            assertTrue(!wave.thermallyInhibited());
        }
        assertEquals(29_988_000.0, train.wave(13).peakHeatJ(), 1.0);
    }

    @Test
    void reducedCoolantThroughputCreatesARealThermalFailureMode() {
        ShipMathematicsV06ThermalHarness.EmitterTrain degraded =
                ShipMathematicsV06ThermalHarness.runEmitterTrain(1_000_000.0);

        assertEquals(16.66, degraded.wave(1).deliveredBeamS(), 0.021);
        assertEquals(0.0, degraded.wave(1).throttledS(), 1.0e-12);
        assertTrue(degraded.wave(2).throttledS() > 1.3);
        assertTrue(degraded.wave(2).thermallyInhibited());
        assertTrue(degraded.wave(13).deliveredBeamS() < 15.0);
        assertTrue(degraded.wave(13).throttledS() > 1.8);
        assertEquals(36_000_000.0, degraded.wave(13).peakHeatJ(), 1.0);
    }

    @Test
    void referenceHullThermalMarginsRemainRoleSpecificRatherThanClassBonuses() {
        List<ShipMathematicsV06ThermalHarness.ShipThermalPoint> points =
                ShipMathematicsV06ThermalHarness.referenceShipThermals();
        assertEquals(7, points.size());

        ShipMathematicsV06ThermalHarness.ShipThermalPoint recon =
                ShipMathematicsV06ThermalHarness.findShip(points, "RECON_FRIGATE");
        ShipMathematicsV06ThermalHarness.ShipThermalPoint destroyer =
                ShipMathematicsV06ThermalHarness.findShip(points, "ESCORT_DESTROYER");
        ShipMathematicsV06ThermalHarness.ShipThermalPoint battleship =
                ShipMathematicsV06ThermalHarness.findShip(points, "BATTLESHIP");

        assertTrue(recon.minimumRadiatorFraction() > 0.95);
        assertTrue(destroyer.minimumRadiatorFraction() < 0.31);
        assertTrue(battleship.minimumRadiatorFraction() < 0.35);
        assertEquals(3_500_000.0, recon.continuousHeatMarginW(), 1.0e-6);
        assertEquals(139_200_000.0, destroyer.continuousHeatMarginW(), 1.0e-6);
        assertEquals(2_612_700_000.0, battleship.continuousHeatMarginW(), 1.0e-6);

        assertTrue(recon.zeroRadiatorEmergencyEnduranceS()
                < destroyer.zeroRadiatorEmergencyEnduranceS());
        assertTrue(destroyer.zeroRadiatorEmergencyEnduranceS()
                < battleship.zeroRadiatorEmergencyEnduranceS());
    }
}

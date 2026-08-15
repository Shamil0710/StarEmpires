package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV07ProtectionAcceptanceTest {
    @Test
    void intactShipWeaponsAreExplicitlyOutsideTheMmodCalibrationGuardrail() {
        ShipMathematicsV07ProtectionHarness.ImpactPacket missile =
                ShipMathematicsV07ProtectionHarness.missileImpact();
        ShipMathematicsV07ProtectionHarness.ImpactPacket capital =
                ShipMathematicsV07ProtectionHarness.capitalKineticImpact();
        ShipMathematicsV07ProtectionHarness.ImpactPacket medium =
                ShipMathematicsV07ProtectionHarness.mediumCoilgunImpact();

        assertEquals(1_944_000_000_000.0, missile.kineticEnergyJ(), 1.0);
        assertEquals(216_000_000.0, missile.momentumNs(), 1.0e-6);
        assertEquals(450_000_000_000.0, capital.kineticEnergyJ(), 1.0);
        assertEquals(2_812_500_000.0, medium.kineticEnergyJ(), 1.0);

        ShipMathematicsV07ProtectionHarness.CalibrationGuardrail missileGuard =
                ShipMathematicsV07ProtectionHarness.calibrationGuardrail(missile);
        ShipMathematicsV07ProtectionHarness.CalibrationGuardrail capitalGuard =
                ShipMathematicsV07ProtectionHarness.calibrationGuardrail(capital);
        ShipMathematicsV07ProtectionHarness.CalibrationGuardrail mediumGuard =
                ShipMathematicsV07ProtectionHarness.calibrationGuardrail(medium);

        assertEquals(ShipMathematicsV07ProtectionHarness.CalibrationStatus.EXTRAPOLATION_FORBIDDEN,
                missileGuard.status());
        assertEquals(129_600.0, missileGuard.energyRatioToNasaLargeReference(), 1.0e-9);
        assertEquals(ShipMathematicsV07ProtectionHarness.CalibrationStatus.EXTRAPOLATION_FORBIDDEN,
                capitalGuard.status());
        assertEquals(30_000.0, capitalGuard.energyRatioToNasaLargeReference(), 1.0e-9);
        assertEquals(ShipMathematicsV07ProtectionHarness.CalibrationStatus.EXTRAPOLATION_FORBIDDEN,
                mediumGuard.status());
        assertEquals(187.5, mediumGuard.energyRatioToNasaLargeReference(), 1.0e-9);
    }

    @Test
    void standOffFragmentationReducesConcentratedExposureWithoutDeletingEnergy() {
        List<ShipMathematicsV07ProtectionHarness.DebrisExposure> sweep =
                ShipMathematicsV07ProtectionHarness.centralDispersionStandoffSweep();
        assertEquals(4, sweep.size());

        assertEquals(0.1130267742080761, sweep.get(0).shipHitFraction(), 1.0e-12);
        assertEquals(0.029648284673255892, sweep.get(1).shipHitFraction(), 1.0e-12);
        assertEquals(0.004808902419802274, sweep.get(2).shipHitFraction(), 1.0e-12);
        assertEquals(0.0012045830508669343, sweep.get(3).shipHitFraction(), 1.0e-12);

        for (int index = 1; index < sweep.size(); index++) {
            assertTrue(sweep.get(index).shipHitFraction() < sweep.get(index - 1).shipHitFraction());
            assertTrue(sweep.get(index).intersectingEnergyJ() < sweep.get(index - 1).intersectingEnergyJ());
        }

        assertEquals(219_724_049_060.5, sweep.get(0).intersectingEnergyJ(), 1.0);
        assertEquals(2_341_709_450.9, sweep.get(3).intersectingEnergyJ(), 2.0);
    }

    @Test
    void moreLateralDispersionReducesExposureAtTheSameInterceptDistance() {
        ShipMathematicsV07ProtectionHarness.ImpactPacket missile =
                ShipMathematicsV07ProtectionHarness.missileImpact();
        ShipMathematicsV07ProtectionHarness.DebrisExposure narrow =
                ShipMathematicsV07ProtectionHarness.debrisExposure(missile, 20_000.0, 50.0);
        ShipMathematicsV07ProtectionHarness.DebrisExposure central =
                ShipMathematicsV07ProtectionHarness.debrisExposure(missile, 20_000.0, 200.0);
        ShipMathematicsV07ProtectionHarness.DebrisExposure wide =
                ShipMathematicsV07ProtectionHarness.debrisExposure(missile, 20_000.0, 500.0);

        assertTrue(narrow.shipHitFraction() > central.shipHitFraction());
        assertTrue(central.shipHitFraction() > wide.shipHitFraction());
        assertEquals(0.3766872129155317, narrow.shipHitFraction(), 1.0e-12);
        assertEquals(0.004808902419802274, wide.shipHitFraction(), 1.0e-12);
    }

    @Test
    void compartmentProjectionConservesTheShipIntersectingFraction() {
        ShipMathematicsV07ProtectionHarness.DebrisExposure exposure =
                ShipMathematicsV07ProtectionHarness.debrisExposure(
                        ShipMathematicsV07ProtectionHarness.missileImpact(),
                        20_000.0,
                        200.0);

        assertEquals(exposure.shipHitFraction(), exposure.zoneFractionSum(), 1.0e-15);
        double energySum = exposure.zones().stream()
                .mapToDouble(ShipMathematicsV07ProtectionHarness.ZoneExposure::intersectingEnergyJ)
                .sum();
        assertEquals(exposure.intersectingEnergyJ(), energySum, 1.0e-4);

        ShipMathematicsV07ProtectionHarness.ZoneExposure citadel =
                ShipMathematicsV07ProtectionHarness.findZone(exposure, "CENTRAL_CITADEL");
        ShipMathematicsV07ProtectionHarness.ZoneExposure coolant =
                ShipMathematicsV07ProtectionHarness.findZone(exposure, "PORT_COOLANT");
        assertEquals(8_750_572_477.0, citadel.intersectingEnergyJ(), 2.0);
        assertEquals(18_245_966_922.0, coolant.intersectingEnergyJ(), 2.0);
        assertTrue(coolant.intersectingEnergyJ() > citadel.intersectingEnergyJ());
    }
}

package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV09IntegratedAcceptanceTest {
    @Test
    void allModuleFamiliesConvergeIntoOnePhysicalBudgetContract() {
        ShipMathematicsV09IntegratedHarness.ModuleBudget budget =
                ShipMathematicsV09IntegratedHarness.integrateModules(
                        ShipMathematicsV09IntegratedHarness.integrationDemonstratorModules());

        assertEquals(450_000.0, budget.massKg(), 1.0e-9);
        assertEquals(910.0, budget.volumeM3(), 1.0e-9);
        assertEquals(300_000_000.0, budget.continuousPowerSupplyW(), 1.0e-6);
        assertEquals(106_000_000.0, budget.continuousPowerDemandW(), 1.0e-6);
        assertEquals(194_000_000.0, budget.continuousPowerMarginW(), 1.0e-6);
        assertEquals(146_000_000.0, budget.peakPowerDemandW(), 1.0e-6);
        assertEquals(20_000_000_000.0, budget.storedEnergyCapacityJ(), 1.0e-3);
        assertEquals(142_500_000.0, budget.wasteHeatW(), 1.0e-6);
        assertEquals(180_000_000.0, budget.heatRejectionW(), 1.0e-6);
        assertEquals(37_500_000.0, budget.continuousHeatMarginW(), 1.0e-6);
        assertEquals(600_000_000.0, budget.localThermalCapacityJ(), 1.0e-3);
        assertEquals(30_000_000.0, budget.coolantTransferDemandW(), 1.0e-6);
        assertEquals(24, budget.crewRequired());
    }

    @Test
    void kineticWeaponsNowCarryExplicitPenetratorGeometryInsteadOfEnergyOnly() {
        ShipMathematicsV09IntegratedHarness.ProjectileGeometry medium =
                ShipMathematicsV09IntegratedHarness.mediumKineticGeometry();
        ShipMathematicsV09IntegratedHarness.ProjectileGeometry large =
                ShipMathematicsV09IntegratedHarness.largeKineticGeometry();
        ShipMathematicsV09IntegratedHarness.ProjectileGeometry capital =
                ShipMathematicsV09IntegratedHarness.capitalKineticGeometry();

        assertEquals(0.6701260761764014, medium.lengthM(), 1.0e-12);
        assertEquals(13.402521523528028, medium.finenessRatio(), 1.0e-12);
        assertEquals(1.0051891142646021, large.lengthM(), 1.0e-12);
        assertEquals(10.05189114264602, large.finenessRatio(), 1.0e-12);
        assertEquals(1.6753151904410035, capital.lengthM(), 1.0e-12);
        assertEquals(8.376575952205018, capital.finenessRatio(), 1.0e-12);

        assertTrue(capital.kineticEnergyPerFrontalAreaJPerM2()
                > large.kineticEnergyPerFrontalAreaJPerM2());
        assertTrue(large.kineticEnergyPerFrontalAreaJPerM2()
                > medium.kineticEnergyPerFrontalAreaJPerM2());
    }

    @Test
    void heavyImpactResolutionRequiresCalibratedMaterialGeometrySurface() {
        ShipMathematicsV09IntegratedHarness.HeavyImpactPolicy policy =
                ShipMathematicsV09IntegratedHarness.heavyImpactPolicy(
                        ShipMathematicsV09IntegratedHarness.capitalKineticGeometry());

        assertEquals(
                ShipMathematicsV09IntegratedHarness.HeavyImpactResolutionMode.CALIBRATED_RESPONSE_SURFACE_REQUIRED,
                policy.mode());
        assertTrue(policy.projectileMaterialRequired());
        assertTrue(policy.projectileGeometryRequired());
        assertTrue(policy.targetLayerStackRequired());
        assertTrue(policy.incidenceRequired());
        assertTrue(policy.calibrationBoundsRequired());
    }

    @Test
    void drivePlumeSignatureComesFromJetPowerAndHasExplicitAspectSensitivity() {
        ShipMathematicsV09IntegratedHarness.PlumeSignature corvette =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.CORVETTE_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);
        ShipMathematicsV09IntegratedHarness.PlumeSignature destroyer =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);
        ShipMathematicsV09IntegratedHarness.PlumeSignature battleship =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.BATTLESHIP_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);

        assertEquals(110_000_000_000.0, corvette.minimumJetPowerW(), 1.0e-3);
        assertEquals(660_000_000_000.0, destroyer.minimumJetPowerW(), 1.0e-3);
        assertEquals(6_875_000_000_000.0, battleship.minimumJetPowerW(), 1.0e-3);
        assertEquals(12_308_262_869.364532, corvette.passiveDetectionRangeM(), 2.0);
        assertEquals(30_148_963_649.987473, destroyer.passiveDetectionRangeM(), 2.0);
        assertEquals(97_305_361_768.17854, battleship.passiveDetectionRangeM(), 2.0);

        ShipMathematicsV09IntegratedHarness.PlumeSignature forward =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_FORWARD);
        ShipMathematicsV09IntegratedHarness.PlumeSignature aft =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_AFT);

        assertEquals(destroyer.passiveDetectionRangeM() * 0.5, forward.passiveDetectionRangeM(), 2.0);
        assertEquals(destroyer.passiveDetectionRangeM() * 2.0, aft.passiveDetectionRangeM(), 2.0);
    }

    @Test
    void plumeRadiativeEfficiencyRemainsSensitivityAxisNotHiddenStealthRating() {
        ShipMathematicsV09IntegratedHarness.PlumeSignature low =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_LOW,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);
        ShipMathematicsV09IntegratedHarness.PlumeSignature central =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);
        ShipMathematicsV09IntegratedHarness.PlumeSignature high =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        ShipMathematicsV09IntegratedHarness.MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_HIGH,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);

        assertEquals(9_533_939_422.75839, low.passiveDetectionRangeM(), 2.0);
        assertEquals(30_148_963_649.987473, central.passiveDetectionRangeM(), 2.0);
        assertEquals(95_339_394_227.5839, high.passiveDetectionRangeM(), 2.0);
        assertTrue(low.passiveDetectionRangeM() < central.passiveDetectionRangeM());
        assertTrue(central.passiveDetectionRangeM() < high.passiveDetectionRangeM());
    }

    @Test
    void futureWorldScaleUsesAccelerationAndFiniteDeltaVInsteadOfArbitraryDistanceUnits() {
        List<ShipMathematicsV09IntegratedHarness.TravelEnvelope> freighter =
                ShipMathematicsV09IntegratedHarness.bulkFreighterWorldScaleSweep();
        List<ShipMathematicsV09IntegratedHarness.TravelEnvelope> destroyer =
                ShipMathematicsV09IntegratedHarness.escortDestroyerWorldScaleSweep();

        assertEquals(4, freighter.size());
        assertEquals(4, destroyer.size());

        assertEquals(21_832.69719175042, freighter.get(0).totalTimeS(), 1.0e-8);
        assertEquals(69_041.05059069325, freighter.get(1).totalTimeS(), 1.0e-8);
        assertEquals(221_696.18224171107, freighter.get(2).totalTimeS(), 1.0e-8);
        assertEquals(1_392_595.39755981, freighter.get(3).totalTimeS(), 1.0e-8);
        assertEquals(ShipMathematicsV09IntegratedHarness.TravelRegime.ACCEL_COAST_BRAKE,
                freighter.get(2).regime());

        assertEquals(16_302.816265351768, destroyer.get(0).totalTimeS(), 1.0e-8);
        assertEquals(51_554.03167375159, destroyer.get(1).totalTimeS(), 1.0e-8);
        assertEquals(163_028.16265351768, destroyer.get(2).totalTimeS(), 1.0e-8);
        assertEquals(647_849.3709360299, destroyer.get(3).totalTimeS(), 1.0e-8);
        assertEquals(ShipMathematicsV09IntegratedHarness.TravelRegime.ACCEL_BRAKE,
                destroyer.get(2).regime());
        assertEquals(ShipMathematicsV09IntegratedHarness.TravelRegime.ACCEL_COAST_BRAKE,
                destroyer.get(3).regime());
    }
}

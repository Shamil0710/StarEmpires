package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV10DesignBaselineAcceptanceTest {
    @Test
    void v10ClosesEveryArchitecturalDomainRequiredBeforeStage175() {
        List<ShipMathematicsV10DesignBaselineHarness.ClosureDomain> domains =
                ShipMathematicsV10DesignBaselineHarness.closureDomains();

        assertEquals(17, domains.size());
        assertTrue(ShipMathematicsV10DesignBaselineHarness.architectureClosed());
        assertTrue(domains.stream().allMatch(
                ShipMathematicsV10DesignBaselineHarness.ClosureDomain::architectureClosed));
    }

    @Test
    void commonModuleContractCoversEveryDeclaredModuleFamilyAndIntegratedFitBalances() {
        Set<ShipMathematicsV10DesignBaselineHarness.ModuleFamily> families =
                ShipMathematicsV10DesignBaselineHarness.requiredModuleFamilies();
        assertEquals(15, families.size());
        assertTrue(families.contains(ShipMathematicsV10DesignBaselineHarness.ModuleFamily.SHIELD_FIELD));
        assertTrue(families.contains(ShipMathematicsV10DesignBaselineHarness.ModuleFamily.FTL_JUMP));
        assertTrue(families.contains(
                ShipMathematicsV10DesignBaselineHarness.ModuleFamily.MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE));

        ShipMathematicsV10DesignBaselineHarness.FitValidation validation =
                ShipMathematicsV10DesignBaselineHarness.validateIntegratedDestroyerFit();
        ShipMathematicsV10DesignBaselineHarness.IntegratedFitBudget budget = validation.budget();

        assertTrue(validation.valid());
        assertEquals(4_330_000.0, budget.massKg(), 1.0e-9);
        assertEquals(18_900.0, budget.volumeM3(), 1.0e-9);
        assertEquals(1_000_000_000.0, budget.continuousPowerSupplyW(), 1.0e-6);
        assertEquals(297_000_000.0, budget.continuousPowerDemandW(), 1.0e-6);
        assertEquals(703_000_000.0, budget.continuousPowerMarginW(), 1.0e-6);
        assertEquals(197_000_000.0, budget.wasteHeatW(), 1.0e-6);
        assertEquals(300_000_000.0, budget.heatRejectionW(), 1.0e-6);
        assertEquals(103_000_000.0, budget.continuousHeatMarginW(), 1.0e-6);
        assertEquals(216, budget.crewRequired());
    }

    @Test
    void representativeMilitaryAndCivilianShipsRecomposeMassAndUseSameMovementEquations() {
        List<ShipMathematicsV10DesignBaselineHarness.ReferenceShip> ships =
                ShipMathematicsV10DesignBaselineHarness.representativeShips();
        assertEquals(5, ships.size());

        double[] expectedAcceleration = {
                1.02803738317757,
                0.601997537282802,
                0.2519399375188955,
                0.08391608391608392,
                0.14705882352941177
        };
        double[] expectedDeltaV = {
                32_902.34126082223,
                38_454.71005152617,
                45_642.912661282484,
                15_372.800463539408,
                19_415.60144409574
        };

        for (int i = 0; i < ships.size(); i++) {
            ShipMathematicsV10DesignBaselineHarness.ReferenceShip ship = ships.get(i);
            ShipMathematicsV10DesignBaselineHarness.ShipDerived derived =
                    ShipMathematicsV10DesignBaselineHarness.derive(ship);
            assertEquals(ship.departureMassKg(), derived.recomposedDepartureMassKg(), 1.0e-6);
            assertEquals(expectedAcceleration[i], derived.maxAccelerationMps2(), 1.0e-14);
            assertEquals(expectedDeltaV[i], derived.nominalDeltaVMps(), 1.0e-9);
        }

        ShipMathematicsV10DesignBaselineHarness.ShipDerived freighter =
                ShipMathematicsV10DesignBaselineHarness.derive(ships.get(3));
        ShipMathematicsV10DesignBaselineHarness.ShipDerived destroyer =
                ShipMathematicsV10DesignBaselineHarness.derive(ships.get(1));
        assertTrue(freighter.maxAccelerationMps2() < destroyer.maxAccelerationMps2());
        assertTrue(freighter.nominalDeltaVMps() < destroyer.nominalDeltaVMps());
    }

    @Test
    void materialCatalogIsExplicitAndHeavyImpactEvaluationIsBoundedByCalibrationDomain() {
        List<ShipMathematicsV10DesignBaselineHarness.MaterialSeed> materials =
                ShipMathematicsV10DesignBaselineHarness.baselineMaterials();
        Set<String> ids = new HashSet<>();
        for (ShipMathematicsV10DesignBaselineHarness.MaterialSeed material : materials) {
            assertTrue(material.densityKgPerM3() > 0.0);
            assertTrue(ids.add(material.id()));
        }
        assertEquals(5, materials.size());

        ShipMathematicsV10DesignBaselineHarness.ImpactSurfaceEvaluation central =
                ShipMathematicsV10DesignBaselineHarness.evaluateHeavyImpactDomain(
                        new ShipMathematicsV10DesignBaselineHarness.ImpactQuery(
                                "material.high_density_penetrator_v1",
                                15_000.0,
                                0.05,
                                0.67,
                                0.0));
        assertTrue(central.insideCalibrationDomain());
        assertEquals(0.525, central.residualMassFraction(), 1.0e-12);
        assertEquals(0.575, central.residualVelocityFraction(), 1.0e-12);
        assertEquals(0.826421875, central.depositedEnergyFraction(), 1.0e-12);

        ShipMathematicsV10DesignBaselineHarness.ImpactSurfaceEvaluation outside =
                ShipMathematicsV10DesignBaselineHarness.evaluateHeavyImpactDomain(
                        new ShipMathematicsV10DesignBaselineHarness.ImpactQuery(
                                "material.high_density_penetrator_v1",
                                30_000.0,
                                0.20,
                                1.67,
                                0.0));
        assertFalse(outside.insideCalibrationDomain());
    }

    @Test
    void shieldIsEnergyPowerAndHeatLimitedInsteadOfGenericHitPoints() {
        ShipMathematicsV10DesignBaselineHarness.ShieldResolution first =
                ShipMathematicsV10DesignBaselineHarness.resolveShieldImpact(
                        ShipMathematicsV10DesignBaselineHarness.SHIELD_FIELD_CAPACITY_J,
                        450_000_000_000.0,
                        0.1);
        assertEquals(450_000_000_000.0, first.deflectedEnergyJ(), 1.0e-3);
        assertEquals(0.0, first.residualIncidentEnergyJ(), 1.0e-3);
        assertEquals(112_500_000_000.0, first.fieldEnergySpentJ(), 1.0e-3);
        assertEquals(22_500_000_000.0, first.wasteHeatJ(), 1.0e-3);
        assertEquals(7_500_000_000.0, first.remainingFieldEnergyJ(), 1.0e-3);
        assertFalse(first.collapsed());

        ShipMathematicsV10DesignBaselineHarness.ShieldResolution second =
                ShipMathematicsV10DesignBaselineHarness.resolveShieldImpact(
                        first.remainingFieldEnergyJ(),
                        450_000_000_000.0,
                        0.1);
        assertEquals(30_000_000_000.0, second.deflectedEnergyJ(), 1.0e-3);
        assertEquals(420_000_000_000.0, second.residualIncidentEnergyJ(), 1.0e-3);
        assertTrue(second.collapsed());

        ShipMathematicsV10DesignBaselineHarness.ShieldRecharge recharge =
                ShipMathematicsV10DesignBaselineHarness.rechargeShield(first.remainingFieldEnergyJ());
        assertEquals(220.58823529411765, recharge.rechargeTimeS(), 1.0e-12);
        assertEquals(19_852_941_176.470592, recharge.wasteHeatJ(), 1.0e-3);
        assertEquals(20.0, recharge.restartDelayS(), 1.0e-12);
    }

    @Test
    void jumpDriveUsesTranslatedMassEnergyAndExplicitEdgeTimeInsteadOfTeleportRating() {
        ShipMathematicsV10DesignBaselineHarness.JumpPlan destroyer =
                ShipMathematicsV10DesignBaselineHarness.planReferenceJump(21_927_000.0, 30.0);
        assertTrue(destroyer.massCompatible());
        assertEquals(548_175_000_000.0, destroyer.requiredTranslationEnergyJ(), 1.0e-3);
        assertEquals(137.04375, destroyer.spoolTimeS(), 1.0e-12);
        assertEquals(30.0, destroyer.transitTimeS(), 1.0e-12);
        assertEquals(90.0, destroyer.cooldownS(), 1.0e-12);

        ShipMathematicsV10DesignBaselineHarness.JumpPlan battleship =
                ShipMathematicsV10DesignBaselineHarness.planReferenceJump(545_765_000.0, 30.0);
        assertFalse(battleship.massCompatible());
    }

    @Test
    void worldSensorCombatAndLogisticsScalesShareOnePhysicalCoordinateMeaning() {
        ShipMathematicsV10DesignBaselineHarness.ScaleHierarchy scale =
                ShipMathematicsV10DesignBaselineHarness.scaleHierarchy();

        assertEquals(3_000_000.0, scale.representativeHeavyWeaponEnvelopeM(), 1.0e-9);
        assertEquals(100_000_000.0, scale.localLogisticsLegM(), 1.0e-9);
        assertEquals(30_148_963_649.987473, scale.destroyerPlumeDetectionRangeM(), 2.0);
        assertEquals(69_041.05059069325, scale.loadedFreighterTravelTimeS(), 1.0e-8);
        assertTrue(scale.representativeHeavyWeaponEnvelopeM() < scale.localLogisticsLegM());
        assertTrue(scale.localLogisticsLegM() < scale.destroyerPlumeDetectionRangeM());
    }
}
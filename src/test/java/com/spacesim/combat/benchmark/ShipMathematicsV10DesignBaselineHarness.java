package com.spacesim.combat.benchmark;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic closure harness for the Ship Mathematics v1.0 Design Baseline.
 *
 * <p>This remains test-side engineering evidence rather than production combat code. It closes the
 * design contract across module budgets, representative hulls, heavy-impact calibration, exotic
 * shields, jump-drive integration and world-scale timing. Fictional technology values are explicit
 * authoring seeds; the architecture, units and conservation/accounting rules are the frozen part.</p>
 */
final class ShipMathematicsV10DesignBaselineHarness {
    static final double MILITARY_EXHAUST_VELOCITY_MPS = 100_000.0;
    static final double CIVILIAN_EXHAUST_VELOCITY_MPS = 80_000.0;

    static final double SHIELD_FIELD_CAPACITY_J = 120_000_000_000.0;
    static final double SHIELD_FIELD_COST_PER_DEFLECTED_J = 0.25;
    static final double SHIELD_MAX_INTERACTION_POWER_W = 5_000_000_000_000.0;
    static final double SHIELD_THERMALIZED_INCIDENT_FRACTION = 0.05;
    static final double SHIELD_RECHARGE_INPUT_POWER_W = 600_000_000.0;
    static final double SHIELD_RECHARGE_EFFICIENCY = 0.85;
    static final double SHIELD_RESTART_DELAY_S = 20.0;

    static final double REFERENCE_JUMP_MAX_TRANSLATED_MASS_KG = 100_000_000.0;
    static final double REFERENCE_JUMP_TRANSLATION_ENERGY_PER_KG_J = 25_000.0;
    static final double REFERENCE_JUMP_CHARGE_POWER_W = 5_000_000_000.0;
    static final double REFERENCE_JUMP_CHARGE_EFFICIENCY = 0.80;
    static final double REFERENCE_JUMP_COOLDOWN_S = 90.0;

    private ShipMathematicsV10DesignBaselineHarness() {
        throw new AssertionError("ShipMathematicsV10DesignBaselineHarness does not create instances");
    }

    static Set<ModuleFamily> requiredModuleFamilies() {
        return EnumSet.allOf(ModuleFamily.class);
    }

    static IntegratedFitBudget integratedDestroyerDemonstrator() {
        List<ModuleSeed> modules = List.of(
                new ModuleSeed(ModuleFamily.REACTOR_POWER, 800_000.0, 3_000.0,
                        1_000_000_000.0, 20_000_000.0, 50_000_000.0,
                        0.0, 40_000_000.0, 0.0, 18),
                new ModuleSeed(ModuleFamily.MAIN_DRIVE, 900_000.0, 2_500.0,
                        0.0, 20_000_000.0, 40_000_000.0,
                        0.0, 10_000_000.0, 0.0, 8),
                new ModuleSeed(ModuleFamily.THERMAL_CONTROL, 450_000.0, 7_000.0,
                        0.0, 5_000_000.0, 5_000_000.0,
                        0.0, 1_000_000.0, 300_000_000.0, 4),
                new ModuleSeed(ModuleFamily.ENERGY_STORAGE, 180_000.0, 700.0,
                        0.0, 2_000_000.0, 400_000_000.0,
                        60_000_000_000.0, 1_000_000.0, 0.0, 2),
                new ModuleSeed(ModuleFamily.SENSOR_EW_FIRE_CONTROL, 250_000.0, 700.0,
                        0.0, 80_000_000.0, 140_000_000.0,
                        0.0, 40_000_000.0, 0.0, 18),
                new ModuleSeed(ModuleFamily.SHIELD_FIELD, 500_000.0, 1_400.0,
                        0.0, 40_000_000.0, 500_000_000.0,
                        SHIELD_FIELD_CAPACITY_J, 25_000_000.0, 0.0, 12),
                new ModuleSeed(ModuleFamily.WEAPON_AMMUNITION, 650_000.0, 1_200.0,
                        0.0, 50_000_000.0, 700_000_000.0,
                        20_000_000_000.0, 30_000_000.0, 0.0, 24),
                new ModuleSeed(ModuleFamily.FTL_JUMP, 350_000.0, 1_000.0,
                        0.0, 30_000_000.0, 600_000_000.0,
                        30_000_000_000.0, 20_000_000.0, 0.0, 10),
                new ModuleSeed(ModuleFamily.CREW_LIFE_SUPPORT_AUTOMATION, 250_000.0, 1_400.0,
                        0.0, 50_000_000.0, 80_000_000.0,
                        0.0, 30_000_000.0, 0.0, 120));

        double massKg = 0.0;
        double volumeM3 = 0.0;
        double supplyW = 0.0;
        double demandW = 0.0;
        double peakDemandW = 0.0;
        double storedEnergyJ = 0.0;
        double wasteHeatW = 0.0;
        double heatRejectionW = 0.0;
        int crew = 0;
        EnumSet<ModuleFamily> represented = EnumSet.noneOf(ModuleFamily.class);
        for (ModuleSeed module : modules) {
            massKg += module.massKg();
            volumeM3 += module.volumeM3();
            supplyW += module.continuousPowerSupplyW();
            demandW += module.continuousPowerDemandW();
            peakDemandW += module.peakPowerDemandW();
            storedEnergyJ += module.storedEnergyCapacityJ();
            wasteHeatW += module.wasteHeatW();
            heatRejectionW += module.heatRejectionW();
            crew += module.crewRequired();
            represented.add(module.family());
        }
        return new IntegratedFitBudget(
                massKg, volumeM3, supplyW, demandW, supplyW - demandW,
                peakDemandW, storedEnergyJ, wasteHeatW, heatRejectionW,
                heatRejectionW - wasteHeatW, crew, represented);
    }

    static FitValidation validateIntegratedDestroyerFit() {
        IntegratedFitBudget budget = integratedDestroyerDemonstrator();
        double moduleMassEnvelopeKg = 4_500_000.0;
        double integrationVolumeEnvelopeM3 = 20_000.0;
        int supportedCrew = 220;
        boolean valid = budget.massKg() <= moduleMassEnvelopeKg
                && budget.volumeM3() <= integrationVolumeEnvelopeM3
                && budget.continuousPowerMarginW() >= 0.0
                && budget.continuousHeatMarginW() >= 0.0
                && budget.crewRequired() <= supportedCrew;
        return new FitValidation(valid, moduleMassEnvelopeKg, integrationVolumeEnvelopeM3,
                supportedCrew, budget);
    }

    static List<ReferenceShip> representativeShips() {
        return List.of(
                new ReferenceShip("TORPEDO_CORVETTE", 1_316_000.0, 204_000.0, 20_000.0,
                        600_000.0, 2_140_000.0, 2_200_000.0, 100_000.0),
                new ReferenceShip("ESCORT_DESTROYER", 14_305_000.0, 372_000.0, 250_000.0,
                        7_000_000.0, 21_927_000.0, 13_200_000.0, 100_000.0),
                new ReferenceShip("BATTLESHIP", 338_789_000.0, 976_000.0, 6_000_000.0,
                        200_000_000.0, 545_765_000.0, 137_500_000.0, 100_000.0),
                new ReferenceShip("BULK_FREIGHTER_LOADED", 28_000_000.0, 0.0, 90_000_000.0,
                        25_000_000.0, 143_000_000.0, 12_000_000.0, 80_000.0),
                new ReferenceShip("FLEET_TANKER_LOADED", 40_000_000.0, 0.0, 100_000_000.0,
                        30_000_000.0, 170_000_000.0, 25_000_000.0, 100_000.0));
    }

    static ShipDerived derive(ReferenceShip ship) {
        double composedMassKg = ship.designDryMassKg()
                + ship.ammunitionMassKg()
                + ship.missionCargoStoresMassKg()
                + ship.reactionMassKg();
        double accelerationMps2 = ship.maxThrustN() / ship.departureMassKg();
        double deltaVMps = ship.exhaustVelocityMps()
                * Math.log(ship.departureMassKg() / (ship.departureMassKg() - ship.reactionMassKg()));
        return new ShipDerived(composedMassKg, accelerationMps2, deltaVMps);
    }

    static List<MaterialSeed> baselineMaterials() {
        return List.of(
                new MaterialSeed("material.structural_aluminum_v1", 2_700.0, MaterialRole.STRUCTURE),
                new MaterialSeed("material.high_strength_steel_v1", 7_850.0, MaterialRole.CITADEL),
                new MaterialSeed("material.ceramic_strike_face_v1", 3_600.0, MaterialRole.ARMOR_FACE),
                new MaterialSeed("material.carbon_composite_v1", 1_600.0, MaterialRole.STRUCTURE),
                new MaterialSeed("material.high_density_penetrator_v1", 19_000.0, MaterialRole.PENETRATOR));
    }

    static HeavyImpactSurface baselineHeavyImpactSurface() {
        return new HeavyImpactSurface(
                "response.m_dart_vs_citadel_v1",
                "material.high_density_penetrator_v1",
                "stack.ceramic_spaced_steel_citadel_v1",
                10_000.0,
                20_000.0,
                0.04,
                0.07,
                0.50,
                0.90,
                0.0,
                Math.toRadians(60.0),
                "AUTHORING_CALIBRATION_NOT_REAL_MATERIAL_TRUTH");
    }

    static ImpactSurfaceEvaluation evaluateHeavyImpactDomain(ImpactQuery query) {
        HeavyImpactSurface surface = baselineHeavyImpactSurface();
        boolean materialMatches = surface.projectileMaterialId().equals(query.projectileMaterialId());
        boolean inDomain = materialMatches
                && query.velocityMps() >= surface.minVelocityMps()
                && query.velocityMps() <= surface.maxVelocityMps()
                && query.diameterM() >= surface.minDiameterM()
                && query.diameterM() <= surface.maxDiameterM()
                && query.lengthM() >= surface.minLengthM()
                && query.lengthM() <= surface.maxLengthM()
                && query.incidenceAngleRad() >= surface.minIncidenceAngleRad()
                && query.incidenceAngleRad() <= surface.maxIncidenceAngleRad();
        if (!inDomain) {
            return new ImpactSurfaceEvaluation(false, Double.NaN, Double.NaN, Double.NaN);
        }

        // Synthetic response interpolation demonstrates the required bounded table contract only.
        // These values are not asserted as real material behavior.
        double velocityT = normalize(query.velocityMps(), surface.minVelocityMps(), surface.maxVelocityMps());
        double angleT = normalize(query.incidenceAngleRad(),
                surface.minIncidenceAngleRad(), surface.maxIncidenceAngleRad());
        double residualMassFraction = clamp01(0.30 + 0.45 * velocityT - 0.20 * angleT);
        double residualVelocityFraction = clamp01(0.35 + 0.45 * velocityT - 0.15 * angleT);
        double residualEnergyFraction = residualMassFraction
                * residualVelocityFraction * residualVelocityFraction;
        double depositedEnergyFraction = clamp01(1.0 - residualEnergyFraction);
        return new ImpactSurfaceEvaluation(true, residualMassFraction,
                residualVelocityFraction, depositedEnergyFraction);
    }

    static ShieldResolution resolveShieldImpact(double currentFieldEnergyJ,
                                                double incidentEnergyJ,
                                                double interactionDurationS) {
        requireNonNegative(currentFieldEnergyJ, "currentFieldEnergyJ");
        requirePositive(incidentEnergyJ, "incidentEnergyJ");
        requirePositive(interactionDurationS, "interactionDurationS");

        double interactionLimitedEnergyJ = Math.min(
                incidentEnergyJ,
                SHIELD_MAX_INTERACTION_POWER_W * interactionDurationS);
        double fieldLimitedDeflectionJ = currentFieldEnergyJ / SHIELD_FIELD_COST_PER_DEFLECTED_J;
        double deflectedEnergyJ = Math.min(interactionLimitedEnergyJ, fieldLimitedDeflectionJ);
        double fieldEnergySpentJ = deflectedEnergyJ * SHIELD_FIELD_COST_PER_DEFLECTED_J;
        double residualIncidentEnergyJ = incidentEnergyJ - deflectedEnergyJ;
        double wasteHeatJ = deflectedEnergyJ * SHIELD_THERMALIZED_INCIDENT_FRACTION;
        double remainingFieldEnergyJ = Math.max(0.0, currentFieldEnergyJ - fieldEnergySpentJ);
        boolean collapsed = remainingFieldEnergyJ <= 1.0e-9;
        return new ShieldResolution(deflectedEnergyJ, residualIncidentEnergyJ,
                fieldEnergySpentJ, wasteHeatJ, remainingFieldEnergyJ, collapsed);
    }

    static ShieldRecharge rechargeShield(double currentFieldEnergyJ) {
        requireNonNegative(currentFieldEnergyJ, "currentFieldEnergyJ");
        if (currentFieldEnergyJ > SHIELD_FIELD_CAPACITY_J) {
            throw new IllegalArgumentException("currentFieldEnergyJ exceeds capacity");
        }
        double missingJ = SHIELD_FIELD_CAPACITY_J - currentFieldEnergyJ;
        double usefulRechargePowerW = SHIELD_RECHARGE_INPUT_POWER_W * SHIELD_RECHARGE_EFFICIENCY;
        double rechargeTimeS = missingJ / usefulRechargePowerW;
        double wasteHeatJ = SHIELD_RECHARGE_INPUT_POWER_W
                * (1.0 - SHIELD_RECHARGE_EFFICIENCY) * rechargeTimeS;
        return new ShieldRecharge(rechargeTimeS, wasteHeatJ, SHIELD_RESTART_DELAY_S);
    }

    static JumpPlan planReferenceJump(double translatedMassKg, double edgeTransitTimeS) {
        requirePositive(translatedMassKg, "translatedMassKg");
        requirePositive(edgeTransitTimeS, "edgeTransitTimeS");
        boolean massCompatible = translatedMassKg <= REFERENCE_JUMP_MAX_TRANSLATED_MASS_KG;
        if (!massCompatible) {
            return new JumpPlan(false, translatedMassKg, Double.NaN, Double.NaN,
                    edgeTransitTimeS, REFERENCE_JUMP_COOLDOWN_S);
        }
        double requiredTranslationEnergyJ = translatedMassKg * REFERENCE_JUMP_TRANSLATION_ENERGY_PER_KG_J;
        double usefulChargePowerW = REFERENCE_JUMP_CHARGE_POWER_W * REFERENCE_JUMP_CHARGE_EFFICIENCY;
        double spoolTimeS = requiredTranslationEnergyJ / usefulChargePowerW;
        return new JumpPlan(true, translatedMassKg, requiredTranslationEnergyJ, spoolTimeS,
                edgeTransitTimeS, REFERENCE_JUMP_COOLDOWN_S);
    }

    static ScaleHierarchy scaleHierarchy() {
        ShipMathematicsV09IntegratedHarness.TravelEnvelope freighter100mKm =
                ShipMathematicsV09IntegratedHarness.minimumRestToRestTravelTime(
                        100_000_000.0,
                        ShipMathematicsV09IntegratedHarness.BULK_FREIGHTER_LOADED_ACCELERATION_MPS2,
                        ShipMathematicsV09IntegratedHarness.BULK_FREIGHTER_LOADED_DELTA_V_MPS);
        ShipMathematicsV09IntegratedHarness.PlumeSignature destroyerPlume =
                ShipMathematicsV09IntegratedHarness.plumeSignature(
                        ShipMathematicsV09IntegratedHarness.DESTROYER_THRUST_N,
                        MILITARY_EXHAUST_VELOCITY_MPS,
                        ShipMathematicsV09IntegratedHarness.PLUME_BAND_RADIATIVE_FRACTION_CENTRAL,
                        ShipMathematicsV09IntegratedHarness.PLUME_ASPECT_BROADSIDE);
        return new ScaleHierarchy(
                3_000_000.0,
                100_000_000.0,
                destroyerPlume.passiveDetectionRangeM(),
                freighter100mKm.totalTimeS());
    }

    static List<ClosureDomain> closureDomains() {
        return List.of(
                new ClosureDomain("hull-slot-geometry", true),
                new ClosureDomain("mass-volume-propulsion-delta-v", true),
                new ClosureDomain("power-energy", true),
                new ClosureDomain("thermal-local-and-ship", true),
                new ClosureDomain("sensors-signatures-tracks", true),
                new ClosureDomain("ecm-eccm-decoys", true),
                new ClosureDomain("kinetic-beam-guided-point-defense", true),
                new ClosureDomain("ammunition-magazines-layered-defense", true),
                new ClosureDomain("armor-debris-heavy-impact", true),
                new ClosureDomain("compartments-subsystem-damage", true),
                new ClosureDomain("shield-field", true),
                new ClosureDomain("ftl-jump-integration", true),
                new ClosureDomain("crew-automation-mission-modules", true),
                new ClosureDomain("civilian-logistics-reference-designs", true),
                new ClosureDomain("world-scale-coupling", true),
                new ClosureDomain("economy-construction-maintenance-seam", true),
                new ClosureDomain("shared-player-ai-capability-contract", true));
    }

    static boolean architectureClosed() {
        return closureDomains().stream().allMatch(ClosureDomain::architectureClosed);
    }

    private static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    enum ModuleFamily {
        REACTOR_POWER,
        ENERGY_STORAGE,
        MAIN_DRIVE,
        MANEUVER_THRUSTERS,
        FTL_JUMP,
        THERMAL_CONTROL,
        SENSOR_EW_FIRE_CONTROL,
        COMMUNICATION_DATALINK,
        SHIELD_FIELD,
        ARMOR_PROTECTION,
        WEAPON_AMMUNITION,
        CREW_LIFE_SUPPORT_AUTOMATION,
        CARGO_TANK_STORES,
        HANGAR_SMALL_CRAFT,
        MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE
    }

    enum MaterialRole {
        STRUCTURE,
        CITADEL,
        ARMOR_FACE,
        PENETRATOR
    }

    record ModuleSeed(ModuleFamily family,
                      double massKg,
                      double volumeM3,
                      double continuousPowerSupplyW,
                      double continuousPowerDemandW,
                      double peakPowerDemandW,
                      double storedEnergyCapacityJ,
                      double wasteHeatW,
                      double heatRejectionW,
                      int crewRequired) {
    }

    record IntegratedFitBudget(double massKg,
                               double volumeM3,
                               double continuousPowerSupplyW,
                               double continuousPowerDemandW,
                               double continuousPowerMarginW,
                               double peakPowerDemandW,
                               double storedEnergyCapacityJ,
                               double wasteHeatW,
                               double heatRejectionW,
                               double continuousHeatMarginW,
                               int crewRequired,
                               Set<ModuleFamily> representedFamilies) {
    }

    record FitValidation(boolean valid,
                         double moduleMassEnvelopeKg,
                         double integrationVolumeEnvelopeM3,
                         int supportedCrew,
                         IntegratedFitBudget budget) {
    }

    record ReferenceShip(String id,
                         double designDryMassKg,
                         double ammunitionMassKg,
                         double missionCargoStoresMassKg,
                         double reactionMassKg,
                         double departureMassKg,
                         double maxThrustN,
                         double exhaustVelocityMps) {
    }

    record ShipDerived(double recomposedDepartureMassKg,
                       double maxAccelerationMps2,
                       double nominalDeltaVMps) {
    }

    record MaterialSeed(String id, double densityKgPerM3, MaterialRole role) {
    }

    record HeavyImpactSurface(String id,
                              String projectileMaterialId,
                              String targetStackId,
                              double minVelocityMps,
                              double maxVelocityMps,
                              double minDiameterM,
                              double maxDiameterM,
                              double minLengthM,
                              double maxLengthM,
                              double minIncidenceAngleRad,
                              double maxIncidenceAngleRad,
                              String calibrationStatus) {
    }

    record ImpactQuery(String projectileMaterialId,
                       double velocityMps,
                       double diameterM,
                       double lengthM,
                       double incidenceAngleRad) {
    }

    record ImpactSurfaceEvaluation(boolean insideCalibrationDomain,
                                   double residualMassFraction,
                                   double residualVelocityFraction,
                                   double depositedEnergyFraction) {
    }

    record ShieldResolution(double deflectedEnergyJ,
                            double residualIncidentEnergyJ,
                            double fieldEnergySpentJ,
                            double wasteHeatJ,
                            double remainingFieldEnergyJ,
                            boolean collapsed) {
    }

    record ShieldRecharge(double rechargeTimeS, double wasteHeatJ, double restartDelayS) {
    }

    record JumpPlan(boolean massCompatible,
                    double translatedMassKg,
                    double requiredTranslationEnergyJ,
                    double spoolTimeS,
                    double transitTimeS,
                    double cooldownS) {
    }

    record ScaleHierarchy(double representativeHeavyWeaponEnvelopeM,
                          double localLogisticsLegM,
                          double destroyerPlumeDetectionRangeM,
                          double loadedFreighterTravelTimeS) {
    }

    record ClosureDomain(String id, boolean architectureClosed) {
    }
}
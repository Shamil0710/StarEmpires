package com.spacesim.combat.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Ship Mathematics v0.7 protection/debris engineering harness.
 *
 * <p>This harness intentionally refuses to extrapolate MMOD ballistic-limit equations to intact
 * ship weapons. It keeps raw SI impact packets, models stand-off fragmentation geometrically, and
 * projects a Gaussian debris/momentum cloud onto a reference battleship cross-section and a small
 * compartment map. Material penetration remains a separately calibrated response layer.</p>
 */
final class ShipMathematicsV07ProtectionHarness {
    static final double M_MISSILE_MASS_KG = 12_000.0;
    static final double M_MISSILE_SPEED_MPS = 18_000.0;
    static final double XL_KINETIC_MASS_KG = 1_000.0;
    static final double XL_KINETIC_SPEED_MPS = 30_000.0;
    static final double M_COILGUN_PROJECTILE_MASS_KG = 25.0;
    static final double M_COILGUN_SPEED_MPS = 15_000.0;

    /** Largest-energy NASA shield reference deliberately used only as a guardrail, not a BLE. */
    static final double NASA_LARGE_SHIELD_TEST_MASS_KG = 0.598;
    static final double NASA_LARGE_SHIELD_TEST_SPEED_MPS = 6_905.0;
    static final double NASA_LARGE_SHIELD_TEST_ENERGY_J = 15_000_000.0;

    static final double BATTLESHIP_PROJECTED_WIDTH_M = 110.0;
    static final double BATTLESHIP_PROJECTED_HEIGHT_M = 85.0;

    static final double[] STANDOFF_DISTANCES_M = {10_000.0, 20_000.0, 50_000.0, 100_000.0};
    static final double[] LATERAL_DISPERSION_SIGMA_MPS = {50.0, 200.0, 500.0};

    private static final double SQRT_TWO = Math.sqrt(2.0);

    private static final List<ProjectedZone> BATTLESHIP_ZONES = List.of(
            new ProjectedZone("PORT_COOLANT", -55.0, -20.0, -42.5, 42.5),
            new ProjectedZone("STARBOARD_POWER", 20.0, 55.0, -42.5, 42.5),
            new ProjectedZone("CENTRAL_CITADEL", -20.0, 20.0, -17.5, 17.5),
            new ProjectedZone("DORSAL_WEAPONS", -20.0, 20.0, 17.5, 42.5),
            new ProjectedZone("VENTRAL_SERVICE", -20.0, 20.0, -42.5, -17.5));

    private ShipMathematicsV07ProtectionHarness() {
        throw new AssertionError("ShipMathematicsV07ProtectionHarness does not create instances");
    }

    static ImpactPacket missileImpact() {
        return impact(M_MISSILE_MASS_KG, M_MISSILE_SPEED_MPS);
    }

    static ImpactPacket capitalKineticImpact() {
        return impact(XL_KINETIC_MASS_KG, XL_KINETIC_SPEED_MPS);
    }

    static ImpactPacket mediumCoilgunImpact() {
        return impact(M_COILGUN_PROJECTILE_MASS_KG, M_COILGUN_SPEED_MPS);
    }

    static ImpactPacket nasaLargeShieldReference() {
        return impact(NASA_LARGE_SHIELD_TEST_MASS_KG, NASA_LARGE_SHIELD_TEST_SPEED_MPS);
    }

    static ImpactPacket impact(double massKg, double speedMps) {
        if (!(massKg > 0.0) || !(speedMps > 0.0)) {
            throw new IllegalArgumentException("mass and speed must be positive");
        }
        return new ImpactPacket(
                massKg,
                speedMps,
                0.5 * massKg * speedMps * speedMps,
                massKg * speedMps);
    }

    static CalibrationGuardrail calibrationGuardrail(ImpactPacket packet) {
        double energyRatio = packet.kineticEnergyJ / NASA_LARGE_SHIELD_TEST_ENERGY_J;
        double massRatio = packet.massKg / NASA_LARGE_SHIELD_TEST_MASS_KG;
        boolean withinReferenceScale = packet.massKg <= NASA_LARGE_SHIELD_TEST_MASS_KG
                && packet.kineticEnergyJ <= NASA_LARGE_SHIELD_TEST_ENERGY_J;
        return new CalibrationGuardrail(
                withinReferenceScale ? CalibrationStatus.REFERENCE_SCALE_ONLY : CalibrationStatus.EXTRAPOLATION_FORBIDDEN,
                energyRatio,
                massRatio);
    }

    static double cloudSigmaM(double standoffM, double lateralSigmaMps, double axialSpeedMps) {
        if (!(standoffM > 0.0) || !(lateralSigmaMps > 0.0) || !(axialSpeedMps > 0.0)) {
            throw new IllegalArgumentException("cloud inputs must be positive");
        }
        return standoffM * lateralSigmaMps / axialSpeedMps;
    }

    static DebrisExposure debrisExposure(
            ImpactPacket source,
            double standoffM,
            double lateralSigmaMps) {
        double sigmaM = cloudSigmaM(standoffM, lateralSigmaMps, source.speedMps);
        double hitFraction = rectangleGaussianFraction(
                -BATTLESHIP_PROJECTED_WIDTH_M / 2.0,
                BATTLESHIP_PROJECTED_WIDTH_M / 2.0,
                -BATTLESHIP_PROJECTED_HEIGHT_M / 2.0,
                BATTLESHIP_PROJECTED_HEIGHT_M / 2.0,
                sigmaM);

        List<ZoneExposure> zones = new ArrayList<>(BATTLESHIP_ZONES.size());
        double zoneFractionSum = 0.0;
        for (ProjectedZone zone : BATTLESHIP_ZONES) {
            double fraction = rectangleGaussianFraction(
                    zone.minY,
                    zone.maxY,
                    zone.minZ,
                    zone.maxZ,
                    sigmaM);
            zoneFractionSum += fraction;
            zones.add(new ZoneExposure(
                    zone.id,
                    fraction,
                    source.massKg * fraction,
                    source.kineticEnergyJ * fraction,
                    source.momentumNs * fraction));
        }

        return new DebrisExposure(
                standoffM,
                lateralSigmaMps,
                sigmaM,
                hitFraction,
                source.massKg * hitFraction,
                source.kineticEnergyJ * hitFraction,
                source.momentumNs * hitFraction,
                zoneFractionSum,
                List.copyOf(zones));
    }

    static List<DebrisExposure> centralDispersionStandoffSweep() {
        List<DebrisExposure> result = new ArrayList<>(STANDOFF_DISTANCES_M.length);
        for (double standoff : STANDOFF_DISTANCES_M) {
            result.add(debrisExposure(missileImpact(), standoff, 200.0));
        }
        return List.copyOf(result);
    }

    static List<DebrisExposure> fullSensitivitySweep() {
        List<DebrisExposure> result = new ArrayList<>(
                STANDOFF_DISTANCES_M.length * LATERAL_DISPERSION_SIGMA_MPS.length);
        for (double dispersion : LATERAL_DISPERSION_SIGMA_MPS) {
            for (double standoff : STANDOFF_DISTANCES_M) {
                result.add(debrisExposure(missileImpact(), standoff, dispersion));
            }
        }
        return List.copyOf(result);
    }

    static ZoneExposure findZone(DebrisExposure exposure, String zoneId) {
        for (ZoneExposure zone : exposure.zones) {
            if (zone.id.equals(zoneId)) {
                return zone;
            }
        }
        throw new IllegalArgumentException("Unknown zone: " + zoneId);
    }

    private static double rectangleGaussianFraction(
            double minY,
            double maxY,
            double minZ,
            double maxZ,
            double sigmaM) {
        double y = normalCdf(maxY, sigmaM) - normalCdf(minY, sigmaM);
        double z = normalCdf(maxZ, sigmaM) - normalCdf(minZ, sigmaM);
        return y * z;
    }

    private static double normalCdf(double x, double sigma) {
        return 0.5 * (1.0 + erf(x / (sigma * SQRT_TWO)));
    }

    /** Abramowitz-Stegun 7.1.26; deterministic and adequate for benchmark integration. */
    private static double erf(double x) {
        double sign = x < 0.0 ? -1.0 : 1.0;
        double value = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * value);
        double polynomial = (((((1.061405429 * t - 1.453152027) * t)
                + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        double result = 1.0 - polynomial * Math.exp(-value * value);
        return sign * result;
    }

    enum CalibrationStatus {
        REFERENCE_SCALE_ONLY,
        EXTRAPOLATION_FORBIDDEN
    }

    record ImpactPacket(
            double massKg,
            double speedMps,
            double kineticEnergyJ,
            double momentumNs) {
    }

    record CalibrationGuardrail(
            CalibrationStatus status,
            double energyRatioToNasaLargeReference,
            double massRatioToNasaLargeReference) {
    }

    record ProjectedZone(
            String id,
            double minY,
            double maxY,
            double minZ,
            double maxZ) {
    }

    record ZoneExposure(
            String id,
            double fractionOfSource,
            double intersectingMassKg,
            double intersectingEnergyJ,
            double intersectingMomentumNs) {
    }

    record DebrisExposure(
            double standoffM,
            double lateralDispersionSigmaMps,
            double cloudSigmaM,
            double shipHitFraction,
            double intersectingMassKg,
            double intersectingEnergyJ,
            double intersectingMomentumNs,
            double zoneFractionSum,
            List<ZoneExposure> zones) {
    }
}

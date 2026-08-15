package com.spacesim.combat.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Ship Mathematics v0.6 thermal/power engineering harness.
 *
 * <p>This is an authoring benchmark, not production combat code. It separates a weapon-local
 * thermal loop from the ship-wide heat-rejection system so local emitter limits are not confused
 * with the much larger whole-ship radiator budget.</p>
 */
final class ShipMathematicsV06ThermalHarness {
    static final double STEFAN_BOLTZMANN_W_PER_M2_K4 = 5.670374419e-8;
    static final double HOT_RADIATOR_TEMPERATURE_K = 1_100.0;
    static final double HOT_RADIATOR_EMISSIVITY = 0.90;

    static final double PD_ELECTRICAL_INPUT_W = 8_000_000.0;
    static final double PD_BEAM_POWER_W = 5_000_000.0;
    static final double PD_WASTE_HEAT_W = 3_000_000.0;
    static final double PD_LOCAL_COOLANT_TRANSFER_W = 1_200_000.0;
    static final double PD_LOCAL_THERMAL_BUFFER_J = 36_000_000.0;
    static final double PD_RESTART_HEAT_J = 18_000_000.0;

    static final double PD_RANGE_M = 300_000.0;
    static final double INCOMING_SPEED_MPS = 18_000.0;
    static final double WAVE_INITIAL_RANGE_M = 800_000.0;
    static final int ENDURANCE_WAVES = 13;
    static final double INTEGRATION_STEP_S = 0.02;

    private static final List<ShipThermalSeed> SHIP_SEEDS = List.of(
            new ShipThermalSeed("CORVETTE", 150_000_000.0, 34_000_000.0,
                    30_000_000.0, 11_000_000.0, 60_000_000_000.0),
            new ShipThermalSeed("RECON_FRIGATE", 400_000_000.0, 194_000_000.0,
                    80_000_000.0, 76_500_000.0, 200_000_000_000.0),
            new ShipThermalSeed("ESCORT_DESTROYER", 1_000_000_000.0, 191_000_000.0,
                    200_000_000.0, 60_800_000.0, 600_000_000_000.0),
            new ShipThermalSeed("GENERAL_CRUISER", 3_000_000_000.0, 1_328_000_000.0,
                    600_000_000.0, 402_300_000.0, 2_000_000_000_000.0),
            new ShipThermalSeed("BATTLECRUISER", 8_000_000_000.0, 3_435_000_000.0,
                    1_500_000_000.0, 840_800_000.0, 6_000_000_000_000.0),
            new ShipThermalSeed("BATTLESHIP", 20_000_000_000.0, 5_909_000_000.0,
                    4_000_000_000.0, 1_387_300_000.0, 20_000_000_000_000.0),
            new ShipThermalSeed("FLEET_CARRIER", 15_000_000_000.0, 3_171_000_000.0,
                    3_000_000_000.0, 1_020_300_000.0, 15_000_000_000_000.0));

    private ShipMathematicsV06ThermalHarness() {
        throw new AssertionError("ShipMathematicsV06ThermalHarness does not create instances");
    }

    static double radiatorFluxWPerM2() {
        return HOT_RADIATOR_EMISSIVITY
                * STEFAN_BOLTZMANN_W_PER_M2_K4
                * Math.pow(HOT_RADIATOR_TEMPERATURE_K, 4.0);
    }

    static double pointDefenseTerminalWindowS() {
        return PD_RANGE_M / INCOMING_SPEED_MPS;
    }

    static double nonOverlappingWavePeriodS() {
        return WAVE_INITIAL_RANGE_M / INCOMING_SPEED_MPS;
    }

    static double requestedPointDefenseDutyFraction() {
        return pointDefenseTerminalWindowS() / nonOverlappingWavePeriodS();
    }

    static double sustainablePointDefenseDutyFraction(double coolantTransferW) {
        return Math.min(1.0, coolantTransferW / PD_WASTE_HEAT_W);
    }

    static double coldPointDefenseBurstS(double coolantTransferW) {
        double netHeatingW = PD_WASTE_HEAT_W - coolantTransferW;
        return netHeatingW <= 0.0
                ? Double.POSITIVE_INFINITY
                : PD_LOCAL_THERMAL_BUFFER_J / netHeatingW;
    }

    static List<ShipThermalPoint> referenceShipThermals() {
        List<ShipThermalPoint> points = new ArrayList<>(SHIP_SEEDS.size());
        double flux = radiatorFluxWPerM2();
        for (ShipThermalSeed seed : SHIP_SEEDS) {
            points.add(new ShipThermalPoint(
                    seed.id,
                    seed.ratedElectricalPowerW - seed.installedContinuousPowerW,
                    seed.sustainedHeatRejectionW - seed.installedContinuousWasteHeatW,
                    seed.installedContinuousWasteHeatW / seed.sustainedHeatRejectionW,
                    seed.sustainedHeatRejectionW / flux,
                    seed.installedContinuousWasteHeatW / flux,
                    seed.legacyEmergencyThermalCapacityJ / seed.installedContinuousWasteHeatW));
        }
        return List.copyOf(points);
    }

    static ShipThermalPoint findShip(List<ShipThermalPoint> points, String id) {
        for (ShipThermalPoint point : points) {
            if (point.id.equals(id)) {
                return point;
            }
        }
        throw new IllegalArgumentException("Thermal point not found: " + id);
    }

    /**
     * Runs a deliberately conservative emitter-only endurance train.
     *
     * <p>Each wave requests full-power laser fire for the complete 300 km terminal envelope. A new
     * wave starts immediately when the previous 800 km approach ends, so no extra recovery time is
     * granted. The local store receives waste heat while the mount transfers heat into the ship
     * coolant loop. At full local capacity the emitter inhibits until it cools to 50% capacity.</p>
     */
    static EmitterTrain runEmitterTrain(double coolantTransferW) {
        if (!(coolantTransferW >= 0.0)) {
            throw new IllegalArgumentException("coolantTransferW must be non-negative");
        }
        double periodS = nonOverlappingWavePeriodS();
        double fireStartS = periodS - pointDefenseTerminalWindowS();
        int stepsPerWave = (int) Math.round(periodS / INTEGRATION_STEP_S);
        double heatJ = 0.0;
        boolean inhibited = false;
        List<EmitterWave> waves = new ArrayList<>(ENDURANCE_WAVES);

        for (int wave = 1; wave <= ENDURANCE_WAVES; wave++) {
            double deliveredBeamS = 0.0;
            double throttledS = 0.0;
            double peakHeatJ = heatJ;
            boolean inhibitedDuringWave = false;

            for (int step = 0; step < stepsPerWave; step++) {
                double localTimeS = step * INTEGRATION_STEP_S;
                if (inhibited && heatJ <= PD_RESTART_HEAT_J + 1.0e-9) {
                    inhibited = false;
                }

                boolean requested = localTimeS >= fireStartS;
                boolean firing = requested && !inhibited;
                if (firing) {
                    deliveredBeamS += INTEGRATION_STEP_S;
                } else if (requested) {
                    throttledS += INTEGRATION_STEP_S;
                }

                double generatedHeatW = firing ? PD_WASTE_HEAT_W : 0.0;
                if (heatJ > 0.0 || generatedHeatW > coolantTransferW) {
                    heatJ = Math.max(
                            0.0,
                            heatJ + (generatedHeatW - coolantTransferW) * INTEGRATION_STEP_S);
                }
                if (heatJ >= PD_LOCAL_THERMAL_BUFFER_J) {
                    heatJ = PD_LOCAL_THERMAL_BUFFER_J;
                    inhibited = true;
                    inhibitedDuringWave = true;
                }
                peakHeatJ = Math.max(peakHeatJ, heatJ);
            }

            waves.add(new EmitterWave(
                    wave,
                    deliveredBeamS,
                    throttledS,
                    heatJ,
                    peakHeatJ,
                    inhibitedDuringWave || inhibited));
        }

        return new EmitterTrain(coolantTransferW, List.copyOf(waves));
    }

    record ShipThermalSeed(
            String id,
            double ratedElectricalPowerW,
            double installedContinuousPowerW,
            double sustainedHeatRejectionW,
            double installedContinuousWasteHeatW,
            double legacyEmergencyThermalCapacityJ) {
    }

    record ShipThermalPoint(
            String id,
            double electricalPowerMarginW,
            double continuousHeatMarginW,
            double minimumRadiatorFraction,
            double fullHotRadiatorAreaM2,
            double radiatorAreaNeededAtInstalledHeatM2,
            double zeroRadiatorEmergencyEnduranceS) {
    }

    record EmitterWave(
            int wave,
            double deliveredBeamS,
            double throttledS,
            double endHeatJ,
            double peakHeatJ,
            boolean thermallyInhibited) {
    }

    record EmitterTrain(double coolantTransferW, List<EmitterWave> waves) {
        EmitterWave wave(int waveNumber) {
            if (waveNumber < 1 || waveNumber > waves.size()) {
                throw new IllegalArgumentException("wave outside train");
            }
            return waves.get(waveNumber - 1);
        }
    }
}

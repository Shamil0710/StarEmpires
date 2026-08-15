package com.spacesim.combat.benchmark;

import java.util.List;

/**
 * Deterministic Ship Mathematics v0.9 integration harness.
 *
 * <p>The harness joins previously separate engineering domains without promoting them into
 * production combat yet: one module-budget contract, explicit heavy-impact calibration axes,
 * drive-plume signature derived from jet power, and finite-delta-v travel timing for future world
 * scale calibration. Heavy impacts intentionally do not use an extrapolated MMOD equation or an
 * energy-divided-by-armor shortcut.</p>
 */
final class ShipMathematicsV09IntegratedHarness {
    static final double SPEED_OF_LIGHT_MPS = ShipMathematicsV08SensorTrackHarness.SPEED_OF_LIGHT_MPS;
    static final double PLANCK_CONSTANT_J_S = ShipMathematicsV08SensorTrackHarness.PLANCK_CONSTANT_J_S;

    static final double PASSIVE_IR_APERTURE_M = ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_APERTURE_M;
    static final double PASSIVE_IR_EFFECTIVE_WAVELENGTH_M =
            ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_EFFECTIVE_WAVELENGTH_M;
    static final double PASSIVE_IR_THROUGHPUT = ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_THROUGHPUT;
    static final double PASSIVE_IR_QUANTUM_EFFICIENCY =
            ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_QUANTUM_EFFICIENCY;
    static final double PASSIVE_IR_BACKGROUND_ELECTRONS_PER_S =
            ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_BACKGROUND_ELECTRONS_PER_S;
    static final double PASSIVE_IR_READ_NOISE_ELECTRONS =
            ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_READ_NOISE_ELECTRONS;
    static final double PASSIVE_IR_INTEGRATION_S = ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_INTEGRATION_S;
    static final double PASSIVE_IR_DETECTION_SNR = ShipMathematicsV08SensorTrackHarness.PASSIVE_IR_DETECTION_SNR;

    static final double PENETRATOR_DENSITY_KG_PER_M3_SEED = 19_000.0;

    static final double CORVETTE_THRUST_N = 2_200_000.0;
    static final double DESTROYER_THRUST_N = 13_200_000.0;
    static final double BATTLESHIP_THRUST_N = 137_500_000.0;
    static final double MILITARY_EXHAUST_VELOCITY_MPS = 100_000.0;

    /** Authoring sensitivity only: fraction of minimum jet kinetic power emitted in 3-5 um. */
    static final double PLUME_BAND_RADIATIVE_FRACTION_LOW = 1.0e-6;
    static final double PLUME_BAND_RADIATIVE_FRACTION_CENTRAL = 1.0e-5;
    static final double PLUME_BAND_RADIATIVE_FRACTION_HIGH = 1.0e-4;

    /** Relative line-of-sight radiance sensitivity, not a normalized final plume phase function. */
    static final double PLUME_ASPECT_FORWARD = 0.25;
    static final double PLUME_ASPECT_BROADSIDE = 1.0;
    static final double PLUME_ASPECT_AFT = 4.0;

    static final double BULK_FREIGHTER_LOADED_ACCELERATION_MPS2 = 0.08391608391608392;
    static final double BULK_FREIGHTER_LOADED_DELTA_V_MPS = 15_372.800463539408;
    static final double ESCORT_DESTROYER_SUSTAINED_ACCELERATION_MPS2 = 0.1504993843207005;
    static final double ESCORT_DESTROYER_NOMINAL_DELTA_V_MPS = 38_454.71005152617;

    private ShipMathematicsV09IntegratedHarness() {
        throw new AssertionError("ShipMathematicsV09IntegratedHarness does not create instances");
    }

    static ModuleBudget integrateModules(List<ModuleEngineeringSeed> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("modules must not be empty");
        }
        double massKg = 0.0;
        double volumeM3 = 0.0;
        double powerSupplyW = 0.0;
        double powerDemandW = 0.0;
        double peakPowerDemandW = 0.0;
        double storedEnergyJ = 0.0;
        double wasteHeatW = 0.0;
        double heatRejectionW = 0.0;
        double localThermalCapacityJ = 0.0;
        double coolantTransferDemandW = 0.0;
        int crew = 0;
        double ammunitionMassKg = 0.0;
        double reactionMassKg = 0.0;

        for (ModuleEngineeringSeed module : modules) {
            requireNonNegative(module.massKg(), "massKg");
            requireNonNegative(module.volumeM3(), "volumeM3");
            requireNonNegative(module.continuousPowerSupplyW(), "continuousPowerSupplyW");
            requireNonNegative(module.continuousPowerDemandW(), "continuousPowerDemandW");
            requireNonNegative(module.peakPowerDemandW(), "peakPowerDemandW");
            requireNonNegative(module.storedEnergyCapacityJ(), "storedEnergyCapacityJ");
            requireNonNegative(module.wasteHeatW(), "wasteHeatW");
            requireNonNegative(module.heatRejectionW(), "heatRejectionW");
            requireNonNegative(module.localThermalCapacityJ(), "localThermalCapacityJ");
            requireNonNegative(module.coolantTransferDemandW(), "coolantTransferDemandW");
            requireNonNegative(module.ammunitionMassKg(), "ammunitionMassKg");
            requireNonNegative(module.reactionMassKg(), "reactionMassKg");
            if (module.crewRequired() < 0) {
                throw new IllegalArgumentException("crewRequired must be non-negative");
            }

            massKg += module.massKg();
            volumeM3 += module.volumeM3();
            powerSupplyW += module.continuousPowerSupplyW();
            powerDemandW += module.continuousPowerDemandW();
            peakPowerDemandW += module.peakPowerDemandW();
            storedEnergyJ += module.storedEnergyCapacityJ();
            wasteHeatW += module.wasteHeatW();
            heatRejectionW += module.heatRejectionW();
            localThermalCapacityJ += module.localThermalCapacityJ();
            coolantTransferDemandW += module.coolantTransferDemandW();
            crew += module.crewRequired();
            ammunitionMassKg += module.ammunitionMassKg();
            reactionMassKg += module.reactionMassKg();
        }

        return new ModuleBudget(
                massKg,
                volumeM3,
                powerSupplyW,
                powerDemandW,
                powerSupplyW - powerDemandW,
                peakPowerDemandW,
                storedEnergyJ,
                wasteHeatW,
                heatRejectionW,
                heatRejectionW - wasteHeatW,
                localThermalCapacityJ,
                coolantTransferDemandW,
                crew,
                ammunitionMassKg,
                reactionMassKg);
    }

    static List<ModuleEngineeringSeed> integrationDemonstratorModules() {
        return List.of(
                new ModuleEngineeringSeed(
                        "demo.power_core", 200_000.0, 250.0,
                        300_000_000.0, 20_000_000.0, 20_000_000.0,
                        0.0, 90_000_000.0, 0.0, 0.0, 0.0, 12,
                        0.0, 0.0),
                new ModuleEngineeringSeed(
                        "demo.sensor_ew", 70_000.0, 60.0,
                        0.0, 80_000_000.0, 120_000_000.0,
                        0.0, 50_000_000.0, 0.0, 100_000_000.0, 30_000_000.0, 8,
                        0.0, 0.0),
                new ModuleEngineeringSeed(
                        "demo.capacitor", 80_000.0, 100.0,
                        0.0, 1_000_000.0, 1_000_000.0,
                        20_000_000_000.0, 500_000.0, 0.0, 500_000_000.0, 0.0, 2,
                        0.0, 0.0),
                new ModuleEngineeringSeed(
                        "demo.radiator", 100_000.0, 500.0,
                        0.0, 5_000_000.0, 5_000_000.0,
                        0.0, 2_000_000.0, 180_000_000.0, 0.0, 0.0, 2,
                        0.0, 0.0));
    }

    static ProjectileGeometry mediumKineticGeometry() {
        return penetrator("M_COILGUN_DART", 25.0, 15_000.0, 0.05);
    }

    static ProjectileGeometry largeKineticGeometry() {
        return penetrator("L_COILGUN_DART", 150.0, 20_000.0, 0.10);
    }

    static ProjectileGeometry capitalKineticGeometry() {
        return penetrator("XL_CAPITAL_DART", 1_000.0, 30_000.0, 0.20);
    }

    static ProjectileGeometry penetrator(String id, double massKg, double velocityMps, double diameterM) {
        requirePositive(massKg, "massKg");
        requirePositive(velocityMps, "velocityMps");
        requirePositive(diameterM, "diameterM");
        double frontalAreaM2 = Math.PI * square(diameterM / 2.0);
        double lengthM = massKg / (PENETRATOR_DENSITY_KG_PER_M3_SEED * frontalAreaM2);
        double energyJ = 0.5 * massKg * square(velocityMps);
        double momentumNs = massKg * velocityMps;
        return new ProjectileGeometry(
                id,
                massKg,
                PENETRATOR_DENSITY_KG_PER_M3_SEED,
                diameterM,
                lengthM,
                lengthM / diameterM,
                velocityMps,
                frontalAreaM2,
                energyJ,
                momentumNs,
                energyJ / frontalAreaM2,
                momentumNs / frontalAreaM2);
    }

    static HeavyImpactPolicy heavyImpactPolicy(ProjectileGeometry projectile) {
        if (projectile == null) {
            throw new IllegalArgumentException("projectile must not be null");
        }
        return new HeavyImpactPolicy(
                HeavyImpactResolutionMode.CALIBRATED_RESPONSE_SURFACE_REQUIRED,
                true,
                true,
                true,
                true,
                true,
                "No direct MMOD BLE extrapolation and no energy/armor scalar shortcut");
    }

    static double minimumJetPowerW(double thrustN, double exhaustVelocityMps) {
        requirePositive(thrustN, "thrustN");
        requirePositive(exhaustVelocityMps, "exhaustVelocityMps");
        return 0.5 * thrustN * exhaustVelocityMps;
    }

    static PlumeSignature plumeSignature(
            double thrustN,
            double exhaustVelocityMps,
            double threeToFiveMicronRadiativeFraction,
            double aspectGain) {
        requirePositive(thrustN, "thrustN");
        requirePositive(exhaustVelocityMps, "exhaustVelocityMps");
        requirePositive(threeToFiveMicronRadiativeFraction, "threeToFiveMicronRadiativeFraction");
        requirePositive(aspectGain, "aspectGain");
        double jetPowerW = minimumJetPowerW(thrustN, exhaustVelocityMps);
        double bandPowerW = jetPowerW * threeToFiveMicronRadiativeFraction * aspectGain;
        return new PlumeSignature(
                thrustN,
                exhaustVelocityMps,
                jetPowerW,
                threeToFiveMicronRadiativeFraction,
                aspectGain,
                bandPowerW,
                maximumPassiveBandDetectionRangeM(bandPowerW));
    }

    static PassiveBandObservation passiveBandObservation(double sourceBandPowerW, double rangeM) {
        requirePositive(sourceBandPowerW, "sourceBandPowerW");
        requirePositive(rangeM, "rangeM");
        double apertureAreaM2 = Math.PI * square(PASSIVE_IR_APERTURE_M / 2.0);
        double receivedBandPowerW = sourceBandPowerW
                * apertureAreaM2
                / (4.0 * Math.PI * square(rangeM))
                * PASSIVE_IR_THROUGHPUT;
        double photonEnergyJ = PLANCK_CONSTANT_J_S
                * SPEED_OF_LIGHT_MPS
                / PASSIVE_IR_EFFECTIVE_WAVELENGTH_M;
        double signalElectrons = receivedBandPowerW
                * PASSIVE_IR_INTEGRATION_S
                / photonEnergyJ
                * PASSIVE_IR_QUANTUM_EFFICIENCY;
        double noiseElectrons = Math.sqrt(
                signalElectrons
                        + PASSIVE_IR_BACKGROUND_ELECTRONS_PER_S * PASSIVE_IR_INTEGRATION_S
                        + square(PASSIVE_IR_READ_NOISE_ELECTRONS));
        double snr = signalElectrons / noiseElectrons;
        return new PassiveBandObservation(
                sourceBandPowerW,
                rangeM,
                receivedBandPowerW,
                signalElectrons,
                snr,
                snr >= PASSIVE_IR_DETECTION_SNR);
    }

    static double maximumPassiveBandDetectionRangeM(double sourceBandPowerW) {
        requirePositive(sourceBandPowerW, "sourceBandPowerW");
        double low = 1_000.0;
        double high = 1.0e14;
        for (int iteration = 0; iteration < 256; iteration++) {
            double midpoint = 0.5 * (low + high);
            if (passiveBandObservation(sourceBandPowerW, midpoint).detected()) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return low;
    }

    static TravelEnvelope minimumRestToRestTravelTime(
            double distanceM,
            double accelerationMps2,
            double availableDeltaVMps) {
        requirePositive(distanceM, "distanceM");
        requirePositive(accelerationMps2, "accelerationMps2");
        requirePositive(availableDeltaVMps, "availableDeltaVMps");

        double idealDeltaVMps = 2.0 * Math.sqrt(accelerationMps2 * distanceM);
        if (idealDeltaVMps <= availableDeltaVMps) {
            double peakVelocityMps = Math.sqrt(accelerationMps2 * distanceM);
            double totalTimeS = 2.0 * Math.sqrt(distanceM / accelerationMps2);
            return new TravelEnvelope(
                    TravelRegime.ACCEL_BRAKE,
                    distanceM,
                    accelerationMps2,
                    availableDeltaVMps,
                    idealDeltaVMps,
                    peakVelocityMps,
                    totalTimeS,
                    0.0);
        }

        double peakVelocityMps = availableDeltaVMps / 2.0;
        double burnTimeS = peakVelocityMps / accelerationMps2;
        double oneBurnDistanceM = 0.5 * accelerationMps2 * square(burnTimeS);
        double coastDistanceM = distanceM - 2.0 * oneBurnDistanceM;
        double coastTimeS = coastDistanceM / peakVelocityMps;
        return new TravelEnvelope(
                TravelRegime.ACCEL_COAST_BRAKE,
                distanceM,
                accelerationMps2,
                availableDeltaVMps,
                availableDeltaVMps,
                peakVelocityMps,
                2.0 * burnTimeS + coastTimeS,
                coastTimeS);
    }

    static List<TravelEnvelope> bulkFreighterWorldScaleSweep() {
        return worldScaleSweep(BULK_FREIGHTER_LOADED_ACCELERATION_MPS2, BULK_FREIGHTER_LOADED_DELTA_V_MPS);
    }

    static List<TravelEnvelope> escortDestroyerWorldScaleSweep() {
        return worldScaleSweep(ESCORT_DESTROYER_SUSTAINED_ACCELERATION_MPS2, ESCORT_DESTROYER_NOMINAL_DELTA_V_MPS);
    }

    private static List<TravelEnvelope> worldScaleSweep(double accelerationMps2, double deltaVMps) {
        return List.of(
                minimumRestToRestTravelTime(10_000_000.0, accelerationMps2, deltaVMps),
                minimumRestToRestTravelTime(100_000_000.0, accelerationMps2, deltaVMps),
                minimumRestToRestTravelTime(1_000_000_000.0, accelerationMps2, deltaVMps),
                minimumRestToRestTravelTime(10_000_000_000.0, accelerationMps2, deltaVMps));
    }

    private static double square(double value) {
        return value * value;
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0.0 || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    enum HeavyImpactResolutionMode {
        CALIBRATED_RESPONSE_SURFACE_REQUIRED
    }

    enum TravelRegime {
        ACCEL_BRAKE,
        ACCEL_COAST_BRAKE
    }

    record ModuleEngineeringSeed(
            String id,
            double massKg,
            double volumeM3,
            double continuousPowerSupplyW,
            double continuousPowerDemandW,
            double peakPowerDemandW,
            double storedEnergyCapacityJ,
            double wasteHeatW,
            double heatRejectionW,
            double localThermalCapacityJ,
            double coolantTransferDemandW,
            int crewRequired,
            double ammunitionMassKg,
            double reactionMassKg) {
    }

    record ModuleBudget(
            double massKg,
            double volumeM3,
            double continuousPowerSupplyW,
            double continuousPowerDemandW,
            double continuousPowerMarginW,
            double peakPowerDemandW,
            double storedEnergyCapacityJ,
            double wasteHeatW,
            double heatRejectionW,
            double continuousHeatMarginW,
            double localThermalCapacityJ,
            double coolantTransferDemandW,
            int crewRequired,
            double ammunitionMassKg,
            double reactionMassKg) {
    }

    record ProjectileGeometry(
            String id,
            double massKg,
            double materialDensityKgPerM3,
            double diameterM,
            double lengthM,
            double finenessRatio,
            double velocityMps,
            double frontalAreaM2,
            double kineticEnergyJ,
            double momentumNs,
            double kineticEnergyPerFrontalAreaJPerM2,
            double momentumPerFrontalAreaNsPerM2) {
    }

    record HeavyImpactPolicy(
            HeavyImpactResolutionMode mode,
            boolean projectileMaterialRequired,
            boolean projectileGeometryRequired,
            boolean targetLayerStackRequired,
            boolean incidenceRequired,
            boolean calibrationBoundsRequired,
            String note) {
    }

    record PlumeSignature(
            double thrustN,
            double exhaustVelocityMps,
            double minimumJetPowerW,
            double threeToFiveMicronRadiativeFraction,
            double aspectGain,
            double apparentBandPowerW,
            double passiveDetectionRangeM) {
    }

    record PassiveBandObservation(
            double sourceBandPowerW,
            double rangeM,
            double receivedBandPowerW,
            double signalElectrons,
            double snr,
            boolean detected) {
    }

    record TravelEnvelope(
            TravelRegime regime,
            double distanceM,
            double accelerationMps2,
            double availableDeltaVMps,
            double usedDeltaVMps,
            double peakVelocityMps,
            double totalTimeS,
            double coastTimeS) {
    }
}

package com.spacesim.combat.benchmark;

/**
 * Deterministic Ship Mathematics v0.8 sensor, tracking and electronic-warfare harness.
 *
 * <p>The harness intentionally separates detection, measurement quality and fire-control state.
 * Passive infrared observations derive signal-to-noise and angular uncertainty from source power,
 * distance and aperture. Active radar derives echo strength from the monostatic radar equation,
 * while jammer power is added as received interference rather than as a percentage penalty.
 * Bearing-only observations do not magically provide range; distributed observers or active
 * ranging are required to collapse that part of the covariance.</p>
 */
final class ShipMathematicsV08SensorTrackHarness {
    static final double PLANCK_CONSTANT_J_S = 6.62607015e-34;
    static final double SPEED_OF_LIGHT_MPS = 299_792_458.0;
    static final double BOLTZMANN_CONSTANT_J_PER_K = 1.380649e-23;

    static final double HOT_RADIATOR_TEMPERATURE_K = 1_100.0;
    static final double HOT_3_TO_5_UM_POWER_FRACTION = 0.3507950859212762;
    static final double HOT_8_TO_12_UM_POWER_FRACTION = 0.07267041590214815;
    static final double WARM_DECOY_TEMPERATURE_K = 600.0;
    static final double WARM_3_TO_5_UM_POWER_FRACTION = 0.2340305210454334;
    static final double WARM_8_TO_12_UM_POWER_FRACTION = 0.21175286026029627;

    static final double PASSIVE_IR_APERTURE_M = 1.5;
    static final double PASSIVE_IR_EFFECTIVE_WAVELENGTH_M = 4.0e-6;
    static final double PASSIVE_IR_THROUGHPUT = 0.35;
    static final double PASSIVE_IR_QUANTUM_EFFICIENCY = 0.70;
    static final double PASSIVE_IR_BACKGROUND_ELECTRONS_PER_S = 1_000_000.0;
    static final double PASSIVE_IR_READ_NOISE_ELECTRONS = 100.0;
    static final double PASSIVE_IR_INTEGRATION_S = 1.0;
    static final double PASSIVE_IR_DETECTION_SNR = 5.0;
    static final double PASSIVE_IR_SYSTEMATIC_ANGULAR_FLOOR_RAD = 5.0e-8;

    static final double CORVETTE_WASTE_HEAT_W = 11_000_000.0;
    static final double RECON_FRIGATE_WASTE_HEAT_W = 76_500_000.0;
    static final double ESCORT_DESTROYER_WASTE_HEAT_W = 60_800_000.0;
    static final double BATTLESHIP_WASTE_HEAT_W = 1_387_300_000.0;

    static final double RADAR_WAVELENGTH_M = 0.03;
    static final double RADAR_APERTURE_M = 10.0;
    static final double RADAR_APERTURE_EFFICIENCY = 0.60;
    static final double RADAR_TRANSMIT_POWER_W = 20_000_000.0;
    static final double RADAR_SYSTEM_NOISE_TEMPERATURE_K = 500.0;
    static final double RADAR_COHERENT_DWELL_S = 1.0;
    static final double RADAR_DETECTION_SNR = 5.0;
    static final double RADAR_WAVEFORM_BANDWIDTH_HZ = 20_000_000.0;
    static final double CORVETTE_RCS_M2_SEED = 100.0;
    static final double BATTLESHIP_RCS_M2_SEED = 10_000.0;

    static final double EW_JAMMER_EIRP_SPECTRAL_DENSITY_W_PER_HZ = 0.10;
    static final double ECCM_EFFECTIVE_OVERLAP_FRACTION_SEED = 0.001;
    static final double INNOVATION_GATE_NIS = 9.0;

    private ShipMathematicsV08SensorTrackHarness() {
        throw new AssertionError("ShipMathematicsV08SensorTrackHarness does not create instances");
    }

    static PassiveObservation passiveIrObservation(double sourceRadiatedPowerW, double rangeM) {
        requirePositive(sourceRadiatedPowerW, "sourceRadiatedPowerW");
        requirePositive(rangeM, "rangeM");

        double apertureAreaM2 = Math.PI * square(PASSIVE_IR_APERTURE_M / 2.0);
        double receivedBandPowerW = sourceRadiatedPowerW
                * HOT_3_TO_5_UM_POWER_FRACTION
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
        double diffractionScaleRad = 1.22
                * PASSIVE_IR_EFFECTIVE_WAVELENGTH_M
                / PASSIVE_IR_APERTURE_M;
        double angularSigmaRad = Math.max(
                PASSIVE_IR_SYSTEMATIC_ANGULAR_FLOOR_RAD,
                diffractionScaleRad / Math.max(snr, 1.0e-30));

        return new PassiveObservation(
                rangeM,
                sourceRadiatedPowerW,
                receivedBandPowerW,
                signalElectrons,
                snr,
                diffractionScaleRad,
                angularSigmaRad,
                snr >= PASSIVE_IR_DETECTION_SNR);
    }

    static double maximumPassiveDetectionRangeM(double sourceRadiatedPowerW) {
        requirePositive(sourceRadiatedPowerW, "sourceRadiatedPowerW");
        double low = 1_000.0;
        double high = 1.0e13;
        for (int iteration = 0; iteration < 256; iteration++) {
            double midpoint = 0.5 * (low + high);
            if (passiveIrObservation(sourceRadiatedPowerW, midpoint).detected()) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return low;
    }

    static TriangulationResult twoObserverBearingTriangulation(
            double targetRangeM,
            double observerBaselineM,
            double angularSigmaRad) {
        requirePositive(targetRangeM, "targetRangeM");
        requirePositive(observerBaselineM, "observerBaselineM");
        requirePositive(angularSigmaRad, "angularSigmaRad");
        double rangeSigmaM = Math.sqrt(2.0)
                * square(targetRangeM)
                * angularSigmaRad
                / observerBaselineM;
        double crossTrackSigmaM = targetRangeM * angularSigmaRad / Math.sqrt(2.0);
        return new TriangulationResult(
                targetRangeM,
                observerBaselineM,
                angularSigmaRad,
                rangeSigmaM,
                crossTrackSigmaM);
    }

    static RadarObservation activeRadarObservation(double rangeM, double radarCrossSectionM2) {
        return activeRadarObservation(rangeM, radarCrossSectionM2, RADAR_COHERENT_DWELL_S, 0.0);
    }

    static RadarObservation activeRadarObservation(
            double rangeM,
            double radarCrossSectionM2,
            double coherentDwellS,
            double additionalNoiseDensityWPerHz) {
        requirePositive(rangeM, "rangeM");
        requirePositive(radarCrossSectionM2, "radarCrossSectionM2");
        requirePositive(coherentDwellS, "coherentDwellS");
        if (additionalNoiseDensityWPerHz < 0.0 || !Double.isFinite(additionalNoiseDensityWPerHz)) {
            throw new IllegalArgumentException("additionalNoiseDensityWPerHz must be finite and non-negative");
        }

        double gain = radarAntennaGain(RADAR_APERTURE_M);
        double receivedEchoPowerW = RADAR_TRANSMIT_POWER_W
                * square(gain)
                * square(RADAR_WAVELENGTH_M)
                * radarCrossSectionM2
                / (Math.pow(4.0 * Math.PI, 3.0) * Math.pow(rangeM, 4.0));
        double thermalNoiseDensityWPerHz = BOLTZMANN_CONSTANT_J_PER_K * RADAR_SYSTEM_NOISE_TEMPERATURE_K;
        double snr = receivedEchoPowerW
                * coherentDwellS
                / (thermalNoiseDensityWPerHz + additionalNoiseDensityWPerHz);
        double rangeResolutionM = SPEED_OF_LIGHT_MPS / (2.0 * RADAR_WAVEFORM_BANDWIDTH_HZ);
        double rangeSigmaM = Math.max(1.0, rangeResolutionM / Math.sqrt(2.0 * Math.max(snr, 1.0e-30)));

        return new RadarObservation(
                rangeM,
                radarCrossSectionM2,
                receivedEchoPowerW,
                thermalNoiseDensityWPerHz,
                additionalNoiseDensityWPerHz,
                coherentDwellS,
                snr,
                rangeResolutionM,
                rangeSigmaM,
                snr >= RADAR_DETECTION_SNR);
    }

    static double maximumActiveRadarDetectionRangeM(double radarCrossSectionM2) {
        requirePositive(radarCrossSectionM2, "radarCrossSectionM2");
        double low = 1_000.0;
        double high = 1.0e11;
        for (int iteration = 0; iteration < 256; iteration++) {
            double midpoint = 0.5 * (low + high);
            if (activeRadarObservation(midpoint, radarCrossSectionM2).detected()) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return low;
    }

    static EmissionIntercept activeRadarMainBeamIntercept(double rangeM, double receiverApertureM) {
        requirePositive(rangeM, "rangeM");
        requirePositive(receiverApertureM, "receiverApertureM");
        double transmitGain = radarAntennaGain(RADAR_APERTURE_M);
        double receiveGain = radarAntennaGain(receiverApertureM);
        double receivedPowerW = RADAR_TRANSMIT_POWER_W
                * transmitGain
                * receiveGain
                * square(RADAR_WAVELENGTH_M)
                / (square(4.0 * Math.PI) * square(rangeM));
        double noiseDensityWPerHz = BOLTZMANN_CONSTANT_J_PER_K * RADAR_SYSTEM_NOISE_TEMPERATURE_K;
        double snr = receivedPowerW * RADAR_COHERENT_DWELL_S / noiseDensityWPerHz;
        double mainBeamScaleRad = 1.22 * RADAR_WAVELENGTH_M / RADAR_APERTURE_M;
        return new EmissionIntercept(rangeM, receiverApertureM, receivedPowerW, snr, mainBeamScaleRad);
    }

    static JammingResult jammedCorvetteRadarAt300000Km(double effectiveOverlapFraction, double coherentDwellS) {
        if (effectiveOverlapFraction < 0.0 || effectiveOverlapFraction > 1.0
                || !Double.isFinite(effectiveOverlapFraction)) {
            throw new IllegalArgumentException("effectiveOverlapFraction must be in [0, 1]");
        }
        requirePositive(coherentDwellS, "coherentDwellS");
        double rangeM = 300_000_000.0;
        double jammerDensity = receivedJammerNoiseDensityWPerHz(rangeM)
                * effectiveOverlapFraction;
        RadarObservation observation = activeRadarObservation(
                rangeM,
                CORVETTE_RCS_M2_SEED,
                coherentDwellS,
                jammerDensity);
        return new JammingResult(
                rangeM,
                effectiveOverlapFraction,
                coherentDwellS,
                jammerDensity,
                observation.snr(),
                observation.detected());
    }

    static double receivedJammerNoiseDensityWPerHz(double rangeM) {
        requirePositive(rangeM, "rangeM");
        double radarReceiveGain = radarAntennaGain(RADAR_APERTURE_M);
        return EW_JAMMER_EIRP_SPECTRAL_DENSITY_W_PER_HZ
                * radarReceiveGain
                * square(RADAR_WAVELENGTH_M)
                / (square(4.0 * Math.PI) * square(rangeM));
    }

    static double radarAntennaGain(double apertureDiameterM) {
        requirePositive(apertureDiameterM, "apertureDiameterM");
        return RADAR_APERTURE_EFFICIENCY
                * square(Math.PI * apertureDiameterM / RADAR_WAVELENGTH_M);
    }

    static double spectralColorRatio8To12Over3To5At1100K() {
        return HOT_8_TO_12_UM_POWER_FRACTION / HOT_3_TO_5_UM_POWER_FRACTION;
    }

    static double spectralColorRatio8To12Over3To5At600K() {
        return WARM_8_TO_12_UM_POWER_FRACTION / WARM_3_TO_5_UM_POWER_FRACTION;
    }

    static InnovationResult normalizedInnovation(
            double residual,
            double predictedSigma,
            double measurementSigma) {
        if (!Double.isFinite(residual)) {
            throw new IllegalArgumentException("residual must be finite");
        }
        requirePositive(predictedSigma, "predictedSigma");
        requirePositive(measurementSigma, "measurementSigma");
        double innovationVariance = square(predictedSigma) + square(measurementSigma);
        double nis = square(residual) / innovationVariance;
        return new InnovationResult(residual, innovationVariance, nis, nis <= INNOVATION_GATE_NIS);
    }

    static TrackAgingResult ageCrossTrackEstimate(
            double initialPositionSigmaM,
            double initialVelocitySigmaMps,
            double unmodeledAccelerationSigmaMps2,
            double ageS) {
        requirePositive(initialPositionSigmaM, "initialPositionSigmaM");
        if (initialVelocitySigmaMps < 0.0 || unmodeledAccelerationSigmaMps2 < 0.0 || ageS < 0.0
                || !Double.isFinite(initialVelocitySigmaMps)
                || !Double.isFinite(unmodeledAccelerationSigmaMps2)
                || !Double.isFinite(ageS)) {
            throw new IllegalArgumentException("track-aging inputs must be finite and non-negative");
        }
        double velocityContributionM = initialVelocitySigmaMps * ageS;
        double maneuverContributionM = 0.5 * unmodeledAccelerationSigmaMps2 * square(ageS);
        double sigmaM = Math.sqrt(
                square(initialPositionSigmaM)
                        + square(velocityContributionM)
                        + square(maneuverContributionM));
        return new TrackAgingResult(
                ageS,
                initialPositionSigmaM,
                initialVelocitySigmaMps,
                unmodeledAccelerationSigmaMps2,
                sigmaM);
    }

    private static double square(double value) {
        return value * value;
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    record PassiveObservation(
            double rangeM,
            double sourceRadiatedPowerW,
            double receivedBandPowerW,
            double signalElectrons,
            double snr,
            double diffractionScaleRad,
            double angularSigmaRad,
            boolean detected) {
    }

    record TriangulationResult(
            double targetRangeM,
            double observerBaselineM,
            double angularSigmaRad,
            double rangeSigmaM,
            double crossTrackSigmaM) {
    }

    record RadarObservation(
            double rangeM,
            double radarCrossSectionM2,
            double receivedEchoPowerW,
            double thermalNoiseDensityWPerHz,
            double additionalNoiseDensityWPerHz,
            double coherentDwellS,
            double snr,
            double rangeResolutionM,
            double rangeSigmaM,
            boolean detected) {
    }

    record EmissionIntercept(
            double rangeM,
            double receiverApertureM,
            double receivedPowerW,
            double snr,
            double mainBeamScaleRad) {
    }

    record JammingResult(
            double rangeM,
            double effectiveOverlapFraction,
            double coherentDwellS,
            double receivedJammerNoiseDensityWPerHz,
            double radarSnr,
            boolean detected) {
    }

    record InnovationResult(
            double residual,
            double innovationVariance,
            double normalizedInnovationSquared,
            boolean passesGate) {
    }

    record TrackAgingResult(
            double ageS,
            double initialPositionSigmaM,
            double initialVelocitySigmaMps,
            double unmodeledAccelerationSigmaMps2,
            double agedPositionSigmaM) {
    }
}

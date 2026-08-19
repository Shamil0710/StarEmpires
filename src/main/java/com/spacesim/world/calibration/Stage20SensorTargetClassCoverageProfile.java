package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ElectronicWarfareState;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.SensorRuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipSensorEngineeringAdapter;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.SignatureState;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20SensorCalibrationCalculator.ThresholdDistances;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage-20A closure profile for representative sensor-observer / target-signature class coverage.
 *
 * <p>The observer remains the current production escort sensor fit. Target signatures deliberately
 * combine production engineering with explicitly provisional accepted benchmark references. Missing
 * channels remain zero/unsupported rather than being inferred from ship class, mass or screen size.
 * This profile therefore broadens world-scale sensor evidence without introducing a scalar stealth
 * score or universal sensor radius.</p>
 *
 * @param version stable closure-profile version
 * @param observerRepresentativeId representative production observer identity
 * @param observerAuthority authority of the observer sensor engineering
 * @param observerProvenanceId exact observer fit provenance
 * @param observerModes production sensor modes consumed by this closure
 * @param targets representative physical target-signature references
 * @param samples measured production-runtime information-state envelopes
 */
public record Stage20SensorTargetClassCoverageProfile(
        String version,
        String observerRepresentativeId,
        CalibrationAuthority observerAuthority,
        String observerProvenanceId,
        Set<Mode> observerModes,
        List<TargetSignatureReference> targets,
        List<TargetObservationSample> samples) {
    /** Current representative sensor/target-class closure profile version. */
    public static final String CURRENT_VERSION = "stage20a.sensor-target-coverage.v1";

    private static final String ESCORT_REPRESENTATIVE_ID = "ESCORT_DESTROYER";
    private static final String ESCORT_FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final Set<Mode> REQUIRED_OBSERVER_MODES = EnumSet.of(Mode.PASSIVE_THERMAL, Mode.ACTIVE_RADAR);

    /** Representative physical target classes selected from already accepted Stage-20 evidence. */
    public enum TargetClass {
        /** Carrier-launched small craft reference. */ CARRIER_INTERCEPTOR,
        /** Small combatant reference. */ TORPEDO_CORVETTE,
        /** Reconnaissance / electronic-warfare frigate reference. */ RECON_EW_FRIGATE,
        /** Current production escort reference. */ ESCORT_DESTROYER,
        /** Independent cruiser reference. */ CRUISER,
        /** Capital line-combatant reference. */ BATTLESHIP,
        /** Large fleet-aviation carrier reference. */ FLEET_CARRIER
    }

    /**
     * Validates and deterministically freezes the closure profile.
     *
     * @param version stable closure-profile version
     * @param observerRepresentativeId representative production observer identity
     * @param observerAuthority authority of the observer sensor engineering
     * @param observerProvenanceId exact observer fit provenance
     * @param observerModes production sensor modes consumed by this closure
     * @param targets representative physical target-signature references
     * @param samples measured production-runtime information-state envelopes
     */
    public Stage20SensorTargetClassCoverageProfile {
        requireNonBlank(version, "version");
        requireNonBlank(observerRepresentativeId, "observerRepresentativeId");
        Objects.requireNonNull(observerAuthority, "observerAuthority");
        requireNonBlank(observerProvenanceId, "observerProvenanceId");
        Objects.requireNonNull(observerModes, "observerModes");
        if (observerModes.isEmpty() || observerModes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("observerModes must be non-empty and contain no nulls");
        }
        observerModes = Set.copyOf(observerModes);

        Objects.requireNonNull(targets, "targets");
        ArrayList<TargetSignatureReference> sortedTargets = new ArrayList<>(targets);
        if (sortedTargets.isEmpty() || sortedTargets.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("targets must be non-empty and contain no nulls");
        }
        sortedTargets.sort(Comparator.comparing(value -> value.targetClass().name()));
        if (sortedTargets.stream().map(TargetSignatureReference::targetClass).distinct().count()
                != sortedTargets.size()) {
            throw new IllegalArgumentException("target classes must be unique");
        }
        targets = List.copyOf(sortedTargets);

        Objects.requireNonNull(samples, "samples");
        ArrayList<TargetObservationSample> sortedSamples = new ArrayList<>(samples);
        if (sortedSamples.isEmpty() || sortedSamples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must be non-empty and contain no nulls");
        }
        sortedSamples.sort(Comparator.comparing((TargetObservationSample value) -> value.targetClass().name())
                .thenComparing(value -> value.mode().name())
                .thenComparing(TargetObservationSample::sensorId));
        samples = List.copyOf(sortedSamples);
    }

    /**
     * Derives the current target-class coverage through the production escort observation runtime.
     *
     * @return deterministic Stage-20A target-class coverage closure
     */
    public static Stage20SensorTargetClassCoverageProfile deriveCurrent() {
        var catalog = ShipEngineeringCatalogLoader.loadDefault();
        var derived = new DerivedShipCalculator(catalog).deriveDemonstrator(
                ESCORT_FIT_ID, ConsumableState.empty(), DamageState.pristine());
        var suite = new ShipSensorEngineeringAdapter().derive(derived);

        List<FittedSensor> fittedSensors = suite.sensors().stream()
                .filter(value -> REQUIRED_OBSERVER_MODES.contains(value.definition().mode()))
                .toList();
        Set<Mode> observerModes = fittedSensors.stream()
                .map(value -> value.definition().mode())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Mode.class)));

        List<TargetSignatureReference> targets = targetReferences(suite.staticSignature());
        ArrayList<TargetObservationSample> samples = new ArrayList<>();
        for (TargetSignatureReference target : targets) {
            for (FittedSensor fitted : fittedSensors) {
                ThresholdDistances thresholds = Stage20SensorCalibrationCalculator.deriveThresholdDistances(
                        fitted.definition(),
                        SensorRuntimeState.nominal(),
                        target.signature(),
                        ElectronicWarfareState.empty());
                samples.add(new TargetObservationSample(
                        target.targetClass(),
                        fitted.definition().id(),
                        fitted.moduleId(),
                        fitted.mountId(),
                        fitted.definition().mode(),
                        thresholds));
            }
        }

        return new Stage20SensorTargetClassCoverageProfile(
                CURRENT_VERSION,
                ESCORT_REPRESENTATIVE_ID,
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                ESCORT_FIT_ID,
                observerModes,
                targets,
                samples);
    }

    /**
     * Returns whether this profile closes the Stage-20B entry requirement for representative sensor
     * target classes without requiring unsupported channels.
     *
     * <p>Closure requires every selected representative class to be physically detectable through
     * passive thermal evidence, production passive-thermal and active-radar observer modes, plus at
     * least two independently sourced non-zero radar-signature cases so active-radar scaling is not
     * calibrated against only the production escort.</p>
     *
     * @return true when representative sensor/target coverage is sufficient for Stage-20B entry
     */
    public boolean closesStage20BEntryCoverage() {
        Set<TargetClass> coveredClasses = targets.stream()
                .map(TargetSignatureReference::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class)));
        if (!coveredClasses.equals(EnumSet.allOf(TargetClass.class))
                || !observerModes.containsAll(REQUIRED_OBSERVER_MODES)) {
            return false;
        }
        boolean everyTargetHasThermalDetection = targets.stream().allMatch(target -> sample(
                target.targetClass(), Mode.PASSIVE_THERMAL).thresholds().detectedMaxDistanceM().isPresent());
        long radarDetectedTargets = targets.stream()
                .filter(target -> target.signature().radarCrossSectionM2() > 0d)
                .filter(target -> sample(target.targetClass(), Mode.ACTIVE_RADAR)
                        .thresholds().detectedMaxDistanceM().isPresent())
                .count();
        boolean authorityVisible = targets.stream().anyMatch(value ->
                value.authority() == CalibrationAuthority.PRODUCTION_ENGINEERING)
                && targets.stream().filter(value ->
                        value.authority() == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE)
                        .allMatch(TargetSignatureReference::stage22ReviewRequired);
        return everyTargetHasThermalDetection && radarDetectedTargets >= 2L && authorityVisible;
    }

    /**
     * Returns the unique observation sample for one representative target and physical sensor mode.
     *
     * @param targetClass representative target class
     * @param mode physical sensor mode
     * @return matching production-runtime observation sample
     */
    public TargetObservationSample sample(TargetClass targetClass, Mode mode) {
        Objects.requireNonNull(targetClass, "targetClass");
        Objects.requireNonNull(mode, "mode");
        return samples.stream()
                .filter(value -> value.targetClass() == targetClass && value.mode() == mode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Stage-20 sensor coverage sample for " + targetClass + " / " + mode));
    }

    private static List<TargetSignatureReference> targetReferences(SignatureState productionEscortSignature) {
        return List.of(
                provisional(
                        TargetClass.CARRIER_INTERCEPTOR,
                        "docs/benchmarks/ship_reference_designs_v0_2.json:benchmark.craft.carrier_interceptor_v0_2/installedContinuousWasteHeatW|accepted_as_stage20_thermal_reference",
                        new SignatureState(4_000_000d, 0d, 0d, 0d, 0d, 0d)),
                provisional(
                        TargetClass.TORPEDO_CORVETTE,
                        "docs/benchmarks/sensor_track_reference_v0_8.json:passiveDetectionBenchmarks/TORPEDO_CORVETTE+activeRadarBenchmarks/CORVETTE_RCS_SEED",
                        new SignatureState(11_000_000d, 0d, 100d, 0d, 0d, 0d)),
                provisional(
                        TargetClass.RECON_EW_FRIGATE,
                        "docs/benchmarks/sensor_track_reference_v0_8.json:passiveDetectionBenchmarks/RECON_EW_FRIGATE",
                        new SignatureState(76_500_000d, 0d, 0d, 0d, 0d, 0d)),
                new TargetSignatureReference(
                        TargetClass.ESCORT_DESTROYER,
                        CalibrationAuthority.PRODUCTION_ENGINEERING,
                        ESCORT_FIT_ID + ":ShipSensorEngineeringAdapter.staticSignature",
                        false,
                        productionEscortSignature),
                provisional(
                        TargetClass.CRUISER,
                        "docs/benchmarks/ship_reference_designs_v0_2.json:benchmark.ship.general_cruiser_v0_2/installedContinuousWasteHeatW|accepted_as_stage20_thermal_reference",
                        new SignatureState(402_300_000d, 0d, 0d, 0d, 0d, 0d)),
                provisional(
                        TargetClass.BATTLESHIP,
                        "docs/benchmarks/sensor_track_reference_v0_8.json:passiveDetectionBenchmarks/BATTLESHIP+activeRadarBenchmarks/BATTLESHIP_RCS_SEED",
                        new SignatureState(1_387_300_000d, 0d, 10_000d, 0d, 0d, 0d)),
                provisional(
                        TargetClass.FLEET_CARRIER,
                        "docs/benchmarks/ship_reference_designs_v0_2.json:benchmark.ship.fleet_carrier_v0_2/installedContinuousWasteHeatW|accepted_as_stage20_thermal_reference",
                        new SignatureState(1_020_300_000d, 0d, 0d, 0d, 0d, 0d)));
    }

    private static TargetSignatureReference provisional(
            TargetClass targetClass,
            String provenanceId,
            SignatureState signature) {
        return new TargetSignatureReference(
                targetClass,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                provenanceId,
                true,
                signature);
    }

    /**
     * One representative physical target-signature input and its exact authority boundary.
     *
     * @param targetClass representative target class
     * @param authority production or provisional accepted-reference authority
     * @param provenanceId exact numeric/engineering source
     * @param stage22ReviewRequired whether content promotion must be revisited in Stage 22
     * @param signature physical channelized target signature
     */
    public record TargetSignatureReference(
            TargetClass targetClass,
            CalibrationAuthority authority,
            String provenanceId,
            boolean stage22ReviewRequired,
            SignatureState signature) {
        /**
         * Validates one representative target reference.
         *
         * @param targetClass representative target class
         * @param authority production or provisional accepted-reference authority
         * @param provenanceId exact numeric/engineering source
         * @param stage22ReviewRequired whether content promotion must be revisited in Stage 22
         * @param signature physical channelized target signature
         */
        public TargetSignatureReference {
            Objects.requireNonNull(targetClass, "targetClass");
            Objects.requireNonNull(authority, "authority");
            requireNonBlank(provenanceId, "provenanceId");
            Objects.requireNonNull(signature, "signature");
            if (signature.thermalRadiantPowerW() <= 0d) {
                throw new IllegalArgumentException("representative target requires positive thermal evidence");
            }
            if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
                throw new IllegalArgumentException("provisional target references require Stage-22 review");
            }
            if (authority == CalibrationAuthority.PRODUCTION_ENGINEERING && stage22ReviewRequired) {
                throw new IllegalArgumentException("production target reference cannot require provisional promotion review");
            }
        }
    }

    /**
     * One production-runtime observation envelope for a representative target and sensor mode.
     *
     * @param targetClass representative target class
     * @param sensorId physical production sensor definition ID
     * @param moduleId fitted production module ID
     * @param mountId fitted production mount ID
     * @param mode physical sensor mode
     * @param thresholds measured physical information-state separation boundaries
     */
    public record TargetObservationSample(
            TargetClass targetClass,
            String sensorId,
            String moduleId,
            String mountId,
            Mode mode,
            ThresholdDistances thresholds) {
        /**
         * Validates one target observation sample.
         *
         * @param targetClass representative target class
         * @param sensorId physical production sensor definition ID
         * @param moduleId fitted production module ID
         * @param mountId fitted production mount ID
         * @param mode physical sensor mode
         * @param thresholds measured physical information-state separation boundaries
         */
        public TargetObservationSample {
            Objects.requireNonNull(targetClass, "targetClass");
            requireNonBlank(sensorId, "sensorId");
            requireNonBlank(moduleId, "moduleId");
            requireNonBlank(mountId, "mountId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(thresholds, "thresholds");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

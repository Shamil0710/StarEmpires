package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ElectronicWarfareState;
import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.SensorDefinition;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.SensorRuntimeState;
import com.spacesim.ship.ShipElectronicWarfareEngineeringAdapter;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipSensorEngineeringAdapter;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.SignatureState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20SensorCalibrationCalculator.ThresholdDistances;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned Stage-20A spatial sensor/signature calibration produced from the production observation model.
 *
 * <p>The profile records information-state distance envelopes rather than a scalar sensor range. The
 * current production escort supplies observer sensors and target signature. Damage is applied through
 * ordinary engineering integrity. EW sensitivity uses the content-provisional but physically authored
 * Stage-17.5I defensive/EW doctrine, with its provisional authority kept machine-visible.</p>
 *
 * @param version stable calibration-profile version
 * @param observerRepresentativeId representative observer identity
 * @param observerAuthority authority of the observer sensor engineering
 * @param observerProvenanceId exact observer fit provenance
 * @param targetRepresentativeId representative target identity
 * @param targetAuthority authority of the target signature engineering
 * @param targetProvenanceId exact target fit/signature provenance
 * @param targetSignature physical target signature used by the current calibration matrix
 * @param trackPolicy current explicitly provisional fused-track policy reference
 * @param samples deterministic sensor-condition spatial measurements
 * @param unresolvedGaps machine-readable sensor-calibration gaps that remain open
 */
public record Stage20SensorCalibrationProfile(
        String version,
        String observerRepresentativeId,
        CalibrationAuthority observerAuthority,
        String observerProvenanceId,
        String targetRepresentativeId,
        CalibrationAuthority targetAuthority,
        String targetProvenanceId,
        SignatureState targetSignature,
        TrackPolicyReference trackPolicy,
        List<SensorEnvelopeSample> samples,
        List<String> unresolvedGaps) {
    /** Current Stage-20A production-observation sensor calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.sensor-spatial.v1";

    private static final String ESCORT_REPRESENTATIVE_ID = "ESCORT_DESTROYER";
    private static final String ESCORT_FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final String SENSOR_MOUNT_ID = "utility_sensor";
    private static final double DAMAGED_SENSOR_INTEGRITY = 0.5d;
    private static final long JAMMER_ENTITY_ID = 20_003L;
    private static final double JAMMER_OBSERVER_DISTANCE_M = 1_000_000d;

    /** Physical calibration condition applied to one fitted sensor sample. */
    public enum CalibrationCondition {
        /** Pristine fitted sensor with no external jammer. */ PRISTINE,
        /** Sensor mount re-derived at 50 percent surviving engineering integrity. */ SENSOR_MOUNT_50_PERCENT,
        /** Pristine active radar under authored noise jamming with ECCM disabled. */ JAMMED_NO_ECCM,
        /** Pristine active radar under authored noise jamming with ECCM enabled. */ JAMMED_WITH_ECCM
    }

    /** Authority of external interference used by a calibration sample. */
    public enum InterferenceAuthority {
        /** No external jammer is present. */ NONE,
        /** Jammer comes from accepted content-provisional Stage-17.5I combat-test engineering. */
        PROVISIONAL_ACCEPTED_COMBAT_TEST
    }

    /** Authority of the currently consumed fused-track quality policy. */
    public enum TrackPolicyAuthority {
        /** Existing pre-Stage20 default that must be revisited after weapon-geometry calibration. */
        PROVISIONAL_PRE_STAGE20_DEFAULT
    }

    /**
     * Creates an immutable deterministically ordered spatial sensor profile.
     *
     * @param version stable calibration-profile version
     * @param observerRepresentativeId representative observer identity
     * @param observerAuthority observer authority
     * @param observerProvenanceId observer fit provenance
     * @param targetRepresentativeId representative target identity
     * @param targetAuthority target authority
     * @param targetProvenanceId target provenance
     * @param targetSignature physical target signature
     * @param trackPolicy provisional fused-track policy reference
     * @param samples sensor-condition measurements
     * @param unresolvedGaps remaining explicit calibration gaps
     */
    public Stage20SensorCalibrationProfile {
        requireNonBlank(version, "version");
        requireNonBlank(observerRepresentativeId, "observerRepresentativeId");
        Objects.requireNonNull(observerAuthority, "observerAuthority");
        requireNonBlank(observerProvenanceId, "observerProvenanceId");
        requireNonBlank(targetRepresentativeId, "targetRepresentativeId");
        Objects.requireNonNull(targetAuthority, "targetAuthority");
        requireNonBlank(targetProvenanceId, "targetProvenanceId");
        Objects.requireNonNull(targetSignature, "targetSignature");
        Objects.requireNonNull(trackPolicy, "trackPolicy");
        Objects.requireNonNull(samples, "samples");
        ArrayList<SensorEnvelopeSample> sortedSamples = new ArrayList<>(samples);
        if (sortedSamples.isEmpty() || sortedSamples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must be non-empty and contain no null entries");
        }
        sortedSamples.sort(Comparator.comparing((SensorEnvelopeSample value) -> value.mode().name())
                .thenComparing(value -> value.condition().name())
                .thenComparing(SensorEnvelopeSample::sensorId));
        samples = List.copyOf(sortedSamples);

        Objects.requireNonNull(unresolvedGaps, "unresolvedGaps");
        ArrayList<String> sortedGaps = new ArrayList<>(unresolvedGaps);
        if (sortedGaps.isEmpty() || sortedGaps.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("unresolvedGaps must be non-empty and contain no blanks");
        }
        sortedGaps.sort(String::compareTo);
        unresolvedGaps = List.copyOf(sortedGaps);
    }

    /**
     * Derives the current sensor profile from the production escort plus accepted provisional EW evidence.
     *
     * @return deterministic current Stage-20A sensor/signature spatial calibration
     */
    public static Stage20SensorCalibrationProfile deriveCurrent() {
        ShipEngineeringCatalog productionCatalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator productionCalculator = new DerivedShipCalculator(productionCatalog);
        var pristineShip = productionCalculator.deriveDemonstrator(
                ESCORT_FIT_ID, ConsumableState.empty(), DamageState.pristine());
        var damagedShip = productionCalculator.deriveDemonstrator(
                ESCORT_FIT_ID,
                ConsumableState.empty(),
                new DamageState(Map.of(SENSOR_MOUNT_ID, DAMAGED_SENSOR_INTEGRITY)));

        ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();
        var pristineSuite = sensorAdapter.derive(pristineShip);
        var damagedSuite = sensorAdapter.derive(damagedShip);
        SignatureState targetSignature = pristineSuite.staticSignature();

        ArrayList<SensorEnvelopeSample> samples = new ArrayList<>();
        for (FittedSensor fitted : pristineSuite.sensors()) {
            samples.add(deriveSample(
                    fitted,
                    CalibrationCondition.PRISTINE,
                    SensorRuntimeState.nominal(),
                    targetSignature,
                    ElectronicWarfareState.empty(),
                    InterferenceAuthority.NONE,
                    "NONE",
                    0d,
                    0d));
        }
        for (FittedSensor fitted : damagedSuite.sensors()) {
            samples.add(deriveSample(
                    fitted,
                    CalibrationCondition.SENSOR_MOUNT_50_PERCENT,
                    SensorRuntimeState.nominal(),
                    targetSignature,
                    ElectronicWarfareState.empty(),
                    InterferenceAuthority.NONE,
                    "NONE",
                    0d,
                    0d));
        }

        NoiseJammer jammer = deriveAcceptedProvisionalJammer();
        ElectronicWarfareState jammedEnvironment = new ElectronicWarfareState(List.of(jammer), List.of());
        for (FittedSensor fitted : pristineSuite.sensors()) {
            if (fitted.definition().mode() != Mode.ACTIVE_RADAR) {
                continue;
            }
            String jammerProvenance = Stage175ICombatTestContentPack.DOCTRINE_RESOURCE
                    + ":" + Stage175IFleetDoctrineCatalog.get(DoctrineId.D_DEFENSIVE_EW).fitId();
            samples.add(deriveSample(
                    fitted,
                    CalibrationCondition.JAMMED_NO_ECCM,
                    SensorRuntimeState.nominal(),
                    targetSignature,
                    jammedEnvironment,
                    InterferenceAuthority.PROVISIONAL_ACCEPTED_COMBAT_TEST,
                    jammerProvenance,
                    JAMMER_OBSERVER_DISTANCE_M,
                    jammer.radiatedPowerW()));
            samples.add(deriveSample(
                    fitted,
                    CalibrationCondition.JAMMED_WITH_ECCM,
                    new SensorRuntimeState(true, true, 1d, 1d),
                    targetSignature,
                    jammedEnvironment,
                    InterferenceAuthority.PROVISIONAL_ACCEPTED_COMBAT_TEST,
                    jammerProvenance,
                    JAMMER_OBSERVER_DISTANCE_M,
                    jammer.radiatedPowerW()));
        }

        TrackQualityPolicy defaultPolicy = TrackQualityPolicy.defaultPolicy();
        TrackPolicyReference trackPolicy = new TrackPolicyReference(
                TrackPolicyAuthority.PROVISIONAL_PRE_STAGE20_DEFAULT,
                "ShipSensorRuntime.TrackQualityPolicy.defaultPolicy",
                defaultPolicy.trackedPositionSigmaM(),
                defaultPolicy.fireControlPositionSigmaM(),
                defaultPolicy.trackedMaxAgeSeconds(),
                defaultPolicy.fireControlMaxAgeSeconds(),
                defaultPolicy.positionProcessNoiseM2PerSecond(),
                defaultPolicy.bearingProcessNoiseRad2PerSecond(),
                defaultPolicy.classificationConfidenceThreshold());

        return new Stage20SensorCalibrationProfile(
                CURRENT_VERSION,
                ESCORT_REPRESENTATIVE_ID,
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                ESCORT_FIT_ID,
                ESCORT_REPRESENTATIVE_ID,
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                ESCORT_FIT_ID + ":static_signature",
                targetSignature,
                trackPolicy,
                samples,
                List.of(
                        "final_fused_track_quality_policy_pending_weapon_geometry",
                        "distributed_passive_triangulation_geometry_not_yet_profiled",
                        "representative_sensor_and_target_class_coverage_incomplete"));
    }

    private static SensorEnvelopeSample deriveSample(
            FittedSensor fitted,
            CalibrationCondition condition,
            SensorRuntimeState runtimeState,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            InterferenceAuthority interferenceAuthority,
            String interferenceProvenanceId,
            double jammerObserverDistanceM,
            double jammerRadiatedPowerW) {
        SensorDefinition definition = fitted.definition();
        ThresholdDistances thresholds = Stage20SensorCalibrationCalculator.deriveThresholdDistances(
                definition, runtimeState, targetSignature, ewState);
        return new SensorEnvelopeSample(
                definition.id(),
                fitted.moduleId(),
                fitted.mountId(),
                definition.mode(),
                condition,
                interferenceAuthority,
                interferenceProvenanceId,
                jammerObserverDistanceM,
                jammerRadiatedPowerW,
                definition.apertureAreaM2(),
                definition.receiverNoisePowerW(),
                definition.detectionSnr(),
                definition.classificationSnr(),
                definition.trackSnr(),
                definition.fireControlSnr(),
                definition.bearingSigmaFloorRad(),
                definition.rangeSigmaFraction(),
                definition.eccmProcessingGain(),
                definition.eccmPowerDemandW(),
                definition.eccmWasteHeatW(),
                thresholds);
    }

    private static NoiseJammer deriveAcceptedProvisionalJammer() {
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.D_DEFENSIVE_EW);
        ShipEngineeringCatalog doctrineCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        var derived = new DerivedShipCalculator(doctrineCatalog).deriveDemonstrator(
                doctrine.fitId(), doctrine.initialConsumables(), DamageState.pristine());
        return new ShipElectronicWarfareEngineeringAdapter().deriveNoiseJammer(
                        JAMMER_ENTITY_ID, 0d, JAMMER_OBSERVER_DISTANCE_M, derived)
                .orElseThrow(() -> new IllegalStateException("Accepted D_DEFENSIVE_EW fixture has no surviving jammer"));
    }

    /**
     * One sensor/condition spatial envelope measured through the production observation runtime.
     *
     * @param sensorId physical sensor definition ID
     * @param moduleId fitted module ID
     * @param mountId fitted mount ID
     * @param mode physical sensor mode
     * @param condition calibration condition
     * @param interferenceAuthority authority of external interference
     * @param interferenceProvenanceId exact external interference provenance or NONE
     * @param jammerObserverDistanceM jammer-to-observer separation, zero when absent
     * @param jammerRadiatedPowerW authored jammer radiated power, zero when absent
     * @param apertureAreaM2 effective fitted aperture area
     * @param receiverNoisePowerW effective fitted receiver noise power
     * @param detectionSnr sensor DETECTED threshold
     * @param classificationSnr sensor CLASSIFIED threshold
     * @param trackSnr sensor TRACKED threshold
     * @param fireControlSnr sensor FIRE_CONTROL threshold
     * @param bearingSigmaFloorRad bearing uncertainty floor
     * @param rangeSigmaFraction active-ranging fractional uncertainty floor
     * @param eccmProcessingGain fitted ECCM processing gain
     * @param eccmPowerDemandW ECCM electrical demand
     * @param eccmWasteHeatW ECCM waste heat
     * @param thresholds measured physical distance boundaries
     */
    public record SensorEnvelopeSample(
            String sensorId,
            String moduleId,
            String mountId,
            Mode mode,
            CalibrationCondition condition,
            InterferenceAuthority interferenceAuthority,
            String interferenceProvenanceId,
            double jammerObserverDistanceM,
            double jammerRadiatedPowerW,
            double apertureAreaM2,
            double receiverNoisePowerW,
            double detectionSnr,
            double classificationSnr,
            double trackSnr,
            double fireControlSnr,
            double bearingSigmaFloorRad,
            double rangeSigmaFraction,
            double eccmProcessingGain,
            double eccmPowerDemandW,
            double eccmWasteHeatW,
            ThresholdDistances thresholds) {
        /**
         * Validates one spatial-envelope measurement and its explicit EW provenance.
         *
         * @param sensorId physical sensor definition ID
         * @param moduleId fitted module ID
         * @param mountId fitted mount ID
         * @param mode physical sensor mode
         * @param condition calibration condition
         * @param interferenceAuthority external interference authority
         * @param interferenceProvenanceId external interference provenance
         * @param jammerObserverDistanceM jammer-to-observer separation
         * @param jammerRadiatedPowerW jammer radiated power
         * @param apertureAreaM2 effective aperture
         * @param receiverNoisePowerW effective receiver noise
         * @param detectionSnr DETECTED threshold
         * @param classificationSnr CLASSIFIED threshold
         * @param trackSnr TRACKED threshold
         * @param fireControlSnr FIRE_CONTROL threshold
         * @param bearingSigmaFloorRad bearing uncertainty floor
         * @param rangeSigmaFraction ranging uncertainty fraction
         * @param eccmProcessingGain ECCM gain
         * @param eccmPowerDemandW ECCM power demand
         * @param eccmWasteHeatW ECCM waste heat
         * @param thresholds measured distance boundaries
         */
        public SensorEnvelopeSample {
            requireNonBlank(sensorId, "sensorId");
            requireNonBlank(moduleId, "moduleId");
            requireNonBlank(mountId, "mountId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(interferenceAuthority, "interferenceAuthority");
            requireNonBlank(interferenceProvenanceId, "interferenceProvenanceId");
            requireNonNegative(jammerObserverDistanceM, "jammerObserverDistanceM");
            requireNonNegative(jammerRadiatedPowerW, "jammerRadiatedPowerW");
            requirePositive(apertureAreaM2, "apertureAreaM2");
            requirePositive(receiverNoisePowerW, "receiverNoisePowerW");
            requirePositive(detectionSnr, "detectionSnr");
            requirePositive(classificationSnr, "classificationSnr");
            requirePositive(trackSnr, "trackSnr");
            requirePositive(fireControlSnr, "fireControlSnr");
            requirePositive(bearingSigmaFloorRad, "bearingSigmaFloorRad");
            requirePositive(rangeSigmaFraction, "rangeSigmaFraction");
            requirePositive(eccmProcessingGain, "eccmProcessingGain");
            requireNonNegative(eccmPowerDemandW, "eccmPowerDemandW");
            requireNonNegative(eccmWasteHeatW, "eccmWasteHeatW");
            Objects.requireNonNull(thresholds, "thresholds");
            if (interferenceAuthority == InterferenceAuthority.NONE
                    && (jammerObserverDistanceM != 0d || jammerRadiatedPowerW != 0d
                    || !"NONE".equals(interferenceProvenanceId))) {
                throw new IllegalArgumentException("non-jammed sample must use canonical zero/NONE interference values");
            }
            if (interferenceAuthority != InterferenceAuthority.NONE
                    && (jammerObserverDistanceM <= 0d || jammerRadiatedPowerW <= 0d)) {
                throw new IllegalArgumentException("jammed sample requires positive physical jammer geometry/power");
            }
        }
    }

    /**
     * Machine-visible reference to the currently consumed fused-track ageing/uncertainty policy.
     *
     * @param authority policy authority status
     * @param provenanceId exact code provenance
     * @param trackedPositionSigmaM maximum one-sigma position error for TRACKED
     * @param fireControlPositionSigmaM maximum one-sigma position error for FIRE_CONTROL
     * @param trackedMaxAgeSeconds maximum TRACKED age
     * @param fireControlMaxAgeSeconds maximum FIRE_CONTROL age
     * @param positionProcessNoiseM2PerSecond stale position/range covariance growth
     * @param bearingProcessNoiseRad2PerSecond stale bearing covariance growth
     * @param classificationConfidenceThreshold fallback classification confidence threshold
     */
    public record TrackPolicyReference(
            TrackPolicyAuthority authority,
            String provenanceId,
            double trackedPositionSigmaM,
            double fireControlPositionSigmaM,
            double trackedMaxAgeSeconds,
            double fireControlMaxAgeSeconds,
            double positionProcessNoiseM2PerSecond,
            double bearingProcessNoiseRad2PerSecond,
            double classificationConfidenceThreshold) {
        /**
         * Validates the provisional track-policy snapshot without promoting it to final Stage-20 tuning.
         *
         * @param authority policy authority status
         * @param provenanceId exact code provenance
         * @param trackedPositionSigmaM TRACKED position sigma
         * @param fireControlPositionSigmaM FIRE_CONTROL position sigma
         * @param trackedMaxAgeSeconds TRACKED max age
         * @param fireControlMaxAgeSeconds FIRE_CONTROL max age
         * @param positionProcessNoiseM2PerSecond position covariance growth
         * @param bearingProcessNoiseRad2PerSecond bearing covariance growth
         * @param classificationConfidenceThreshold classification confidence threshold
         */
        public TrackPolicyReference {
            Objects.requireNonNull(authority, "authority");
            requireNonBlank(provenanceId, "provenanceId");
            requirePositive(trackedPositionSigmaM, "trackedPositionSigmaM");
            requirePositive(fireControlPositionSigmaM, "fireControlPositionSigmaM");
            requirePositive(trackedMaxAgeSeconds, "trackedMaxAgeSeconds");
            requirePositive(fireControlMaxAgeSeconds, "fireControlMaxAgeSeconds");
            requireNonNegative(positionProcessNoiseM2PerSecond, "positionProcessNoiseM2PerSecond");
            requireNonNegative(bearingProcessNoiseRad2PerSecond, "bearingProcessNoiseRad2PerSecond");
            if (!Double.isFinite(classificationConfidenceThreshold)
                    || classificationConfidenceThreshold < 0d || classificationConfidenceThreshold > 1d) {
                throw new IllegalArgumentException("classificationConfidenceThreshold must be finite in [0,1]");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

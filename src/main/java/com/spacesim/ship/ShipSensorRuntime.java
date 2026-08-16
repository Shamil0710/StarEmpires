package com.spacesim.ship;

import com.spacesim.ship.ElectronicWarfareState.DeceptionSource;
import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.SignatureState.Channel;
import com.spacesim.ship.TrackState.InformationState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic Stage-17.5D sensor / track / EW runtime.
 *
 * <p>The runtime has no hard sensor range. It propagates channel-specific signal power through
 * geometry, adds receiver noise and physical jammer interference, then derives a measurement and its
 * covariance from resulting SNR. Bearing-only measurements remain range-unknown until ranging or
 * multi-observer geometry supplies a position solution.</p>
 */
@SuppressWarnings("doclint:missing")
public final class ShipSensorRuntime {
    private static final double FOUR_PI = 4d * StrictMath.PI;
    private static final double FOUR_PI_SQUARED = FOUR_PI * FOUR_PI;
    private static final double MIN_GEOMETRY_DISTANCE_M = 1d;
    private static final double MIN_TRIANGULATION_SINE = 1e-6d;

    /**
     * Produces one physical observation and explicit deception hypotheses.
     *
     * @param observerId stable observer identity
     * @param targetId stable target identity
     * @param sensor sensor definition
     * @param sensorState current physical/processing state
     * @param observer observer position
     * @param target target position
     * @param targetSignature target channelized signature
     * @param ewState current EW environment
     * @param timestampSeconds authoritative measurement time
     * @return observation result; true measurement is absent below detection threshold
     */
    public ObservationResult observe(
            long observerId,
            long targetId,
            SensorDefinition sensor,
            SensorRuntimeState sensorState,
            Position2d observer,
            Position2d target,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            double timestampSeconds) {
        requirePositiveId(observerId, "observerId");
        requirePositiveId(targetId, "targetId");
        SensorDefinition definition = Objects.requireNonNull(sensor, "sensor");
        SensorRuntimeState runtime = Objects.requireNonNull(sensorState, "sensorState");
        Position2d from = Objects.requireNonNull(observer, "observer");
        Position2d to = Objects.requireNonNull(target, "target");
        SignatureState signature = Objects.requireNonNull(targetSignature, "targetSignature");
        ElectronicWarfareState ew = Objects.requireNonNull(ewState, "ewState");
        requireFinite(timestampSeconds, "timestampSeconds");

        double emittedRadarPowerW = runtime.enabled() && definition.mode() == Mode.ACTIVE_RADAR
                ? definition.emittedPowerW() : 0d;
        double eccmPowerW = runtime.enabled() && runtime.eccmEnabled()
                ? definition.eccmPowerDemandW() : 0d;
        SignatureState observerEmission = SignatureState.zero()
                .withActiveRadioEmissions(emittedRadarPowerW, 0d);
        if (!runtime.enabled() || runtime.apertureFraction() <= 0d) {
            return new ObservationResult(Optional.empty(), List.of(), observerEmission, eccmPowerW);
        }

        double dx = to.xM() - from.xM();
        double dy = to.yM() - from.yM();
        double distanceM = Math.max(MIN_GEOMETRY_DISTANCE_M, StrictMath.hypot(dx, dy));
        double trueBearing = StrictMath.atan2(dy, dx);
        double apertureM2 = definition.apertureAreaM2() * runtime.apertureFraction();
        double signalW = receivedTargetSignalW(definition, signature, distanceM, apertureM2);
        double receiverNoiseW = definition.receiverNoisePowerW() / runtime.processingFraction();
        double jammerInterferenceW = receivedJammerInterferenceW(ew.noiseJammers(), from, apertureM2);
        double effectiveInterferenceW = runtime.eccmEnabled()
                ? jammerInterferenceW / definition.eccmProcessingGainLinear()
                : jammerInterferenceW;
        double snr = signalW / (receiverNoiseW + effectiveInterferenceW);

        Optional<SensorMeasurement> trueMeasurement = snr >= definition.detectionSnr()
                ? Optional.of(measure(
                        observerId, targetId, definition, timestampSeconds, from,
                        trueBearing, distanceM, signalW, effectiveInterferenceW, snr))
                : Optional.empty();

        List<MeasurementHypothesis> hypotheses = deceptiveHypotheses(
                observerId,
                targetId,
                definition,
                runtime,
                timestampSeconds,
                from,
                trueBearing,
                distanceM,
                receiverNoiseW,
                effectiveInterferenceW,
                ew.deceptionSources());
        return new ObservationResult(trueMeasurement, hypotheses, observerEmission, eccmPowerW);
    }

    /**
     * Fuses currently delivered measurements for one target into a deterministic track.
     *
     * <p>Range-capable measurements are fused by inverse position variance. If no range measurement
     * exists, two non-parallel bearing observers may triangulate a position. A single bearing can
     * never manufacture range.</p>
     *
     * @param targetId target identity
     * @param measurements raw/local/shared measurements
     * @param datalink delivery/freshness state
     * @param policy track-quality policy
     * @param nowSeconds authoritative current time
     * @return fused track
     */
    public TrackState fuse(
            long targetId,
            List<SensorMeasurement> measurements,
            DatalinkState datalink,
            TrackQualityPolicy policy,
            double nowSeconds) {
        requirePositiveId(targetId, "targetId");
        Objects.requireNonNull(measurements, "measurements");
        DatalinkState link = Objects.requireNonNull(datalink, "datalink");
        TrackQualityPolicy quality = Objects.requireNonNull(policy, "policy");
        requireFinite(nowSeconds, "nowSeconds");

        List<SensorMeasurement> usable = measurements.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.targetId() == targetId)
                .filter(value -> value.timestampSeconds() + link.latencySeconds() <= nowSeconds)
                .filter(value -> nowSeconds - value.timestampSeconds() <= link.maxMeasurementAgeSeconds())
                .sorted(MEASUREMENT_ORDER)
                .toList();
        if (usable.isEmpty()) {
            throw new IllegalArgumentException("No delivered, fresh measurements for target " + targetId);
        }

        double freshest = usable.stream().mapToDouble(SensorMeasurement::timestampSeconds).max().orElseThrow();
        double bearingVariance = inverseVariance(usable.stream()
                .mapToDouble(SensorMeasurement::bearingVarianceRad2).toArray());
        double classificationConfidence = usable.stream()
                .mapToDouble(value -> classificationEvidence(value.evidenceState()))
                .max().orElse(0d);
        Set<Long> observerIds = new HashSet<>();
        usable.forEach(value -> observerIds.add(value.observerId()));

        PositionSolution solution = rangeSolution(usable, nowSeconds, link)
                .or(() -> triangulatedSolution(usable, nowSeconds, link))
                .orElse(null);
        TrackCovariance covariance;
        boolean positionKnown = solution != null;
        double xM = 0d;
        double yM = 0d;
        if (solution != null) {
            xM = solution.xM();
            yM = solution.yM();
            covariance = new TrackCovariance(
                    solution.positionVarianceM2(),
                    bearingVariance,
                    solution.rangeVarianceM2());
        } else {
            covariance = new TrackCovariance(null, bearingVariance, null);
        }

        InformationState informationState = qualityState(
                classificationConfidence,
                positionKnown,
                covariance,
                nowSeconds - freshest,
                quality);
        return new TrackState(
                targetId,
                informationState,
                positionKnown,
                xM,
                yM,
                covariance,
                classificationConfidence,
                freshest,
                observerIds.size(),
                usable.size());
    }

    /**
     * Ages an existing track with explicit process noise and downgrades information quality.
     *
     * @param track existing track
     * @param nowSeconds authoritative current time
     * @param policy process-noise / quality policy
     * @return aged track
     */
    public TrackState ageTrack(TrackState track, double nowSeconds, TrackQualityPolicy policy) {
        TrackState current = Objects.requireNonNull(track, "track");
        TrackQualityPolicy quality = Objects.requireNonNull(policy, "policy");
        double age = current.ageSeconds(nowSeconds);
        TrackCovariance covariance = current.covariance();
        double bearingVariance = covariance.bearingVarianceRad2()
                + quality.bearingProcessNoiseRad2PerSecond() * age;
        Double positionVariance = covariance.positionVarianceM2() == null ? null
                : covariance.positionVarianceM2() + quality.positionProcessNoiseM2PerSecond() * age;
        Double rangeVariance = covariance.rangeVarianceM2() == null ? null
                : covariance.rangeVarianceM2() + quality.positionProcessNoiseM2PerSecond() * age;
        TrackCovariance aged = new TrackCovariance(positionVariance, bearingVariance, rangeVariance);
        InformationState state = qualityState(
                current.classificationConfidence(),
                current.positionKnown(),
                aged,
                age,
                quality);
        return new TrackState(
                current.targetId(),
                state,
                current.positionKnown(),
                current.estimatedXM(),
                current.estimatedYM(),
                aged,
                current.classificationConfidence(),
                current.lastMeasurementSeconds(),
                current.contributingObservers(),
                current.fusedMeasurementCount());
    }

    private static SensorMeasurement measure(
            long observerId,
            long targetId,
            SensorDefinition definition,
            double timestampSeconds,
            Position2d observer,
            double bearingRad,
            double distanceM,
            double signalW,
            double interferenceW,
            double snr) {
        double snrScale = StrictMath.sqrt(Math.max(1d, snr));
        double bearingSigma = definition.bearingSigmaFloorRad() / snrScale;
        boolean ranged = definition.providesRange();
        Double range = ranged ? distanceM : null;
        Double rangeVariance = ranged
                ? StrictMath.pow(Math.max(1d, distanceM * definition.rangeSigmaFraction() / snrScale), 2d)
                : null;
        return new SensorMeasurement(
                observerId,
                targetId,
                definition.channel(),
                timestampSeconds,
                observer.xM(),
                observer.yM(),
                bearingRad,
                range,
                bearingSigma * bearingSigma,
                rangeVariance,
                signalW,
                interferenceW,
                snr,
                evidenceState(definition, snr, ranged));
    }

    private static List<MeasurementHypothesis> deceptiveHypotheses(
            long observerId,
            long targetId,
            SensorDefinition definition,
            SensorRuntimeState runtime,
            double timestampSeconds,
            Position2d observer,
            double trueBearing,
            double trueRangeM,
            double receiverNoiseW,
            double environmentalInterferenceW,
            List<DeceptionSource> sources) {
        List<MeasurementHypothesis> result = new ArrayList<>();
        List<DeceptionSource> ordered = new ArrayList<>(sources);
        ordered.sort(Comparator.comparing(DeceptionSource::hypothesisId)
                .thenComparingLong(DeceptionSource::emitterId));
        for (DeceptionSource source : ordered) {
            double deceptivePower = runtime.eccmEnabled()
                    ? source.receivedEquivalentPowerW() / definition.eccmProcessingGainLinear()
                    : source.receivedEquivalentPowerW();
            double snr = deceptivePower / (receiverNoiseW + environmentalInterferenceW);
            if (snr < definition.detectionSnr()) {
                continue;
            }
            double apparentRange = Math.max(1d, trueRangeM + source.apparentRangeBiasM());
            SensorMeasurement measurement = measure(
                    observerId,
                    targetId,
                    definition,
                    timestampSeconds,
                    observer,
                    normalizeAngle(trueBearing + source.apparentBearingBiasRad()),
                    apparentRange,
                    deceptivePower,
                    environmentalInterferenceW,
                    snr);
            result.add(new MeasurementHypothesis(
                    source.hypothesisId(), source.emitterId(), measurement));
        }
        return List.copyOf(result);
    }

    private static double receivedTargetSignalW(
            SensorDefinition sensor,
            SignatureState signature,
            double rangeM,
            double apertureM2) {
        return switch (sensor.mode()) {
            case PASSIVE_THERMAL -> inverseSquareReceived(
                    signature.thermalRadiantPowerW(), apertureM2, rangeM);
            case PASSIVE_PLUME -> inverseSquareReceived(
                    signature.enginePlumeRadiantPowerW(), apertureM2, rangeM);
            case PASSIVE_OPTICAL -> inverseSquareReceived(
                    signature.reflectedOpticalPowerW(), apertureM2, rangeM);
            case PASSIVE_RADIO -> inverseSquareReceived(
                    signature.activeRadioEmissionPowerW() + signature.jammerEmissionPowerW(),
                    apertureM2,
                    rangeM);
            case ACTIVE_RADAR -> sensor.activeTransmitPowerW()
                    * sensor.transmitGainLinear()
                    * signature.radarCrossSectionM2()
                    * apertureM2
                    / (FOUR_PI_SQUARED * StrictMath.pow(rangeM, 4d));
        };
    }

    private static double receivedJammerInterferenceW(
            List<NoiseJammer> jammers,
            Position2d observer,
            double apertureM2) {
        double result = 0d;
        for (NoiseJammer jammer : jammers) {
            double rangeM = Math.max(
                    MIN_GEOMETRY_DISTANCE_M,
                    StrictMath.hypot(jammer.xM() - observer.xM(), jammer.yM() - observer.yM()));
            result += inverseSquareReceived(
                    jammer.radiatedPowerW() * jammer.gainLinear() * jammer.waveformOverlapFraction(),
                    apertureM2,
                    rangeM);
        }
        return result;
    }

    private static Optional<PositionSolution> rangeSolution(
            List<SensorMeasurement> measurements,
            double nowSeconds,
            DatalinkState link) {
        double sumWeight = 0d;
        double weightedX = 0d;
        double weightedY = 0d;
        double weightedRangeVariance = 0d;
        for (SensorMeasurement value : measurements) {
            if (!value.hasRange()) {
                continue;
            }
            double range = value.rangeM();
            double positionVariance = value.rangeVarianceM2()
                    + range * range * value.bearingVarianceRad2()
                    + transportPenalty(value, nowSeconds, link);
            double weight = 1d / positionVariance;
            double x = value.observerXM() + range * StrictMath.cos(value.bearingRad());
            double y = value.observerYM() + range * StrictMath.sin(value.bearingRad());
            sumWeight += weight;
            weightedX += x * weight;
            weightedY += y * weight;
            weightedRangeVariance += value.rangeVarianceM2() * weight;
        }
        if (sumWeight <= 0d) {
            return Optional.empty();
        }
        return Optional.of(new PositionSolution(
                weightedX / sumWeight,
                weightedY / sumWeight,
                1d / sumWeight,
                Math.max(1e-12d, weightedRangeVariance / sumWeight)));
    }

    private static Optional<PositionSolution> triangulatedSolution(
            List<SensorMeasurement> measurements,
            double nowSeconds,
            DatalinkState link) {
        BearingPair best = null;
        for (int first = 0; first < measurements.size(); first++) {
            SensorMeasurement a = measurements.get(first);
            for (int second = first + 1; second < measurements.size(); second++) {
                SensorMeasurement b = measurements.get(second);
                if (a.observerId() == b.observerId()) {
                    continue;
                }
                double cross = StrictMath.sin(b.bearingRad() - a.bearingRad());
                double geometry = StrictMath.abs(cross);
                if (geometry < MIN_TRIANGULATION_SINE) {
                    continue;
                }
                BearingPair candidate = new BearingPair(a, b, geometry);
                if (best == null || candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        SensorMeasurement a = best.first();
        SensorMeasurement b = best.second();
        double ax = StrictMath.cos(a.bearingRad());
        double ay = StrictMath.sin(a.bearingRad());
        double bx = StrictMath.cos(b.bearingRad());
        double by = StrictMath.sin(b.bearingRad());
        double rx = b.observerXM() - a.observerXM();
        double ry = b.observerYM() - a.observerYM();
        double denominator = cross(ax, ay, bx, by);
        double ta = cross(rx, ry, bx, by) / denominator;
        double tb = cross(rx, ry, ax, ay) / denominator;
        if (ta <= 0d || tb <= 0d) {
            return Optional.empty();
        }
        double x = a.observerXM() + ta * ax;
        double y = a.observerYM() + ta * ay;
        double geometrySquared = best.geometry() * best.geometry();
        double angularPositionVariance = (
                ta * ta * a.bearingVarianceRad2()
                        + tb * tb * b.bearingVarianceRad2()) / geometrySquared;
        double transport = transportPenalty(a, nowSeconds, link)
                + transportPenalty(b, nowSeconds, link);
        double positionVariance = Math.max(1e-12d, angularPositionVariance + transport);
        return Optional.of(new PositionSolution(x, y, positionVariance, positionVariance));
    }

    private static double transportPenalty(
            SensorMeasurement measurement,
            double nowSeconds,
            DatalinkState link) {
        double age = Math.max(0d, nowSeconds - measurement.timestampSeconds());
        return age * link.transportVarianceM2PerSecond();
    }

    private static InformationState qualityState(
            double classificationConfidence,
            boolean positionKnown,
            TrackCovariance covariance,
            double ageSeconds,
            TrackQualityPolicy policy) {
        if (positionKnown && covariance.positionVarianceM2() != null) {
            double sigmaM = StrictMath.sqrt(covariance.positionVarianceM2());
            if (sigmaM <= policy.fireControlPositionSigmaM()
                    && ageSeconds <= policy.fireControlMaxAgeSeconds()) {
                return InformationState.FIRE_CONTROL;
            }
            if (sigmaM <= policy.trackedPositionSigmaM()
                    && ageSeconds <= policy.trackedMaxAgeSeconds()) {
                return InformationState.TRACKED;
            }
        }
        return classificationConfidence >= policy.classificationConfidenceThreshold()
                ? InformationState.CLASSIFIED
                : InformationState.DETECTED;
    }

    private static InformationState evidenceState(
            SensorDefinition sensor,
            double snr,
            boolean ranged) {
        if (ranged && snr >= sensor.fireControlSnr()) {
            return InformationState.FIRE_CONTROL;
        }
        if (ranged && snr >= sensor.trackSnr()) {
            return InformationState.TRACKED;
        }
        if (snr >= sensor.classificationSnr()) {
            return InformationState.CLASSIFIED;
        }
        return InformationState.DETECTED;
    }

    private static double classificationEvidence(InformationState state) {
        return state.ordinal() >= InformationState.CLASSIFIED.ordinal() ? 1d : 0d;
    }

    private static double inverseVariance(double[] variances) {
        double sum = 0d;
        for (double variance : variances) {
            sum += 1d / variance;
        }
        return 1d / sum;
    }

    private static double inverseSquareReceived(double sourcePowerW, double apertureM2, double rangeM) {
        return sourcePowerW * apertureM2 / (FOUR_PI * rangeM * rangeM);
    }

    private static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    private static double normalizeAngle(double angle) {
        double result = angle;
        while (result > StrictMath.PI) {
            result -= 2d * StrictMath.PI;
        }
        while (result <= -StrictMath.PI) {
            result += 2d * StrictMath.PI;
        }
        return result;
    }

    private static void requirePositiveId(long value, String label) {
        if (value <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private static final Comparator<SensorMeasurement> MEASUREMENT_ORDER = Comparator
            .comparingDouble(SensorMeasurement::timestampSeconds)
            .thenComparingLong(SensorMeasurement::observerId)
            .thenComparing(value -> value.channel().name())
            .thenComparingDouble(SensorMeasurement::bearingRad);

    /**
     * Two-dimensional authoritative geometry in meters.
     *
     * @param xM x coordinate
     * @param yM y coordinate
     */
    public record Position2d(double xM, double yM) {
        /** Validates finite geometry. */
        public Position2d {
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
        }
    }

    /**
     * Observation result including the observer's active emission cost/signature.
     *
     * @param measurement true-target measurement if above detection SNR
     * @param deceptionHypotheses explicit alternative measurements, never randomly selected
     * @param observerEmission additional active RF signature caused by this observation
     * @param additionalPowerDemandW explicit ECCM processing power demand
     */
    public record ObservationResult(
            Optional<SensorMeasurement> measurement,
            List<MeasurementHypothesis> deceptionHypotheses,
            SignatureState observerEmission,
            double additionalPowerDemandW) {
        /** Validates immutable result values. */
        public ObservationResult {
            measurement = Objects.requireNonNull(measurement, "measurement");
            deceptionHypotheses = List.copyOf(Objects.requireNonNull(deceptionHypotheses, "deceptionHypotheses"));
            observerEmission = Objects.requireNonNull(observerEmission, "observerEmission");
            if (!Double.isFinite(additionalPowerDemandW) || additionalPowerDemandW < 0d) {
                throw new IllegalArgumentException("additionalPowerDemandW must be finite and non-negative");
            }
        }
    }

    /**
     * Explicit deceptive association hypothesis.
     *
     * @param hypothesisId stable authored/runtime hypothesis ID
     * @param sourceEmitterId physical deceptive emitter
     * @param apparentMeasurement apparent measurement to evaluate/fuse separately
     */
    public record MeasurementHypothesis(
            String hypothesisId,
            long sourceEmitterId,
            SensorMeasurement apparentMeasurement) {
        /** Validates stable hypothesis identity. */
        public MeasurementHypothesis {
            if (hypothesisId == null || hypothesisId.isBlank()) {
                throw new IllegalArgumentException("hypothesisId must be non-blank");
            }
            requirePositiveId(sourceEmitterId, "sourceEmitterId");
            Objects.requireNonNull(apparentMeasurement, "apparentMeasurement");
        }
    }

    /**
     * Policy converting physical covariance/freshness into shared information states.
     *
     * @param trackedPositionSigmaM maximum one-sigma position error for TRACKED
     * @param fireControlPositionSigmaM maximum one-sigma position error for FIRE_CONTROL
     * @param trackedMaxAgeSeconds maximum TRACKED age
     * @param fireControlMaxAgeSeconds maximum FIRE_CONTROL age
     * @param positionProcessNoiseM2PerSecond covariance growth rate for stale position/range
     * @param bearingProcessNoiseRad2PerSecond covariance growth rate for stale bearing
     * @param classificationConfidenceThreshold threshold for CLASSIFIED when no tactical solution remains
     */
    public record TrackQualityPolicy(
            double trackedPositionSigmaM,
            double fireControlPositionSigmaM,
            double trackedMaxAgeSeconds,
            double fireControlMaxAgeSeconds,
            double positionProcessNoiseM2PerSecond,
            double bearingProcessNoiseRad2PerSecond,
            double classificationConfidenceThreshold) {

        /** Validates monotonic quality thresholds. */
        public TrackQualityPolicy {
            requirePositive(trackedPositionSigmaM, "trackedPositionSigmaM");
            requirePositive(fireControlPositionSigmaM, "fireControlPositionSigmaM");
            if (fireControlPositionSigmaM > trackedPositionSigmaM) {
                throw new IllegalArgumentException("fire-control sigma must be <= tracked sigma");
            }
            requirePositive(trackedMaxAgeSeconds, "trackedMaxAgeSeconds");
            requirePositive(fireControlMaxAgeSeconds, "fireControlMaxAgeSeconds");
            if (fireControlMaxAgeSeconds > trackedMaxAgeSeconds) {
                throw new IllegalArgumentException("fire-control max age must be <= tracked max age");
            }
            requireNonNegative(positionProcessNoiseM2PerSecond, "positionProcessNoiseM2PerSecond");
            requireNonNegative(bearingProcessNoiseRad2PerSecond, "bearingProcessNoiseRad2PerSecond");
            if (!Double.isFinite(classificationConfidenceThreshold)
                    || classificationConfidenceThreshold < 0d || classificationConfidenceThreshold > 1d) {
                throw new IllegalArgumentException("classificationConfidenceThreshold must be finite in [0,1]");
            }
        }

        /** @return conservative deterministic default for unit/integration tests before Stage-20 scale calibration */
        public static TrackQualityPolicy defaultPolicy() {
            return new TrackQualityPolicy(10_000d, 1_000d, 60d, 10d, 400d, 1e-8d, 0.5d);
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

    /** Internal fused Cartesian position/covariance solution. */
    private record PositionSolution(
            double xM,
            double yM,
            double positionVarianceM2,
            double rangeVarianceM2) {
        /**
         * Validates one fused Cartesian position solution.
         *
         * @param xM fused target x coordinate in meters
         * @param yM fused target y coordinate in meters
         * @param positionVarianceM2 positive Cartesian position variance
         * @param rangeVarianceM2 positive range variance retained for downstream covariance reporting
         */
        private PositionSolution {
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            if (!Double.isFinite(positionVarianceM2) || positionVarianceM2 <= 0d
                    || !Double.isFinite(rangeVarianceM2) || rangeVarianceM2 <= 0d) {
                throw new IllegalArgumentException("position solution variances must be finite and positive");
            }
        }
    }

    /** Internal candidate pair used to select deterministic two-bearing triangulation geometry. */
    private record BearingPair(
            SensorMeasurement first,
            SensorMeasurement second,
            double geometry) implements Comparable<BearingPair> {
        /**
         * Validates one candidate two-bearing triangulation pair.
         *
         * @param first first bearing measurement
         * @param second second bearing measurement from a distinct observer
         * @param geometry positive absolute sine of bearing intersection angle
         */
        private BearingPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (!Double.isFinite(geometry) || geometry <= 0d) {
                throw new IllegalArgumentException("geometry must be finite and positive");
            }
        }

        @Override
        public int compareTo(BearingPair other) {
            int geometryOrder = -Double.compare(geometry, other.geometry);
            if (geometryOrder != 0) {
                return geometryOrder;
            }
            int firstOrder = Long.compare(first.observerId(), other.first.observerId());
            if (firstOrder != 0) {
                return firstOrder;
            }
            return Long.compare(second.observerId(), other.second.observerId());
        }
    }
}

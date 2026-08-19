package com.spacesim.world.calibration;

import com.spacesim.ship.ElectronicWarfareState;
import com.spacesim.ship.SensorDefinition;
import com.spacesim.ship.SensorMeasurement;
import com.spacesim.ship.SensorRuntimeState;
import com.spacesim.ship.ShipSensorRuntime;
import com.spacesim.ship.SignatureState;
import com.spacesim.ship.TrackState.InformationState;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Measures Stage-20 sensor information-state distance boundaries through the production sensor runtime.
 *
 * <p>This calculator deliberately does not derive or store a hard sensor range. It searches physical
 * separation until {@link ShipSensorRuntime#observe(long, long, SensorDefinition, SensorRuntimeState,
 * ShipSensorRuntime.Position2d, ShipSensorRuntime.Position2d, SignatureState, ElectronicWarfareState,
 * double)} stops supplying the requested information state for the supplied physical sensor,
 * signature, damage/runtime state and EW environment.</p>
 */
public final class Stage20SensorCalibrationCalculator {
    private static final long OBSERVER_ID = 20_001L;
    private static final long TARGET_ID = 20_002L;
    private static final double MIN_PROBE_DISTANCE_M = 1d;
    private static final int MAX_BRACKET_STEPS = 160;
    private static final int BISECTION_STEPS = 96;
    private static final ShipSensorRuntime RUNTIME = new ShipSensorRuntime();
    private static final ShipSensorRuntime.Position2d OBSERVER = new ShipSensorRuntime.Position2d(0d, 0d);

    private Stage20SensorCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /**
     * Measures the maximum physical separation supporting each measurement evidence state.
     *
     * <p>Bearing-only sensors can legitimately return empty TRACKED/FIRE_CONTROL boundaries because
     * one passive bearing does not invent range. Distributed passive triangulation remains a separate
     * geometry problem rather than being hidden inside a scalar range.</p>
     *
     * @param sensor physical fitted sensor definition
     * @param runtimeState current physical/processing sensor state
     * @param targetSignature physical channelized target signature
     * @param ewState physical jammer/deception environment
     * @return deterministic information-state distance boundaries
     */
    public static ThresholdDistances deriveThresholdDistances(
            SensorDefinition sensor,
            SensorRuntimeState runtimeState,
            SignatureState targetSignature,
            ElectronicWarfareState ewState) {
        SensorDefinition checkedSensor = Objects.requireNonNull(sensor, "sensor");
        SensorRuntimeState checkedRuntime = Objects.requireNonNull(runtimeState, "runtimeState");
        SignatureState checkedSignature = Objects.requireNonNull(targetSignature, "targetSignature");
        ElectronicWarfareState checkedEw = Objects.requireNonNull(ewState, "ewState");
        return new ThresholdDistances(
                maxDistanceFor(checkedSensor, checkedRuntime, checkedSignature, checkedEw, InformationState.DETECTED),
                maxDistanceFor(checkedSensor, checkedRuntime, checkedSignature, checkedEw, InformationState.CLASSIFIED),
                maxDistanceFor(checkedSensor, checkedRuntime, checkedSignature, checkedEw, InformationState.TRACKED),
                maxDistanceFor(checkedSensor, checkedRuntime, checkedSignature, checkedEw, InformationState.FIRE_CONTROL));
    }

    private static OptionalDouble maxDistanceFor(
            SensorDefinition sensor,
            SensorRuntimeState runtimeState,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            InformationState requiredState) {
        if (!supportsAtDistance(sensor, runtimeState, targetSignature, ewState, requiredState, MIN_PROBE_DISTANCE_M)) {
            return OptionalDouble.empty();
        }

        double low = MIN_PROBE_DISTANCE_M;
        double high = MIN_PROBE_DISTANCE_M * 2d;
        int bracketSteps = 0;
        while (supportsAtDistance(sensor, runtimeState, targetSignature, ewState, requiredState, high)) {
            low = high;
            high *= 2d;
            bracketSteps++;
            if (!Double.isFinite(high) || bracketSteps >= MAX_BRACKET_STEPS) {
                throw new IllegalStateException("Unable to bracket sensor boundary for "
                        + sensor.id() + " / " + requiredState);
            }
        }

        for (int step = 0; step < BISECTION_STEPS; step++) {
            double midpoint = low + (high - low) * 0.5d;
            if (supportsAtDistance(sensor, runtimeState, targetSignature, ewState, requiredState, midpoint)) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return OptionalDouble.of(low);
    }

    private static boolean supportsAtDistance(
            SensorDefinition sensor,
            SensorRuntimeState runtimeState,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            InformationState requiredState,
            double distanceM) {
        SensorMeasurement measurement = RUNTIME.observe(
                        OBSERVER_ID,
                        TARGET_ID,
                        sensor,
                        runtimeState,
                        OBSERVER,
                        new ShipSensorRuntime.Position2d(distanceM, 0d),
                        targetSignature,
                        ewState,
                        0d)
                .measurement()
                .orElse(null);
        return measurement != null && measurement.evidenceState().ordinal() >= requiredState.ordinal();
    }

    /**
     * Physical separation boundaries for the four ordered sensor information states.
     *
     * @param detectedMaxDistanceM farthest measured DETECTED separation, if any
     * @param classifiedMaxDistanceM farthest measured CLASSIFIED separation, if any
     * @param trackedMaxDistanceM farthest measured TRACKED separation, if any
     * @param fireControlMaxDistanceM farthest measured FIRE_CONTROL separation, if any
     */
    public record ThresholdDistances(
            OptionalDouble detectedMaxDistanceM,
            OptionalDouble classifiedMaxDistanceM,
            OptionalDouble trackedMaxDistanceM,
            OptionalDouble fireControlMaxDistanceM) {
        /**
         * Validates presence hierarchy and monotonically shrinking stronger-information envelopes.
         *
         * @param detectedMaxDistanceM farthest DETECTED separation
         * @param classifiedMaxDistanceM farthest CLASSIFIED separation
         * @param trackedMaxDistanceM farthest TRACKED separation
         * @param fireControlMaxDistanceM farthest FIRE_CONTROL separation
         */
        public ThresholdDistances {
            Objects.requireNonNull(detectedMaxDistanceM, "detectedMaxDistanceM");
            Objects.requireNonNull(classifiedMaxDistanceM, "classifiedMaxDistanceM");
            Objects.requireNonNull(trackedMaxDistanceM, "trackedMaxDistanceM");
            Objects.requireNonNull(fireControlMaxDistanceM, "fireControlMaxDistanceM");
            validateDistance(detectedMaxDistanceM, "detectedMaxDistanceM");
            validateDistance(classifiedMaxDistanceM, "classifiedMaxDistanceM");
            validateDistance(trackedMaxDistanceM, "trackedMaxDistanceM");
            validateDistance(fireControlMaxDistanceM, "fireControlMaxDistanceM");
            requireNested(detectedMaxDistanceM, classifiedMaxDistanceM, "classification");
            requireNested(classifiedMaxDistanceM, trackedMaxDistanceM, "track");
            requireNested(trackedMaxDistanceM, fireControlMaxDistanceM, "fire control");
        }

        private static void validateDistance(OptionalDouble value, String label) {
            if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() <= 0d)) {
                throw new IllegalArgumentException(label + " must be finite and positive when present");
            }
        }

        private static void requireNested(OptionalDouble weaker, OptionalDouble stronger, String label) {
            if (stronger.isPresent() && weaker.isEmpty()) {
                throw new IllegalArgumentException(label + " cannot exist without the weaker evidence state");
            }
            if (stronger.isPresent() && stronger.getAsDouble() > weaker.getAsDouble()) {
                throw new IllegalArgumentException(label + " distance cannot exceed the weaker evidence distance");
            }
        }
    }
}

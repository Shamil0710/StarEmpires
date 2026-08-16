package com.spacesim.ship;

import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.ObservationResult;
import com.spacesim.ship.ShipSensorRuntime.Position2d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Common player/AI Stage-17.5D observation seam.
 *
 * <p>Observation is deliberately two-phase. {@link #planOperation} exposes the exact incremental
 * shared-bus power and heat required by the selected fitted mode. The caller must obtain a physical
 * grant from the common engineering operating budget before {@link #execute} can emit or measure.
 * This prevents a UI or AI command from bypassing power/thermal accounting while keeping sensor
 * physics independent from ownership.</p>
 */
public final class ShipObservationService {
    private static final double EPSILON = 1e-9;
    private final ShipSensorRuntime runtime;

    /** Creates the common service with the production sensor solver. */
    public ShipObservationService() {
        this(new ShipSensorRuntime());
    }

    /**
     * Creates a service around an explicit deterministic solver.
     *
     * @param runtime physical sensor solver
     */
    public ShipObservationService(ShipSensorRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Plans the incremental operating budget for one fitted sensor mode.
     *
     * @param sensor fitted physical sensor mode
     * @param state current sensor mode/ECCM state
     * @return immutable operating request that must be granted before observation
     */
    public OperationPlan planOperation(FittedSensor sensor, SensorRuntimeState state) {
        FittedSensor fitted = Objects.requireNonNull(sensor, "sensor");
        SensorRuntimeState runtimeState = Objects.requireNonNull(state, "state");
        SensorDefinition definition = fitted.definition();
        if (!runtimeState.enabled() || runtimeState.apertureFraction() <= 0d) {
            return new OperationPlan(fitted.mountId(), fitted.moduleId(), definition.id(), 0d, 0d, false);
        }
        double powerW = definition.mode() == SensorDefinition.Mode.ACTIVE_RADAR
                ? definition.activeModePowerDemandW() : 0d;
        double heatW = definition.mode() == SensorDefinition.Mode.ACTIVE_RADAR
                ? definition.activeModeWasteHeatW() : 0d;
        if (runtimeState.eccmEnabled()) {
            powerW += definition.eccmPowerDemandW();
            heatW += definition.eccmWasteHeatW();
        }
        return new OperationPlan(
                fitted.mountId(), fitted.moduleId(), definition.id(), powerW, heatW, true);
    }

    /**
     * Executes one observation only when the shared engineering layer granted its operating load.
     *
     * @param plan previously computed operating request
     * @param grant common engineering power/thermal grant
     * @param sensor fitted sensor used to create the plan
     * @param state current sensor state
     * @param observerId observing entity identity
     * @param targetId target system-local entity identity
     * @param observer observer position
     * @param target target position
     * @param targetSignature target physical signature
     * @param ewState physical EW environment
     * @param timestampSeconds authoritative observation time
     * @return observation execution; denied grants produce no measurement/emission
     */
    public ExecutionResult execute(
            OperationPlan plan,
            EngineeringGrant grant,
            FittedSensor sensor,
            SensorRuntimeState state,
            long observerId,
            long targetId,
            Position2d observer,
            Position2d target,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            double timestampSeconds) {
        OperationPlan request = Objects.requireNonNull(plan, "plan");
        EngineeringGrant permission = Objects.requireNonNull(grant, "grant");
        FittedSensor fitted = Objects.requireNonNull(sensor, "sensor");
        SensorRuntimeState runtimeState = Objects.requireNonNull(state, "state");
        if (!request.mountId().equals(fitted.mountId())
                || !request.moduleId().equals(fitted.moduleId())
                || !request.sensorDefinitionId().equals(fitted.definition().id())) {
            throw new IllegalArgumentException("operation plan does not match fitted sensor");
        }
        if (!request.enabled()) {
            return denied(request);
        }
        if (permission.grantedPowerW() + EPSILON < request.requiredPowerW()
                || permission.grantedHeatW() + EPSILON < request.requiredHeatW()) {
            return denied(request);
        }
        ObservationResult observation = runtime.observe(
                observerId,
                targetId,
                fitted.definition(),
                runtimeState,
                Objects.requireNonNull(observer, "observer"),
                Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(targetSignature, "targetSignature"),
                Objects.requireNonNull(ewState, "ewState"),
                timestampSeconds);
        return new ExecutionResult(
                true,
                observation.measurement(),
                observation.deceptionHypotheses(),
                observation.observerEmission(),
                request.requiredPowerW(),
                request.requiredHeatW());
    }

    private static ExecutionResult denied(OperationPlan request) {
        return new ExecutionResult(
                false, Optional.empty(), List.of(), SignatureState.zero(),
                request.requiredPowerW(), request.requiredHeatW());
    }

    /**
     * Physical operating request for one selected fitted sensor mode.
     *
     * @param mountId physical module mount
     * @param moduleId installed module content ID
     * @param sensorDefinitionId fitted mode definition ID
     * @param requiredPowerW incremental shared-bus electrical demand
     * @param requiredHeatW incremental heat generated at the sensor mount
     * @param enabled whether the selected physical mode can operate at all
     */
    public record OperationPlan(
            String mountId,
            String moduleId,
            String sensorDefinitionId,
            double requiredPowerW,
            double requiredHeatW,
            boolean enabled) {
        /**
         * Validates one operating request.
         *
         * @param mountId physical module mount
         * @param moduleId installed module content ID
         * @param sensorDefinitionId fitted mode definition ID
         * @param requiredPowerW incremental shared-bus electrical demand
         * @param requiredHeatW incremental heat generated at the sensor mount
         * @param enabled whether the selected physical mode can operate at all
         */
        public OperationPlan {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            requireNonBlank(sensorDefinitionId, "sensorDefinitionId");
            requireNonNegative(requiredPowerW, "requiredPowerW");
            requireNonNegative(requiredHeatW, "requiredHeatW");
        }
    }

    /**
     * Grant returned by the common engineering operating budget.
     *
     * @param grantedPowerW power physically available for this operation
     * @param grantedHeatW heat load accepted by current thermal topology
     */
    public record EngineeringGrant(double grantedPowerW, double grantedHeatW) {
        /**
         * Validates non-negative finite granted capacity.
         *
         * @param grantedPowerW granted power
         * @param grantedHeatW granted heat acceptance
         */
        public EngineeringGrant {
            requireNonNegative(grantedPowerW, "grantedPowerW");
            requireNonNegative(grantedHeatW, "grantedHeatW");
        }

        /** @return a zero-capacity denied grant */
        public static EngineeringGrant denied() {
            return new EngineeringGrant(0d, 0d);
        }
    }

    /**
     * One executed or physically denied observation.
     *
     * @param executed whether physical operation was granted and attempted
     * @param measurement true-target measurement when detected
     * @param deceptionHypotheses explicit deceptive alternatives
     * @param observerEmission operational RF signature created by the observation
     * @param consumedPowerW granted incremental electrical load
     * @param generatedHeatW granted incremental heat load
     */
    public record ExecutionResult(
            boolean executed,
            Optional<SensorMeasurement> measurement,
            List<ShipSensorRuntime.MeasurementHypothesis> deceptionHypotheses,
            SignatureState observerEmission,
            double consumedPowerW,
            double generatedHeatW) {
        /**
         * Freezes one observation result.
         *
         * @param executed whether physical operation was granted and attempted
         * @param measurement true-target measurement when detected
         * @param deceptionHypotheses explicit deceptive alternatives
         * @param observerEmission operational RF signature created by the observation
         * @param consumedPowerW granted incremental electrical load
         * @param generatedHeatW granted incremental heat load
         */
        public ExecutionResult {
            measurement = Objects.requireNonNull(measurement, "measurement");
            deceptionHypotheses = List.copyOf(Objects.requireNonNull(deceptionHypotheses, "deceptionHypotheses"));
            observerEmission = Objects.requireNonNull(observerEmission, "observerEmission");
            requireNonNegative(consumedPowerW, "consumedPowerW");
            requireNonNegative(generatedHeatW, "generatedHeatW");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.ship.ShipObservationService.ExecutionResult;
import com.spacesim.ship.ShipObservationService.OperationPlan;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;

import java.util.Objects;

/**
 * Ownership-neutral Stage-17.5H observation facade that closes the sensor power/heat commit seam.
 *
 * <p>Callers supply physical geometry/EW state and one fitted sensor, but they cannot manufacture an
 * {@link ShipObservationService.EngineeringGrant}. The facade plans the fitted mode, obtains and
 * commits a grant through {@link ShipEngineeringGrantService}, then executes the same Stage-17.5D
 * sensor solver. Player and AI therefore share both information physics and engineering budgets.</p>
 */
public final class ShipObservationEngineeringService {
    private final ShipObservationService observations;
    private final ShipEngineeringGrantService grants;

    /**
     * Creates the common production observation facade.
     *
     * @param catalog production engineering catalog used for physical grant accounting
     */
    public ShipObservationEngineeringService(ShipEngineeringCatalog catalog) {
        this(new ShipObservationService(), new ShipEngineeringGrantService(catalog));
    }

    /**
     * Creates the facade around explicit deterministic collaborators for tests/integration.
     *
     * @param observations fitted sensor planning/execution service
     * @param grants common physical engineering grant service
     */
    public ShipObservationEngineeringService(
            ShipObservationService observations,
            ShipEngineeringGrantService grants) {
        this.observations = Objects.requireNonNull(observations, "observations");
        this.grants = Objects.requireNonNull(grants, "grants");
    }

    /**
     * Plans, physically admits/commits and executes one fitted sensor operation.
     *
     * @param engineering authoritative observing ship engineering component
     * @param sensor fitted physical sensor mode
     * @param state current sensor/ECCM state
     * @param operationDurationSeconds positive interval over which incremental power/heat apply
     * @param observerId observing entity ID
     * @param targetId target entity ID
     * @param observer observing ship position
     * @param target target position
     * @param targetSignature target physical signature
     * @param ewState current physical EW state
     * @param timestampSeconds authoritative observation timestamp
     * @return executed or physically denied observation result
     */
    public ExecutionResult observe(
            EngineeringComponent engineering,
            FittedSensor sensor,
            SensorRuntimeState state,
            double operationDurationSeconds,
            long observerId,
            long targetId,
            Position2d observer,
            Position2d target,
            SignatureState targetSignature,
            ElectronicWarfareState ewState,
            double timestampSeconds) {
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        FittedSensor fitted = Objects.requireNonNull(sensor, "sensor");
        SensorRuntimeState runtimeState = Objects.requireNonNull(state, "state");
        OperationPlan plan = observations.planOperation(fitted, runtimeState);
        if (!plan.enabled()) {
            return observations.execute(
                    plan,
                    ShipObservationService.EngineeringGrant.denied(),
                    fitted,
                    runtimeState,
                    observerId,
                    targetId,
                    Objects.requireNonNull(observer, "observer"),
                    Objects.requireNonNull(target, "target"),
                    Objects.requireNonNull(targetSignature, "targetSignature"),
                    Objects.requireNonNull(ewState, "ewState"),
                    timestampSeconds);
        }
        ShipEngineeringGrantService.GrantResult grant = grants.grantAndCommit(
                component,
                plan.mountId(),
                plan.requiredPowerW(),
                plan.requiredHeatW(),
                operationDurationSeconds);
        return observations.execute(
                plan,
                grant.grant(),
                fitted,
                runtimeState,
                observerId,
                targetId,
                Objects.requireNonNull(observer, "observer"),
                Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(targetSignature, "targetSignature"),
                Objects.requireNonNull(ewState, "ewState"),
                timestampSeconds);
    }
}

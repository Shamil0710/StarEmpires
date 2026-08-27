package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;

/** Test-only helpers for scenarios whose focus starts at the ordinary FTL spool boundary. */
public final class GeneratedWorldFtlTestSupport {
    private static final int MAX_FITTED_RECOVERY_STEPS = 10_000;
    private static final float FITTED_RECOVERY_STEP_SECONDS = 0.25f;

    private GeneratedWorldFtlTestSupport() {
        throw new AssertionError("No instances");
    }

    /**
     * Places one generated in-system fleet exactly at the persisted outgoing endpoint for a direct
     * edge. Production movement is not bypassed: this helper exists only for acceptance tests whose
     * subject is transit/cooldown/command behavior rather than the now separately-covered local
     * hub-to-FTL traversal.
     *
     * <p>Fitted fleets are advanced through ordinary idle simulation until the existing production
     * FTL resolver reaches its spool boundary. Only transient cooldown, thermal and recharge
     * conditions may be waited out. Permanent capability/mass failures remain immediate test
     * failures; this helper never repairs damage, refills stores or rewrites engineering state.</p>
     *
     * @param runtime generated runtime owning exact persisted endpoint authority
     * @param fleetId local fleet to position
     * @param destination direct neighboring destination
     */
    public static void placeAtOutgoingEndpoint(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime checked = java.util.Objects.requireNonNull(
                runtime, "runtime");
        FleetId checkedFleetId = java.util.Objects.requireNonNull(fleetId, "fleetId");
        awaitFittedJumpBoundary(checked, checkedFleetId);

        FleetPlacementState placement = checked.world().findFleet(checkedFleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("test departure positioning requires an in-system fleet");
        }
        StarSystemId origin = placement.systemId();
        StarSystemId target = java.util.Objects.requireNonNull(destination, "destination");
        if (!checked.world().getTopology().neighbors(origin).contains(target)) {
            throw new IllegalArgumentException("test departure positioning requires a direct topology edge");
        }
        LocalPhysicalPosition outgoing = checked.arrival()
                .resolve(target, origin)
                .physicalState()
                .position();
        checked.arrival().materialization(origin).updatePhysicalState(
                placement.localEntityId(), LocalPhysicalKinematics.stationary(outgoing));
    }

    private static void awaitFittedJumpBoundary(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        ProductionFittedJumpResolver resolver = new ProductionFittedJumpResolver();
        for (int attempt = 0; attempt < MAX_FITTED_RECOVERY_STEPS; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                throw new IllegalStateException("test FTL recovery requires an in-system fleet");
            }
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (engineering == null) {
                return;
            }

            var plan = resolver.plan(engineering);
            if (plan.allowed()) {
                return;
            }
            JumpFailure failure = plan.failure();
            if (failure != JumpFailure.COOLDOWN_ACTIVE
                    && failure != JumpFailure.THERMAL_LIMIT
                    && failure != JumpFailure.CHARGE_POWER_UNAVAILABLE
                    && failure != JumpFailure.STORED_ENERGY_UNAVAILABLE) {
                throw new AssertionError(
                        "fitted fleet cannot reach the ordinary FTL spool boundary: " + fleetId + ": " + failure);
            }
            runtime.advanceFrame(FITTED_RECOVERY_STEP_SECONDS);
        }
        throw new AssertionError(
                "fitted fleet did not recover to the ordinary FTL spool boundary within the bounded idle window: "
                        + fleetId);
    }
}

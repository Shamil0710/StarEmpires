package com.spacesim.world;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;

/** Test-only helpers for scenarios whose focus starts at the ordinary FTL spool boundary. */
public final class GeneratedWorldFtlTestSupport {
    private GeneratedWorldFtlTestSupport() {
        throw new AssertionError("No instances");
    }

    /**
     * Places one generated in-system fleet exactly at the persisted outgoing endpoint for a direct
     * edge. Production movement is not bypassed: this helper exists only for acceptance tests whose
     * subject is transit/cooldown/command behavior rather than the now separately-covered local
     * hub-to-FTL traversal.
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
        FleetPlacementState placement = checked.world().findFleet(
                java.util.Objects.requireNonNull(fleetId, "fleetId")).orElseThrow();
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
}

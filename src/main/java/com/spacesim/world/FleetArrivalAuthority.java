package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.List;
import java.util.Objects;

/**
 * Optional exact physical endpoint bridge consumed by the ordinary {@link FleetJumpService}.
 *
 * <p>The jump FSM remains authoritative for topology, local approach, energy, spool, detached
 * transit and cooldown. This seam supplies persisted generated-world geometry to that same FSM. It
 * replaces legacy destination float placeholders with exact Stage-20 endpoints and may additionally
 * give the existing {@code MOVING_TO_JUMP} phase a physical origin-local approach to the outgoing
 * endpoint. Implementations that do not own exact local geometry retain the historical timing path
 * through the default approach methods.</p>
 */
public interface FleetArrivalAuthority {
    /**
     * Resolves the exact persisted endpoint for one ordinary direct edge.
     *
     * @param origin explicit direct-edge origin
     * @param destination explicit direct-edge destination
     * @return exact destination authority and non-authoritative legacy render projection
     */
    ResolvedArrival resolve(StarSystemId origin, StarSystemId destination);

    /**
     * Begins the origin-local physical approach to the outgoing endpoint for one direct jump.
     *
     * <p>The default returns zero, instructing the ordinary jump FSM to retain its historical
     * approach timing and perform no exact local movement. Generated-world implementations with
     * persisted local geometry return a positive deterministic tick count and may initialize the
     * exact local kinematics used by subsequent progress callbacks.</p>
     *
     * @param fleetId stable world fleet identity
     * @param originSystemId current local system
     * @param destinationSystemId directly connected destination
     * @param localEntityId current origin-local persistent entity identity
     * @param worldTick authoritative request tick
     * @param fixedStepSeconds authoritative local fixed step
     * @return positive physical approach ticks, or zero to use the legacy timing path
     */
    default long beginDepartureApproach(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            EntityId localEntityId,
            long worldTick,
            float fixedStepSeconds) {
        return 0L;
    }

    /**
     * Advances an exact origin-local approach without creating another movement lifecycle.
     *
     * <p>The ordinary jump FSM calls this only while the same persistent operation is in
     * {@link FleetJumpPhase#MOVING_TO_JUMP}. The default is intentionally a no-op for worlds without
     * Stage-20 exact local geometry.</p>
     *
     * @param fleetId stable world fleet identity
     * @param originSystemId current local system
     * @param destinationSystemId directly connected destination
     * @param localEntityId current origin-local persistent entity identity
     * @param previousWorldTick authoritative previous world tick
     * @param worldTick authoritative current world tick
     * @param phaseEndsTick exact MOVING_TO_JUMP boundary tick
     * @param fixedStepSeconds authoritative local fixed step
     */
    default void advanceDepartureApproach(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            EntityId localEntityId,
            long previousWorldTick,
            long worldTick,
            long phaseEndsTick,
            float fixedStepSeconds) {
        // Compatibility path: no exact local geometry is owned by this authority.
    }

    /**
     * Preflights the complete remaining route against finite local-propulsion resources.
     *
     * <p>The default reports an unsupported compatibility seam. Exact generated-world authorities
     * override this to evaluate every origin-local approach before the first hop is committed.</p>
     *
     * @param fleetId stable physical fleet identity
     * @param orderedSystems complete remaining neighbor route beginning at the current system
     * @return immutable physical fuel-plan result
     */
    default RouteFuelPlan planRouteFuel(FleetId fleetId, List<StarSystemId> orderedSystems) {
        return RouteFuelPlan.compatibility();
    }

    /**
     * Fails closed unless the fleet has physically reached the exact outgoing endpoint.
     *
     * <p>The ordinary jump FSM invokes this immediately before FTL consequences are committed and
     * the fleet is detached. The default is a compatibility no-op for worlds without exact local
     * geometry. Generated-world implementations use it as the hard invariant that prevents a
     * delayed callback, malformed restore or other runtime bug from teleporting a fleet into FTL
     * before it reaches the requested lane.</p>
     *
     * @param fleetId stable world fleet identity
     * @param originSystemId current local system
     * @param destinationSystemId directly connected destination
     * @param localEntityId current origin-local persistent entity identity
     */
    default void validateDepartureReady(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            EntityId localEntityId) {
        // Compatibility path: no exact local geometry is owned by this authority.
    }

    /**
     * Releases origin-local physical authority after the ordinary fleet service has detached it.
     *
     * @param fleetId stable world fleet identity
     * @param originSystemId exact edge origin
     * @param formerLocalEntityId detached origin-local entity identity
     */
    void onDeparted(
            FleetId fleetId,
            StarSystemId originSystemId,
            EntityId formerLocalEntityId);

    /**
     * Applies exact physical authority after ordinary attach allocated a destination-local ID.
     *
     * @param fleetId unchanged world fleet identity
     * @param arrival exact resolved persisted endpoint
     * @param destinationLocalEntityId freshly allocated destination-local identity
     */
    void onArrived(
            FleetId fleetId,
            ResolvedArrival arrival,
            EntityId destinationLocalEntityId);

    /**
     * Immutable full-route reaction-mass preflight.
     *
     * @param supported whether exact route-fuel authority is available for this world
     * @param feasible whether the complete route fits inside the protected physical fuel budget
     * @param requiredDeltaVMps total local-maneuver delta-v required by the remaining route
     * @param consumedReactionMassKg estimated reaction mass consumed by that maneuver budget
     * @param remainingReactionMassKg estimated reaction mass remaining after the route
     * @param reason stable diagnostic reason for unsupported or infeasible plans
     */
    record RouteFuelPlan(
            boolean supported,
            boolean feasible,
            double requiredDeltaVMps,
            double consumedReactionMassKg,
            double remainingReactionMassKg,
            String reason) {
        /**
         * Validates one immutable route-fuel plan.
         *
         * @param supported whether exact route-fuel authority is available
         * @param feasible whether the route is physically feasible
         * @param requiredDeltaVMps required local-maneuver delta-v
         * @param consumedReactionMassKg estimated consumed reaction mass
         * @param remainingReactionMassKg estimated remaining reaction mass
         * @param reason diagnostic reason, normalized to empty text when absent
         */
        public RouteFuelPlan {
            if (!Double.isFinite(requiredDeltaVMps) || requiredDeltaVMps < 0d
                    || !Double.isFinite(consumedReactionMassKg) || consumedReactionMassKg < 0d
                    || !Double.isFinite(remainingReactionMassKg) || remainingReactionMassKg < 0d) {
                throw new IllegalArgumentException("route fuel scalars must be finite and non-negative");
            }
            reason = reason == null ? "" : reason;
        }

        /**
         * Returns the historical compatibility result for worlds without exact route geometry.
         *
         * @return supported=false compatibility plan that does not block legacy movement
         */
        public static RouteFuelPlan compatibility() {
            return new RouteFuelPlan(false, true, 0d, 0d, 0d, "route fuel planning unsupported");
        }
    }

    /**
     * Exact physical endpoint plus a compatibility projection for the legacy float transform.
     *
     * @param originSystemId direct edge origin
     * @param destinationSystemId direct edge destination
     * @param endpointId stable saved arrival anchor identity
     * @param physicalState authoritative hierarchical/double arrival position and velocity
     * @param legacyProjectionX non-authoritative local float-render projection
     * @param legacyProjectionY non-authoritative local float-render projection
     */
    record ResolvedArrival(
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            String endpointId,
            LocalPhysicalKinematics physicalState,
            float legacyProjectionX,
            float legacyProjectionY) {
        /**
         * Validates one exact direct-edge destination authority.
         *
         * @param originSystemId direct edge origin
         * @param destinationSystemId direct edge destination
         * @param endpointId stable saved arrival anchor identity
         * @param physicalState authoritative arrival kinematics
         * @param legacyProjectionX non-authoritative legacy X projection
         * @param legacyProjectionY non-authoritative legacy Y projection
         */
        public ResolvedArrival {
            Objects.requireNonNull(originSystemId, "originSystemId");
            Objects.requireNonNull(destinationSystemId, "destinationSystemId");
            if (originSystemId.equals(destinationSystemId)) {
                throw new IllegalArgumentException("arrival authority must cross one direct edge");
            }
            if (endpointId == null || endpointId.isBlank()) {
                throw new IllegalArgumentException("endpointId must be non-blank");
            }
            Objects.requireNonNull(physicalState, "physicalState");
            if (!Float.isFinite(legacyProjectionX) || !Float.isFinite(legacyProjectionY)) {
                throw new IllegalArgumentException("legacy arrival projection must be finite");
            }
        }
    }
}

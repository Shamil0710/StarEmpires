package com.spacesim.world;

import com.spacesim.persistence.EntityId;

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

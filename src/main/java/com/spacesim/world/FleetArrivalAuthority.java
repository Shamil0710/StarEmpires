package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Optional exact physical-arrival bridge consumed by the ordinary {@link FleetJumpService}.
 *
 * <p>The jump FSM remains authoritative for topology, energy, spool, transit and cooldown. This
 * seam changes only destination-local physical placement, replacing the legacy float placeholder
 * with a persisted Stage-20 endpoint and carrying that authority across the local EntityId change.</p>
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

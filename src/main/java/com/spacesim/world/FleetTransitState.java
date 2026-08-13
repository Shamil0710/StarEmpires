package com.spacesim.world;

import com.spacesim.persistence.EntityState;

import java.util.Objects;

/**
 * Persistent fleet snapshot between two local systems.
 *
 * @param originSystemId origin system
 * @param destinationSystemId destination system
 * @param entityState detached entity snapshot
 */
public record FleetTransitState(
        StarSystemId originSystemId,
        StarSystemId destinationSystemId,
        EntityState entityState) {
    /**
     * @param originSystemId origin system
     * @param destinationSystemId destination system
     * @param entityState detached entity snapshot
     */
    public FleetTransitState {
        Objects.requireNonNull(originSystemId, "Transit origin system is required");
        Objects.requireNonNull(destinationSystemId, "Transit destination system is required");
        Objects.requireNonNull(entityState, "Transit entity state is required");
        if (originSystemId.equals(destinationSystemId)) {
            throw new IllegalArgumentException("Transit must change system");
        }
        if (entityState.identity() == null || !"FLEET".equals(entityState.identity().kindName())) {
            throw new IllegalArgumentException("Transit state must describe a fleet");
        }
    }
}

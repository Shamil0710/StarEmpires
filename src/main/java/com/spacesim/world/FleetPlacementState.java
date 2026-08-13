package com.spacesim.world;

import com.spacesim.persistence.EntityId;

/**
 * Persistent placement of one fleet in the world layer.
 *
 * @param id stable fleet identifier
 * @param locationKind location category
 * @param systemId local system or null
 * @param localEntityId local entity identifier or null
 * @param transitState travel snapshot or null
 */
public record FleetPlacementState(
        FleetId id,
        FleetLocationKind locationKind,
        StarSystemId systemId,
        EntityId localEntityId,
        FleetTransitState transitState) implements Comparable<FleetPlacementState> {
    @Override
    public int compareTo(FleetPlacementState other) {
        return id.compareTo(other.id);
    }
}

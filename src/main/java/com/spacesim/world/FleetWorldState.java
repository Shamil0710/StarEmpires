package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Persistent world-level location одного fleet.
 *
 * <p>IN_SYSTEM хранит system-local EntityId и не имеет transit state. IN_TRANSIT не имеет local
 * EntityId вообще и хранит detached snapshot в FleetTransitState. Эти взаимоисключающие формы
 * являются основным Stage-10A invariant против одновременного существования fleet в двух systems.</p>
 */
public record FleetWorldState(
        FleetId id,
        FleetLocationKind locationKind,
        StarSystemId systemId,
        EntityId localEntityId,
        FleetTransitState transitState) implements Comparable<FleetWorldState> {

    /** Валидирует взаимоисключающие location representations. */
    public FleetWorldState {
        Objects.requireNonNull(id, "FleetId не задан");
        Objects.requireNonNull(locationKind, "Fleet location kind не задан");
        if (locationKind == FleetLocationKind.IN_SYSTEM) {
            Objects.requireNonNull(systemId, "IN_SYSTEM fleet не имеет StarSystemId");
            Objects.requireNonNull(localEntityId, "IN_SYSTEM fleet не имеет local EntityId");
            if (transitState != null) {
                throw new IllegalArgumentException("IN_SYSTEM fleet не может иметь transit state");
            }
        } else {
            if (systemId != null || localEntityId != null) {
                throw new IllegalArgumentException("IN_TRANSIT fleet не может иметь local location");
            }
            Objects.requireNonNull(transitState, "IN_TRANSIT fleet не имеет transit state");
        }
    }

    /** Создаёт local location state. */
    public static FleetWorldState inSystem(FleetId id, StarSystemId systemId, EntityId entityId) {
        return new FleetWorldState(id, FleetLocationKind.IN_SYSTEM, systemId, entityId, null);
    }

    /** Создаёт transit location state. */
    public static FleetWorldState inTransit(FleetId id, FleetTransitState transitState) {
        return new FleetWorldState(id, FleetLocationKind.IN_TRANSIT, null, null, transitState);
    }

    @Override
    public int compareTo(FleetWorldState other) {
        return id.compareTo(other.id);
    }
}

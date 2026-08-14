package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Persistent reference to one physical station owned by the player.
 *
 * <p>Ownership is intentionally kept in the playable envelope instead of {@code WorldState}.
 * The referenced station remains an ordinary world entity and may keep an independent legal or
 * faction affiliation.</p>
 *
 * @param systemId system containing the station entity
 * @param stationEntityId stable system-local station entity ID
 */
public record OwnedStationRef(
        StarSystemId systemId,
        EntityId stationEntityId) implements Comparable<OwnedStationRef> {

    /**
     * Validates a persistent owned-station reference.
     *
     * @param systemId system containing the station entity
     * @param stationEntityId stable system-local station entity ID
     */
    public OwnedStationRef {
        Objects.requireNonNull(systemId, "Owned station system not set");
        Objects.requireNonNull(stationEntityId, "Owned station EntityId not set");
    }

    @Override
    public int compareTo(OwnedStationRef other) {
        int bySystem = systemId.compareTo(other.systemId);
        return bySystem != 0 ? bySystem : stationEntityId.compareTo(other.stationEntityId);
    }
}

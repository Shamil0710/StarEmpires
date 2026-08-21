package com.spacesim.world;

import java.util.Objects;

/**
 * Runtime representation of a faction presence in a generated world location.
 *
 * This component does not simulate diplomacy or AI. It only preserves the
 * materialized world state required by later simulation stages.
 */
public record MaterializedFactionPresence(
        String factionId,
        String systemId,
        String presenceType
) {
    public MaterializedFactionPresence {
        Objects.requireNonNull(factionId, "factionId");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(presenceType, "presenceType");
    }
}

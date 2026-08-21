package com.spacesim.world;

import java.util.Objects;

/**
 * Runtime representation of infrastructure created during world materialization.
 *
 * Stage 20F keeps generation and simulation responsibilities separated:
 * generation decides what infrastructure exists, while materialization assigns
 * a stable runtime identity.
 */
public record MaterializedInfrastructureSite(
        String infrastructureId,
        String systemId,
        String infrastructureType
) {
    public MaterializedInfrastructureSite {
        Objects.requireNonNull(infrastructureId, "infrastructureId");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(infrastructureType, "infrastructureType");
    }
}

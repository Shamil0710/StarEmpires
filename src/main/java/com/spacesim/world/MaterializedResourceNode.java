package com.spacesim.world;

import java.util.Objects;

/**
 * Runtime representation of a resource node produced during world materialization.
 *
 * Stage 20F keeps this model intentionally small. Generation owns discovery of
 * available resources; materialization gives them a persistent runtime identity.
 */
public record MaterializedResourceNode(String resourceNodeId, String systemId, String resourceType) {
    public MaterializedResourceNode {
        Objects.requireNonNull(resourceNodeId, "resourceNodeId");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(resourceType, "resourceType");
    }
}

package com.spacesim.world;

import java.util.Objects;

/**
 * Minimal authoritative system identity created from generated world data.
 */
public record MaterializedSystem(String systemId) {
    public MaterializedSystem {
        Objects.requireNonNull(systemId, "systemId");
    }
}

package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Authoritative runtime representation of a materialized generated world.
 *
 * Stage 20F foundation: generated data becomes a runtime-owned object instead
 * of remaining only a validation artifact.
 */
public final class GeneratedWorldState {
    private final long seed;
    private final List<MaterializedSystem> systems;
    private final List<MaterializedResourceNode> resourceNodes;

    public GeneratedWorldState(long seed, List<MaterializedSystem> systems) {
        this(seed, systems, List.of());
    }

    public GeneratedWorldState(long seed, List<MaterializedSystem> systems,
                               List<MaterializedResourceNode> resourceNodes) {
        this.seed = seed;
        this.systems = List.copyOf(Objects.requireNonNull(systems));
        this.resourceNodes = List.copyOf(Objects.requireNonNull(resourceNodes));
    }

    public long seed() {
        return seed;
    }

    public List<MaterializedSystem> systems() {
        return systems;
    }

    public List<MaterializedResourceNode> resourceNodes() {
        return resourceNodes;
    }
}

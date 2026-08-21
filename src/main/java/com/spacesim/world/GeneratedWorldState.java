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

    public GeneratedWorldState(long seed, List<MaterializedSystem> systems) {
        this.seed = seed;
        this.systems = List.copyOf(Objects.requireNonNull(systems));
    }

    public long seed() {
        return seed;
    }

    public List<MaterializedSystem> systems() {
        return systems;
    }
}

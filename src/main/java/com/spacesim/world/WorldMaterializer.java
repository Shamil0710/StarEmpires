package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Converts validated generated-world output into authoritative runtime state.
 *
 * Stage 20F deliberately keeps the boundary small: generation decides what
 * exists, materialization decides how it becomes a persistent runtime object.
 */
public final class WorldMaterializer {

    public GeneratedWorldState materialize(long seed, List<String> generatedSystemIds) {
        Objects.requireNonNull(generatedSystemIds, "generatedSystemIds");

        List<MaterializedSystem> systems = generatedSystemIds.stream()
                .map(MaterializedSystem::new)
                .toList();

        return new GeneratedWorldState(seed, systems);
    }
}

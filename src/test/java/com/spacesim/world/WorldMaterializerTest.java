package com.spacesim.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorldMaterializerTest {

    @Test
    void materializationKeepsSeedAndGeneratedOrder() {
        WorldMaterializer materializer = new WorldMaterializer();

        GeneratedWorldState state = materializer.materialize(
                42L,
                List.of("Sol", "Alpha-Centauri"));

        assertEquals(42L, state.seed());
        assertEquals(List.of("Sol", "Alpha-Centauri"),
                state.systems().stream().map(MaterializedSystem::systemId).toList());
    }
}

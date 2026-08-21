package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldMaterializationDeterminismTest {

    @Test
    void materializationKeepsSeedAndSystemOrder() {
        WorldMaterializer materializer = new WorldMaterializer();

        GeneratedWorldState state = materializer.materialize(
                42L,
                List.of("Sol", "Alpha", "Beta")
        );

        assertEquals(42L, state.seed());
        assertEquals("Sol", state.systems().get(0).systemId());
        assertEquals("Alpha", state.systems().get(1).systemId());
        assertEquals("Beta", state.systems().get(2).systemId());
    }
}

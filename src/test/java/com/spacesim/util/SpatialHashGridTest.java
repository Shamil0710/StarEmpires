package com.spacesim.util;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialHashGridTest {
    @Test
    void поискВозвращаетСущностиИзСоседнихЯчеек() {
        SpatialHashGrid grid = new SpatialHashGrid();
        Entity center = new Entity();
        Entity nearby = new Entity();
        Entity far = new Entity();

        grid.insert(center, new Vector2(10, 10));
        grid.insert(nearby, new Vector2(210, 10));
        grid.insert(far, new Vector2(1000, 1000));

        List<Entity> result = grid.getNearby(new Vector2(10, 10), 1);

        assertTrue(result.contains(center));
        assertTrue(result.contains(nearby));
        assertFalse(result.contains(far));
    }
}

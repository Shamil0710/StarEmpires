package com.spacesim.util;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.LongMap;
import com.spacesim.constants.Constants;
import java.util.ArrayList;
import java.util.List;

public class SpatialHashGrid {
    private final LongMap<List<Entity>> grid = new LongMap<>();

    public SpatialHashGrid() {
    }

    public SpatialHashGrid(int ignoredCellSize) {
        // Cell size is configured globally through Constants.CELL_SIZE.
    }

    /**
     * Rebuild-oriented insertion. Call clear() once before indexing entities for the current frame.
     */
    public void insert(Entity entity, Vector2 pos) {
        long key = getKey(pos.x, pos.y);
        List<Entity> cell = grid.get(key);
        if (cell == null) {
            cell = new ArrayList<>();
            grid.put(key, cell);
        }
        cell.add(entity);
    }

    public List<Entity> getNearby(Vector2 pos, int radiusCells) {
        List<Entity> result = new ArrayList<>();
        int cx = getCellCoordinate(pos.x);
        int cy = getCellCoordinate(pos.y);

        for (int x = -radiusCells; x <= radiusCells; x++) {
            for (int y = -radiusCells; y <= radiusCells; y++) {
                long key = getHashKey(cx + x, cy + y);
                List<Entity> cell = grid.get(key);
                if (cell != null) result.addAll(cell);
            }
        }
        return result;
    }

    public void clear() { grid.clear(); }

    private long getKey(float x, float y) {
        return getHashKey(getCellCoordinate(x), getCellCoordinate(y));
    }

    private int getCellCoordinate(float value) {
        return (int)Math.floor(value / Constants.CELL_SIZE);
    }

    private long getHashKey(int x, int y) {
        return ((long)x << 32) | ((long)y & 0xFFFFFFFFL);
    }
}

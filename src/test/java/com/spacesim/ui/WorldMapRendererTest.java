package com.spacesim.ui;

import com.badlogic.gdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapRendererTest {
    @Test
    void проверяетМатрицуБезСозданияГрафическихРесурсов() {
        Matrix4 matrix = new Matrix4();
        assertTrue(WorldMapRenderer.isFiniteMatrix(matrix));

        matrix.val[Matrix4.M02] = Float.NaN;
        assertFalse(WorldMapRenderer.isFiniteMatrix(matrix));
        assertFalse(WorldMapRenderer.isFiniteMatrix(null));
    }
}

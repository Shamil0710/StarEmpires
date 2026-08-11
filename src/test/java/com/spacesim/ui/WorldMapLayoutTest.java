package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapLayoutTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void проецируетГраницыМираИВыполняетОбратноеПреобразование() {
        WorldMapLayout layout = new WorldMapLayout(10f, 20f, 740f, 590f, 20f);
        Vector2 point = new Vector2();

        assertEquals(30f, layout.getMapX(), EPSILON);
        assertEquals(40f, layout.getMapY(), EPSILON);
        assertEquals(700f, layout.getMapWidth(), EPSILON);
        assertEquals(550f, layout.getMapHeight(), EPSILON);
        assertEquals(1f, layout.getScale(), EPSILON);

        assertTrue(layout.worldToScreen(0f, 0f, point));
        assertEquals(30f, point.x, EPSILON);
        assertEquals(40f, point.y, EPSILON);

        assertTrue(layout.worldToScreen(WorldMapLayout.WORLD_WIDTH, WorldMapLayout.WORLD_HEIGHT, point));
        assertEquals(730f, point.x, EPSILON);
        assertEquals(590f, point.y, EPSILON);

        assertTrue(layout.screenToWorld(380f, 315f, point));
        assertEquals(350f, point.x, EPSILON);
        assertEquals(275f, point.y, EPSILON);
    }

    @Test
    void сохраняетПропорцииИЦентрируетМирВСвободномПространстве() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 1000f, 600f, 25f);

        assertEquals(150f, layout.getMapX(), EPSILON);
        assertEquals(25f, layout.getMapY(), EPSILON);
        assertEquals(700f, layout.getMapWidth(), EPSILON);
        assertEquals(550f, layout.getMapHeight(), EPSILON);
        assertEquals(
                WorldMapLayout.WORLD_WIDTH / WorldMapLayout.WORLD_HEIGHT,
                layout.getMapWidth() / layout.getMapHeight(),
                EPSILON);
    }

    @Test
    void сохраняетТочкуПриНеуспешномПреобразовании() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 700f, 550f, 0f);
        Vector2 point = new Vector2(12f, 34f);

        assertFalse(layout.worldToScreen(Float.NaN, 10f, point));
        assertEquals(12f, point.x);
        assertEquals(34f, point.y);

        assertFalse(layout.screenToWorld(10f, Float.POSITIVE_INFINITY, point));
        assertEquals(12f, point.x);
        assertEquals(34f, point.y);
        assertThrows(NullPointerException.class, () -> layout.worldToScreen(0f, 0f, null));
        assertThrows(NullPointerException.class, () -> layout.screenToWorld(0f, 0f, null));
    }

    @Test
    void проверяетПопаданиеВМирИВписаннуюОбласть() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 1000f, 600f, 25f);

        assertTrue(layout.containsWorldPoint(0f, 0f));
        assertTrue(layout.containsWorldPoint(700f, 550f));
        assertFalse(layout.containsWorldPoint(-0.01f, 10f));
        assertFalse(layout.containsWorldPoint(10f, 550.01f));
        assertFalse(layout.containsWorldPoint(Float.NaN, 10f));

        assertTrue(layout.containsMapPoint(150f, 25f));
        assertTrue(layout.containsMapPoint(850f, 575f));
        assertFalse(layout.containsMapPoint(149f, 300f));
        assertFalse(layout.containsMapPoint(Float.POSITIVE_INFINITY, 300f));
    }

    @Test
    void отклоняетНекорректнуюГеометрию() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(Float.NaN, 0f, 700f, 550f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 0f, 550f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 100f, 100f, 50f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(Float.MAX_VALUE, 0f, Float.MAX_VALUE, 550f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, Float.MIN_VALUE, Float.MIN_VALUE, 0f));
    }
}

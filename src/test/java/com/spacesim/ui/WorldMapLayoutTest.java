package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapLayoutTest {
    private static final float EPSILON = 0.001f;

    @Test
    void проецируетГраницыРасширенногоМираИВыполняетОбратноеПреобразование() {
        WorldMapLayout layout = new WorldMapLayout(10f, 20f, 2040f, 1440f, 20f);
        Vector2 point = new Vector2();

        assertEquals(Constants.WORLD_WIDTH, WorldMapLayout.WORLD_WIDTH);
        assertEquals(Constants.WORLD_HEIGHT, WorldMapLayout.WORLD_HEIGHT);
        assertEquals(30f, layout.getMapX(), EPSILON);
        assertEquals(40f, layout.getMapY(), EPSILON);
        assertEquals(2000f, layout.getMapWidth(), EPSILON);
        assertEquals(1400f, layout.getMapHeight(), EPSILON);
        assertEquals(1f, layout.getScale(), EPSILON);
        assertEquals(WorldMapLayout.MIN_ZOOM, layout.getZoom(), EPSILON);

        assertTrue(layout.worldToScreen(0f, 0f, point));
        assertEquals(30f, point.x, EPSILON);
        assertEquals(40f, point.y, EPSILON);

        assertTrue(layout.worldToScreen(WorldMapLayout.WORLD_WIDTH, WorldMapLayout.WORLD_HEIGHT, point));
        assertEquals(2030f, point.x, EPSILON);
        assertEquals(1440f, point.y, EPSILON);

        assertTrue(layout.screenToWorld(1030f, 740f, point));
        assertEquals(1000f, point.x, EPSILON);
        assertEquals(700f, point.y, EPSILON);
    }

    @Test
    void сохраняетПропорцииИЦентрируетПолныйОбзорВСвободномПространстве() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 1000f, 600f, 25f);

        assertEquals(25f, layout.getMapY(), EPSILON);
        assertEquals(550f, layout.getMapHeight(), EPSILON);
        assertEquals(
                WorldMapLayout.WORLD_WIDTH / WorldMapLayout.WORLD_HEIGHT,
                layout.getMapWidth() / layout.getMapHeight(),
                EPSILON);
        assertEquals((1000f - layout.getMapWidth()) / 2f, layout.getMapX(), EPSILON);
        assertEquals(
                WorldMapLayout.WORLD_HEIGHT / 2f,
                layout.getCenterWorldY(),
                EPSILON);
    }

    @Test
    void масштабируетВокругКурсораИОбращаетПреобразованиеПослеСдвига() {
        WorldMapLayout initial = new WorldMapLayout(0f, 0f, 1000f, 700f, 0f);
        Vector2 anchorBefore = new Vector2();
        Vector2 anchorAfter = new Vector2();
        assertTrue(initial.screenToWorld(750f, 350f, anchorBefore));

        WorldMapLayout zoomed = initial.zoomAt(750f, 350f, 3f);

        assertEquals(3f, zoomed.getZoom(), EPSILON);
        assertEquals(initial.getScale() * 3f, zoomed.getScale(), EPSILON);
        assertTrue(zoomed.screenToWorld(750f, 350f, anchorAfter));
        assertEquals(anchorBefore.x, anchorAfter.x, EPSILON);
        assertEquals(anchorBefore.y, anchorAfter.y, EPSILON);

        WorldMapLayout panned = zoomed.panByScreen(-90f, 45f);
        Vector2 screen = new Vector2();
        Vector2 restored = new Vector2();
        assertTrue(panned.worldToScreen(1250f, 620f, screen));
        assertTrue(panned.screenToWorld(screen.x, screen.y, restored));
        assertEquals(1250f, restored.x, EPSILON);
        assertEquals(620f, restored.y, EPSILON);
    }

    @Test
    void колесоОграничиваетУвеличениеИИгнорируетСобытиеВнеКарты() {
        WorldMapLayout initial = new WorldMapLayout(0f, 0f, 1000f, 700f, 0f);

        WorldMapLayout maximum = initial.zoomByScroll(500f, 350f, -100f);
        assertEquals(WorldMapLayout.MAX_ZOOM, maximum.getZoom(), EPSILON);

        WorldMapLayout minimum = maximum.zoomByScroll(500f, 350f, 100f);
        assertEquals(WorldMapLayout.MIN_ZOOM, minimum.getZoom(), EPSILON);
        assertSame(initial, initial.zoomByScroll(-1f, 350f, -1f));
        assertSame(initial, initial.zoomByScroll(500f, 350f, Float.NaN));
    }

    @Test
    void ограничиваетПанорамированиеГраницамиМира() {
        WorldMapLayout zoomed = new WorldMapLayout(
                0f,
                0f,
                1000f,
                700f,
                0f,
                1000f,
                700f,
                2f);

        WorldMapLayout lowerLeft = zoomed.panByScreen(1_000_000f, 1_000_000f);
        assertEquals(0f, lowerLeft.getVisibleWorldMinX(), EPSILON);
        assertEquals(0f, lowerLeft.getVisibleWorldMinY(), EPSILON);
        assertEquals(1000f, lowerLeft.getVisibleWorldMaxX(), EPSILON);
        assertEquals(700f, lowerLeft.getVisibleWorldMaxY(), EPSILON);

        WorldMapLayout upperRight = lowerLeft.panByScreen(-1_000_000f, -1_000_000f);
        assertEquals(1000f, upperRight.getVisibleWorldMinX(), EPSILON);
        assertEquals(700f, upperRight.getVisibleWorldMinY(), EPSILON);
        assertEquals(WorldMapLayout.WORLD_WIDTH, upperRight.getVisibleWorldMaxX(), EPSILON);
        assertEquals(WorldMapLayout.WORLD_HEIGHT, upperRight.getVisibleWorldMaxY(), EPSILON);
    }

    @Test
    void отличаетПолныйМирОтТекущегоОбзора() {
        WorldMapLayout lowerLeft = new WorldMapLayout(
                0f,
                0f,
                1000f,
                700f,
                0f,
                0f,
                0f,
                2f);

        assertTrue(lowerLeft.containsWorldPoint(1500f, 900f));
        assertFalse(lowerLeft.containsVisibleWorldPoint(1500f, 900f));
        assertTrue(lowerLeft.containsVisibleWorldPoint(0f, 0f));
        assertTrue(lowerLeft.containsVisibleWorldPoint(1000f, 700f));
        assertFalse(lowerLeft.containsVisibleWorldPoint(1000.01f, 700f));
    }

    @Test
    void сохраняетКамеруПриИзмененииЭкранногоПрямоугольника() {
        WorldMapLayout initial = new WorldMapLayout(
                0f,
                0f,
                1000f,
                700f,
                0f,
                1200f,
                650f,
                2.5f);

        WorldMapLayout resized = initial.resize(10f, 20f, 800f, 600f, 15f);

        assertEquals(initial.getZoom(), resized.getZoom(), EPSILON);
        assertEquals(initial.getCenterWorldX(), resized.getCenterWorldX(), EPSILON);
        assertEquals(initial.getCenterWorldY(), resized.getCenterWorldY(), EPSILON);
        assertEquals(10f, resized.getX(), EPSILON);
        assertEquals(20f, resized.getY(), EPSILON);
    }

    @Test
    void сохраняетТочкуПриНеуспешномПреобразовании() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 1000f, 700f, 0f);
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
    void проверяетПопаданиеВИнтерактивнуюОбластьКарты() {
        WorldMapLayout layout = new WorldMapLayout(0f, 0f, 1000f, 600f, 25f);

        assertTrue(layout.containsMapPoint(layout.getMapX(), layout.getMapY()));
        assertTrue(layout.containsMapPoint(
                layout.getMapX() + layout.getMapWidth(),
                layout.getMapY() + layout.getMapHeight()));
        assertFalse(layout.containsMapPoint(layout.getMapX() - 0.01f, 300f));
        assertFalse(layout.containsMapPoint(Float.POSITIVE_INFINITY, 300f));
    }

    @Test
    void отклоняетНекорректнуюГеометриюИСостояниеКамеры() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(Float.NaN, 0f, 1000f, 700f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 0f, 700f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 100f, 100f, 50f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(Float.MAX_VALUE, 0f, Float.MAX_VALUE, 700f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, Float.MIN_VALUE, Float.MIN_VALUE, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 1000f, 700f, 0f, Float.NaN, 0f, 1f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 1000f, 700f, 0f, 0f, 0f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldMapLayout(0f, 0f, 1000f, 700f, 0f).zoomAt(
                        100f,
                        100f,
                        Float.NaN));
    }
}

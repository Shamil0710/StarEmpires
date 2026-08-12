package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void назначаетПятиТипамКораблейУникальныеФормыИЦветаБезOpenGl() {
        Set<WorldMapRenderer.MarkerShape> shapes =
                EnumSet.noneOf(WorldMapRenderer.MarkerShape.class);
        Set<String> colors = new HashSet<>();

        for (ShipType shipType : ShipType.values()) {
            WorldMapRenderer.MarkerStyle style = WorldMapRenderer.markerStyle(shipType);

            assertNotNull(style);
            assertNotNull(style.shape());
            assertTrue(style.red() >= 0f && style.red() <= 1f, shipType.name());
            assertTrue(style.green() >= 0f && style.green() <= 1f, shipType.name());
            assertTrue(style.blue() >= 0f && style.blue() <= 1f, shipType.name());
            shapes.add(style.shape());
            colors.add(style.red() + ":" + style.green() + ":" + style.blue());
        }

        assertEquals(ShipType.values().length, shapes.size());
        assertEquals(ShipType.values().length, colors.size());
    }

    @Test
    void сохраняетУниверсальныйМаркерДляLegacyКорабляБезТипа() {
        WorldMapRenderer.MarkerStyle style = WorldMapRenderer.markerStyle(null);

        assertEquals(WorldMapRenderer.MarkerShape.GENERIC_ARROW, style.shape());
        assertTrue(Float.isFinite(style.red()));
        assertTrue(Float.isFinite(style.green()));
        assertTrue(Float.isFinite(style.blue()));
    }

    @Test
    void вычисляетЯдроАстероидаПоОставшемусяРесурсу() {
        AsteroidComponent asteroid = new AsteroidComponent(
                "A-1", Constants.ITEM_ORE, 100L);

        assertEquals(6.5f, WorldMapRenderer.asteroidCoreRadius(asteroid), 0.0001f);
        asteroid.remainingResource = 50L;
        assertEquals(4.25f, WorldMapRenderer.asteroidCoreRadius(asteroid), 0.0001f);
        asteroid.remainingResource = 0L;
        assertEquals(0f, WorldMapRenderer.asteroidCoreRadius(asteroid), 0f);
        assertEquals(0f, WorldMapRenderer.asteroidCoreRadius(null), 0f);
    }

    @Test
    void выбираетНавигационнуюЦельПоЭтапуДобычи() {
        MiningComponent mining = new MiningComponent();
        Entity asteroid = new Entity();
        Entity base = new Entity();
        mining.targetAsteroid = asteroid;
        mining.homeBase = base;

        mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
        assertSame(asteroid, WorldMapRenderer.miningNavigationTarget(mining));
        assertTrue(WorldMapRenderer.isActiveMiningRoute(mining));
        mining.state = MiningComponent.State.MINING;
        assertSame(asteroid, WorldMapRenderer.miningNavigationTarget(mining));
        mining.state = MiningComponent.State.RETURNING_TO_BASE;
        assertSame(base, WorldMapRenderer.miningNavigationTarget(mining));
        mining.state = MiningComponent.State.UNLOADING;
        assertSame(base, WorldMapRenderer.miningNavigationTarget(mining));
        mining.state = MiningComponent.State.SEARCHING;
        assertNull(WorldMapRenderer.miningNavigationTarget(mining));
        assertFalse(WorldMapRenderer.isActiveMiningRoute(mining));
        mining.state = MiningComponent.State.PAUSED;
        assertNull(WorldMapRenderer.miningNavigationTarget(mining));
        assertFalse(WorldMapRenderer.isActiveMiningRoute(mining));
        assertNull(WorldMapRenderer.miningNavigationTarget(null));
        assertFalse(WorldMapRenderer.isActiveMiningRoute(null));
    }
}

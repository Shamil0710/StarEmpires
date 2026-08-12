package com.spacesim.presentation.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProjectShipSpritesTest {
    @Test
    void heavyCorvetteUsesExplicitProductionLikeGeometry() {
        ShipSpriteSpec spec = ProjectShipSprites.whiteHeavyCorvette01();

        assertEquals("ship.heavy_corvette.white_01", spec.assetId());
        assertEquals(ProjectShipSprites.WHITE_HEAVY_CORVETTE_01_BASE, spec.baseTexturePath());
        assertEquals(ProjectShipSprites.WHITE_HEAVY_CORVETTE_01_EMISSIVE, spec.emissiveTexturePath());
        assertEquals(120f, spec.worldWidth());
        assertEquals(72f, spec.worldHeight());
        assertEquals(0.5f, spec.pivotX());
        assertEquals(0.5f, spec.pivotY());
        assertEquals(86.4f, spec.collisionWidth());
        assertEquals(41.8f, spec.collisionHeight());
        assertTrue(spec.collisionWidth() < spec.worldWidth());
        assertTrue(spec.collisionHeight() < spec.worldHeight());
    }

    @Test
    void heavyCorvetteDefinesExpectedEngineWeaponAndUtilityHardpoints() {
        ShipSpriteSpec spec = ProjectShipSprites.whiteHeavyCorvette01();

        long engines = spec.hardpoints().stream()
                .filter(hardpoint -> hardpoint.type() == VisualHardpointType.ENGINE)
                .count();
        long weapons = spec.hardpoints().stream()
                .filter(hardpoint -> hardpoint.type() == VisualHardpointType.WEAPON)
                .count();
        long utility = spec.hardpoints().stream()
                .filter(hardpoint -> hardpoint.type() == VisualHardpointType.UTILITY)
                .count();
        Set<String> ids = spec.hardpoints().stream()
                .map(VisualHardpoint::id)
                .collect(Collectors.toSet());

        assertEquals(3L, engines);
        assertEquals(5L, weapons);
        assertEquals(1L, utility);
        assertTrue(ids.contains("engine_main_top"));
        assertTrue(ids.contains("engine_main_mid"));
        assertTrue(ids.contains("engine_main_bottom"));
        assertTrue(ids.contains("weapon_nose_primary"));
        assertTrue(ids.contains("utility_center"));

        spec.hardpoints().stream()
                .filter(hardpoint -> hardpoint.type() == VisualHardpointType.ENGINE)
                .forEach(hardpoint -> assertEquals(0f, hardpoint.directionDegrees()));
        spec.hardpoints().stream()
                .filter(hardpoint -> hardpoint.type() == VisualHardpointType.WEAPON)
                .forEach(hardpoint -> assertEquals(180f, hardpoint.directionDegrees()));
    }
}

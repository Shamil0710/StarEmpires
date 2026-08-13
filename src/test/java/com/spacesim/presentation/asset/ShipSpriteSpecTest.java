package com.spacesim.presentation.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipSpriteSpecTest {
    @Test
    void keepsPresentationGeometryExplicitAndDefensive() {
        VisualHardpoint engine = new VisualHardpoint(
                "engine-left", VisualHardpointType.ENGINE, 0.08f, 0.35f, 180f);
        ArrayList<VisualHardpoint> source = new ArrayList<>(List.of(engine));

        ShipSpriteSpec spec = new ShipSpriteSpec(
                "ship.trader.small",
                "presentation/ships/trader-small.png",
                "presentation/ships/trader-small-emissive.png",
                42f,
                18f,
                0.5f,
                0.5f,
                12f,
                source);
        source.clear();

        assertEquals("ship.trader.small", spec.assetId());
        assertEquals("presentation/ships/trader-small.png", spec.baseTexturePath());
        assertEquals(
                "presentation/ships/trader-small-emissive.png",
                spec.emissiveTexturePath());
        assertEquals(42f, spec.worldWidth());
        assertEquals(18f, spec.worldHeight());
        assertEquals(0.5f, spec.pivotX());
        assertEquals(0.5f, spec.pivotY());
        assertEquals(12f, spec.collisionRadius());
        assertEquals(SourceFacing.RIGHT, spec.sourceFacing());
        assertEquals(List.of(engine), spec.hardpoints());
        assertThrows(UnsupportedOperationException.class, () -> spec.hardpoints().clear());
    }

    @Test
    void supportsExplicitLeftFacingSourceArt() {
        ShipSpriteSpec spec = new ShipSpriteSpec(
                "ship.left",
                "left.png",
                null,
                20f,
                10f,
                0.5f,
                0.5f,
                14f,
                6f,
                SourceFacing.LEFT,
                List.of());

        assertEquals(SourceFacing.LEFT, spec.sourceFacing());
        assertEquals(14f, spec.collisionWidth());
        assertEquals(6f, spec.collisionHeight());
    }

    @Test
    void normalizesOptionalEmissiveAndHardpointMetadata() {
        VisualHardpoint weapon = new VisualHardpoint(
                " muzzle ", VisualHardpointType.WEAPON, 1f, 0.5f, 0f);
        ShipSpriteSpec spec = new ShipSpriteSpec(
                " ship.test ",
                " presentation/test.png ",
                "   ",
                10f,
                5f,
                0f,
                1f,
                2f,
                List.of(weapon));

        assertEquals("ship.test", spec.assetId());
        assertEquals("presentation/test.png", spec.baseTexturePath());
        assertNull(spec.emissiveTexturePath());
        assertEquals("muzzle", spec.hardpoints().get(0).id());
    }

    @Test
    void rejectsImplicitOrAmbiguousGeometry() {
        VisualHardpoint engine = new VisualHardpoint(
                "engine", VisualHardpointType.ENGINE, 0f, 0f, 180f);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipSpriteSpec("id", "base.png", null, 0f, 1f, 0.5f, 0.5f, 1f, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipSpriteSpec("id", "base.png", null, 1f, 1f, -0.1f, 0.5f, 1f, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipSpriteSpec("id", "base.png", null, 1f, 1f, 0.5f, 0.5f, 0f, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShipSpriteSpec(
                        "id",
                        "base.png",
                        null,
                        1f,
                        1f,
                        0.5f,
                        0.5f,
                        1f,
                        List.of(engine, engine)));
        assertThrows(
                NullPointerException.class,
                () -> new ShipSpriteSpec(
                        "id",
                        "base.png",
                        null,
                        1f,
                        1f,
                        0.5f,
                        0.5f,
                        1f,
                        1f,
                        null,
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VisualHardpoint("id", VisualHardpointType.ENGINE, -0.1f, 0.5f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VisualHardpoint("id", VisualHardpointType.ENGINE, 0.5f, 1.1f, 0f));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VisualHardpoint("id", VisualHardpointType.ENGINE, 0.5f, 0.5f, Float.NaN));
    }
}

package com.spacesim.presentation.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpriteOrientationTransformTest {
    @Test
    void leftFacingSourceMirrorsHardpointPositionsAndDirections() {
        ShipSpriteSpec spec = ProjectShipSprites.whiteHeavyCorvette01();
        VisualHardpoint engine = hardpoint(spec, "engine_main_mid");
        VisualHardpoint muzzle = hardpoint(spec, "weapon_nose_primary");

        assertEquals(-1f, SpriteOrientationTransform.horizontalScale(spec));
        assertEquals(-43.92f, SpriteOrientationTransform.localX(spec, engine.normalizedX(), 1f), 0.001f);
        assertEquals(53.4f, SpriteOrientationTransform.localX(spec, muzzle.normalizedX(), 1f), 0.001f);
        assertEquals(180f, SpriteOrientationTransform.directionDegrees(spec, engine.directionDegrees()));
        assertEquals(0f, SpriteOrientationTransform.directionDegrees(spec, muzzle.directionDegrees()));
    }

    @Test
    void rightFacingSourceRemainsUnchanged() {
        ShipSpriteSpec spec = new ShipSpriteSpec(
                "right",
                "right.png",
                null,
                20f,
                10f,
                0.5f,
                0.5f,
                4f,
                List.of());

        assertEquals(1f, SpriteOrientationTransform.horizontalScale(spec));
        assertEquals(5f, SpriteOrientationTransform.localX(spec, 0.75f, 1f), 0.001f);
        assertEquals(270f, SpriteOrientationTransform.directionDegrees(spec, -90f));
    }

    @Test
    void rejectsInvalidPreviewCoordinatesAndScale() {
        ShipSpriteSpec spec = ProjectShipSprites.whiteHeavyCorvette01();

        assertThrows(IllegalArgumentException.class, () -> SpriteOrientationTransform.localX(spec, -0.1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> SpriteOrientationTransform.localY(spec, 1.1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> SpriteOrientationTransform.localX(spec, 0.5f, 0f));
        assertThrows(IllegalArgumentException.class, () -> SpriteOrientationTransform.directionDegrees(spec, Float.NaN));
    }

    private static VisualHardpoint hardpoint(ShipSpriteSpec spec, String id) {
        return spec.hardpoints().stream()
                .filter(hardpoint -> id.equals(hardpoint.id()))
                .findFirst()
                .orElseThrow();
    }
}

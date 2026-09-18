package com.spacesim.presentation.asset;

import com.spacesim.presentation.asset.Stage22EnginePlumeGeometry.PlumeTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22EnginePlumeGeometryTest {
    private static final float EPSILON = 1.0e-5f;

    @Test
    void engineOffProducesNoPlume() {
        assertTrue(Stage22EnginePlumeGeometry.resolve(
                100f, 50f, 120f, 72f, 0f, 0d).isEmpty());
    }

    @Test
    void zeroDegreeHeadingPlacesPlumeBehindNegativeXSide() {
        PlumeTransform plume = Stage22EnginePlumeGeometry.resolve(
                100f, 50f, 120f, 72f, 0f, 1d).orElseThrow();

        assertTrue(plume.centerX() < 100f - 60f);
        assertEquals(50f, plume.centerY(), EPSILON);
        assertEquals(0f, plume.rotationDegrees(), EPSILON);
        assertTrue(plume.lengthPixels() > 0f);
        assertTrue(plume.widthPixels() > 0f);
    }

    @Test
    void quarterTurnPlacesPlumeBehindNegativeYSide() {
        PlumeTransform plume = Stage22EnginePlumeGeometry.resolve(
                100f, 50f, 120f, 72f, 90f, 1d).orElseThrow();

        assertEquals(100f, plume.centerX(), 2.0e-4f);
        assertTrue(plume.centerY() < 50f - 60f);
        assertEquals(90f, plume.rotationDegrees(), EPSILON);
    }

    @Test
    void strongerThrustLengthensAndBrightensEffect() {
        PlumeTransform low = Stage22EnginePlumeGeometry.resolve(
                0f, 0f, 200f, 80f, 0f, 0.25d).orElseThrow();
        PlumeTransform high = Stage22EnginePlumeGeometry.resolve(
                0f, 0f, 200f, 80f, 0f, 1d).orElseThrow();

        assertTrue(high.lengthPixels() > low.lengthPixels());
        assertTrue(high.widthPixels() > low.widthPixels());
        assertTrue(high.alpha() > low.alpha());
    }

    @Test
    void invalidPropulsionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Stage22EnginePlumeGeometry.resolve(0f, 0f, 100f, 40f, 0f, -0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22EnginePlumeGeometry.resolve(0f, 0f, 100f, 40f, 0f, 1.1d));
    }
}

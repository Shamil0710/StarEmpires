package com.spacesim.presentation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GraphicsValidationProfileTest {
    @Test
    void representativeProfileMatchesRoadmapGate() {
        GraphicsValidationProfile profile = GraphicsValidationProfile.representative();

        assertEquals(1920, profile.width());
        assertEquals(1080, profile.height());
        assertEquals(50, profile.shipCount());
        assertEquals(500, profile.asteroidCount());
        assertEquals(2000, profile.particleCount());
        assertEquals(2550, profile.totalObjectCount());
    }

    @Test
    void rejectsInvalidDimensionsAndCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsValidationProfile(0, 1080, 50, 500, 2000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsValidationProfile(1920, -1, 50, 500, 2000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsValidationProfile(1920, 1080, -1, 500, 2000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsValidationProfile(1920, 1080, 50, -1, 2000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphicsValidationProfile(1920, 1080, 50, 500, -1));
    }
}

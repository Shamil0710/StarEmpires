package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The baked glow shapes: headless, no texture is created. */
class MapHaloRendererProfileTest {
    @Test
    void haloIsBrightInTheMiddleAndFadesToNothingAtTheEdge() {
        assertEquals(1f, MapHaloRenderer.haloProfile(0f), 1e-6f);
        float previous = 1f;
        for (float d = 0.05f; d < 1f; d += 0.05f) {
            float alpha = MapHaloRenderer.haloProfile(d);
            assertTrue(alpha <= previous + 1e-6f, "monotonic falloff at " + d);
            previous = alpha;
        }
        assertTrue(MapHaloRenderer.haloProfile(0.5f) > 0.05f, "a long tail, not a hard disc");
        assertTrue(MapHaloRenderer.haloProfile(0.98f) < 0.01f, "nearly transparent at the texture edge");
        assertEquals(0f, MapHaloRenderer.haloProfile(1f));
        assertEquals(0f, MapHaloRenderer.haloProfile(Float.NaN));
    }

    @Test
    void flareHasSpikesAlongTheAxesOnly() {
        assertEquals(1f, MapHaloRenderer.flareProfile(0f, 0f), 1e-6f);
        float spike = MapHaloRenderer.flareProfile(0.5f, 0f);
        float diagonal = MapHaloRenderer.flareProfile(0.354f, 0.354f);
        assertTrue(spike > 0.1f, "visible spike along X");
        assertEquals(spike, MapHaloRenderer.flareProfile(0f, 0.5f), 1e-6f, "the same spike along Y");
        assertTrue(diagonal < spike * 0.2f, "dark between the spikes");
        assertEquals(0f, MapHaloRenderer.flareProfile(1f, 0f));
    }
}

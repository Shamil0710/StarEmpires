package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveUiMetricsTest {
    @Test
    void typographyRemainsBoundedFromCompactDesktopToFourK() {
        ResponsiveUiMetrics compact = ResponsiveUiMetrics.resolve(900, 620, 1f);
        ResponsiveUiMetrics fullHd = ResponsiveUiMetrics.resolve(1920, 1080, 1f);
        ResponsiveUiMetrics fourK = ResponsiveUiMetrics.resolve(3840, 2160, 1f);

        assertEquals(0.80f, compact.scale(), 0f);
        assertTrue(fullHd.bodyFontPixels() > compact.bodyFontPixels());
        assertTrue(fourK.bodyFontPixels() > fullHd.bodyFontPixels());
        assertTrue(fourK.scale() <= 2f);
        assertTrue(compact.hitRadius() >= 24f);
        assertTrue(compact.compact(900));
        assertFalse(fullHd.compact(1920));
    }

    @Test
    void highDensityAssistsLogicalPixelViewportWithoutUnboundedGrowth() {
        ResponsiveUiMetrics normal = ResponsiveUiMetrics.resolve(1600, 900, 1f);
        ResponsiveUiMetrics dense = ResponsiveUiMetrics.resolve(1600, 900, 2f);

        assertTrue(dense.scale() > normal.scale());
        assertTrue(dense.titleFontPixels() > normal.titleFontPixels());
        assertTrue(dense.inspectorWidth() < 1600f * 0.5f);
    }
}

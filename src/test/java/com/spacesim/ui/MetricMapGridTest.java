package com.spacesim.ui;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricMapGridTest {
    @Test
    void spacingIsRoundAndMajorLinesFallOnRoundValues() {
        assertSpacing(MetricMapGrid.spacing(0.1d, 40d), 500d, 1_000d);
        assertSpacing(MetricMapGrid.spacing(1d, 40d), 50d, 100d);
        assertSpacing(MetricMapGrid.spacing(1d, 15d), 20d, 100d);
        assertSpacing(MetricMapGrid.spacing(0.01d, 40d), 5_000d, 10_000d);
        assertSpacing(MetricMapGrid.spacing(1.0e-6d, 40d), 5.0e7d, 1.0e8d);
        assertSpacing(MetricMapGrid.spacing(100d, 40d), 0.5d, 1d);
        assertThrows(IllegalArgumentException.class, () -> MetricMapGrid.spacing(0d, 40d));
    }

    @Test
    void linesAreAnchoredToTheMetreOriginAndFollowPan() {
        var spacing = new MetricMapGrid.Spacing(100d, 500d);
        var lines = MetricMapGrid.lines(123.5d, 0.5d, spacing, 0d, 400d);
        assertEquals(List.of(23.5f, 73.5f, 123.5f, 173.5f, 223.5f, 273.5f, 323.5f, 373.5f),
                lines.stream().map(MetricMapGrid.Line::screen).toList());
        // The origin line and the line 500 m to its right are major; the rest are minor.
        assertEquals(List.of(false, false, true, false, false, false, false, true),
                lines.stream().map(MetricMapGrid.Line::major).toList());

        var panned = MetricMapGrid.lines(123.5d + 37d, 0.5d, spacing, 0d, 400d);
        assertEquals(60.5f, panned.get(1).screen(), 1e-4f);
        assertTrue(panned.get(3).major());
    }

    @Test
    void gridLineCoincidesWithAProjectedObjectAtEveryZoom() {
        var a = object("a", 0d, 0d);
        var b = object("b", 2_000d, 1_000d);
        var projection = new PhysicalMapProjection(List.of(a, b), 0d, 0d, 1_000d, 600d);
        float centerX = 500f;
        float centerY = 300f;
        for (double zoom : new double[] {1d, 3d, 40d, 2_500d, 1.0e6d, 1.0e8d, MapCameraState.MAX_ZOOM}) {
            var camera = new MapCameraState();
            camera.inspect(zoom);
            camera.focus(projection.point("b").x(), projection.point("b").y(), centerX, centerY);
            camera.pan(-90f, 55f);
            assertLineThroughObject(projection, camera, centerX, centerY, "zoom " + zoom);
            // A small pan at inspection zoom must move the grid together with the object.
            camera.pan(10f, -4f);
            assertLineThroughObject(projection, camera, centerX, centerY, "zoom " + zoom + " after pan");
        }
    }

    private static void assertLineThroughObject(PhysicalMapProjection projection, MapCameraState camera,
            float centerX, float centerY, String label) {
        double pixelsPerMetre = projection.pixelsPerMetre() * camera.zoom();
        var spacing = MetricMapGrid.spacing(pixelsPerMetre, 40d);
        var origin = projection.metreOrigin();
        float bx = camera.transformX(projection.point("b").x(), centerX);
        float by = camera.transformY(projection.point("b").y(), centerY);
        var vertical = MetricMapGrid.lines(camera.transformXPrecise(origin.x(), centerX),
                pixelsPerMetre, spacing, 0d, 1_000d);
        var horizontal = MetricMapGrid.lines(camera.transformYPrecise(origin.y(), centerY),
                pixelsPerMetre, spacing, 0d, 600d);
        assertTrue(vertical.stream().anyMatch(line -> Math.abs(line.screen() - bx) < 0.5f),
                "vertical line through b at " + label);
        assertTrue(horizontal.stream().anyMatch(line -> Math.abs(line.screen() - by) < 0.5f),
                "horizontal line through b at " + label);
    }

    @Test
    void degenerateScalesProduceNoLines() {
        var spacing = new MetricMapGrid.Spacing(1d, 5d);
        assertTrue(MetricMapGrid.lines(0d, 0.001d, spacing, 0d, 1_000d).isEmpty());
        assertTrue(MetricMapGrid.lines(Double.NaN, 1d, spacing, 0d, 100d).isEmpty());
        assertTrue(MetricMapGrid.lines(0d, 1d, spacing, 100d, 0d).isEmpty());
        assertFalse(MetricMapGrid.lines(0d, 10d, spacing, 0d, 100d).isEmpty());
    }

    @Test
    void scaleBarIsTheLongestRoundLengthThatFits() {
        var bar = MetricMapGrid.scaleBar(0.35d, 200d);
        assertEquals(500d, bar.lengthMetres());
        assertEquals(175f, bar.pixels(), 1e-4f);
        assertEquals("500 м", bar.label());

        assertEquals("200 000 км", MetricMapGrid.scaleBar(1.0e-6d, 200d).label());
        assertEquals("0.2 м", MetricMapGrid.scaleBar(1_000d, 200d).label());
        var exact = MetricMapGrid.scaleBar(0.1d, 200d);
        assertEquals(2_000d, exact.lengthMetres(), 1e-9);
        assertEquals(200f, exact.pixels(), 1e-4f);
    }

    @Test
    void lengthsUseMetresKilometresAndMillionsOfKilometres() {
        assertEquals("0 м", MetricMapGrid.formatLength(0d));
        assertEquals("0.05 м", MetricMapGrid.formatLength(0.05d));
        assertEquals("500 м", MetricMapGrid.formatLength(500d));
        assertEquals("1 км", MetricMapGrid.formatLength(1_000d));
        assertEquals("1 500 км", MetricMapGrid.formatLength(1.5e6d));
        assertEquals("500 000 км", MetricMapGrid.formatLength(5.0e8d));
        assertEquals("2 млн км", MetricMapGrid.formatLength(2.0e9d));
        assertEquals("2 000 млн км", MetricMapGrid.formatLength(2.0e12d));
        assertThrows(IllegalArgumentException.class, () -> MetricMapGrid.formatLength(-1d));
    }

    private static void assertSpacing(MetricMapGrid.Spacing spacing, double minor, double major) {
        assertEquals(minor, spacing.minorMetres(), minor * 1e-9);
        assertEquals(major, spacing.majorMetres(), major * 1e-9);
    }

    private static LocalObjectView object(String id, double x, double y) {
        return new LocalObjectView(id, ObjectKind.FLEET, id, "ship", new StarSystemId(1),
                LocalPhysicalPosition.origin().translated(x, y), "", "",
                Stage20MinimumPlayableSpriteCatalog.binding(VisualRole.CARGO_TRANSPORT_SHIP),
                List.of(), 100d, 20d);
    }
}

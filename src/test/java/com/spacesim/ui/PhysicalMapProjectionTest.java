package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalMapProjectionTest {
    private LocalObjectView object(String id, double x, double y, double length, double width) {
        return new LocalObjectView(id, ObjectKind.FLEET, id, "ship", new StarSystemId(1),
                LocalPhysicalPosition.origin().translated(x, y), "", "",
                Stage20MinimumPlayableSpriteCatalog.binding(VisualRole.CARGO_TRANSPORT_SHIP),
                List.of(), length, width);
    }

    @Test
    void uniformMetreScalePreservesPositionsAndHullRatios() {
        var small = object("corvette", 0, 0, 100, 20);
        var carrier = object("carrier", 1000, 1000, 1000, 300);
        var p = new PhysicalMapProjection(List.of(small, carrier), 0, 0, 1000, 400);
        assertEquals(p.point("carrier").x() - p.point("corvette").x(),
                p.point("carrier").y() - p.point("corvette").y(), 1e-9);
        double zoom = p.inspectionZoom(carrier, 1000, 400);
        double carrierPixels = carrier.physicalLengthM() * p.pixelsPerMetre() * zoom;
        assertEquals(600d, carrierPixels, 1e-9);
        assertEquals(10d, carrierPixels / (small.physicalLengthM() * p.pixelsPerMetre() * zoom), 1e-9);
    }

    @Test
    void overlappingObjectsAreNotDisplacedAndSingleObjectHasUsefulFit() {
        var p = new PhysicalMapProjection(List.of(object("a", 0, 0, 100, 20),
                object("b", 0, 0, 100, 20)), 0, 0, 1000, 600);
        assertEquals(p.point("a"), p.point("b"));
        assertEquals(10d, p.pixelsPerMetre());
    }

    @Test
    void systemOverviewCanInspectHundredMetreHullWithoutFloatQuantization() {
        var small = object("a", 0, 0, 100, 20);
        var nearby = object("b", 200, 0, 100, 20);
        var distant = object("c", 1e12, 1e12, 1000, 300);
        var p = new PhysicalMapProjection(List.of(small, nearby, distant), 0, 0, 1000, 600);
        var camera = new MapCameraState();
        camera.inspect(p.inspectionZoom(small, 1000, 600));
        camera.focus(p.point("a").x(), p.point("a").y(), 500, 300);
        assertEquals(500, camera.transformX(p.point("a").x(), 500), .01);
        assertEquals(300, camera.transformY(p.point("a").y(), 300), .01);
        assertEquals(1200, camera.transformX(p.point("b").x(), 500) - 500, .01);
        assertEquals(600, small.physicalLengthM() * p.pixelsPerMetre() * camera.zoom(), .01);
        camera.zoomAt(-1, 500, 300, 500, 300);
        assertEquals(500, camera.transformX(p.point("a").x(), 500), .01);
        camera.pan(13, -7);
        assertEquals(513, camera.transformX(p.point("a").x(), 500), .01);
    }
}

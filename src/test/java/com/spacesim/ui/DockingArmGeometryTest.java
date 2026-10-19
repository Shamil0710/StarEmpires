package com.spacesim.ui;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockingArmGeometryTest {
    private static final List<VisualHardpoint> CROSS = List.of(
            new VisualHardpoint("aft", VisualHardpointType.DOCKING, 0f, 0.5f, 180f),
            new VisualHardpoint("fore", VisualHardpointType.DOCKING, 1f, 0.5f, 0f),
            new VisualHardpoint("top", VisualHardpointType.DOCKING, 0.5f, 1f, 90f),
            new VisualHardpoint("gun", VisualHardpointType.WEAPON, 0.5f, 0.5f, 0f));

    @Test
    void portsTurnWithTheStationAndOnlyDockingPointsCount() {
        var level = DockingArmGeometry.ports(CROSS, 500f, 300f, 400f, 200f, 0d);
        assertEquals(3, level.size(), "the weapon hardpoint is not a port");
        assertEquals(300f, level.get(0).x(), 1e-3f);
        assertEquals(700f, level.get(1).x(), 1e-3f);
        assertEquals(400f, level.get(2).y(), 1e-3f, "y is up: the top port is above the centre");
        assertEquals(90f, level.get(2).directionDegrees(), 1e-3f);

        var turned = DockingArmGeometry.ports(CROSS, 500f, 300f, 400f, 200f, Math.PI / 2d);
        assertEquals(500f, turned.get(1).x(), 1e-3f);
        assertEquals(500f, turned.get(1).y(), 1e-3f, "the fore port points up after a quarter turn");
        assertEquals(90f, turned.get(1).directionDegrees(), 1e-3f);
        assertEquals(400f, turned.get(2).x(), 1e-3f, "the top port turns to the left");

        var nearest = DockingArmGeometry.nearest(level, 760f, 310f);
        assertEquals(700f, nearest.x(), 1e-3f);
        assertNull(DockingArmGeometry.nearest(List.of(), 0f, 0f));
    }

    @Test
    void armRunsFromThePortToTheShipHullWithACollarAtTheShipEnd() {
        var port = new DockingArmGeometry.Port(0f, 0f, 0f);
        // A ship 300 px to the right, broadside on (heading up), 120 px long and 40 px wide; tube 16 px.
        var arm = DockingArmGeometry.layout(port, 300f, 0f, 120f, 40f, Math.PI / 2d, 16f);
        float quad = 16f / DockingArmGeometry.TUBE_SHARE;
        assertEquals(32f, quad, 1e-3f);
        // Ship end: its spine (the centre here) pulled back by a quarter of the hull width.
        float shipEnd = 300f - 10f;
        assertEquals(shipEnd - quad, arm.tubeEndX(), 1e-3f);
        assertEquals(shipEnd - quad / 2f, arm.collarX(), 1e-3f);
        assertEquals(0f, arm.rotationDegrees(), 1e-3f);
        assertEquals((shipEnd - quad) / (quad * DockingArmGeometry.TILE_ASPECT), arm.tileRepeats(), 1e-4f);

        // A ship lying along the arm: the end moves along its spine towards the port, within reach.
        var along = DockingArmGeometry.layout(port, 300f, 0f, 120f, 40f, 0d, 16f);
        float reach = (float) (60d * DockingArmGeometry.SPINE_REACH);
        assertEquals(300f - reach - 10f - quad, along.tubeEndX(), 1e-3f);

        // Right at the port only the collar is drawn.
        var close = DockingArmGeometry.layout(port, 20f, 0f, 0f, 0f, 0d, 16f);
        assertEquals(0f, close.tileRepeats(), 1e-6f);
        assertNull(DockingArmGeometry.layout(port, 0f, 0f, 0f, 0f, 0d, 16f));
    }

    @Test
    void everyStationSpriteHasDockingPortsInsideItsVisibleRegion() {
        for (VisualRole role : List.of(VisualRole.TRADE_DOCK_STATION, VisualRole.INDUSTRIAL_STATION, VisualRole.SHIPYARD_STATION)) {
            var ports = Stage20MinimumPlayableSpriteCatalog.binding(role).hardpoints().stream()
                    .filter(h -> h.type() == VisualHardpointType.DOCKING).toList();
            assertTrue(ports.size() >= 2, role + " has docking ports");
            for (var port : ports) {
                // Each port points outwards: away from the sprite centre.
                double outX = port.normalizedX() - 0.5d;
                double outY = port.normalizedY() - 0.5d;
                double dirX = Math.cos(Math.toRadians(port.directionDegrees()));
                double dirY = Math.sin(Math.toRadians(port.directionDegrees()));
                assertTrue(outX * dirX + outY * dirY > 0d, role + " " + port.id() + " points out of the hull");
            }
        }
    }
}

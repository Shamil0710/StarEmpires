package com.spacesim.ui;

import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen geometry of a boarding arm: from the station's docking port nearest the ship to the
 * ship's hull, drawn as a repeating tube segment and a docking collar at the ship end.
 *
 * <p>Ports are {@link VisualHardpointType#DOCKING} hardpoints of the station sprite, in its
 * visible region with {@code y} up, turned with the station. The ship end lies on the ship's spine
 * (as for the mining beam, since ship sprites have wide transparent margins across the hull),
 * pulled back towards the port by a quarter of the hull width. Pure presentation geometry.</p>
 */
final class DockingArmGeometry {
    /** Physical tube width; the drawn width follows the zoom within pixel bounds. */
    static final double ARM_WIDTH_M = 12d;
    /** Share of the segment texture height the tube body fills (32 of 64 texels; the collar ring is 36). */
    static final float TUBE_SHARE = 32f / 64f;
    /** Segment texture width over its height (256 by 64 texels). */
    static final float TILE_ASPECT = 256f / 64f;
    /** Share of the visible half-length the ship end may move along the spine from the centre. */
    static final double SPINE_REACH = 0.48d;

    private DockingArmGeometry() {
    }

    /**
     * @param stationLengthPx station sprite length on screen
     * @param stationMarkerPx station marker size
     * @param shipLengthPx ship sprite length on screen
     * @param shipMarkerPx ship marker size
     * @return whether both ends are drawn as hulls; an arm onto a marker would cover it
     */
    static boolean shown(float stationLengthPx, float stationMarkerPx, float shipLengthPx, float shipMarkerPx) {
        return stationLengthPx >= stationMarkerPx && shipLengthPx >= shipMarkerPx
                && Float.isFinite(stationLengthPx) && Float.isFinite(shipLengthPx);
    }

    /**
     * One docking port on screen.
     *
     * @param x screen X
     * @param y screen Y
     * @param directionDegrees direction out of the hull
     */
    record Port(float x, float y, float directionDegrees) { }

    /**
     * One arm on screen.
     *
     * @param portX port X, where the tube starts
     * @param portY port Y
     * @param tubeEndX end of the repeating tube, where the collar starts
     * @param tubeEndY end of the repeating tube
     * @param collarX collar centre X
     * @param collarY collar centre Y
     * @param collarLength collar length along the arm: the quad height, or less on a short arm
     * @param quadHeight height of the drawn texture quads (the tube fills {@link #TUBE_SHARE} of it)
     * @param rotationDegrees arm direction, port to ship
     * @param tileRepeats how many segment tiles the tube spans; zero when only the collar fits
     */
    record Layout(float portX, float portY, float tubeEndX, float tubeEndY, float collarX, float collarY,
            float collarLength, float quadHeight, float rotationDegrees, float tileRepeats) { }

    /**
     * @param hardpoints station sprite hardpoints
     * @param centerX station centre X
     * @param centerY station centre Y
     * @param lengthPx station sprite length on screen
     * @param widthPx station sprite width on screen
     * @param headingRad station heading
     * @return the station's docking ports on screen
     */
    static List<Port> ports(List<VisualHardpoint> hardpoints, float centerX, float centerY, float lengthPx, float widthPx,
            double headingRad) {
        double heading = Double.isFinite(headingRad) ? headingRad : 0d;
        double cos = Math.cos(heading);
        double sin = Math.sin(heading);
        List<Port> ports = new ArrayList<>();
        for (VisualHardpoint hardpoint : hardpoints) {
            if (hardpoint.type() != VisualHardpointType.DOCKING) continue;
            double localX = (hardpoint.normalizedX() - 0.5d) * lengthPx;
            double localY = (hardpoint.normalizedY() - 0.5d) * widthPx;
            ports.add(new Port((float) (centerX + localX * cos - localY * sin), (float) (centerY + localX * sin + localY * cos),
                    (float) (hardpoint.directionDegrees() + Math.toDegrees(heading))));
        }
        return List.copyOf(ports);
    }

    /**
     * @param ports docking ports on screen
     * @param x ship X
     * @param y ship Y
     * @return the port nearest the ship, or null without ports
     */
    static Port nearest(List<Port> ports, float x, float y) {
        Port best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Port port : ports) {
            double distance = Math.hypot(port.x() - x, port.y() - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = port;
            }
        }
        return best;
    }

    /**
     * Lays out the arm from a port to the ship.
     *
     * @param port station docking port
     * @param shipX ship centre X
     * @param shipY ship centre Y
     * @param shipLengthPx ship sprite length on screen
     * @param shipWidthPx ship sprite width on screen
     * @param shipHeadingRad ship heading
     * @param armWidthPx drawn tube width
     * @return the arm, or null when the ship end coincides with the port
     */
    static Layout layout(Port port, float shipX, float shipY, float shipLengthPx, float shipWidthPx, double shipHeadingRad,
            float armWidthPx) {
        double heading = Double.isFinite(shipHeadingRad) ? shipHeadingRad : 0d;
        double axisX = Math.cos(heading);
        double axisY = Math.sin(heading);
        double reach = Math.max(0d, shipLengthPx) * 0.5d * SPINE_REACH;
        double along = Math.max(-reach, Math.min(reach, (port.x() - shipX) * axisX + (port.y() - shipY) * axisY));
        double spineX = shipX + axisX * along;
        double spineY = shipY + axisY * along;
        double dx = spineX - port.x();
        double dy = spineY - port.y();
        double toSpine = Math.hypot(dx, dy);
        if (!(toSpine > 1e-3d) || !Double.isFinite(toSpine)) return null;
        double ux = dx / toSpine;
        double uy = dy / toSpine;
        double distance = Math.max(1e-3d, toSpine - Math.min(toSpine * 0.5d, Math.max(0d, shipWidthPx) * 0.25d));
        float quad = Math.max(1f, armWidthPx) / TUBE_SHARE;
        double collar = Math.min(quad, distance);
        double tube = distance - collar;
        double endX = port.x() + ux * tube;
        double endY = port.y() + uy * tube;
        return new Layout(port.x(), port.y(), (float) endX, (float) endY,
                (float) (endX + ux * collar * 0.5d), (float) (endY + uy * collar * 0.5d), (float) collar, quad,
                (float) Math.toDegrees(Math.atan2(uy, ux)), (float) (tube / (quad * TILE_ASPECT)));
    }
}

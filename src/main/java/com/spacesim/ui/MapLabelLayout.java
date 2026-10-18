package com.spacesim.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Placement of system-map name plates with leader lines.
 *
 * <p>Plates are placed greedily in priority order (the selection first). Each plate tries
 * positions around its object, just outside the object's on-screen clearance, then a second ring
 * further out for crowded groups. A plate never overlaps an accepted plate, a fixed map overlay
 * (title, scale bar) or the marker of any other visible object, labelled or not, and stays inside
 * the map. A label that finds no free place is hidden, except the selection, which also tries
 * rings further out and then takes the first in-map position clear of the fixed overlays, or any
 * in-map position as a last resort. A large hull with no free place just past its
 * edge (a hull filling the view) gets its plate on the hull itself, clear of other markers.
 * Pure presentation geometry: it never reads or changes simulation state.</p>
 */
final class MapLabelLayout {
    /** Directions tried around an object, as unit offsets: right first, then left, diagonals, top, bottom. */
    private static final float[][] DIRECTIONS = {
            {1f, 0f}, {-1f, 0f}, {1f, 1f}, {1f, -1f}, {-1f, 1f}, {-1f, -1f}, {0f, 1f}, {0f, -1f}};
    private static final float SECOND_RING = 2.2f;
    /** Rings tried for the selection, which must be labelled even in crowds or under overlays. */
    private static final float[] REQUIRED_RINGS = {1f, SECOND_RING, 3.5f, 5f, 8f};
    /** Vertical steps, in plate heights, tried for a plate placed on a large hull. */
    private static final float[] ON_HULL_STEPS = {0f, 1.5f, -1.5f, 3f, -3f};

    /**
     * Axis-aligned box.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width width
     * @param height height
     */
    record Box(float x, float y, float width, float height) {
        float right() {
            return x + width;
        }

        float top() {
            return y + height;
        }

        boolean overlaps(Box other) {
            return x < other.right() && right() > other.x() && y < other.top() && top() > other.y();
        }

        boolean inside(Box outer) {
            return x >= outer.x() && y >= outer.y() && right() <= outer.right() && top() <= outer.top();
        }

        boolean coversDisc(float centerX, float centerY, float radius) {
            float nearestX = Math.max(x, Math.min(centerX, right()));
            float nearestY = Math.max(y, Math.min(centerY, top()));
            float dx = centerX - nearestX;
            float dy = centerY - nearestY;
            return dx * dx + dy * dy < radius * radius;
        }
    }

    /**
     * Marker of one visible object, which plates of other objects must not cover.
     *
     * @param id stable object id
     * @param x marker centre X
     * @param y marker centre Y
     * @param radius marker radius
     */
    record Marker(String id, float x, float y, float radius) { }

    /**
     * One label to place.
     *
     * @param id stable object id
     * @param anchorX object centre X
     * @param anchorY object centre Y
     * @param clearance on-screen radius the plate must stay outside of
     * @param plateWidth plate width including padding
     * @param plateHeight plate height including padding
     * @param priority lower values are placed first
     * @param required true when the label must be shown even if crowded (the selection)
     */
    record Request(String id, float anchorX, float anchorY, float clearance,
            float plateWidth, float plateHeight, int priority, boolean required) { }

    /**
     * One placed label.
     *
     * @param id stable object id
     * @param plate plate box
     * @param leader leader line from the object's clearance edge to the plate, or null
     * @param onObject true when the plate sits on an object too large to label from outside
     */
    record Placement(String id, Box plate, Leader leader, boolean onObject) { }

    /**
     * Leader line from an object to its plate.
     *
     * @param x1 start X on the object's clearance edge
     * @param y1 start Y on the object's clearance edge
     * @param x2 end X on the plate
     * @param y2 end Y on the plate
     */
    record Leader(float x1, float y1, float x2, float y2) { }

    private MapLabelLayout() {
    }

    /**
     * @param lengthPixels hull length on screen
     * @param widthPixels hull width on screen
     * @return radius of the circle that holds the hull at any heading (half its diagonal)
     */
    static float hullClearance(float lengthPixels, float widthPixels) {
        return (float) (Math.hypot(lengthPixels, widthPixels) / 2d);
    }

    /**
     * Places labels inside the map.
     *
     * @param requests labels to place
     * @param markers markers of every visible object, labelled or not
     * @param map map bounds
     * @param overlays fixed map overlays plates must avoid, such as the map title or scale bar
     * @param gap distance between the clearance edge and a plate
     * @param minLeader shortest leader line worth drawing
     * @return placements in placing order; hidden labels are absent
     */
    static List<Placement> place(List<Request> requests, List<Marker> markers, Box map, List<Box> overlays,
            float gap, float minLeader) {
        List<Request> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparingInt(Request::priority).thenComparing(Request::id));
        float onObjectLimit = Math.min(map.width(), map.height()) / 3f;
        List<Placement> placed = new ArrayList<>();
        List<Box> plates = new ArrayList<>();
        for (Request request : ordered) {
            boolean large = request.clearance() > onObjectLimit;
            Placement placement = around(request, markers, map, overlays, plates, gap, minLeader, large);
            if (placement == null && large) placement = onObject(request, markers, map, overlays, plates);
            if (placement != null) {
                placed.add(placement);
                plates.add(placement.plate());
            }
        }
        return List.copyOf(placed);
    }

    private static Placement around(Request request, List<Marker> markers, Box map, List<Box> overlays,
            List<Box> plates, float gap, float minLeader, boolean large) {
        Box fallback = null;
        Box lastResort = null;
        // A large hull is labelled just past its edge or, failing that, on the hull itself. The
        // selection may move further out, with a leader, to get clear of crowding and overlays.
        float[] rings = large ? new float[] {1f}
                : request.required() ? REQUIRED_RINGS : new float[] {1f, SECOND_RING};
        for (float ring : rings) {
            for (float[] direction : DIRECTIONS) {
                Box plate = candidate(request, direction, request.clearance() * ring + gap * ring);
                if (!plate.inside(map) || plate.coversDisc(request.anchorX(), request.anchorY(), request.clearance())) {
                    continue;
                }
                if (lastResort == null) lastResort = plate;
                boolean clearOfOverlays = overlays.stream().noneMatch(plate::overlaps);
                if (fallback == null && clearOfOverlays) fallback = plate;
                if (clearOfOverlays && plates.stream().noneMatch(plate::overlaps)
                        && !coversOtherMarker(plate, request.id(), markers)) {
                    return new Placement(request.id(), plate, leader(request, plate, minLeader), false);
                }
            }
        }
        Box chosen = fallback != null ? fallback : lastResort;
        if (!large && request.required() && chosen != null) {
            return new Placement(request.id(), chosen, leader(request, chosen, minLeader), false);
        }
        return null;
    }

    private static Placement onObject(Request request, List<Marker> markers, Box map, List<Box> overlays,
            List<Box> plates) {
        Box fallback = null;
        Box lastResort = null;
        for (float step : onHullSteps(request, map)) {
            float x = clamp(request.anchorX() - request.plateWidth() / 2f, map.x(), map.right() - request.plateWidth());
            float y = clamp(request.anchorY() - request.plateHeight() / 2f + step * request.plateHeight(),
                    map.y(), map.top() - request.plateHeight());
            Box plate = new Box(x, y, request.plateWidth(), request.plateHeight());
            if (!plate.inside(map)) continue;
            if (lastResort == null) lastResort = plate;
            if (overlays.stream().anyMatch(plate::overlaps)) continue;
            if (fallback == null) fallback = plate;
            if (plates.stream().noneMatch(plate::overlaps) && !coversOtherMarker(plate, request.id(), markers)) {
                return new Placement(request.id(), plate, null, true);
            }
        }
        Box chosen = fallback != null ? fallback : lastResort;
        return request.required() && chosen != null ? new Placement(request.id(), chosen, null, true) : null;
    }

    /** Vertical steps along a large hull: a few near its centre, or the whole map height for the selection. */
    private static float[] onHullSteps(Request request, Box map) {
        if (!request.required()) return ON_HULL_STEPS;
        int count = (int) Math.ceil(map.height() / (1.5f * request.plateHeight()));
        float[] steps = new float[2 * count + 1];
        for (int index = 1; index <= count; index++) {
            steps[2 * index - 1] = 1.5f * index;
            steps[2 * index] = -1.5f * index;
        }
        return steps;
    }

    /** Plate whose nearest edge or corner sits at the given distance from the anchor. */
    private static Box candidate(Request request, float[] direction, float distance) {
        float dx = direction[0];
        float dy = direction[1];
        float length = (float) Math.hypot(dx, dy);
        float cx = request.anchorX() + dx / length * distance;
        float cy = request.anchorY() + dy / length * distance;
        float x = dx > 0f ? cx : dx < 0f ? cx - request.plateWidth() : cx - request.plateWidth() / 2f;
        float y = dy > 0f ? cy : dy < 0f ? cy - request.plateHeight() : cy - request.plateHeight() / 2f;
        return new Box(x, y, request.plateWidth(), request.plateHeight());
    }

    private static boolean coversOtherMarker(Box plate, String owner, List<Marker> markers) {
        for (Marker marker : markers) {
            if (!marker.id().equals(owner) && plate.coversDisc(marker.x(), marker.y(), marker.radius())) {
                return true;
            }
        }
        return false;
    }

    /** Leader from the clearance circle toward the nearest plate point, or null when too short. */
    private static Leader leader(Request request, Box plate, float minLeader) {
        float nearestX = clamp(request.anchorX(), plate.x(), plate.right());
        float nearestY = clamp(request.anchorY(), plate.y(), plate.top());
        float dx = nearestX - request.anchorX();
        float dy = nearestY - request.anchorY();
        float distance = (float) Math.hypot(dx, dy);
        float length = distance - request.clearance();
        if (!(distance > 0f) || length < minLeader) return null;
        float startX = request.anchorX() + dx / distance * request.clearance();
        float startY = request.anchorY() + dy / distance * request.clearance();
        return new Leader(startX, startY, nearestX, nearestY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Additive halos of the system map, drawn from pre-baked glow textures instead of a bloom pass:
 * a soft halo behind stations, outposts, jump anchors and special locations, and blinking
 * marker lights at both ends of a station hull.
 *
 * <p>Sizes follow the object on screen within fixed pixel bounds, so the glow stays readable
 * when zoomed out and never floods the map when zoomed in. Beacon blinking is a presentation
 * clock, phased per object; it reads no simulation state. Pure presentation geometry.</p>
 */
final class MapHaloLayout {
    /** Most halos drawn in one frame, a fixed frame-budget bound. */
    static final int MAX_HALOS = 160;
    /** Seconds of one beacon blink cycle. */
    static final float BEACON_PERIOD_SECONDS = 2.4f;
    /** Share of the cycle a beacon is lit. */
    static final float BEACON_DUTY = 0.3f;
    /** Shortest station hull on screen, in unscaled pixels, that shows beacons. */
    static final float BEACON_MIN_HULL_PX = 28f;
    /** Beacon distance from the hull centre, as a share of the hull length. */
    static final float BEACON_REACH = 0.42f;

    /** Colour family of a halo. */
    enum Tint {
        /** Warm white station floodlight. */ STATION,
        /** Amber outpost work lights. */ OUTPOST,
        /** Cyan jump-anchor field. */ JUMP,
        /** Violet anomaly glow. */ ANOMALY,
        /** Red marker light at the hull's aft end. */ BEACON_RED,
        /** White marker light at the hull's fore end. */ BEACON_WHITE
    }

    /** Whether a halo goes behind the sprites or over them. */
    enum Layer {
        /** Under markers and hulls. */ BEHIND,
        /** Over hulls, for small lights. */ OVER
    }

    /**
     * One object as the map draws it.
     *
     * @param stableId persistent map id, for the blink phase
     * @param kind object family
     * @param x screen centre X
     * @param y screen centre Y
     * @param lengthPx hull length on screen (zero without a hull)
     * @param widthPx hull width on screen
     * @param headingRad hull heading
     * @param markerPx marker size on screen
     */
    record Body(String stableId, ObjectKind kind, float x, float y, float lengthPx, float widthPx,
            double headingRad, float markerPx) { }

    /**
     * One halo to draw.
     *
     * @param x screen centre X
     * @param y screen centre Y
     * @param radiusPx halo radius on screen
     * @param tint colour family
     * @param alpha opacity in {@code (0, 1]}
     * @param layer behind or over the sprites
     * @param flare whether to use the four-spike light flare instead of the soft halo
     */
    record Halo(float x, float y, float radiusPx, Tint tint, float alpha, Layer layer, boolean flare) { }

    private MapHaloLayout() {
    }

    /**
     * Lays out the halos of one frame.
     *
     * @param bodies objects on the map
     * @param map map box; halos entirely outside it are dropped
     * @param timeSeconds presentation clock for blinking and pulsing
     * @param scale UI scale
     * @return halos in body order, at most {@link #MAX_HALOS}
     */
    static List<Halo> layout(List<Body> bodies, MapLabelLayout.Box map, float timeSeconds, float scale) {
        float time = Float.isFinite(timeSeconds) ? timeSeconds : 0f;
        List<Halo> halos = new ArrayList<>();
        for (Body body : bodies) {
            if (halos.size() >= MAX_HALOS) break;
            if (!Float.isFinite(body.x()) || !Float.isFinite(body.y())) continue;
            float hull = hullRadius(body);
            switch (body.kind()) {
                case STATION -> {
                    add(halos, map, new Halo(body.x(), body.y(), clamp(1.4f * Math.max(hull, body.markerPx() * 1.6f),
                            12f * scale, 320f * scale), Tint.STATION, 0.5f, Layer.BEHIND, false));
                    addBeacons(halos, map, body, time, scale);
                }
                case EXTRACTION_OUTPOST -> add(halos, map, new Halo(body.x(), body.y(),
                        clamp(1.3f * Math.max(hull, body.markerPx() * 1.6f), 10f * scale, 220f * scale),
                        Tint.OUTPOST, 0.45f, Layer.BEHIND, false));
                case JUMP_ANCHOR -> add(halos, map, new Halo(body.x(), body.y(), body.markerPx() * 3.2f, Tint.JUMP,
                        0.6f * (0.9f + 0.1f * (float) Math.sin(time * 2d * Math.PI / 3.2d)), Layer.BEHIND, false));
                case SPECIAL_LOCATION -> add(halos, map, new Halo(body.x(), body.y(), body.markerPx() * 3f,
                        Tint.ANOMALY, 0.5f, Layer.BEHIND, false));
                default -> { }
            }
        }
        return halos.size() > MAX_HALOS ? List.copyOf(halos.subList(0, MAX_HALOS)) : List.copyOf(halos);
    }

    /**
     * Beacon brightness over the blink cycle: a short smooth flash, dark for the rest.
     *
     * @param phase cycle position in {@code [0, 1)}
     * @return brightness in {@code [0, 1]}
     */
    static float blink(float phase) {
        float p = phase - (float) Math.floor(phase);
        return p < BEACON_DUTY ? (float) Math.sin(Math.PI * p / BEACON_DUTY) : 0f;
    }

    /**
     * @param stableId object id
     * @return stable blink phase offset in {@code [0, 1)}
     */
    static float phaseOffset(String stableId) {
        int hash = stableId.hashCode() * 0x9E3779B1;
        return ((hash >>> 8) & 0xFFFFFF) / (float) (1 << 24);
    }

    private static void addBeacons(List<Halo> halos, MapLabelLayout.Box map, Body body, float time, float scale) {
        if (!(body.lengthPx() >= BEACON_MIN_HULL_PX * scale) || !(body.widthPx() > 0f)) return;
        double heading = Double.isFinite(body.headingRad()) ? body.headingRad() : 0d;
        float axisX = (float) Math.cos(heading);
        float axisY = (float) Math.sin(heading);
        float reach = body.lengthPx() * BEACON_REACH;
        float radius = clamp(body.lengthPx() * 0.06f, 7f * scale, 22f * scale);
        float cycle = time / BEACON_PERIOD_SECONDS + phaseOffset(body.stableId());
        // The aft red and fore white marker lights alternate, half a cycle apart.
        addLight(halos, map, body.x() - axisX * reach, body.y() - axisY * reach, radius, Tint.BEACON_RED, blink(cycle));
        addLight(halos, map, body.x() + axisX * reach, body.y() + axisY * reach, radius, Tint.BEACON_WHITE,
                blink(cycle + 0.5f));
    }

    /** One marker light: a soft glow over the hull and the spiked flare on top of it. */
    private static void addLight(List<Halo> halos, MapLabelLayout.Box map, float x, float y, float radius, Tint tint,
            float brightness) {
        if (!(brightness > 0f)) return;
        add(halos, map, new Halo(x, y, radius * 2.2f, tint, 0.45f * brightness, Layer.OVER, false));
        add(halos, map, new Halo(x, y, radius, tint, 0.95f * brightness, Layer.OVER, true));
    }

    private static void add(List<Halo> halos, MapLabelLayout.Box map, Halo halo) {
        if (!(halo.radiusPx() > 0f) || !(halo.alpha() > 0f)) return;
        if (halo.x() + halo.radiusPx() < map.x() || halo.x() - halo.radiusPx() > map.right()
                || halo.y() + halo.radiusPx() < map.y() || halo.y() - halo.radiusPx() > map.top()) return;
        halos.add(halo);
    }

    private static float hullRadius(Body body) {
        if (!(body.lengthPx() > 0f) || !(body.widthPx() > 0f)) return 0f;
        return MapLabelLayout.hullClearance(body.lengthPx(), body.widthPx());
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

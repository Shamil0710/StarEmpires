package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.ui.MapHaloLayout.Body;
import com.spacesim.ui.MapHaloLayout.Layer;
import com.spacesim.ui.MapHaloLayout.Tint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapHaloLayoutTest {
    private static final MapLabelLayout.Box MAP = new MapLabelLayout.Box(0f, 0f, 900f, 600f);

    private static Body station(float length, double heading) {
        return new Body("station:hub.8", ObjectKind.STATION, 400f, 300f, length, length * 0.63f, heading, 16f);
    }

    /** A time at which neither marker light of the station is lit. */
    private static float darkTime() {
        float offset = MapHaloLayout.phaseOffset("station:hub.8");
        return (0.35f - offset + 1f) * MapHaloLayout.BEACON_PERIOD_SECONDS;
    }

    @Test
    void stationHaloSpillsPastTheHullWithinPixelBounds() {
        var halos = MapHaloLayout.layout(List.of(station(300f, 0d)), MAP, darkTime(), 1f);
        assertEquals(1, halos.size(), "dark beacons draw nothing");
        var halo = halos.get(0);
        assertEquals(Tint.STATION, halo.tint());
        assertEquals(Layer.BEHIND, halo.layer());
        // Behind an opaque hull only the part past the hull shows, so the halo reaches beyond it.
        assertEquals(1.4f * MapLabelLayout.hullClearance(300f, 189f), halo.radiusPx(), 1e-3f);
        assertTrue(halo.radiusPx() > 150f, "past the hull's half-length");

        // Zoomed far in the halo stops growing; zoomed out it keeps a readable minimum around the marker.
        assertEquals(320f, MapHaloLayout.layout(List.of(station(5000f, 0d)), MAP, darkTime(), 1f).get(0).radiusPx(), 1e-3f);
        assertEquals(1.4f * 16f * 1.6f, MapHaloLayout.layout(List.of(station(0f, 0d)), MAP, darkTime(), 1f).get(0).radiusPx(), 1e-3f);
        assertEquals(12f * 2f, MapHaloLayout.layout(List.of(new Body("s", ObjectKind.STATION, 10f, 10f, 0f, 0f, 0d, 4f)),
                MAP, darkTime(), 2f).get(0).radiusPx(), 1e-3f);
    }

    @Test
    void markerLightsAlternateAtBothEndsOfTheHull() {
        float offset = MapHaloLayout.phaseOffset("station:hub.8");
        // Halfway through its lit share of the cycle the aft red light peaks; the fore white light is dark.
        float redTime = (MapHaloLayout.BEACON_DUTY / 2f - offset + 1f) * MapHaloLayout.BEACON_PERIOD_SECONDS;
        var red = MapHaloLayout.layout(List.of(station(300f, 0d)), MAP, redTime, 1f);
        assertEquals(3, red.size(), "station halo, then the light's soft glow and its flare");
        var glow = red.get(1);
        var flare = red.get(2);
        for (var light : List.of(glow, flare)) {
            assertEquals(Tint.BEACON_RED, light.tint());
            assertEquals(Layer.OVER, light.layer());
            assertEquals(400f - 0.42f * 300f, light.x(), 1e-3f);
            assertEquals(300f, light.y(), 1e-3f);
        }
        assertTrue(flare.flare());
        assertEquals(0.95f, flare.alpha(), 1e-3f);
        assertEquals(18f, flare.radiusPx(), 1e-3f);
        assertEquals(18f * 2.2f, glow.radiusPx(), 1e-3f);
        assertEquals(0.45f, glow.alpha(), 1e-3f);

        // Half a cycle later the fore white light flashes, on the far end; turned a quarter, it is above.
        float whiteTime = redTime + 0.5f * MapHaloLayout.BEACON_PERIOD_SECONDS;
        var white = MapHaloLayout.layout(List.of(station(300f, Math.PI / 2d)), MAP, whiteTime, 1f);
        assertEquals(3, white.size());
        assertEquals(Tint.BEACON_WHITE, white.get(2).tint());
        assertEquals(400f, white.get(2).x(), 1e-3f);
        assertEquals(300f + 0.42f * 300f, white.get(2).y(), 1e-3f);

        // A station too small on screen shows its halo only.
        assertEquals(1, MapHaloLayout.layout(List.of(station(20f, 0d)), MAP, redTime, 1f).size());
    }

    @Test
    void blinkIsAShortSmoothFlash() {
        assertEquals(0f, MapHaloLayout.blink(0f), 1e-6f);
        assertEquals(1f, MapHaloLayout.blink(MapHaloLayout.BEACON_DUTY / 2f), 1e-6f);
        assertEquals(0f, MapHaloLayout.blink(0.5f));
        assertEquals(MapHaloLayout.blink(0.05f), MapHaloLayout.blink(3.05f), 1e-5f);
        float phase = MapHaloLayout.phaseOffset("station:hub.8");
        assertTrue(phase >= 0f && phase < 1f);
        assertEquals(phase, MapHaloLayout.phaseOffset("station:hub.8"));
    }

    @Test
    void otherKindsAndOffMapObjectsGetTheirOwnOrNoHalo() {
        float t = darkTime();
        var bodies = List.of(
                new Body("fleet:1", ObjectKind.FLEET, 100f, 100f, 80f, 20f, 0d, 14f),
                new Body("resource:r", ObjectKind.RESOURCE, 120f, 100f, 60f, 60f, 0d, 12f),
                new Body("outpost:o", ObjectKind.EXTRACTION_OUTPOST, 200f, 100f, 0f, 0f, 0d, 14f),
                new Body("jump:j", ObjectKind.JUMP_ANCHOR, 300f, 100f, 0f, 0f, 0d, 12f),
                new Body("special:s", ObjectKind.SPECIAL_LOCATION, 400f, 100f, 0f, 0f, 0d, 12f),
                new Body("station:far", ObjectKind.STATION, 5000f, 100f, 300f, 200f, 0d, 16f),
                new Body("station:nan", ObjectKind.STATION, Float.NaN, 100f, 300f, 200f, 0d, 16f));
        var halos = MapHaloLayout.layout(bodies, MAP, t, 1f);
        assertEquals(List.of(Tint.OUTPOST, Tint.JUMP, Tint.ANOMALY), halos.stream().map(MapHaloLayout.Halo::tint).toList(),
                "ships and bare resources have no halo; an off-map or invalid station is dropped");
        assertEquals(12f * 3.2f, halos.get(1).radiusPx(), 1e-3f);
        float pulse = halos.get(1).alpha();
        assertTrue(pulse >= 0.6f * 0.8f - 1e-6f && pulse <= 0.6f + 1e-6f, "the jump field pulses gently");
    }

    @Test
    void haloCountIsBounded() {
        var many = new ArrayList<Body>();
        for (int i = 0; i < 400; i++) many.add(new Body("station:" + i, ObjectKind.STATION, 400f, 300f, 300f, 200f, 0d, 16f));
        assertEquals(MapHaloLayout.MAX_HALOS, MapHaloLayout.layout(many, MAP, 0.1f, 1f).size());
    }
}

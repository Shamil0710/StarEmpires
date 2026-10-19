package com.spacesim.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation state of mining beams between frames: each beam fades in when its order appears,
 * emits a spark burst each time the order's authoritative watermark advances (one completed
 * extraction), and fades out after the order is gone.
 *
 * <p>Bursts are driven only by observed watermark advances, never by a local timer, so a paused
 * or stopped campaign emits nothing new. The state never reads or changes simulation state and is
 * not saved: after a load the first frame only records the watermarks.</p>
 */
final class MiningBeamEffects {
    /** Seconds a beam takes to reach full intensity. */
    static final float FADE_IN_SECONDS = 0.15f;
    /** Seconds a beam takes to fade out after its order ends. */
    static final float FADE_OUT_SECONDS = 0.35f;
    /** Seconds one spark burst lasts. */
    static final float BURST_SECONDS = 0.45f;
    /** Upper bound on tracked beams, so a flood of orders cannot grow the state. */
    static final int MAX_TRACKED = 64;

    /**
     * One beam to draw this frame.
     *
     * @param shipStableId system-map id of the mining ship
     * @param sourceStableId system-map id of the contacted source
     * @param intensity overall opacity in {@code (0, 1]}, including fades
     * @param active whether the order still exists (false while fading out)
     * @param burstAge seconds since the last extraction burst, or a negative value without one
     * @param burstSeed deterministic seed of the current burst
     * @param ageSeconds seconds since the beam appeared, for the pulse phase
     */
    record Effect(String shipStableId, String sourceStableId, float intensity, boolean active,
            float burstAge, long burstSeed, float ageSeconds) {
        boolean bursting() {
            return burstAge >= 0f && burstAge < BURST_SECONDS;
        }
    }

    private static final class Track {
        final String ship;
        final String source;
        long watermark;
        float intensity;
        boolean active = true;
        float burstAge = -1f;
        long burstSeed;
        float age;

        Track(MiningBeam beam) {
            ship = beam.shipStableId();
            source = beam.sourceStableId();
            watermark = beam.lastProcessedTick();
        }
    }

    private final Map<String, Track> tracks = new LinkedHashMap<>();

    /**
     * Advances the effects by one frame.
     *
     * @param live beams of the current authoritative orders
     * @param deltaSeconds frame time; non-finite or negative counts as zero
     * @return beams to draw, in first-seen order
     */
    List<Effect> advance(List<MiningBeam> live, float deltaSeconds) {
        float dt = Float.isFinite(deltaSeconds) && deltaSeconds > 0f ? deltaSeconds : 0f;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MiningBeam beam : live) {
            if (!seen.add(beam.key())) continue;
            Track track = tracks.get(beam.key());
            if (track == null) {
                if (tracks.size() >= MAX_TRACKED) continue;
                tracks.put(beam.key(), new Track(beam));
                continue;
            }
            if (!track.active) {
                // A new order on a fading pair: its watermark is an assignment, not an extraction.
                track.active = true;
                track.watermark = beam.lastProcessedTick();
                continue;
            }
            if (beam.lastProcessedTick() > track.watermark) {
                track.watermark = beam.lastProcessedTick();
                track.burstAge = 0f;
                track.burstSeed = seed(beam.key(), track.watermark);
            }
        }
        List<Effect> effects = new ArrayList<>();
        var iterator = tracks.values().iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            track.active = seen.contains(track.ship + '|' + track.source);
            track.age += dt;
            if (track.burstAge >= 0f) track.burstAge += dt;
            if (track.active) {
                track.intensity = Math.min(1f, track.intensity + dt / FADE_IN_SECONDS);
            } else {
                track.intensity -= dt / FADE_OUT_SECONDS;
                if (track.intensity <= 0f) {
                    iterator.remove();
                    continue;
                }
            }
            if (track.intensity <= 0f) continue;
            effects.add(new Effect(track.ship, track.source, track.intensity, track.active,
                    track.burstAge, track.burstSeed, track.age));
        }
        return List.copyOf(effects);
    }

    /** Forgets every beam, for a loaded or replaced campaign. */
    void clear() {
        tracks.clear();
    }

    private static long seed(String key, long tick) {
        long z = key.hashCode() * 0x9E3779B97F4A7C15L + tick;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

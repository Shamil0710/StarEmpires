package com.spacesim.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Presentation state of mining beams between frames: a beam fades in while its order is active
 * and fades out after it ends; each newer extraction receipt the state observes fires one spark
 * burst, also on a fading beam (the final extraction of a depleted source).
 *
 * <p>Bursts are driven only by authoritative extraction receipts, never by a local timer or the
 * order's assignment, so a pause, a stop or a restart emits nothing. The renderer advances the
 * state every frame on every tab, so nothing is missed while the system map is hidden. The state
 * never reads or changes simulation state and is not saved: a new track only records the newest
 * receipt.</p>
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
        long lastExtraction;
        float intensity;
        boolean active = true;
        float burstAge = -1f;
        long burstSeed;
        float age;

        Track(MiningBeam beam) {
            ship = beam.shipStableId();
            source = beam.sourceStableId();
            lastExtraction = beam.lastExtractionTick();
        }
    }

    private final Map<String, Track> tracks = new LinkedHashMap<>();

    /**
     * Advances the effects by one frame.
     *
     * @param live current mining contacts: active orders and the latest tick's extractions
     * @param deltaSeconds frame time; non-finite or negative counts as zero
     * @return beams to draw, in first-seen order
     */
    List<Effect> advance(List<MiningBeam> live, float deltaSeconds) {
        float dt = Float.isFinite(deltaSeconds) && deltaSeconds > 0f ? deltaSeconds : 0f;
        Set<String> seen = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (MiningBeam beam : live) {
            if (!seen.add(beam.key())) continue;
            Track track = tracks.get(beam.key());
            if (track == null) {
                // An extraction without an order or a visible beam has nothing to light up.
                if (!beam.active() || tracks.size() >= MAX_TRACKED) continue;
                tracks.put(beam.key(), new Track(beam));
                active.add(beam.key());
                continue;
            }
            if (beam.active()) active.add(beam.key());
            if (beam.lastExtractionTick() > track.lastExtraction) {
                track.lastExtraction = beam.lastExtractionTick();
                track.burstAge = 0f;
                track.burstSeed = seed(beam.key(), track.lastExtraction);
            }
        }
        List<Effect> effects = new ArrayList<>();
        var iterator = tracks.values().iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            track.active = active.contains(track.ship + '|' + track.source);
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

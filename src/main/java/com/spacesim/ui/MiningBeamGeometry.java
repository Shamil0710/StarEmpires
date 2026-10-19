package com.spacesim.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Screen geometry of a mining beam: from an emitter on the ship's spine to the surface of the
 * contacted source, with a deterministic spark burst at the contact spot.
 *
 * <p>The emitter sits on the hull's centre line, moved along it towards the source, so the beam
 * always leaves the visible hull even though the artwork has wide transparent margins across the
 * hull. The source is a circle narrowed by {@link #HULL_FILL}. Pure presentation geometry: it
 * never reads or changes simulation state.</p>
 */
final class MiningBeamGeometry {
    /** Share of the sprite covered by the visible hull or rock. */
    static final double HULL_FILL = 0.8d;
    /** Share of the visible half-length the emitter may move along the spine from the centre. */
    static final double EMITTER_REACH = 0.6d;
    /** Sparks in one burst. */
    static final int SPARKS_PER_BURST = 8;
    /** Half-angle of the spark fan around the reflected beam direction, radians. */
    static final double SPARK_FAN_RAD = Math.toRadians(70d);

    private MiningBeamGeometry() {
    }

    /**
     * One spark of a burst.
     *
     * @param x screen X
     * @param y screen Y
     * @param directionDegrees direction of flight, for a streak
     * @param alpha opacity in {@code [0, 1]}
     */
    record Spark(float x, float y, float directionDegrees, float alpha) { }

    /**
     * Beam segment from the ship's spine emitter to the source's surface.
     *
     * @param shipX ship centre X
     * @param shipY ship centre Y
     * @param shipLengthPx ship sprite length on screen
     * @param shipHeadingRad ship heading, counter-clockwise from +X
     * @param sourceX source centre X
     * @param sourceY source centre Y
     * @param sourceDiameterPx source sprite size on screen (its smaller side)
     * @return {x1, y1, x2, y2}, or null when the emitter is inside the source on screen
     */
    static float[] segment(float shipX, float shipY, float shipLengthPx, double shipHeadingRad,
            float sourceX, float sourceY, float sourceDiameterPx) {
        double heading = Double.isFinite(shipHeadingRad) ? shipHeadingRad : 0d;
        double axisX = Math.cos(heading);
        double axisY = Math.sin(heading);
        double dx = (double) sourceX - shipX;
        double dy = (double) sourceY - shipY;
        double reach = Math.max(0d, shipLengthPx) * 0.5d * HULL_FILL * EMITTER_REACH;
        double along = Math.max(-reach, Math.min(reach, dx * axisX + dy * axisY));
        double startX = shipX + axisX * along;
        double startY = shipY + axisY * along;
        double beamX = sourceX - startX;
        double beamY = sourceY - startY;
        double distance = Math.hypot(beamX, beamY);
        double end = Math.max(0d, sourceDiameterPx) * 0.5d * HULL_FILL;
        if (!(distance > end) || !Double.isFinite(distance)) return null;
        return new float[] {(float) startX, (float) startY,
                (float) (sourceX - beamX / distance * end), (float) (sourceY - beamY / distance * end)};
    }

    /**
     * Sparks of one burst, flying from the contact spot back towards the ship in a fan.
     *
     * @param seed burst seed
     * @param age seconds since the burst began
     * @param duration burst length in seconds
     * @param contactX contact spot X
     * @param contactY contact spot Y
     * @param beamX beam direction X (ship to source)
     * @param beamY beam direction Y
     * @param reachPx distance a spark travels over the burst
     * @return sparks, empty outside the burst
     */
    static List<Spark> sparks(long seed, float age, float duration, float contactX, float contactY,
            double beamX, double beamY, float reachPx) {
        double length = Math.hypot(beamX, beamY);
        if (!(age >= 0f) || !(duration > 0f) || age >= duration || !(length > 0d)) return List.of();
        double back = Math.atan2(-beamY, -beamX);
        double progress = age / (double) duration;
        // Fast out, slowing down: the travelled share eases out.
        double travelled = 1d - (1d - progress) * (1d - progress);
        SplittableRandom random = new SplittableRandom(seed);
        List<Spark> sparks = new ArrayList<>(SPARKS_PER_BURST);
        for (int index = 0; index < SPARKS_PER_BURST; index++) {
            double angle = back + (random.nextDouble() * 2d - 1d) * SPARK_FAN_RAD;
            double reach = reachPx * (0.45d + 0.55d * random.nextDouble()) * travelled;
            float alpha = (float) ((1d - progress) * (0.6d + 0.4d * random.nextDouble()));
            sparks.add(new Spark((float) (contactX + Math.cos(angle) * reach), (float) (contactY + Math.sin(angle) * reach),
                    (float) Math.toDegrees(angle), alpha));
        }
        return List.copyOf(sparks);
    }

    /**
     * @param ageSeconds seconds since the beam appeared
     * @return pulse factor in {@code [0.78, 1]}, a slow shimmer of the beam core
     */
    static float pulse(float ageSeconds) {
        if (!Float.isFinite(ageSeconds)) return 1f;
        return (float) (0.89d + 0.11d * Math.sin(ageSeconds * 2d * Math.PI * 1.7d));
    }
}

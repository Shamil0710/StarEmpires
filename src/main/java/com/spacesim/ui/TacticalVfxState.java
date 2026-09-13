package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Presentation-only temporal state for tactical particles and flashes.
 *
 * <p>This class consumes immutable tactical snapshots and never writes back to simulation state.
 * Event identities seed all random-looking motion so repeated captures remain reproducible while
 * particle lifetime is driven only by presentation frame time.</p>
 */
final class TacticalVfxState {
    static final int MAX_PARTICLES = 512;
    static final int MAX_FLASHES = 96;
    static final int MAX_SEEN_IMPACTS = 2048;
    private static final double MAX_FRAME_SECONDS = 0.10d;

    enum ParticleKind {
        SHIELD_ARC,
        SPARK,
        HOT_FRAGMENT,
        PLASMA
    }

    enum FlashKind {
        SHIELD,
        ARMOR,
        PENETRATION,
        DESTRUCTION
    }

    static final class Particle {
        private double xM;
        private double yM;
        private final double vxMps;
        private final double vyMps;
        private final double radiusM;
        private final double lifetimeSeconds;
        private final ParticleKind kind;
        private double ageSeconds;

        private Particle(
                double xM,
                double yM,
                double vxMps,
                double vyMps,
                double radiusM,
                double lifetimeSeconds,
                ParticleKind kind) {
            this.xM = xM;
            this.yM = yM;
            this.vxMps = vxMps;
            this.vyMps = vyMps;
            this.radiusM = radiusM;
            this.lifetimeSeconds = lifetimeSeconds;
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        double xM() {
            return xM;
        }

        double yM() {
            return yM;
        }

        double vxMps() {
            return vxMps;
        }

        double vyMps() {
            return vyMps;
        }

        double radiusM() {
            return radiusM;
        }

        ParticleKind kind() {
            return kind;
        }

        double remainingFraction() {
            return Math.max(0d, 1d - ageSeconds / lifetimeSeconds);
        }

        private boolean advance(double seconds) {
            ageSeconds += seconds;
            xM += vxMps * seconds;
            yM += vyMps * seconds;
            return ageSeconds < lifetimeSeconds;
        }
    }

    static final class Flash {
        private final double xM;
        private final double yM;
        private final double radiusM;
        private final double lifetimeSeconds;
        private final FlashKind kind;
        private double ageSeconds;

        private Flash(
                double xM,
                double yM,
                double radiusM,
                double lifetimeSeconds,
                FlashKind kind) {
            this.xM = xM;
            this.yM = yM;
            this.radiusM = radiusM;
            this.lifetimeSeconds = lifetimeSeconds;
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        double xM() {
            return xM;
        }

        double yM() {
            return yM;
        }

        double radiusM() {
            return radiusM;
        }

        FlashKind kind() {
            return kind;
        }

        double remainingFraction() {
            return Math.max(0d, 1d - ageSeconds / lifetimeSeconds);
        }

        private boolean advance(double seconds) {
            ageSeconds += seconds;
            return ageSeconds < lifetimeSeconds;
        }
    }

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<Flash> flashes = new ArrayList<>();
    private final LinkedHashSet<Long> seenImpactEvents = new LinkedHashSet<>();
    private Map<Long, Boolean> previousWreckState = Map.of();
    private boolean wreckBaselineEstablished;

    void advance(TacticalPrototypeVisualSnapshot snapshot, double frameSeconds) {
        Objects.requireNonNull(snapshot, "snapshot");
        double seconds = sanitizeFrameSeconds(frameSeconds);
        particles.removeIf(particle -> !particle.advance(seconds));
        flashes.removeIf(flash -> !flash.advance(seconds));

        for (ImpactGlyph impact : snapshot.impacts()) {
            if (seenImpactEvents.add(impact.eventId())) {
                spawnImpact(impact);
            }
        }
        trimSeenEvents();
        observeWreckTransitions(snapshot.ships());
        enforceBudgets();
    }

    List<Particle> particles() {
        return List.copyOf(particles);
    }

    List<Flash> flashes() {
        return List.copyOf(flashes);
    }

    int seenImpactCount() {
        return seenImpactEvents.size();
    }

    private void observeWreckTransitions(List<ShipGlyph> ships) {
        HashMap<Long, Boolean> current = new HashMap<>();
        for (ShipGlyph ship : ships) {
            current.put(ship.entityId(), ship.wreck());
            if (wreckBaselineEstablished
                    && ship.wreck()
                    && Boolean.FALSE.equals(previousWreckState.get(ship.entityId()))) {
                spawnDestruction(ship);
            }
        }
        previousWreckState = Map.copyOf(current);
        wreckBaselineEstablished = true;
    }

    private void spawnImpact(ImpactGlyph impact) {
        double energyScale = clamp(Math.log10(1d + impact.energyJ()) / 9d, 0.20d, 1.40d);
        double radius = 1.8d + 5.5d * energyScale;
        FlashKind flashKind = switch (impact.kind()) {
            case SHIELD -> FlashKind.SHIELD;
            case ARMOR -> FlashKind.ARMOR;
            case PENETRATION -> FlashKind.PENETRATION;
        };
        flashes.add(new Flash(impact.xM(), impact.yM(), radius * 2.4d, 0.18d, flashKind));

        int count = switch (impact.kind()) {
            case SHIELD -> 5;
            case ARMOR -> 8;
            case PENETRATION -> 12;
        };
        ParticleKind kind = switch (impact.kind()) {
            case SHIELD -> ParticleKind.SHIELD_ARC;
            case ARMOR -> ParticleKind.SPARK;
            case PENETRATION -> ParticleKind.HOT_FRAGMENT;
        };
        long seed = mix64(impact.eventId() ^ ((long) impact.kind().ordinal() << 56));
        spawnRadial(
                seed,
                impact.xM(),
                impact.yM(),
                radius,
                count,
                kind,
                0.25d,
                impact.kind() == ImpactKind.PENETRATION ? 0.85d : 0.60d);
    }

    private void spawnDestruction(ShipGlyph ship) {
        double hullScale = Math.max(ship.widthM() * 0.45d, ship.lengthM() * 0.16d);
        double radius = Math.max(4d, hullScale);
        flashes.add(new Flash(
                ship.xM(),
                ship.yM(),
                radius * 2.2d,
                0.48d,
                FlashKind.DESTRUCTION));

        int fragments = (int) Math.round(clamp(18d + ship.lengthM() / 18d, 20d, 42d));
        long seed = mix64(ship.entityId() ^ 0x6A09E667F3BCC909L);
        spawnRadial(seed, ship.xM(), ship.yM(), radius, fragments,
                ParticleKind.HOT_FRAGMENT, 0.65d, 1.65d);
        spawnRadial(seed ^ 0xBB67AE8584CAA73BL, ship.xM(), ship.yM(), radius * 0.70d,
                Math.max(8, fragments / 2), ParticleKind.PLASMA, 0.35d, 0.95d);
    }

    private void spawnRadial(
            long seed,
            double xM,
            double yM,
            double sourceRadiusM,
            int count,
            ParticleKind kind,
            double minLifetime,
            double maxLifetime) {
        DeterministicRandom random = new DeterministicRandom(seed);
        for (int index = 0; index < count; index++) {
            double angle = random.nextUnit() * Math.PI * 2d;
            double speed = sourceRadiusM * (0.55d + random.nextUnit() * 2.15d);
            double radialOffset = sourceRadiusM * random.nextUnit() * 0.22d;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double lifetime = minLifetime + (maxLifetime - minLifetime) * random.nextUnit();
            double radius = sourceRadiusM * (0.035d + random.nextUnit() * 0.075d);
            particles.add(new Particle(
                    xM + cos * radialOffset,
                    yM + sin * radialOffset,
                    cos * speed,
                    sin * speed,
                    Math.max(0.35d, radius),
                    lifetime,
                    kind));
        }
    }

    private void enforceBudgets() {
        while (particles.size() > MAX_PARTICLES) {
            particles.remove(0);
        }
        while (flashes.size() > MAX_FLASHES) {
            flashes.remove(0);
        }
    }

    private void trimSeenEvents() {
        while (seenImpactEvents.size() > MAX_SEEN_IMPACTS) {
            Long oldest = seenImpactEvents.iterator().next();
            seenImpactEvents.remove(oldest);
        }
    }

    private static double sanitizeFrameSeconds(double frameSeconds) {
        if (!Double.isFinite(frameSeconds) || frameSeconds <= 0d) {
            return 0d;
        }
        return Math.min(MAX_FRAME_SECONDS, frameSeconds);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long mix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static final class DeterministicRandom {
        private long state;

        private DeterministicRandom(long seed) {
            state = seed;
        }

        private double nextUnit() {
            state += 0x9E3779B97F4A7C15L;
            long value = state;
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            value ^= value >>> 31;
            return (value >>> 11) * 0x1.0p-53;
        }
    }
}

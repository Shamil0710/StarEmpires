package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

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
    static final int MAX_WRECK_EFFECTS = 48;
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
        DESTRUCTION,
        SECONDARY_DETONATION
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

    static final class WreckEffect {
        private final long entityId;
        private final double radiusM;
        private final double lifetimeSeconds;
        private final int pulseCount;
        private double xM;
        private double yM;
        private double ageSeconds;
        private int nextPulseIndex;

        private WreckEffect(
                long entityId,
                double xM,
                double yM,
                double radiusM,
                double lifetimeSeconds,
                int pulseCount) {
            this.entityId = entityId;
            this.xM = xM;
            this.yM = yM;
            this.radiusM = radiusM;
            this.lifetimeSeconds = lifetimeSeconds;
            this.pulseCount = pulseCount;
        }

        long entityId() {
            return entityId;
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

        double remainingFraction() {
            return Math.max(0d, 1d - ageSeconds / lifetimeSeconds);
        }

        int pulseCount() {
            return pulseCount;
        }

        int emittedPulseCount() {
            return nextPulseIndex;
        }

        private double pulseTimeSeconds(int pulseIndex) {
            return lifetimeSeconds * (0.14d + (0.58d * (pulseIndex + 1d) / (pulseCount + 1d)));
        }
    }

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<Flash> flashes = new ArrayList<>();
    private final ArrayList<WreckEffect> wreckEffects = new ArrayList<>();
    private final List<Particle> particleView = Collections.unmodifiableList(particles);
    private final List<Flash> flashView = Collections.unmodifiableList(flashes);
    private final List<WreckEffect> wreckEffectView = Collections.unmodifiableList(wreckEffects);
    private final LinkedHashSet<Long> seenImpactEvents = new LinkedHashSet<>();
    private long[] previousShipIds = new long[0];
    private boolean[] previousShipWrecks = new boolean[0];
    private int previousShipCount;
    private boolean wreckBaselineEstablished;

    void advance(TacticalPrototypeVisualSnapshot snapshot, double frameSeconds) {
        Objects.requireNonNull(snapshot, "snapshot");
        double seconds = sanitizeFrameSeconds(frameSeconds);
        advanceParticles(seconds);
        advanceFlashes(seconds);
        advanceWreckEffects(snapshot.ships(), seconds);

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
        return particleView;
    }

    List<Flash> flashes() {
        return flashView;
    }

    List<WreckEffect> wreckEffects() {
        return wreckEffectView;
    }

    int seenImpactCount() {
        return seenImpactEvents.size();
    }

    private void advanceParticles(double seconds) {
        int writeIndex = 0;
        int originalSize = particles.size();
        for (int readIndex = 0; readIndex < originalSize; readIndex++) {
            Particle particle = particles.get(readIndex);
            if (!particle.advance(seconds)) {
                continue;
            }
            if (writeIndex != readIndex) {
                particles.set(writeIndex, particle);
            }
            writeIndex++;
        }
        if (writeIndex < originalSize) {
            particles.subList(writeIndex, originalSize).clear();
        }
    }

    private void advanceFlashes(double seconds) {
        int writeIndex = 0;
        int originalSize = flashes.size();
        for (int readIndex = 0; readIndex < originalSize; readIndex++) {
            Flash flash = flashes.get(readIndex);
            if (!flash.advance(seconds)) {
                continue;
            }
            if (writeIndex != readIndex) {
                flashes.set(writeIndex, flash);
            }
            writeIndex++;
        }
        if (writeIndex < originalSize) {
            flashes.subList(writeIndex, originalSize).clear();
        }
    }

    private void observeWreckTransitions(List<ShipGlyph> ships) {
        int previousIndex = 0;
        for (ShipGlyph ship : ships) {
            long entityId = ship.entityId();
            while (previousIndex < previousShipCount && previousShipIds[previousIndex] < entityId) {
                previousIndex++;
            }
            boolean existedPreviously = previousIndex < previousShipCount
                    && previousShipIds[previousIndex] == entityId;
            if (wreckBaselineEstablished
                    && ship.wreck()
                    && existedPreviously
                    && !previousShipWrecks[previousIndex]) {
                spawnDestruction(ship);
            }
        }

        ensurePreviousShipCapacity(ships.size());
        for (int index = 0; index < ships.size(); index++) {
            ShipGlyph ship = ships.get(index);
            previousShipIds[index] = ship.entityId();
            previousShipWrecks[index] = ship.wreck();
        }
        previousShipCount = ships.size();
        wreckBaselineEstablished = true;
    }

    private void advanceWreckEffects(List<ShipGlyph> ships, double seconds) {
        for (int index = wreckEffects.size() - 1; index >= 0; index--) {
            WreckEffect effect = wreckEffects.get(index);
            ShipGlyph ship = findShip(ships, effect.entityId);
            if (ship == null || !ship.wreck()) {
                wreckEffects.remove(index);
                continue;
            }
            effect.xM = ship.xM();
            effect.yM = ship.yM();
            effect.ageSeconds += seconds;
            while (effect.nextPulseIndex < effect.pulseCount
                    && effect.ageSeconds >= effect.pulseTimeSeconds(effect.nextPulseIndex)) {
                spawnSecondaryDetonation(effect, effect.nextPulseIndex);
                effect.nextPulseIndex++;
            }
            if (effect.ageSeconds >= effect.lifetimeSeconds) {
                wreckEffects.remove(index);
            }
        }
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
        double radius = destructionRadius(ship.lengthM(), ship.widthM());
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

        double duration = clamp(0.85d + ship.lengthM() / 260d, 1.0d, 2.6d);
        int pulseCount = (int) Math.round(clamp(1.5d + ship.lengthM() / 180d, 2d, 5d));
        wreckEffects.add(new WreckEffect(
                ship.entityId(),
                ship.xM(),
                ship.yM(),
                radius,
                duration,
                pulseCount));
    }

    private void spawnSecondaryDetonation(WreckEffect effect, int pulseIndex) {
        long seed = mix64(effect.entityId ^ (0xD1B54A32D192ED03L * (pulseIndex + 1L)));
        DeterministicRandom random = new DeterministicRandom(seed);
        double angle = random.nextUnit() * Math.PI * 2d;
        double offset = effect.radiusM * (0.15d + random.nextUnit() * 0.55d);
        double xM = effect.xM + Math.cos(angle) * offset;
        double yM = effect.yM + Math.sin(angle) * offset;
        double pulseScale = 0.44d + random.nextUnit() * 0.28d;
        double pulseRadius = effect.radiusM * pulseScale;

        flashes.add(new Flash(
                xM,
                yM,
                pulseRadius * 1.65d,
                0.24d,
                FlashKind.SECONDARY_DETONATION));
        int fragments = (int) Math.round(clamp(4d + pulseRadius / 8d, 5d, 12d));
        spawnRadial(seed ^ 0x94D049BB133111EBL, xM, yM, pulseRadius,
                fragments, ParticleKind.HOT_FRAGMENT, 0.35d, 0.90d);
        spawnRadial(seed ^ 0xBF58476D1CE4E5B9L, xM, yM, pulseRadius * 0.65d,
                Math.max(3, fragments / 2), ParticleKind.PLASMA, 0.20d, 0.55d);
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
        trimOldestToBudget(particles, MAX_PARTICLES);
        trimOldestToBudget(flashes, MAX_FLASHES);
        trimOldestToBudget(wreckEffects, MAX_WRECK_EFFECTS);
    }

    private void trimSeenEvents() {
        while (seenImpactEvents.size() > MAX_SEEN_IMPACTS) {
            Long oldest = seenImpactEvents.iterator().next();
            seenImpactEvents.remove(oldest);
        }
    }

    private void ensurePreviousShipCapacity(int required) {
        if (previousShipIds.length >= required) {
            return;
        }
        int capacity = Math.max(required, Math.max(8, previousShipIds.length * 2));
        previousShipIds = new long[capacity];
        previousShipWrecks = new boolean[capacity];
    }

    private static ShipGlyph findShip(List<ShipGlyph> ships, long entityId) {
        int low = 0;
        int high = ships.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            ShipGlyph ship = ships.get(middle);
            if (ship.entityId() < entityId) {
                low = middle + 1;
            } else if (ship.entityId() > entityId) {
                high = middle - 1;
            } else {
                return ship;
            }
        }
        return null;
    }

    private static <T> void trimOldestToBudget(ArrayList<T> values, int maximum) {
        int overflow = values.size() - maximum;
        if (overflow > 0) {
            values.subList(0, overflow).clear();
        }
    }

    private static double destructionRadius(double lengthM, double widthM) {
        return Math.max(4d, Math.max(widthM * 0.45d, lengthM * 0.16d));
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

package com.spacesim.world;

import java.util.Objects;

/**
 * Deterministic Stage-10B timing policy for one direct jump edge.
 *
 * <p>The strategic in-transit duration is derived from immutable topology coordinates with
 * {@link StrictMath}, then converted to authoritative fixed ticks. Approach/pending/arrival are
 * explicit structural barriers; the default one-tick values intentionally avoid inventing local
 * jump-gate geometry before physical jump anchors are modeled.</p>
 *
 * @param approachTicks local preparation barrier ticks
 * @param pendingTicks committed jump barrier ticks
 * @param arrivalTicks local arrival barrier ticks
 * @param strategicUnitsPerSecond inter-system strategic travel speed
 */
public record JumpTransitTiming(
        long approachTicks,
        long pendingTicks,
        long arrivalTicks,
        double strategicUnitsPerSecond) {
    /** Default minimal timing policy used by Stage 10B runtime. */
    public static final JumpTransitTiming DEFAULT = new JumpTransitTiming(1L, 1L, 1L, 20d);

    /** Validates positive finite timing parameters. */
    public JumpTransitTiming {
        if (approachTicks <= 0L || pendingTicks <= 0L || arrivalTicks <= 0L) {
            throw new IllegalArgumentException("Jump barrier durations должны быть положительными");
        }
        if (!Double.isFinite(strategicUnitsPerSecond) || strategicUnitsPerSecond <= 0d) {
            throw new IllegalArgumentException("Strategic jump speed должна быть положительной и конечной");
        }
    }

    /**
     * Calculates deterministic detached transit duration for one direct topology edge.
     *
     * @param topology authoritative galaxy topology
     * @param origin jump origin
     * @param destination jump destination
     * @param fixedStepSeconds authoritative fixed tick duration
     * @return at least one transit tick
     */
    public long transitTicks(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            float fixedStepSeconds) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        StarSystemNode from = checkedTopology.findSystem(
                Objects.requireNonNull(origin, "Jump origin не задан")).orElseThrow(
                () -> new IllegalArgumentException("Unknown jump origin: " + origin));
        StarSystemNode to = checkedTopology.findSystem(
                Objects.requireNonNull(destination, "Jump destination не задан")).orElseThrow(
                () -> new IllegalArgumentException("Unknown jump destination: " + destination));
        if (!checkedTopology.neighbors(from.id()).contains(to.id())) {
            throw new IllegalArgumentException("Jump systems are not directly connected: " + from.id() + " -> " + to.id());
        }
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
            throw new IllegalArgumentException("Fixed step должен быть положительным и конечным");
        }

        double distance = StrictMath.hypot(to.x() - from.x(), to.y() - from.y());
        double ticks = StrictMath.ceil(distance / strategicUnitsPerSecond / fixedStepSeconds);
        if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Jump duration не представима в long ticks");
        }
        return Math.max(1L, (long) ticks);
    }
}

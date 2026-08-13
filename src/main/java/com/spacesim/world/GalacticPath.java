package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic multi-hop path through galaxy jump connections.
 *
 * @param systems ordered systems including origin and destination
 * @param totalJumpTicks total authoritative ticks for all direct jump edges, including structural barriers
 * @param totalJumpSeconds total jump time represented by {@code totalJumpTicks}
 * @param strategicDistance sum of topology-coordinate distances of all jump edges
 */
public record GalacticPath(
        List<StarSystemId> systems,
        long totalJumpTicks,
        double totalJumpSeconds,
        double strategicDistance) {

    /**
     * Validates canonical path metrics.
     *
     * @param systems ordered systems including origin and destination
     * @param totalJumpTicks non-negative total authoritative ticks
     * @param totalJumpSeconds non-negative total jump time
     * @param strategicDistance non-negative strategic distance
     */
    public GalacticPath {
        Objects.requireNonNull(systems, "Galactic path systems не заданы");
        if (systems.isEmpty()) {
            throw new IllegalArgumentException("Galactic path не может быть пустым");
        }
        systems = List.copyOf(systems);
        for (StarSystemId systemId : systems) {
            Objects.requireNonNull(systemId, "Galactic path содержит null StarSystemId");
        }
        if (totalJumpTicks < 0L
                || !Double.isFinite(totalJumpSeconds)
                || totalJumpSeconds < 0d
                || !Double.isFinite(strategicDistance)
                || strategicDistance < 0d) {
            throw new IllegalArgumentException("Galactic path metrics некорректны");
        }
        if (systems.size() == 1 && (totalJumpTicks != 0L
                || Double.compare(totalJumpSeconds, 0d) != 0
                || Double.compare(strategicDistance, 0d) != 0)) {
            throw new IllegalArgumentException("Zero-hop GalacticPath должен иметь нулевые metrics");
        }
        if (systems.size() > 1 && totalJumpTicks <= 0L) {
            throw new IllegalArgumentException("Multi-hop GalacticPath должен иметь положительный jump time");
        }
    }

    /** @return origin system */
    public StarSystemId origin() {
        return systems.get(0);
    }

    /** @return destination system */
    public StarSystemId destination() {
        return systems.get(systems.size() - 1);
    }

    /** @return number of direct jump edges */
    public int jumpCount() {
        return systems.size() - 1;
    }
}

package com.spacesim.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic neighbor-only route planner with an injected Stage-17 legal-access boundary. */
public final class FleetStrategicRoutePlanner {
    private final GalaxyTopology topology;

    public FleetStrategicRoutePlanner(GalaxyTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
    }

    public Optional<Route> plan(
            int factionId,
            StarSystemId origin,
            StarSystemId destination,
            long tick,
            TransitAccessPolicy accessPolicy) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        if (topology.findSystem(origin).isEmpty() || topology.findSystem(destination).isEmpty()) {
            return Optional.empty();
        }
        if (origin.equals(destination)) {
            return Optional.of(new Route(List.of(origin)));
        }
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, origin);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : topology.neighbors(current)) {
                if (previous.containsKey(neighbor)) {
                    continue;
                }
                boolean isDestination = neighbor.equals(destination);
                if (!accessPolicy.canEnter(factionId, current, neighbor, tick, isDestination)) {
                    continue;
                }
                previous.put(neighbor, current);
                if (isDestination) {
                    return Optional.of(reconstruct(previous, origin, destination));
                }
                queue.addLast(neighbor);
            }
        }
        return Optional.empty();
    }

    private static Route reconstruct(
            Map<StarSystemId, StarSystemId> previous,
            StarSystemId origin,
            StarSystemId destination) {
        ArrayList<StarSystemId> reversed = new ArrayList<>();
        StarSystemId cursor = destination;
        while (!cursor.equals(origin)) {
            reversed.add(cursor);
            cursor = previous.get(cursor);
            if (cursor == null) {
                throw new IllegalStateException("route predecessor chain is incomplete");
            }
        }
        reversed.add(origin);
        java.util.Collections.reverse(reversed);
        return new Route(reversed);
    }

    /** Implementations must delegate law to existing Stage-17 ownership/treaty/war authority. */
    @FunctionalInterface
    public interface TransitAccessPolicy {
        boolean canEnter(
                int factionId,
                StarSystemId from,
                StarSystemId to,
                long tick,
                boolean destination);
    }

    /** A route always contains origin and destination and advances only through neighboring hops. */
    public record Route(List<StarSystemId> systems) {
        public Route {
            Objects.requireNonNull(systems, "systems");
            if (systems.isEmpty()) {
                throw new IllegalArgumentException("route cannot be empty");
            }
            systems = List.copyOf(systems);
        }

        public StarSystemId origin() { return systems.get(0); }
        public StarSystemId destination() { return systems.get(systems.size() - 1); }
        public int hopCount() { return systems.size() - 1; }
    }
}

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

    /**
     * Creates a deterministic planner over the existing galaxy topology.
     *
     * @param topology authoritative neighbor graph used for all strategic hops
     */
    public FleetStrategicRoutePlanner(GalaxyTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
    }

    /**
     * Finds the deterministic shortest lawful neighbor-only route between two systems.
     *
     * <p>Legal access is delegated to the injected policy, which must in turn use existing Stage-17
     * ownership/treaty/war authority. The planner owns no access law and never synthesizes jumps.</p>
     *
     * @param factionId faction requesting transit
     * @param origin starting system
     * @param destination target system
     * @param tick simulation tick used for access evaluation
     * @param accessPolicy legal transit policy backed by existing authority
     * @return lawful route when reachable, otherwise empty
     */
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
        /**
         * Evaluates whether a faction may enter one adjacent system from another.
         *
         * @param factionId faction requesting transit
         * @param from current route node
         * @param to adjacent candidate route node
         * @param tick simulation tick used by the legal authority
         * @param destination whether {@code to} is the requested final destination
         * @return {@code true} only when existing political/legal authority allows entry
         */
        boolean canEnter(
                int factionId,
                StarSystemId from,
                StarSystemId to,
                long tick,
                boolean destination);
    }

    /**
     * Immutable strategic route containing at least its origin and advancing only through neighboring hops.
     *
     * @param systems ordered route nodes from origin through destination
     */
    public record Route(List<StarSystemId> systems) {
        /**
         * Freezes and validates the route node sequence.
         *
         * @param systems non-empty ordered route nodes
         */
        public Route {
            Objects.requireNonNull(systems, "systems");
            if (systems.isEmpty()) {
                throw new IllegalArgumentException("route cannot be empty");
            }
            systems = List.copyOf(systems);
        }

        /**
         * Returns the first route node.
         *
         * @return route origin
         */
        public StarSystemId origin() { return systems.get(0); }

        /**
         * Returns the final route node.
         *
         * @return route destination
         */
        public StarSystemId destination() { return systems.get(systems.size() - 1); }

        /**
         * Returns the number of neighbor transitions in the route.
         *
         * @return non-negative hop count
         */
        public int hopCount() { return systems.size() - 1; }
    }
}

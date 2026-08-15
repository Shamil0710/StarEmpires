package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/** Pure deterministic shortest-time planner over immutable jump topology. */
public final class GalacticPathPlanner {
    private final GalaxyTopology topology;
    private final JumpTransitTiming timing;
    private final float fixedStepSeconds;

    /**
     * @param topology immutable galaxy topology
     * @param timing authoritative direct-jump timing policy
     * @param fixedStepSeconds authoritative fixed tick duration
     */
    public GalacticPathPlanner(GalaxyTopology topology, JumpTransitTiming timing, float fixedStepSeconds) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        this.timing = Objects.requireNonNull(timing, "JumpTransitTiming не задан");
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
            throw new IllegalArgumentException("Fixed step должен быть положительным и конечным");
        }
        this.fixedStepSeconds = fixedStepSeconds;
    }

    /**
     * Finds the minimum authoritative jump-time path. Equal-time paths prefer fewer edges and then
     * lexicographically smaller StarSystemId sequences.
     *
     * @param origin known origin system
     * @param destination known destination system
     * @return canonical path or empty when disconnected
     */
    public Optional<GalacticPath> findPath(StarSystemId origin, StarSystemId destination) {
        StarSystemId from = requireKnown(origin, "Path origin");
        StarSystemId to = requireKnown(destination, "Path destination");
        return findPath(from, to, Set.of());
    }

    /**
     * Finds the minimum-time route that shares no jump connection with the supplied primary path.
     *
     * <p>The primary route itself is never mutated and topology is not rewritten. Every consecutive
     * primary-system pair becomes a canonical excluded {@link JumpConnection}; the ordinary
     * deterministic shortest-path search then runs against the remaining physical topology. A result
     * therefore represents actual link redundancy rather than merely a different presentation of the
     * same chokepoint. If the topology has no such route, the vulnerability remains explicit.</p>
     *
     * @param primary existing physical primary route whose jump connections must all be avoided
     * @return best edge-disjoint physical route, or empty when none exists
     */
    public Optional<GalacticPath> findEdgeDisjointAlternative(GalacticPath primary) {
        GalacticPath checked = Objects.requireNonNull(primary, "Primary galactic path not set");
        StarSystemId from = requireKnown(checked.origin(), "Primary path origin");
        StarSystemId to = requireKnown(checked.destination(), "Primary path destination");
        if (checked.jumpCount() <= 0) {
            return Optional.empty();
        }
        Set<JumpConnection> excluded = new HashSet<>();
        List<StarSystemId> systems = checked.systems();
        for (int index = 1; index < systems.size(); index++) {
            StarSystemId previous = requireKnown(systems.get(index - 1), "Primary path system");
            StarSystemId current = requireKnown(systems.get(index), "Primary path system");
            JumpConnection connection = new JumpConnection(previous, current);
            if (!topology.connections().contains(connection)) {
                throw new IllegalArgumentException(
                        "Primary path uses a jump connection absent from topology: " + connection);
            }
            excluded.add(connection);
        }
        return findPath(from, to, Set.copyOf(excluded));
    }

    /**
     * @param origin directly connected origin
     * @param destination directly connected destination
     * @return approach + pending + detached transit + arrival ticks
     */
    public long directEdgeTicks(StarSystemId origin, StarSystemId destination) {
        long ticks = timing.transitTicks(topology, origin, destination, fixedStepSeconds);
        ticks = safeAdd(ticks, timing.approachTicks());
        ticks = safeAdd(ticks, timing.pendingTicks());
        return safeAdd(ticks, timing.arrivalTicks());
    }

    private Optional<GalacticPath> findPath(
            StarSystemId from,
            StarSystemId to,
            Set<JumpConnection> excludedConnections) {
        if (from.equals(to)) {
            return Optional.of(new GalacticPath(List.of(from), 0L, 0d, 0d));
        }
        Set<JumpConnection> excluded = Objects.requireNonNull(
                excludedConnections, "Excluded jump connections not set");
        PriorityQueue<State> frontier = new PriorityQueue<>(ORDER);
        Map<StarSystemId, State> best = new HashMap<>();
        State start = new State(from, 0L, 0d, List.of(from));
        frontier.add(start);
        best.put(from, start);
        while (!frontier.isEmpty()) {
            State current = frontier.remove();
            if (ORDER.compare(current, best.get(current.systemId())) != 0) {
                continue;
            }
            if (current.systemId().equals(to)) {
                double seconds = current.ticks() * (double) fixedStepSeconds;
                if (!Double.isFinite(seconds)) {
                    throw new IllegalStateException("Galactic path time overflow");
                }
                return Optional.of(new GalacticPath(
                        current.path(), current.ticks(), seconds, current.distance()));
            }
            for (StarSystemId neighbor : topology.neighbors(current.systemId())) {
                if (excluded.contains(new JumpConnection(current.systemId(), neighbor))) {
                    continue;
                }
                State candidate = extend(current, neighbor);
                State previous = best.get(neighbor);
                if (previous == null || ORDER.compare(candidate, previous) < 0) {
                    best.put(neighbor, candidate);
                    frontier.add(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private State extend(State current, StarSystemId next) {
        long ticks = safeAdd(current.ticks(), directEdgeTicks(current.systemId(), next));
        StarSystemNode first = topology.findSystem(current.systemId()).orElseThrow();
        StarSystemNode second = topology.findSystem(next).orElseThrow();
        double distance = current.distance() + StrictMath.hypot(second.x() - first.x(), second.y() - first.y());
        if (!Double.isFinite(distance)) {
            throw new IllegalStateException("Galactic path distance overflow");
        }
        List<StarSystemId> path = new ArrayList<>(current.path());
        path.add(next);
        return new State(next, ticks, distance, List.copyOf(path));
    }

    private StarSystemId requireKnown(StarSystemId id, String label) {
        StarSystemId value = Objects.requireNonNull(id, label + " не задан");
        if (topology.findSystem(value).isEmpty()) {
            throw new IllegalArgumentException(label + " отсутствует в topology: " + value);
        }
        return value;
    }

    private static long safeAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Galactic path tick overflow", exception);
        }
    }

    private static int comparePaths(List<StarSystemId> first, List<StarSystemId> second) {
        int common = Math.min(first.size(), second.size());
        for (int index = 0; index < common; index++) {
            int value = first.get(index).compareTo(second.get(index));
            if (value != 0) {
                return value;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    private static final Comparator<State> ORDER = Comparator.comparingLong(State::ticks)
            .thenComparingInt(state -> state.path().size())
            .thenComparing(State::path, GalacticPathPlanner::comparePaths);

    private record State(StarSystemId systemId, long ticks, double distance, List<StarSystemId> path) {
    }
}

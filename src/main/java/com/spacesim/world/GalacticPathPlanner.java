package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

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
        if (from.equals(to)) {
            return Optional.of(new GalacticPath(List.of(from), 0L, 0d, 0d));
        }

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

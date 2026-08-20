package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Pure deterministic Stage-20D route planner over explicit ordinary {@link JumpConnection} edges.
 *
 * <p>The planner consumes an already-authoritative fitted one-edge {@link JumpPlan}. It therefore
 * does not duplicate translated-mass, power, energy, heat or cooldown physics. The current fitted
 * edge-transit time is used uniformly when no Stage-20D edge catalog is supplied because Stage-20A
 * intentionally does not yet author a generated per-edge transit distribution. When an exact-coverage
 * {@link Stage20JumpEdgeCatalog} is supplied, the same planner also respects world-global physical
 * edge closure and explicit edge transit parameters without changing graph adjacency.</p>
 *
 * <p>Path cost follows the accepted Stage-20A cadence semantics:</p>
 * <pre>
 * arrival(h hops)
 * = h * spool
 * + sum(edge transit)
 * + (h - 1) * cooldown
 * </pre>
 *
 * <p>Gross route energy/heat are planning consequences only. Every actual hop remains a separate
 * world action and must be revalidated after arrival.</p>
 */
public final class Stage20PhysicalGalacticRoutePlanner {
    private final GalaxyTopology topology;
    private final JumpPlan fittedPlan;
    private final EdgeTransitProvider transitProvider;
    private final EdgeAvailabilityProvider availabilityProvider;

    /**
     * Creates a planner using the fitted plan's current ordinary-edge transit time for every edge.
     *
     * @param topology authoritative ordinary jump graph
     * @param fittedPlan currently executable fitted one-edge jump plan
     */
    public Stage20PhysicalGalacticRoutePlanner(GalaxyTopology topology, JumpPlan fittedPlan) {
        this(topology, fittedPlan, uniformTransit(requireAllowed(fittedPlan).edgeTransitSeconds()), edge -> true);
    }

    /**
     * Creates a planner with an explicit physical transit provider for future edge metadata.
     *
     * @param topology authoritative ordinary jump graph
     * @param fittedPlan currently executable fitted one-edge jump plan
     * @param transitProvider physical transit seconds for each explicit edge
     */
    public Stage20PhysicalGalacticRoutePlanner(
            GalaxyTopology topology,
            JumpPlan fittedPlan,
            EdgeTransitProvider transitProvider) {
        this(topology, fittedPlan, transitProvider, edge -> true);
    }

    /**
     * Creates a planner whose physical traversal state and transit parameters come from the
     * exact-coverage Stage-20D edge catalog.
     *
     * @param topology authoritative ordinary jump graph
     * @param fittedPlan currently executable fitted one-edge jump plan
     * @param edgeCatalog current Stage-20D physical edge metadata
     */
    public Stage20PhysicalGalacticRoutePlanner(
            GalaxyTopology topology,
            JumpPlan fittedPlan,
            Stage20JumpEdgeCatalog edgeCatalog) {
        this(
                topology,
                fittedPlan,
                edge -> Objects.requireNonNull(edgeCatalog, "edgeCatalog").transitSeconds(edge, fittedPlan),
                edge -> Objects.requireNonNull(edgeCatalog, "edgeCatalog").isPhysicallyTraversable(edge));
        if (!this.topology.equals(edgeCatalog.topology())) {
            throw new IllegalArgumentException("edgeCatalog must cover the same authoritative topology");
        }
    }

    private Stage20PhysicalGalacticRoutePlanner(
            GalaxyTopology topology,
            JumpPlan fittedPlan,
            EdgeTransitProvider transitProvider,
            EdgeAvailabilityProvider availabilityProvider) {
        this.topology = Objects.requireNonNull(topology, "topology");
        this.fittedPlan = requireAllowed(fittedPlan);
        this.transitProvider = Objects.requireNonNull(transitProvider, "transitProvider");
        this.availabilityProvider = Objects.requireNonNull(availabilityProvider, "availabilityProvider");
    }

    /**
     * Finds the minimum physical fitted-jump arrival-time route.
     *
     * <p>Equal-time routes prefer fewer hops and then lexicographically smaller stable system-ID
     * sequences. No candidate may traverse a non-neighbor edge.</p>
     *
     * @param origin known origin system
     * @param destination known route destination
     * @return physical route estimate, or empty when graph-disconnected
     */
    public Optional<Stage20PhysicalGalacticRoute> findPath(
            StarSystemId origin,
            StarSystemId destination) {
        StarSystemId from = requireKnown(origin, "origin");
        StarSystemId to = requireKnown(destination, "destination");
        if (from.equals(to)) {
            return Optional.of(new Stage20PhysicalGalacticRoute(
                    List.of(from),
                    List.of(),
                    0d,
                    0d,
                    0d,
                    0d,
                    fittedPlan.translatedMassKg(),
                    false));
        }

        PriorityQueue<State> frontier = new PriorityQueue<>(STATE_ORDER);
        Map<StarSystemId, State> best = new HashMap<>();
        State start = new State(from, 0d, List.of(from), List.of());
        frontier.add(start);
        best.put(from, start);

        while (!frontier.isEmpty()) {
            State current = frontier.remove();
            State accepted = best.get(current.systemId());
            if (accepted == null || STATE_ORDER.compare(current, accepted) != 0) {
                continue;
            }
            if (current.systemId().equals(to)) {
                return Optional.of(route(current));
            }
            for (StarSystemId neighbor : topology.neighbors(current.systemId())) {
                JumpConnection edge = new JumpConnection(current.systemId(), neighbor);
                if (!availabilityProvider.isTraversable(edge)) {
                    continue;
                }
                double transit = checkedTransit(edge);
                double cooldownBefore = current.path().size() == 1 ? 0d : fittedPlan.cooldownSeconds();
                double arrival = safeAdd(current.arrivalSeconds(), cooldownBefore);
                arrival = safeAdd(arrival, fittedPlan.spoolSeconds());
                arrival = safeAdd(arrival, transit);

                ArrayList<StarSystemId> path = new ArrayList<>(current.path());
                path.add(neighbor);
                ArrayList<Stage20PhysicalGalacticRoute.EdgeEstimate> edges = new ArrayList<>(current.edges());
                edges.add(new Stage20PhysicalGalacticRoute.EdgeEstimate(
                        edge, transit, cooldownBefore, arrival));
                State candidate = new State(neighbor, arrival, List.copyOf(path), List.copyOf(edges));
                State previous = best.get(neighbor);
                if (previous == null || STATE_ORDER.compare(candidate, previous) < 0) {
                    best.put(neighbor, candidate);
                    frontier.add(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private Stage20PhysicalGalacticRoute route(State state) {
        int hops = state.edges().size();
        double readyAgain = safeAdd(state.arrivalSeconds(), fittedPlan.cooldownSeconds());
        double grossEnergy = safeMultiply(fittedPlan.requiredEnergyJ(), hops, "gross jump energy");
        double grossHeat = safeMultiply(fittedPlan.jumpHeatJ(), hops, "cumulative jump heat");
        return new Stage20PhysicalGalacticRoute(
                state.path(),
                state.edges(),
                state.arrivalSeconds(),
                readyAgain,
                grossEnergy,
                grossHeat,
                fittedPlan.translatedMassKg(),
                true);
    }

    private double checkedTransit(JumpConnection edge) {
        if (!topology.connections().contains(edge)) {
            throw new IllegalArgumentException("transit provider requested for non-topology edge: " + edge);
        }
        double seconds = transitProvider.transitSeconds(edge);
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            throw new IllegalArgumentException("edge transit must be positive and finite for " + edge);
        }
        return seconds;
    }

    private StarSystemId requireKnown(StarSystemId id, String field) {
        StarSystemId checked = Objects.requireNonNull(id, field);
        if (topology.findSystem(checked).isEmpty()) {
            throw new IllegalArgumentException(field + " is absent from topology: " + checked);
        }
        return checked;
    }

    private static JumpPlan requireAllowed(JumpPlan plan) {
        JumpPlan checked = Objects.requireNonNull(plan, "fittedPlan");
        if (!checked.allowed()) {
            throw new IllegalArgumentException("physical route planning requires executable fitted jump plan: "
                    + checked.failure());
        }
        if (!(checked.spoolSeconds() > 0d)
                || !(checked.edgeTransitSeconds() > 0d)
                || checked.cooldownSeconds() < 0d
                || !(checked.requiredEnergyJ() > 0d)
                || checked.jumpHeatJ() < 0d) {
            throw new IllegalArgumentException("fitted jump plan lacks positive physical route capability");
        }
        return checked;
    }

    private static EdgeTransitProvider uniformTransit(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            throw new IllegalArgumentException("uniform edge transit must be positive and finite");
        }
        return edge -> seconds;
    }

    private static double safeAdd(double first, double second) {
        double result = first + second;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("physical route time overflow");
        }
        return result;
    }

    private static double safeMultiply(double value, int multiplier, String field) {
        double result = value * multiplier;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException(field + " overflow");
        }
        return result;
    }

    private static int comparePaths(List<StarSystemId> first, List<StarSystemId> second) {
        int common = Math.min(first.size(), second.size());
        for (int index = 0; index < common; index++) {
            int comparison = first.get(index).compareTo(second.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    private static final Comparator<State> STATE_ORDER = Comparator.comparingDouble(State::arrivalSeconds)
            .thenComparingInt(value -> value.path().size())
            .thenComparing(State::path, Stage20PhysicalGalacticRoutePlanner::comparePaths);

    /** Physical world-global availability source keyed only by an explicit canonical edge. */
    @FunctionalInterface
    private interface EdgeAvailabilityProvider {
        boolean isTraversable(JumpConnection connection);
    }

    /** Physical edge-transit source keyed only by an explicit canonical production edge. */
    @FunctionalInterface
    public interface EdgeTransitProvider {
        /**
         * Returns physical transit duration for one explicit topology edge.
         *
         * @param connection explicit topology edge
         * @return positive finite physical transit seconds
         */
        double transitSeconds(JumpConnection connection);
    }

    private record State(
            StarSystemId systemId,
            double arrivalSeconds,
            List<StarSystemId> path,
            List<Stage20PhysicalGalacticRoute.EdgeEstimate> edges) {
    }
}

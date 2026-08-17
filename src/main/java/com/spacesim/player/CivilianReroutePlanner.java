package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only Stage-19D civilian reroute decision layer over the existing cumulative route-risk planner.
 *
 * <p>The class deliberately does not own topology, movement, jump execution, cargo, storage or
 * logistics. {@link PlayerFleetRoutePlanner} remains the single route authority and therefore only
 * discovered, physically connected topology can appear in a selected route. This layer merely
 * compares that current actor-known optimum with a previously committed transient path so callers
 * can distinguish route continuation from a real reroute.</p>
 *
 * <p>An unavailable route produces {@link Action#HOLD}; the planner never fabricates an emergency
 * edge or hidden safe corridor. Because route risk is supplied by the existing player knowledge
 * model, hidden remote combat state cannot influence this decision.</p>
 */
public final class CivilianReroutePlanner {
    /** Strategic route action derived without mutating authoritative simulation state. */
    public enum Action {
        /** The previously committed physical path is still the selected path. */
        CONTINUE,
        /** Actor-known risk/topology now selects a different real path. */
        REROUTE,
        /** No discovered physical route is currently available. */
        HOLD
    }

    /**
     * Immutable reroute decision.
     *
     * @param action route action
     * @param committedPath previously selected transient path, possibly empty before first planning
     * @param selectedRoute currently selected physical route, empty only for {@link Action#HOLD}
     */
    public record Decision(
            Action action,
            List<StarSystemId> committedPath,
            Optional<PlayerRouteRiskView> selectedRoute) {
        /**
         * Validates immutable route decision state.
         *
         * @param action route action
         * @param committedPath prior transient path
         * @param selectedRoute selected route when one exists
         */
        public Decision {
            Objects.requireNonNull(action, "action");
            committedPath = List.copyOf(Objects.requireNonNull(committedPath, "committedPath"));
            selectedRoute = Objects.requireNonNull(selectedRoute, "selectedRoute");
            if ((action == Action.HOLD) == selectedRoute.isPresent()) {
                throw new IllegalArgumentException("HOLD must be the only decision without a selected route");
            }
        }

        /** @return selected real path, or an empty list when the actor must hold */
        public List<StarSystemId> selectedPath() {
            return selectedRoute.map(PlayerRouteRiskView::path).orElse(List.of());
        }
    }

    private final PlayerFleetRoutePlanner routePlanner;

    /**
     * Creates the Stage-19D planner over the production route-risk authority.
     *
     * @param runtime playable runtime whose topology and actor-known intelligence are consulted
     */
    public CivilianReroutePlanner(PlayerRuntime runtime) {
        this(new PlayerFleetRoutePlanner(Objects.requireNonNull(runtime, "runtime")));
    }

    /**
     * Creates a planner with an explicit route dependency for deterministic composition.
     *
     * @param routePlanner existing Stage-15 route authority
     */
    public CivilianReroutePlanner(PlayerFleetRoutePlanner routePlanner) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
    }

    /**
     * Re-evaluates one civilian route using current actor-visible strategic knowledge.
     *
     * <p>The committed path is transient execution context only; it is never treated as topology
     * truth. An empty committed path represents first planning and therefore returns
     * {@link Action#REROUTE} when a physical route exists. Repeated calls over unchanged state and
     * the selected path return {@link Action#CONTINUE} deterministically.</p>
     *
     * @param fleetId physical fleet whose current vulnerability affects route risk
     * @param origin current discovered system
     * @param destination discovered destination system
     * @param committedPath previously selected route, or empty before first planning
     * @return immutable strategic route decision
     */
    public Decision plan(
            FleetId fleetId,
            StarSystemId origin,
            StarSystemId destination,
            List<StarSystemId> committedPath) {
        FleetId actor = Objects.requireNonNull(fleetId, "fleetId");
        StarSystemId from = Objects.requireNonNull(origin, "origin");
        StarSystemId to = Objects.requireNonNull(destination, "destination");
        List<StarSystemId> previous = List.copyOf(Objects.requireNonNull(committedPath, "committedPath"));

        Optional<PlayerRouteRiskView> selected = routePlanner.plan(actor, from, to);
        if (selected.isEmpty()) {
            return new Decision(Action.HOLD, previous, Optional.empty());
        }
        Action action = selected.orElseThrow().path().equals(previous) ? Action.CONTINUE : Action.REROUTE;
        return new Decision(action, previous, selected);
    }
}

package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;

import java.util.Objects;
import java.util.Optional;

/**
 * Stage-20D ordinary-route execution seam that deliberately exposes only the immediate next hop.
 *
 * <p>Every invocation replans from the current system against current physical edge state and the
 * current fitted jump plan. Callers must invoke it again after arrival, so access, engineering,
 * damage or topology-state changes are naturally revalidated between hops.</p>
 */
public final class Stage20JumpRouteExecutionPlanner {
    private Stage20JumpRouteExecutionPlanner() {
        throw new AssertionError("Stage20JumpRouteExecutionPlanner has no instances");
    }

    /**
     * Plans exactly one immediate neighbor hop toward a final ordinary-route destination.
     *
     * @param edgeCatalog current exact-coverage physical edge catalog
     * @param fittedPlan current executable fitted jump plan
     * @param currentSystem current fleet system
     * @param routeDestination final requested ordinary-route destination
     * @return empty when already at destination or no currently open physical route exists
     */
    public static Optional<Stage20NextJumpExecutionPlan> planNextHop(
            Stage20JumpEdgeCatalog edgeCatalog,
            JumpPlan fittedPlan,
            StarSystemId currentSystem,
            StarSystemId routeDestination) {
        Stage20JumpEdgeCatalog catalog = Objects.requireNonNull(edgeCatalog, "edgeCatalog");
        StarSystemId current = Objects.requireNonNull(currentSystem, "currentSystem");
        StarSystemId destination = Objects.requireNonNull(routeDestination, "routeDestination");
        if (catalog.topology().system(current).isEmpty() || catalog.topology().system(destination).isEmpty()) {
            throw new IllegalArgumentException("current/destination must exist in Stage20 topology");
        }
        if (current.equals(destination)) {
            return Optional.empty();
        }

        Stage20PhysicalGalacticRoutePlanner planner = new Stage20PhysicalGalacticRoutePlanner(
                catalog,
                Objects.requireNonNull(fittedPlan, "fittedPlan"));
        Optional<Stage20PhysicalGalacticRoute> route = planner.findRoute(current, destination);
        if (route.isEmpty()) {
            return Optional.empty();
        }
        Stage20PhysicalGalacticRoute resolved = route.orElseThrow();
        if (resolved.systems().size() < 2 || resolved.edges().isEmpty()) {
            throw new IllegalStateException("non-zero Stage20 route must expose one immediate explicit edge");
        }
        StarSystemId immediate = resolved.systems().get(1);
        JumpConnection connection = resolved.edges().get(0).connection();
        if (!connection.other(current).equals(immediate)) {
            throw new IllegalStateException("Stage20 first route edge does not lead to immediate neighbor");
        }
        Stage20JumpEdgeState edge = catalog.require(connection);
        return Optional.of(new Stage20NextJumpExecutionPlan(
                destination,
                immediate,
                edge.edgeId(),
                connection,
                catalog.arrivalIn(connection, immediate),
                resolved));
    }
}

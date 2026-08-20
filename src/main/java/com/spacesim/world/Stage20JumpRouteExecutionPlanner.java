package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;

import java.util.Objects;
import java.util.Optional;

/**
 * Stage-20D hop-by-hop ordinary-route execution planner.
 *
 * <p>Every invocation replans from the fleet's <em>current</em> system using the current fitted
 * {@link JumpPlan} and current physical edge catalog. The returned command candidate contains only
 * the first direct-neighbor edge. Calling this method again after arrival naturally revalidates
 * physical edge availability, fitted capability and route choice; no multi-hop teleport reservation
 * exists.</p>
 */
public final class Stage20JumpRouteExecutionPlanner {
    private Stage20JumpRouteExecutionPlanner() {
        throw new AssertionError("No instances");
    }

    /**
     * Plans exactly one immediate neighbor hop toward a final destination.
     *
     * @param catalog current exact-coverage physical edge catalog
     * @param fittedPlan current executable fitted one-edge jump plan
     * @param currentSystem fleet's authoritative current system
     * @param routeDestination desired final route destination
     * @return next-hop plan, empty when already at destination or no open physical route exists
     */
    public static Optional<Stage20NextJumpExecutionPlan> planNextHop(
            Stage20JumpEdgeCatalog catalog,
            JumpPlan fittedPlan,
            StarSystemId currentSystem,
            StarSystemId routeDestination) {
        Stage20JumpEdgeCatalog checkedCatalog = Objects.requireNonNull(catalog, "catalog");
        StarSystemId current = Objects.requireNonNull(currentSystem, "currentSystem");
        StarSystemId destination = Objects.requireNonNull(routeDestination, "routeDestination");
        if (checkedCatalog.topology().findSystem(current).isEmpty()
                || checkedCatalog.topology().findSystem(destination).isEmpty()) {
            throw new IllegalArgumentException("current/destination system must exist in authoritative topology");
        }
        if (current.equals(destination)) {
            return Optional.empty();
        }
        Optional<Stage20PhysicalGalacticRoute> route = new Stage20PhysicalGalacticRoutePlanner(
                checkedCatalog.topology(),
                Objects.requireNonNull(fittedPlan, "fittedPlan"),
                checkedCatalog)
                .findPath(current, destination);
        if (route.isEmpty() || route.orElseThrow().jumpCount() == 0) {
            return Optional.empty();
        }
        Stage20PhysicalGalacticRoute resolved = route.orElseThrow();
        StarSystemId immediate = resolved.systems().get(1);
        JumpConnection connection = resolved.edges().get(0).connection();
        Stage20JumpEdgeState edge = checkedCatalog.require(connection);
        return Optional.of(new Stage20NextJumpExecutionPlan(
                destination,
                immediate,
                edge.edgeId(),
                connection,
                checkedCatalog.arrivalIn(connection, immediate),
                resolved));
    }
}

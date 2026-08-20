package com.spacesim.world;

import com.spacesim.world.Stage20JumpEdgeState.ArrivalEndpoint;

import java.util.Objects;

/**
 * Read-only Stage-20D plan for exactly one immediate ordinary neighbor hop toward a farther route
 * destination.
 *
 * <p>This type deliberately carries {@link LocalPhysicalPosition} arrival geometry instead of the
 * legacy float coordinate pair accepted by the current Stage-10 jump FSM. Creating this object does
 * not mutate fleet state or spend engineering resources. A future runtime bridge must preserve this
 * physical geometry exactly or reject the transition; silent precision loss is forbidden.</p>
 *
 * @param routeDestination final requested route destination
 * @param immediateDestination direct neighboring system for the next authoritative jump
 * @param edgeId stable Stage-20D edge identity
 * @param connection exact ordinary topology edge to execute
 * @param arrivalEndpoint destination-local physical arrival geometry
 * @param currentRoute freshly replanned physical route from current system to route destination
 */
public record Stage20NextJumpExecutionPlan(
        StarSystemId routeDestination,
        StarSystemId immediateDestination,
        String edgeId,
        JumpConnection connection,
        ArrivalEndpoint arrivalEndpoint,
        Stage20PhysicalGalacticRoute currentRoute) {
    /**
     * Validates one immediate-hop execution plan.
     *
     * @param routeDestination final requested route destination
     * @param immediateDestination direct neighboring system for the next authoritative jump
     * @param edgeId stable Stage-20D edge identity
     * @param connection exact ordinary topology edge to execute
     * @param arrivalEndpoint destination-local physical arrival geometry
     * @param currentRoute freshly replanned physical route from current system to route destination
     */
    public Stage20NextJumpExecutionPlan {
        Objects.requireNonNull(routeDestination, "routeDestination");
        Objects.requireNonNull(immediateDestination, "immediateDestination");
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId must not be blank");
        }
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(arrivalEndpoint, "arrivalEndpoint");
        Objects.requireNonNull(currentRoute, "currentRoute");
        if (currentRoute.jumpCount() <= 0) {
            throw new IllegalArgumentException("next-hop plan requires a non-zero-hop route");
        }
        if (!routeDestination.equals(currentRoute.destination())) {
            throw new IllegalArgumentException("routeDestination must match current route destination");
        }
        if (!immediateDestination.equals(currentRoute.systems().get(1))) {
            throw new IllegalArgumentException("immediateDestination must be the first neighbor in current route");
        }
        if (!connection.equals(currentRoute.edges().get(0).connection())) {
            throw new IllegalArgumentException("connection must equal the first current-route edge");
        }
        if (!immediateDestination.equals(arrivalEndpoint.systemId())) {
            throw new IllegalArgumentException("arrival endpoint must belong to immediate destination");
        }
        if (!Stage20JumpEdgeState.ordinaryEdgeId(connection).equals(edgeId)) {
            throw new IllegalArgumentException("edgeId must match immediate connection");
        }
    }
}

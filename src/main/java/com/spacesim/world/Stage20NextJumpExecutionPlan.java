package com.spacesim.world;

import com.spacesim.world.Stage20JumpEdgeState.ArrivalEndpoint;

import java.util.Objects;

/**
 * One direct-neighbor execution step selected from a freshly planned Stage-20D physical route.
 *
 * @param routeDestination final requested route destination
 * @param immediateDestination direct ordinary neighbor to jump to now
 * @param edgeId stable physical edge identity
 * @param connection exact ordinary topology edge to execute
 * @param arrivalEndpoint destination-local authoritative arrival geometry
 * @param replannedRoute full current physical route used only as planning evidence
 */
public record Stage20NextJumpExecutionPlan(
        StarSystemId routeDestination,
        StarSystemId immediateDestination,
        String edgeId,
        JumpConnection connection,
        ArrivalEndpoint arrivalEndpoint,
        Stage20PhysicalGalacticRoute replannedRoute) {
    /** Validates that this object represents exactly the first edge of the supplied current route. */
    public Stage20NextJumpExecutionPlan {
        Objects.requireNonNull(routeDestination, "routeDestination");
        Objects.requireNonNull(immediateDestination, "immediateDestination");
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId must not be blank");
        }
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(arrivalEndpoint, "arrivalEndpoint");
        Objects.requireNonNull(replannedRoute, "replannedRoute");
        if (replannedRoute.jumpCount() <= 0 || replannedRoute.systems().size() < 2) {
            throw new IllegalArgumentException("next-hop plan requires a non-zero replanned route");
        }
        if (!replannedRoute.destination().equals(routeDestination)
                || !replannedRoute.systems().get(1).equals(immediateDestination)) {
            throw new IllegalArgumentException("next-hop identities must match the replanned route");
        }
        if (!replannedRoute.edges().get(0).connection().equals(connection)) {
            throw new IllegalArgumentException("connection must be the first explicit replanned route edge");
        }
        if (!arrivalEndpoint.systemId().equals(immediateDestination)) {
            throw new IllegalArgumentException("arrival endpoint must belong to the immediate destination");
        }
        if (!Stage20JumpEdgeState.ordinaryEdgeId(connection).equals(edgeId)) {
            throw new IllegalArgumentException("edgeId must match the canonical immediate connection");
        }
    }
}

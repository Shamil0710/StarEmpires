package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thin strategic command facade used by the Stage-15 global map.
 *
 * <p>The facade never moves fleets directly. It submits durable orders through
 * {@link PlayerFleetOrderService} and asks {@link PlayerFleetRoutePlanner} for read-only route
 * diagnostics. Consequently the map cannot invent pathing, travel or economic outcomes.</p>
 */
public final class PlayerStrategicCommandService {
    private final PlayerRuntime runtime;
    private final PlayerFleetOrderService orders;
    private final PlayerFleetRoutePlanner routes;

    /**
     * Creates the strategic command adapter.
     *
     * @param runtime current playable runtime
     */
    public PlayerStrategicCommandService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.orders = new PlayerFleetOrderService(runtime);
        this.routes = new PlayerFleetRoutePlanner(runtime);
    }

    /**
     * Issues a strategic MOVE toward the conventional local arrival origin of a discovered system.
     *
     * @param fleetId owned fleet
     * @param destination discovered destination system
     * @return true when the durable order was accepted
     */
    public boolean move(FleetId fleetId, StarSystemId destination) {
        return orders.issue(PlayerFleetOrderState.move(
                Objects.requireNonNull(fleetId, "Strategic FleetId not set"),
                Objects.requireNonNull(destination, "Strategic destination not set"),
                0f,
                0f));
    }

    /**
     * Explicitly holds one owned fleet.
     *
     * @param fleetId owned fleet
     * @return true when the HOLD order was accepted
     */
    public boolean hold(FleetId fleetId) {
        return orders.issue(PlayerFleetOrderState.hold(
                Objects.requireNonNull(fleetId, "Strategic FleetId not set")));
    }

    /**
     * Assigns FOLLOW to another physical FleetId.
     *
     * @param fleetId owned follower
     * @param targetFleetId physical target fleet
     * @return true when accepted
     */
    public boolean follow(FleetId fleetId, FleetId targetFleetId) {
        return orders.issue(PlayerFleetOrderState.follow(fleetId, targetFleetId));
    }

    /**
     * Assigns ESCORT to another physical FleetId.
     *
     * @param fleetId owned escort
     * @param protectedFleetId protected physical fleet
     * @return true when accepted
     */
    public boolean escort(FleetId fleetId, FleetId protectedFleetId) {
        return orders.issue(PlayerFleetOrderState.escort(fleetId, protectedFleetId));
    }

    /**
     * Assigns a deterministic patrol cycle over discovered systems.
     *
     * @param fleetId owned patrol fleet
     * @param systems at least two discovered systems in cycle order
     * @return true when accepted
     */
    public boolean patrol(FleetId fleetId, List<StarSystemId> systems) {
        return orders.issue(PlayerFleetOrderState.patrol(fleetId, systems));
    }

    /**
     * Previews the same cumulative-risk route that autonomous execution will use.
     *
     * @param fleetId owned physical fleet
     * @param destination discovered destination system
     * @return route diagnostics, or empty if the fleet/destination cannot be planned
     */
    public Optional<PlayerRouteRiskView> previewMove(FleetId fleetId, StarSystemId destination) {
        if (fleetId == null || destination == null || !runtime.player().ownedFleetIds().contains(fleetId)) {
            return Optional.empty();
        }
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return Optional.empty();
        }
        return routes.plan(fleetId, placement.systemId(), destination);
    }

    /** @return current player-known global-map projection */
    public GlobalFleetMapSnapshot mapSnapshot() {
        return GlobalFleetMapModel.capture(runtime);
    }
}

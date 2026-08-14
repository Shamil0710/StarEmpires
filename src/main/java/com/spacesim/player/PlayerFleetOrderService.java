package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative player-facing Stage-15 service for assigning durable orders to owned FleetIds.
 *
 * <p>The service changes only persistent player intent. It never moves a ship, jumps a fleet,
 * creates cargo or performs a trade. Runtime execution is delegated to the ordinary simulation
 * boundaries by {@link PlayerFleetOrderExecutor}.</p>
 */
public final class PlayerFleetOrderService {
    private final PlayerRuntime runtime;

    /**
     * Creates an order service for one playable runtime.
     *
     * @param runtime current player/world runtime
     */
    public PlayerFleetOrderService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Assigns or replaces the single durable order for an owned FleetId.
     *
     * @param order validated declarative order
     * @return true when the order references currently legal known targets and was persisted
     */
    public boolean issue(PlayerFleetOrderState order) {
        PlayerFleetOrderState checked = Objects.requireNonNull(order, "Fleet order not set");
        PlayerState player = runtime.player();
        if (!player.ownedFleetIds().contains(checked.fleetId()) || !referencesAreKnown(player, checked)) {
            return false;
        }
        List<PlayerFleetOrderState> orders = new ArrayList<>(player.fleetOrders());
        orders.removeIf(existing -> existing.fleetId().equals(checked.fleetId()));
        orders.add(checked);
        runtime.replacePlayerState(copyWithOrders(player, orders));
        return true;
    }

    /**
     * Clears delegated intent. An inactive owned fleet without an order defaults to physical HOLD.
     *
     * @param fleetId owned fleet whose explicit order should be removed
     * @return true when an existing explicit order was removed
     */
    public boolean clear(FleetId fleetId) {
        FleetId checked = Objects.requireNonNull(fleetId, "FleetId not set");
        PlayerState player = runtime.player();
        List<PlayerFleetOrderState> orders = new ArrayList<>(player.fleetOrders());
        boolean removed = orders.removeIf(order -> order.fleetId().equals(checked));
        if (removed) {
            runtime.replacePlayerState(copyWithOrders(player, orders));
        }
        return removed;
    }

    /**
     * Returns the durable order currently assigned to a FleetId.
     *
     * @param fleetId fleet to inspect
     * @return current explicit order or empty
     */
    public Optional<PlayerFleetOrderState> order(FleetId fleetId) {
        if (fleetId == null) {
            return Optional.empty();
        }
        for (PlayerFleetOrderState order : runtime.player().fleetOrders()) {
            if (fleetId.equals(order.fleetId())) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    /** @return immutable canonical list of all explicit player fleet orders */
    public List<PlayerFleetOrderState> orders() {
        return runtime.player().fleetOrders();
    }

    private boolean referencesAreKnown(PlayerState player, PlayerFleetOrderState order) {
        if (order.targetSystemId() != null && !knownSystem(player, order.targetSystemId())) {
            return false;
        }
        if (order.secondarySystemId() != null && !knownSystem(player, order.secondarySystemId())) {
            return false;
        }
        if (order.targetEntityId() != null && !player.discoveredObjects().contains(
                new DiscoveredObjectRef(order.targetSystemId(), order.targetEntityId()))) {
            return false;
        }
        if (order.secondaryEntityId() != null && !player.discoveredObjects().contains(
                new DiscoveredObjectRef(order.secondarySystemId(), order.secondaryEntityId()))) {
            return false;
        }
        if (order.targetFleetId() != null && runtime.world().findFleet(order.targetFleetId()).isEmpty()) {
            return false;
        }
        if (order.itemContentId() != null && runtime.content().findItem(order.itemContentId()) == null) {
            return false;
        }
        for (StarSystemId systemId : order.patrolSystemIds()) {
            if (!knownSystem(player, systemId)) {
                return false;
            }
        }
        return true;
    }

    private boolean knownSystem(PlayerState player, StarSystemId systemId) {
        return player.discoveredSystemIds().contains(systemId)
                && runtime.world().getTopology().findSystem(systemId).isPresent();
    }

    private static PlayerState copyWithOrders(PlayerState source, List<PlayerFleetOrderState> orders) {
        return new PlayerState(
                source.walletMilliCredits(),
                source.factionContentId(),
                source.reputations(),
                source.ownedFleetIds(),
                source.activeFleetId(),
                source.discoveredSystemIds(),
                source.discoveredObjects(),
                source.homeSystemId(),
                source.dockedAt(),
                orders);
    }
}

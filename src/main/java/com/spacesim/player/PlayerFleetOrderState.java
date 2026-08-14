package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Durable declarative order assigned to one player-owned physical FleetId.
 *
 * <p>The record intentionally stores stable IDs and value data only. Runtime Ashley entities,
 * routes and movement commands are re-resolved from authoritative world state after load. This
 * keeps orders persistent without serializing transient execution objects. Stage-16 construction
 * supply targets the persistent physical construction-site EntityId; the owning project is resolved
 * through PlayerState ownership rather than serializing a second redundant project reference.</p>
 *
 * @param fleetId player-owned fleet receiving the order
 * @param type durable order category
 * @param targetSystemId primary target system, when required
 * @param targetEntityId primary system-local target entity, when required
 * @param secondarySystemId secondary target system for two-endpoint orders
 * @param secondaryEntityId secondary system-local target entity
 * @param targetFleetId physical fleet followed/protected by escort/follow orders
 * @param itemContentId stable item content ID used by trade/mining/supply orders
 * @param targetX local MOVE target X
 * @param targetY local MOVE target Y
 * @param patrolSystemIds deterministic PATROL cycle
 */
public record PlayerFleetOrderState(
        FleetId fleetId,
        FleetOrderType type,
        StarSystemId targetSystemId,
        EntityId targetEntityId,
        StarSystemId secondarySystemId,
        EntityId secondaryEntityId,
        FleetId targetFleetId,
        String itemContentId,
        float targetX,
        float targetY,
        List<StarSystemId> patrolSystemIds) implements Comparable<PlayerFleetOrderState> {

    /**
     * Validates and canonicalizes one durable order.
     *
     * @param fleetId player-owned fleet receiving the order
     * @param type durable order category
     * @param targetSystemId primary target system, when required
     * @param targetEntityId primary system-local target entity, when required
     * @param secondarySystemId secondary target system for two-endpoint orders
     * @param secondaryEntityId secondary system-local target entity
     * @param targetFleetId physical fleet followed/protected by escort/follow orders
     * @param itemContentId stable item content ID used by trade/mining/supply orders
     * @param targetX local MOVE target X
     * @param targetY local MOVE target Y
     * @param patrolSystemIds deterministic PATROL cycle
     */
    public PlayerFleetOrderState {
        fleetId = Objects.requireNonNull(fleetId, "Fleet order FleetId not set");
        type = Objects.requireNonNull(type, "Fleet order type not set");
        if (!Float.isFinite(targetX) || !Float.isFinite(targetY)) {
            throw new IllegalArgumentException("Fleet order coordinates must be finite");
        }
        if ((targetSystemId == null) != (targetEntityId == null)
                && type != FleetOrderType.MOVE) {
            throw new IllegalArgumentException("Entity target requires a matching StarSystemId");
        }
        if ((secondarySystemId == null) != (secondaryEntityId == null)) {
            throw new IllegalArgumentException("Secondary entity target requires a matching StarSystemId");
        }
        if (itemContentId != null) {
            itemContentId = itemContentId.strip();
            if (itemContentId.isEmpty()) {
                throw new IllegalArgumentException("Fleet order item content ID cannot be blank");
            }
        }

        List<StarSystemId> patrolCopy = new ArrayList<>(Objects.requireNonNull(
                patrolSystemIds, "Fleet order patrol systems not set"));
        Set<StarSystemId> uniquePatrolSystems = new HashSet<>();
        for (StarSystemId systemId : patrolCopy) {
            if (!uniquePatrolSystems.add(Objects.requireNonNull(systemId, "Patrol StarSystemId not set"))) {
                throw new IllegalArgumentException("Duplicate patrol StarSystemId: " + systemId);
            }
        }
        patrolSystemIds = List.copyOf(patrolCopy);
        validateShape(type, targetSystemId, targetEntityId, secondarySystemId, secondaryEntityId,
                targetFleetId, itemContentId, patrolSystemIds, fleetId);
    }

    /**
     * Creates a persistent physical HOLD order.
     *
     * @param fleetId ordered fleet
     * @return validated HOLD order
     */
    public static PlayerFleetOrderState hold(FleetId fleetId) {
        return new PlayerFleetOrderState(
                fleetId, FleetOrderType.HOLD, null, null, null, null, null, null,
                0f, 0f, List.of());
    }

    /**
     * Creates a persistent physical MOVE order.
     *
     * @param fleetId ordered fleet
     * @param systemId destination system
     * @param x destination-local X
     * @param y destination-local Y
     * @return validated MOVE order
     */
    public static PlayerFleetOrderState move(FleetId fleetId, StarSystemId systemId, float x, float y) {
        return new PlayerFleetOrderState(
                fleetId, FleetOrderType.MOVE, Objects.requireNonNull(systemId, "MOVE system not set"),
                null, null, null, null, null, x, y, List.of());
    }

    /**
     * Creates a persistent two-market trade order.
     *
     * @param fleetId ordered fleet
     * @param source physical source market
     * @param destination physical destination market
     * @param itemContentId stable traded item ID
     * @return validated TRADE order
     */
    public static PlayerFleetOrderState trade(
            FleetId fleetId,
            DiscoveredObjectRef source,
            DiscoveredObjectRef destination,
            String itemContentId) {
        DiscoveredObjectRef checkedSource = Objects.requireNonNull(source, "Trade source not set");
        DiscoveredObjectRef checkedDestination = Objects.requireNonNull(destination, "Trade destination not set");
        return new PlayerFleetOrderState(
                fleetId,
                FleetOrderType.TRADE,
                checkedSource.systemId(),
                checkedSource.entityId(),
                checkedDestination.systemId(),
                checkedDestination.entityId(),
                null,
                itemContentId,
                0f,
                0f,
                List.of());
    }

    /**
     * Creates a persistent mining order with an optional sale/delivery market.
     *
     * @param fleetId ordered mining fleet
     * @param asteroid physical asteroid target
     * @param delivery optional market that receives mined cargo
     * @param itemContentId stable mined item ID
     * @return validated MINE order
     */
    public static PlayerFleetOrderState mine(
            FleetId fleetId,
            DiscoveredObjectRef asteroid,
            DiscoveredObjectRef delivery,
            String itemContentId) {
        DiscoveredObjectRef checkedAsteroid = Objects.requireNonNull(asteroid, "Mining asteroid not set");
        return new PlayerFleetOrderState(
                fleetId,
                FleetOrderType.MINE,
                checkedAsteroid.systemId(),
                checkedAsteroid.entityId(),
                delivery == null ? null : delivery.systemId(),
                delivery == null ? null : delivery.entityId(),
                null,
                itemContentId,
                0f,
                0f,
                List.of());
    }

    /**
     * Creates a persistent construction-supply order targeting a physical owned site.
     *
     * @param fleetId ordered cargo fleet
     * @param constructionSite persistent physical construction-site reference
     * @param itemContentId required material item to acquire and deliver
     * @return validated SUPPLY_PROJECT order
     */
    public static PlayerFleetOrderState supplyProject(
            FleetId fleetId,
            DiscoveredObjectRef constructionSite,
            String itemContentId) {
        DiscoveredObjectRef site = Objects.requireNonNull(constructionSite, "Construction site not set");
        return new PlayerFleetOrderState(
                fleetId,
                FleetOrderType.SUPPLY_PROJECT,
                site.systemId(),
                site.entityId(),
                null,
                null,
                null,
                itemContentId,
                0f,
                0f,
                List.of());
    }

    /**
     * Creates a persistent ESCORT order targeting another FleetId.
     *
     * @param fleetId ordered escort fleet
     * @param protectedFleetId physical fleet to protect
     * @return validated ESCORT order
     */
    public static PlayerFleetOrderState escort(FleetId fleetId, FleetId protectedFleetId) {
        return fleetTarget(fleetId, FleetOrderType.ESCORT, protectedFleetId);
    }

    /**
     * Creates a persistent FOLLOW order targeting another FleetId.
     *
     * @param fleetId ordered follower fleet
     * @param followedFleetId physical fleet to follow
     * @return validated FOLLOW order
     */
    public static PlayerFleetOrderState follow(FleetId fleetId, FleetId followedFleetId) {
        return fleetTarget(fleetId, FleetOrderType.FOLLOW, followedFleetId);
    }

    /**
     * Creates a persistent deterministic patrol cycle.
     *
     * @param fleetId ordered fleet
     * @param systems at least two unique systems in cycle order
     * @return validated PATROL order
     */
    public static PlayerFleetOrderState patrol(FleetId fleetId, List<StarSystemId> systems) {
        return new PlayerFleetOrderState(
                fleetId, FleetOrderType.PATROL, null, null, null, null, null, null,
                0f, 0f, systems);
    }

    private static PlayerFleetOrderState fleetTarget(
            FleetId fleetId,
            FleetOrderType type,
            FleetId targetFleetId) {
        return new PlayerFleetOrderState(
                fleetId, type, null, null, null, null,
                Objects.requireNonNull(targetFleetId, "Target FleetId not set"), null,
                0f, 0f, List.of());
    }

    private static void validateShape(
            FleetOrderType type,
            StarSystemId targetSystemId,
            EntityId targetEntityId,
            StarSystemId secondarySystemId,
            EntityId secondaryEntityId,
            FleetId targetFleetId,
            String itemContentId,
            List<StarSystemId> patrolSystems,
            FleetId fleetId) {
        switch (type) {
            case HOLD -> requireNoTargets(targetSystemId, targetEntityId, secondarySystemId,
                    secondaryEntityId, targetFleetId, itemContentId, patrolSystems);
            case MOVE -> {
                if (targetSystemId == null || targetEntityId != null || secondarySystemId != null
                        || targetFleetId != null || itemContentId != null || !patrolSystems.isEmpty()) {
                    throw new IllegalArgumentException("MOVE requires only a target system and coordinates");
                }
            }
            case TRADE -> {
                if (targetSystemId == null || targetEntityId == null
                        || secondarySystemId == null || secondaryEntityId == null
                        || targetFleetId != null || itemContentId == null || !patrolSystems.isEmpty()) {
                    throw new IllegalArgumentException("TRADE requires source, destination and item");
                }
            }
            case MINE -> {
                if (targetSystemId == null || targetEntityId == null
                        || targetFleetId != null || itemContentId == null || !patrolSystems.isEmpty()) {
                    throw new IllegalArgumentException("MINE requires asteroid target and item");
                }
            }
            case SUPPLY_PROJECT -> {
                if (targetSystemId == null || targetEntityId == null
                        || secondarySystemId != null || secondaryEntityId != null
                        || targetFleetId != null || itemContentId == null || !patrolSystems.isEmpty()) {
                    throw new IllegalArgumentException("SUPPLY_PROJECT requires construction site and item only");
                }
            }
            case ESCORT, FOLLOW -> {
                if (targetFleetId == null || fleetId.equals(targetFleetId)
                        || targetSystemId != null || secondarySystemId != null
                        || itemContentId != null || !patrolSystems.isEmpty()) {
                    throw new IllegalArgumentException(type + " requires another target FleetId only");
                }
            }
            case PATROL -> {
                if (patrolSystems.size() < 2 || targetSystemId != null || secondarySystemId != null
                        || targetFleetId != null || itemContentId != null) {
                    throw new IllegalArgumentException("PATROL requires at least two unique systems only");
                }
            }
        }
    }

    private static void requireNoTargets(
            StarSystemId targetSystemId,
            EntityId targetEntityId,
            StarSystemId secondarySystemId,
            EntityId secondaryEntityId,
            FleetId targetFleetId,
            String itemContentId,
            List<StarSystemId> patrolSystems) {
        if (targetSystemId != null || targetEntityId != null || secondarySystemId != null
                || secondaryEntityId != null || targetFleetId != null || itemContentId != null
                || !patrolSystems.isEmpty()) {
            throw new IllegalArgumentException("HOLD cannot contain targets");
        }
    }

    /** Orders are canonicalized by stable FleetId. */
    @Override
    public int compareTo(PlayerFleetOrderState other) {
        return fleetId.compareTo(Objects.requireNonNull(other, "Other fleet order not set").fleetId);
    }
}

package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Stage-21D strategic command state that references ordinary {@link FleetId} values only.
 *
 * <p>The record stores command-group membership and strategic order metadata while leaving physical
 * fleet identity, placement, engineering state, movement and combat authority in the pre-existing
 * world and warfare layers.</p>
 *
 * @param nextCommandGroupId next positive allocator watermark for command-group identities
 * @param nextOrderId next positive allocator watermark for strategic-order identities
 * @param groups persistent command groups sorted canonically by identifier
 * @param orders persistent strategic orders sorted canonically by identifier
 */
public record FleetCommandState(
        long nextCommandGroupId,
        long nextOrderId,
        List<CommandGroupState> groups,
        List<FleetOrderState> orders) {

    /**
     * Validates and canonicalizes persistent Stage-21D command state.
     *
     * @param nextCommandGroupId next positive allocator watermark for command-group identities
     * @param nextOrderId next positive allocator watermark for strategic-order identities
     * @param groups command groups to validate and canonicalize
     * @param orders strategic orders to validate and canonicalize
     */
    public FleetCommandState {
        if (nextCommandGroupId <= 0L || nextOrderId <= 0L) {
            throw new IllegalArgumentException("command/order allocator watermarks must be positive");
        }
        groups = canonicalGroups(groups);
        orders = canonicalOrders(orders);
        Set<Long> groupIds = new HashSet<>();
        Set<FleetId> assignedMembers = new HashSet<>();
        long maxGroupId = 0L;
        for (CommandGroupState group : groups) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("duplicate command group id: " + group.id());
            maxGroupId = Math.max(maxGroupId, group.id());
            for (FleetId fleetId : group.memberFleetIds()) {
                if (!assignedMembers.add(fleetId)) {
                    throw new IllegalArgumentException("FleetId assigned to multiple command groups: " + fleetId);
                }
            }
        }
        if (nextCommandGroupId <= maxGroupId) {
            throw new IllegalArgumentException("nextCommandGroupId must be above every persisted command-group id");
        }
        Set<Long> orderIds = new HashSet<>();
        Set<Long> activeGroupIds = new HashSet<>();
        long maxOrderId = 0L;
        for (FleetOrderState order : orders) {
            if (!orderIds.add(order.id())) throw new IllegalArgumentException("duplicate fleet order id: " + order.id());
            maxOrderId = Math.max(maxOrderId, order.id());
            if (!groupIds.contains(order.commandGroupId())) {
                throw new IllegalArgumentException("order references unknown command group: " + order.commandGroupId());
            }
            if (order.status().active() && !activeGroupIds.add(order.commandGroupId())) {
                throw new IllegalArgumentException("command group has multiple active orders: " + order.commandGroupId());
            }
        }
        if (nextOrderId <= maxOrderId) {
            throw new IllegalArgumentException("nextOrderId must be above every persisted fleet-order id");
        }
    }

    /**
     * Creates an empty command state with allocator watermarks initialized to one.
     *
     * @return canonical empty command state
     */
    public static FleetCommandState empty() { return new FleetCommandState(1L, 1L, List.of(), List.of()); }

    /**
     * Resolves a command group or fails closed when the identifier is unknown.
     *
     * @param groupId positive command-group identifier
     * @return matching persistent command group
     * @throws IllegalArgumentException when no such group exists
     */
    public CommandGroupState requireGroup(long groupId) {
        return groups.stream().filter(group -> group.id() == groupId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown command group: " + groupId));
    }

    /**
     * Resolves a strategic order or fails closed when the identifier is unknown.
     *
     * @param orderId positive strategic-order identifier
     * @return matching persistent strategic order
     * @throws IllegalArgumentException when no such order exists
     */
    public FleetOrderState requireOrder(long orderId) {
        return orders.stream().filter(order -> order.id() == orderId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown fleet order: " + orderId));
    }

    /**
     * Finds the single active strategic order for a command group.
     *
     * @param groupId command-group identifier
     * @return active order when present
     */
    public java.util.Optional<FleetOrderState> activeOrderFor(long groupId) {
        return orders.stream().filter(order -> order.commandGroupId() == groupId && order.status().active()).findFirst();
    }

    /**
     * Returns a new state containing the supplied command group and an advanced allocator watermark.
     *
     * @param group validated command group to add
     * @return immutable state containing the new group
     */
    public FleetCommandState addGroup(CommandGroupState group) {
        Objects.requireNonNull(group, "group");
        ArrayList<CommandGroupState> next = new ArrayList<>(groups);
        next.add(group);
        return new FleetCommandState(Math.max(nextCommandGroupId, Math.addExact(group.id(), 1L)), nextOrderId, next, orders);
    }

    /**
     * Returns a new state containing the supplied strategic order and an advanced allocator watermark.
     *
     * @param order validated strategic order to add
     * @return immutable state containing the new order
     */
    public FleetCommandState addOrder(FleetOrderState order) {
        Objects.requireNonNull(order, "order");
        ArrayList<FleetOrderState> next = new ArrayList<>(orders);
        next.add(order);
        return new FleetCommandState(nextCommandGroupId, Math.max(nextOrderId, Math.addExact(order.id(), 1L)), groups, next);
    }

    /**
     * Replaces an existing order without changing its identity.
     *
     * @param replacement replacement state for an existing strategic order
     * @return immutable state containing the replacement
     * @throws IllegalArgumentException when the order identifier is unknown
     */
    public FleetCommandState replaceOrder(FleetOrderState replacement) {
        Objects.requireNonNull(replacement, "replacement");
        ArrayList<FleetOrderState> next = new ArrayList<>(orders.size());
        boolean replaced = false;
        for (FleetOrderState order : orders) {
            if (order.id() == replacement.id()) { next.add(replacement); replaced = true; }
            else next.add(order);
        }
        if (!replaced) throw new IllegalArgumentException("unknown fleet order: " + replacement.id());
        return new FleetCommandState(nextCommandGroupId, nextOrderId, groups, next);
    }

    private static List<CommandGroupState> canonicalGroups(List<CommandGroupState> source) {
        Objects.requireNonNull(source, "groups");
        ArrayList<CommandGroupState> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparingLong(CommandGroupState::id));
        return List.copyOf(copy);
    }

    private static List<FleetOrderState> canonicalOrders(List<FleetOrderState> source) {
        Objects.requireNonNull(source, "orders");
        ArrayList<FleetOrderState> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparingLong(FleetOrderState::id));
        return List.copyOf(copy);
    }

    /**
     * Persistent strategic wrapper around one or more ordinary fleets.
     *
     * <p>{@code maxStrategicRiskBps} is a doctrine ceiling used by order validation; it is not a
     * combat-power score and does not replace Stage-19 physical warfare authority.</p>
     *
     * @param id positive command-group identifier
     * @param factionId owning faction runtime identifier
     * @param name normalized display name
     * @param memberFleetIds non-empty unique ordinary fleet identities
     * @param homeSystemId doctrine-defined home system used by reserve/home-defense constraints
     * @param reserve whether the group is reserved from ordinary forward commitments
     * @param homeDefense whether the group is constrained to home-defense commitments
     * @param maxStrategicRiskBps maximum accepted strategic route risk in basis points
     */
    public record CommandGroupState(
            long id,
            int factionId,
            String name,
            List<FleetId> memberFleetIds,
            StarSystemId homeSystemId,
            boolean reserve,
            boolean homeDefense,
            int maxStrategicRiskBps) {
        /**
         * Validates and canonicalizes a strategic command group.
         *
         * @param id positive command-group identifier
         * @param factionId non-negative owning faction runtime identifier
         * @param name normalized display name
         * @param memberFleetIds non-empty unique ordinary fleet identities
         * @param homeSystemId doctrine-defined home system
         * @param reserve reserve commitment flag
         * @param homeDefense home-defense commitment flag
         * @param maxStrategicRiskBps doctrine risk ceiling in basis points
         */
        public CommandGroupState {
            if (id <= 0L) throw new IllegalArgumentException("command group id must be positive");
            if (factionId < 0) throw new IllegalArgumentException("command group factionId must be non-negative");
            name = normalized(name, "command group name");
            if (name.length() > 1024) throw new IllegalArgumentException("command group name is too long");
            Objects.requireNonNull(memberFleetIds, "memberFleetIds");
            Objects.requireNonNull(homeSystemId, "homeSystemId");
            if (maxStrategicRiskBps < 0 || maxStrategicRiskBps > FleetReadinessState.FULL) {
                throw new IllegalArgumentException("maxStrategicRiskBps must be in 0..10000");
            }
            ArrayList<FleetId> members = new ArrayList<>(memberFleetIds);
            members.sort(Comparator.naturalOrder());
            if (members.isEmpty()) throw new IllegalArgumentException("command group cannot be empty");
            Set<FleetId> unique = new HashSet<>(members);
            if (unique.size() != members.size()) throw new IllegalArgumentException("duplicate FleetId in command group");
            memberFleetIds = List.copyOf(members);
        }
    }

    /**
     * Persistent strategic order issued to a command group.
     *
     * @param id positive order identifier
     * @param commandGroupId command group receiving the order
     * @param type strategic order family
     * @param source player or AI submission source
     * @param targetSystemId final strategic destination
     * @param route canonical neighbor-by-neighbor system route ending at the target
     * @param routeCursor index of the route node currently reached by the command group
     * @param submittedTick simulation tick when the order was accepted
     * @param stagingDeadlineTick latest staging tick recorded for the order
     * @param status current strategic order lifecycle state
     */
    public record FleetOrderState(
            long id,
            long commandGroupId,
            OrderType type,
            OrderSource source,
            StarSystemId targetSystemId,
            List<StarSystemId> route,
            int routeCursor,
            long submittedTick,
            long stagingDeadlineTick,
            OrderStatus status) {
        /**
         * Validates a strategic order and freezes its route.
         *
         * @param id positive order identifier
         * @param commandGroupId command group receiving the order
         * @param type strategic order family
         * @param source player or AI submission source
         * @param targetSystemId final strategic destination
         * @param route non-empty route ending at the target system
         * @param routeCursor index of the currently reached route node
         * @param submittedTick non-negative acceptance tick
         * @param stagingDeadlineTick deadline not earlier than submission
         * @param status current strategic order lifecycle state
         */
        public FleetOrderState {
            if (id <= 0L) throw new IllegalArgumentException("order id must be positive");
            if (commandGroupId <= 0L) throw new IllegalArgumentException("commandGroupId must be positive");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(targetSystemId, "targetSystemId");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(status, "status");
            route = List.copyOf(route);
            if (route.isEmpty()) throw new IllegalArgumentException("order route cannot be empty");
            if (!route.get(route.size() - 1).equals(targetSystemId)) {
                throw new IllegalArgumentException("order route must end at target system");
            }
            if (routeCursor < 0 || routeCursor >= route.size()) throw new IllegalArgumentException("routeCursor outside route");
            if (submittedTick < 0L || stagingDeadlineTick < submittedTick) throw new IllegalArgumentException("invalid order timing");
        }

        /**
         * Returns a copy of this order with a different lifecycle status.
         *
         * @param nextStatus replacement lifecycle status
         * @return immutable order copy with the supplied status
         */
        public FleetOrderState withStatus(OrderStatus nextStatus) {
            return new FleetOrderState(id, commandGroupId, type, source, targetSystemId, route,
                    routeCursor, submittedTick, stagingDeadlineTick, nextStatus);
        }

        /**
         * Advances the route cursor by one node when another route node exists.
         *
         * @return advanced immutable order, or this instance when already at the final route node
         */
        public FleetOrderState advanceRoute() {
            if (routeCursor + 1 >= route.size()) return this;
            return new FleetOrderState(id, commandGroupId, type, source, targetSystemId, route,
                    routeCursor + 1, submittedTick, stagingDeadlineTick, status);
        }
    }

    /** Strategic order families accepted by the Stage-21D command boundary. */
    public enum OrderType {
        /** Maintain a recurring presence in or around the target system. */
        PATROL,
        /** Hold a target system or protected position. */
        GUARD,
        /** Accompany and protect another strategic movement or asset. */
        ESCORT,
        /** Concentrate forces at a staging destination without resolving combat. */
        STAGE,
        /** Move forces to strengthen an existing friendly commitment. */
        REINFORCE,
        /** Move to meet an observed or designated hostile movement. */
        INTERCEPT,
        /** Follow a target while preserving the physical sensing and contact authority. */
        SHADOW,
        /** Prepare or conduct a limited hostile operation whose physical resolution belongs to Stage 19. */
        RAID,
        /** Establish a blockade commitment whose physical effects are resolved by warfare/logistics authority. */
        BLOCKADE,
        /** Commit forces toward an invasion objective without shortcutting physical combat resolution. */
        INVADE,
        /** Disengage from the present strategic commitment. */
        WITHDRAW,
        /** Request propellant/reaction-mass replenishment through existing service authority. */
        REFUEL,
        /** Request ammunition replenishment through existing service authority. */
        REARM,
        /** Request engineering repair through existing shipyard/service authority. */
        REPAIR,
        /** Return the command group toward its designated home system. */
        RETURN;

        /**
         * Reports whether this order delegates work to an existing replenishment or repair service.
         *
         * @return {@code true} for refuel, rearm or repair orders
         */
        public boolean serviceOrder() { return this == REFUEL || this == REARM || this == REPAIR; }

        /**
         * Reports whether this order represents an explicitly offensive strategic commitment.
         *
         * @return {@code true} for raid, blockade or invade orders
         */
        public boolean offensiveOrder() { return this == RAID || this == BLOCKADE || this == INVADE; }

        /**
         * Reports whether the order can lead to physical military contact without resolving that contact here.
         *
         * @return {@code true} for combat-oriented strategic commitments
         */
        public boolean combatOrder() {
            return this == GUARD || this == ESCORT || this == REINFORCE || this == INTERCEPT
                    || this == SHADOW || offensiveOrder();
        }
    }

    /** Origin of a submitted strategic order. */
    public enum OrderSource {
        /** Order submitted through the player command path. */
        PLAYER,
        /** Order submitted through the AI command path. */
        AI
    }

    /** Persistent lifecycle states for a Stage-21D strategic order. */
    public enum OrderStatus {
        /** Order is accepted but still waiting for staging conditions. */
        STAGING,
        /** Order is actively executing ordinary movement or strategic positioning. */
        ACTIVE,
        /** Order is waiting for an existing refuel, rearm or repair authority to satisfy a request. */
        SERVICE_PENDING,
        /** Order has reached its Stage-21D completion condition. */
        COMPLETE,
        /** Order was explicitly cancelled before completion. */
        CANCELLED,
        /** Order failed validation or execution after acceptance. */
        FAILED;

        /**
         * Reports whether the lifecycle state still occupies the command group's single active-order slot.
         *
         * @return {@code true} for staging, active or service-pending states
         */
        public boolean active() { return this == STAGING || this == ACTIVE || this == SERVICE_PENDING; }
    }

    private static String normalized(String value, String label) {
        String result = Objects.requireNonNull(value, label).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " cannot be empty");
        return result;
    }
}

package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Persistent Stage-21D command state referencing ordinary FleetIds only. */
public record FleetCommandState(
        long nextCommandGroupId,
        long nextOrderId,
        List<CommandGroupState> groups,
        List<FleetOrderState> orders) {

    public FleetCommandState {
        if (nextCommandGroupId <= 0L || nextOrderId <= 0L) {
            throw new IllegalArgumentException("command/order allocator watermarks must be positive");
        }
        groups = canonicalGroups(groups);
        orders = canonicalOrders(orders);
        Set<Long> groupIds = new HashSet<>();
        Set<FleetId> assignedMembers = new HashSet<>();
        for (CommandGroupState group : groups) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("duplicate command group id: " + group.id());
            for (FleetId fleetId : group.memberFleetIds()) {
                if (!assignedMembers.add(fleetId)) {
                    throw new IllegalArgumentException("FleetId assigned to multiple command groups: " + fleetId);
                }
            }
        }
        Set<Long> orderIds = new HashSet<>();
        Set<Long> activeGroupIds = new HashSet<>();
        for (FleetOrderState order : orders) {
            if (!orderIds.add(order.id())) throw new IllegalArgumentException("duplicate fleet order id: " + order.id());
            if (!groupIds.contains(order.commandGroupId())) {
                throw new IllegalArgumentException("order references unknown command group: " + order.commandGroupId());
            }
            if (order.status().active() && !activeGroupIds.add(order.commandGroupId())) {
                throw new IllegalArgumentException("command group has multiple active orders: " + order.commandGroupId());
            }
        }
    }

    public static FleetCommandState empty() { return new FleetCommandState(1L, 1L, List.of(), List.of()); }

    public CommandGroupState requireGroup(long groupId) {
        return groups.stream().filter(group -> group.id() == groupId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown command group: " + groupId));
    }

    public FleetOrderState requireOrder(long orderId) {
        return orders.stream().filter(order -> order.id() == orderId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown fleet order: " + orderId));
    }

    public java.util.Optional<FleetOrderState> activeOrderFor(long groupId) {
        return orders.stream().filter(order -> order.commandGroupId() == groupId && order.status().active()).findFirst();
    }

    public FleetCommandState addGroup(CommandGroupState group) {
        Objects.requireNonNull(group, "group");
        ArrayList<CommandGroupState> next = new ArrayList<>(groups);
        next.add(group);
        return new FleetCommandState(Math.max(nextCommandGroupId, group.id() + 1L), nextOrderId, next, orders);
    }

    public FleetCommandState addOrder(FleetOrderState order) {
        Objects.requireNonNull(order, "order");
        ArrayList<FleetOrderState> next = new ArrayList<>(orders);
        next.add(order);
        return new FleetCommandState(nextCommandGroupId, Math.max(nextOrderId, order.id() + 1L), groups, next);
    }

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

    /** maxStrategicRiskBps is a persistent doctrine ceiling, not a combat-stat replacement. */
    public record CommandGroupState(
            long id,
            int factionId,
            String name,
            List<FleetId> memberFleetIds,
            StarSystemId homeSystemId,
            boolean reserve,
            boolean homeDefense,
            int maxStrategicRiskBps) {
        public CommandGroupState {
            if (id <= 0L) throw new IllegalArgumentException("command group id must be positive");
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

        public FleetOrderState withStatus(OrderStatus nextStatus) {
            return new FleetOrderState(id, commandGroupId, type, source, targetSystemId, route,
                    routeCursor, submittedTick, stagingDeadlineTick, nextStatus);
        }

        public FleetOrderState advanceRoute() {
            if (routeCursor + 1 >= route.size()) return this;
            return new FleetOrderState(id, commandGroupId, type, source, targetSystemId, route,
                    routeCursor + 1, submittedTick, stagingDeadlineTick, status);
        }
    }

    public enum OrderType {
        PATROL, GUARD, ESCORT, STAGE, REINFORCE, INTERCEPT, SHADOW, RAID, BLOCKADE, INVADE,
        WITHDRAW, REFUEL, REARM, REPAIR, RETURN;
        public boolean serviceOrder() { return this == REFUEL || this == REARM || this == REPAIR; }
        public boolean offensiveOrder() { return this == RAID || this == BLOCKADE || this == INVADE; }
        public boolean combatOrder() {
            return this == GUARD || this == ESCORT || this == REINFORCE || this == INTERCEPT
                    || this == SHADOW || offensiveOrder();
        }
    }

    public enum OrderSource { PLAYER, AI }

    public enum OrderStatus {
        STAGING, ACTIVE, SERVICE_PENDING, COMPLETE, CANCELLED, FAILED;
        public boolean active() { return this == STAGING || this == ACTIVE || this == SERVICE_PENDING; }
    }

    private static String normalized(String value, String label) {
        String result = Objects.requireNonNull(value, label).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " cannot be empty");
        return result;
    }
}

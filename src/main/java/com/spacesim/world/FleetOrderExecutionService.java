package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts validated Stage-21D orders into existing world operations.
 *
 * <p>Movement is applied only through {@link WorldSimulation#requestFleetJump(FleetId,
 * StarSystemId)}. Refuel/rearm/repair are returned as explicit service requests for the existing
 * Stage-18 authority; this class never changes engineering consumables, damage or economy.</p>
 */
public final class FleetOrderExecutionService {
    private final GalaxyTopology topology;

    public FleetOrderExecutionService(GalaxyTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
    }

    public List<Operation> planNextOperations(
            FleetCommandState commandState,
            FleetForceRegistry forces,
            long orderId) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(forces, "forces");
        FleetOrderState order = commandState.requireOrder(orderId);
        if (!order.status().active()) {
            return List.of();
        }
        CommandGroupState group = commandState.requireGroup(order.commandGroupId());
        if (order.routeCursor() + 1 < order.route().size()) {
            StarSystemId current = order.route().get(order.routeCursor());
            StarSystemId next = order.route().get(order.routeCursor() + 1);
            if (!topology.neighbors(current).contains(next)) {
                throw new IllegalStateException("persisted order contains non-neighbor route hop");
            }
            ArrayList<Operation> operations = new ArrayList<>();
            for (FleetId fleetId : group.memberFleetIds()) {
                FleetForceRegistry.Entry force = forces.find(fleetId)
                        .orElseThrow(() -> new IllegalStateException("missing FleetId: " + fleetId));
                if (force.locationKind() != FleetLocationKind.IN_SYSTEM || !current.equals(force.systemId())) {
                    throw new IllegalStateException("fleet is not staged at persisted route cursor: " + fleetId);
                }
                operations.add(new MovementOperation(fleetId, current, next));
            }
            return List.copyOf(operations);
        }
        if (order.type().serviceOrder()) {
            return group.memberFleetIds().stream()
                    .map(fleetId -> (Operation) new ServiceOperation(fleetId, order.targetSystemId(), order.type()))
                    .toList();
        }
        return List.of();
    }

    /** Applies only movement operations through the existing jump FSM. */
    public FleetCommandState dispatchMovementHop(
            WorldSimulation world,
            FleetCommandState commandState,
            FleetForceRegistry forces,
            long orderId) {
        Objects.requireNonNull(world, "world");
        List<Operation> operations = planNextOperations(commandState, forces, orderId);
        if (operations.isEmpty()) return commandState;
        for (Operation operation : operations) {
            if (!(operation instanceof MovementOperation movement)) {
                throw new IllegalStateException("service operation must be executed by Stage-18 service authority");
            }
            if (world.findFleetJump(movement.fleetId()).isPresent()) {
                throw new IllegalStateException("fleet already has an active jump: " + movement.fleetId());
            }
        }
        for (Operation operation : operations) {
            MovementOperation movement = (MovementOperation) operation;
            world.requestFleetJump(movement.fleetId(), movement.destinationSystemId());
        }
        FleetOrderState order = commandState.requireOrder(orderId);
        return commandState.replaceOrder(order.withStatus(OrderStatus.ACTIVE));
    }

    /**
     * Advances the persistent route cursor only after every member has physically arrived. It never
     * manufactures arrival state and therefore cannot duplicate or teleport a fleet.
     */
    public FleetCommandState reconcilePhysicalArrival(
            FleetCommandState commandState,
            FleetForceRegistry forces,
            long orderId) {
        FleetOrderState order = commandState.requireOrder(orderId);
        if (order.routeCursor() + 1 >= order.route().size()) return commandState;
        StarSystemId expected = order.route().get(order.routeCursor() + 1);
        CommandGroupState group = commandState.requireGroup(order.commandGroupId());
        for (FleetId fleetId : group.memberFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException("missing FleetId: " + fleetId));
            if (force.locationKind() != FleetLocationKind.IN_SYSTEM || !expected.equals(force.systemId())) {
                return commandState;
            }
        }
        FleetOrderState advanced = order.advanceRoute();
        if (advanced.routeCursor() + 1 >= advanced.route().size()) {
            advanced = advanced.withStatus(order.type().serviceOrder()
                    ? OrderStatus.SERVICE_PENDING
                    : OrderStatus.COMPLETE);
        }
        return commandState.replaceOrder(advanced);
    }

    public sealed interface Operation permits MovementOperation, ServiceOperation { }

    public record MovementOperation(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId) implements Operation {
        public MovementOperation {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(originSystemId, "originSystemId");
            Objects.requireNonNull(destinationSystemId, "destinationSystemId");
            if (originSystemId.equals(destinationSystemId)) {
                throw new IllegalArgumentException("movement operation must change system");
            }
        }
    }

    /** A request only; Stage-21D deliberately has no API that can grant the service for free. */
    public record ServiceOperation(
            FleetId fleetId,
            StarSystemId systemId,
            OrderType serviceType) implements Operation {
        public ServiceOperation {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(serviceType, "serviceType");
            if (!serviceType.serviceOrder()) {
                throw new IllegalArgumentException("service operation requires REFUEL/REARM/REPAIR");
            }
        }
    }
}

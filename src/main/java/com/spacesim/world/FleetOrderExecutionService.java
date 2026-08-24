package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
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
 * <p>Movement is applied only through the existing {@link WorldSimulation#requestFleetJump(
 * FleetId, StarSystemId, float, float)} boundary. Refuel/rearm/repair are returned as explicit
 * service requests for the existing Stage-18 authority; this class never changes engineering
 * consumables, damage or economy.</p>
 */
public final class FleetOrderExecutionService {
    private static final ProductionFittedJumpResolver FITTED_JUMP_READINESS =
            new ProductionFittedJumpResolver();

    private final GalaxyTopology topology;

    /**
     * Creates an execution adapter bound to the existing physical galaxy topology.
     *
     * @param topology authoritative neighbor topology used to validate persisted route hops
     */
    public FleetOrderExecutionService(GalaxyTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
    }

    /**
     * Projects the next ordinary operations required by an active strategic order.
     *
     * <p>Movement operations are neighbor-only. Members already physically executing the exact
     * persisted hop, or already present at its destination, produce no duplicate operation. This
     * keeps a multi-fleet command recoverable when the ordinary jump authority accepts some members
     * before another member becomes temporarily infeasible.</p>
     *
     * <p>Service operations are requests only and must be fulfilled by the pre-existing Stage-18
     * service authority.</p>
     *
     * @param commandState persistent Stage-21D command state
     * @param forces current read-only reconstruction of ordinary fleets
     * @param orderId strategic order identifier to inspect
     * @return immutable next-operation list, empty when no Stage-21D dispatch is currently required
     */
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
                if (force.locationKind() == FleetLocationKind.IN_SYSTEM && current.equals(force.systemId())) {
                    operations.add(new MovementOperation(fleetId, current, next));
                    continue;
                }
                if (force.locationKind() == FleetLocationKind.IN_SYSTEM && next.equals(force.systemId())) {
                    continue;
                }
                if (force.locationKind() == FleetLocationKind.IN_TRANSIT
                        && current.equals(force.transitOriginSystemId())
                        && next.equals(force.transitDestinationSystemId())) {
                    continue;
                }
                throw new IllegalStateException(
                        "fleet is outside persisted route hop " + current + " -> " + next + ": " + fleetId);
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

    /**
     * Applies only movement operations through the existing jump finite-state machine.
     *
     * <p>An already-active jump is accepted only when it is the same persisted route hop; in that
     * case no duplicate request is created. A different active jump fails closed. Fitted fleets that
     * have physically arrived but are still in the ordinary engineering FTL cooldown remain on the
     * current route node without a synthetic reset or replacement jump; a later dispatch attempt may
     * start the next hop only after normal simulation time clears that cooldown. Other fitted-jump
     * failures are still delegated to the authoritative jump boundary and therefore fail closed.</p>
     *
     * @param world authoritative world simulation that owns physical fleet jumps
     * @param commandState persistent strategic command state
     * @param forces current reconstruction of ordinary fleet placement and readiness
     * @param orderId active strategic order to dispatch
     * @return updated command state with the order marked active after accepted or physically-waiting requests
     * @throws IllegalStateException when a service operation is routed here, an active jump targets another hop,
     *                               or ordinary jump authority rejects a non-cooldown movement request
     */
    public FleetCommandState dispatchMovementHop(
            WorldSimulation world,
            FleetCommandState commandState,
            FleetForceRegistry forces,
            long orderId) {
        Objects.requireNonNull(world, "world");
        List<Operation> operations = planNextOperations(commandState, forces, orderId);
        if (operations.isEmpty()) return commandState;
        ArrayList<MovementOperation> pending = new ArrayList<>();
        for (Operation operation : operations) {
            if (!(operation instanceof MovementOperation movement)) {
                throw new IllegalStateException("service operation must be executed by Stage-18 service authority");
            }
            var activeJump = world.findFleetJump(movement.fleetId());
            if (activeJump.isEmpty()) {
                pending.add(movement);
                continue;
            }
            FleetJumpState jump = activeJump.orElseThrow();
            if (!movement.originSystemId().equals(jump.originSystemId())
                    || !movement.destinationSystemId().equals(jump.destinationSystemId())) {
                throw new IllegalStateException(
                        "fleet already has a different active jump: " + movement.fleetId());
            }
        }
        for (MovementOperation movement : pending) {
            FleetForceRegistry.Entry force = forces.find(movement.fleetId())
                    .orElseThrow(() -> new IllegalStateException("missing FleetId: " + movement.fleetId()));
            if (waitingForFittedCooldown(force)) {
                continue;
            }
            world.requestFleetJump(movement.fleetId(), movement.destinationSystemId(), 0f, 0f);
        }
        FleetOrderState order = commandState.requireOrder(orderId);
        return commandState.replaceOrder(order.withStatus(OrderStatus.ACTIVE));
    }

    /**
     * Advances the persistent route cursor only after every member has physically arrived. It never
     * manufactures arrival state and therefore cannot duplicate or teleport a fleet.
     *
     * @param commandState persistent strategic command state
     * @param forces current reconstruction of ordinary fleet placement after world simulation progress
     * @param orderId strategic order whose next physical arrival is being reconciled
     * @return unchanged state until all members arrive, otherwise state advanced by one route node
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

    private static boolean waitingForFittedCooldown(FleetForceRegistry.Entry force) {
        Entity entity = EntityStateMapper.restore(force.entityState());
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        if (engineering == null) {
            return false;
        }
        var plan = FITTED_JUMP_READINESS.plan(engineering);
        return !plan.allowed() && plan.failure() == JumpFailure.COOLDOWN_ACTIVE;
    }

    /** Marker for ordinary operations projected from a validated strategic order. */
    public sealed interface Operation permits MovementOperation, ServiceOperation { }

    /**
     * One neighbor-only physical fleet movement request.
     *
     * @param fleetId stable ordinary fleet identity
     * @param originSystemId route node from which the fleet must depart
     * @param destinationSystemId adjacent route node requested through the existing jump authority
     */
    public record MovementOperation(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId) implements Operation {
        /**
         * Validates one physical movement request.
         *
         * @param fleetId stable ordinary fleet identity
         * @param originSystemId non-null origin system
         * @param destinationSystemId non-null distinct destination system
         */
        public MovementOperation {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(originSystemId, "originSystemId");
            Objects.requireNonNull(destinationSystemId, "destinationSystemId");
            if (originSystemId.equals(destinationSystemId)) {
                throw new IllegalArgumentException("movement operation must change system");
            }
        }
    }

    /**
     * A service request only; Stage-21D deliberately has no API that can grant the service for free.
     *
     * @param fleetId stable ordinary fleet identity requiring service
     * @param systemId system where existing service authority must satisfy the request
     * @param serviceType refuel, rearm or repair request type
     */
    public record ServiceOperation(
            FleetId fleetId,
            StarSystemId systemId,
            OrderType serviceType) implements Operation {
        /**
         * Validates a request delegated to the existing Stage-18 service authority.
         *
         * @param fleetId stable ordinary fleet identity requiring service
         * @param systemId service location
         * @param serviceType refuel, rearm or repair order type
         */
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

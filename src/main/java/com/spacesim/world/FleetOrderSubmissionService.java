package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;

import java.util.Objects;

/** Single Stage-21D validation boundary used identically by player and AI order sources. */
public final class FleetOrderSubmissionService {
    private static final int MOVEMENT_PROPELLANT_MIN_BPS = 1;
    private static final int COMBAT_STRUCTURAL_MIN_BPS = 5_000;
    private static final int COMBAT_AMMUNITION_MIN_BPS = 1;
    private static final int COMBAT_CREW_MIN_BPS = 5_000;
    private static final int COMBAT_SENSOR_MIN_BPS = 1;

    private final FleetStrategicRoutePlanner routePlanner;

    public FleetOrderSubmissionService(FleetStrategicRoutePlanner routePlanner) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
    }

    public SubmissionResult submit(
            FleetCommandState state,
            FleetForceRegistry forces,
            long commandGroupId,
            OrderType type,
            OrderSource source,
            StarSystemId targetSystemId,
            long currentTick,
            FleetStrategicRoutePlanner.TransitAccessPolicy accessPolicy,
            ServiceCapabilityPolicy servicePolicy,
            StrategicRiskPolicy riskPolicy) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forces, "forces");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetSystemId, "targetSystemId");
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        Objects.requireNonNull(servicePolicy, "servicePolicy");
        Objects.requireNonNull(riskPolicy, "riskPolicy");
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");

        CommandGroupState group = state.requireGroup(commandGroupId);
        if (state.activeOrderFor(commandGroupId).isPresent()) {
            throw new IllegalStateException("command group already has an active order: " + commandGroupId);
        }
        if ((group.reserve() || group.homeDefense()) && type.offensiveOrder()
                && !group.homeSystemId().equals(targetSystemId)) {
            throw new IllegalStateException("reserve/home-defense group cannot execute offensive order away from home");
        }

        StarSystemId origin = null;
        for (FleetId fleetId : group.memberFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException("command group references missing FleetId: " + fleetId));
            if (force.factionId() != group.factionId()) {
                throw new IllegalStateException("command group contains fleet owned by another faction: " + fleetId);
            }
            if (force.locationKind() != FleetLocationKind.IN_SYSTEM || force.systemId() == null) {
                throw new IllegalStateException("fleet is already in transit or lacks a local placement: " + fleetId);
            }
            if (origin == null) origin = force.systemId();
            if (!origin.equals(force.systemId())) {
                throw new IllegalStateException("command group fleets must stage in one physical system before dispatch");
            }
            validateReadiness(type, force.readiness(), fleetId, !origin.equals(targetSystemId));
        }
        if (origin == null) throw new IllegalStateException("command group contains no fleets");

        FleetStrategicRoutePlanner.Route route = routePlanner.plan(
                        group.factionId(), origin, targetSystemId, currentTick, accessPolicy)
                .orElseThrow(() -> new IllegalStateException("no lawful neighbor-only route to target"));
        int routeRiskBps = riskPolicy.riskBps(group.factionId(), type, route, currentTick);
        if (routeRiskBps < 0 || routeRiskBps > FleetReadinessState.FULL) {
            throw new IllegalStateException("risk authority returned value outside 0..10000");
        }
        if (routeRiskBps > group.maxStrategicRiskBps()) {
            throw new IllegalStateException("route risk exceeds command-group doctrine ceiling");
        }

        ServiceCapability capability = servicePolicy.capability(group.factionId(), targetSystemId, currentTick);
        if (capability == null) capability = ServiceCapability.none();
        if (type.serviceOrder() && !capability.supports(type)) {
            throw new IllegalStateException("target lacks required Stage-18 service capability: " + type);
        }

        long travelTicks = Math.multiplyExact(route.hopCount(), Math.max(1L, capability.nominalHopTravelTicks()));
        long handlingTicks = Math.max(0L, capability.handlingTicks());
        long stagingDeadline = Math.addExact(currentTick, Math.addExact(travelTicks, handlingTicks));
        FleetOrderState order = new FleetOrderState(
                state.nextOrderId(), commandGroupId, type, source, targetSystemId, route.systems(), 0,
                currentTick, stagingDeadline,
                route.hopCount() == 0 && type.serviceOrder() ? OrderStatus.SERVICE_PENDING : OrderStatus.STAGING);
        return new SubmissionResult(state.addOrder(order), order);
    }

    private static void validateReadiness(OrderType type, FleetReadinessState readiness, FleetId fleetId, boolean movementRequired) {
        if (movementRequired && readiness.propellantBps() < MOVEMENT_PROPELLANT_MIN_BPS) {
            throw new IllegalStateException("fleet lacks reaction mass for movement: " + fleetId);
        }
        if (readiness.crewBps() <= 0) throw new IllegalStateException("fleet has no observed crew availability: " + fleetId);
        if (type.combatOrder() && (readiness.structuralBps() < COMBAT_STRUCTURAL_MIN_BPS
                || readiness.ammunitionBps() < COMBAT_AMMUNITION_MIN_BPS
                || readiness.crewBps() < COMBAT_CREW_MIN_BPS
                || readiness.sensorsBps() < COMBAT_SENSOR_MIN_BPS)) {
            throw new IllegalStateException("fleet is not physically ready for combat order: " + fleetId);
        }
        if (type.serviceOrder() && readiness.supplyAccessBps() <= 0) {
            throw new IllegalStateException("fleet has no observed supply/service access: " + fleetId);
        }
    }

    public record SubmissionResult(FleetCommandState state, FleetOrderState order) {
        public SubmissionResult { Objects.requireNonNull(state, "state"); Objects.requireNonNull(order, "order"); }
    }

    @FunctionalInterface
    public interface ServiceCapabilityPolicy {
        ServiceCapability capability(int factionId, StarSystemId systemId, long tick);
    }

    /** Read-only risk observation supplied by Stage-21A/19 awareness, never a combat authority. */
    @FunctionalInterface
    public interface StrategicRiskPolicy {
        int riskBps(int factionId, OrderType type, FleetStrategicRoutePlanner.Route route, long tick);
    }

    /** Read-only observation of existing Stage-18 handling/service capability. */
    public record ServiceCapability(boolean refuel, boolean rearm, boolean repair, long handlingTicks, long nominalHopTravelTicks) {
        public ServiceCapability {
            if (handlingTicks < 0L || nominalHopTravelTicks <= 0L) {
                throw new IllegalArgumentException("service timing must be non-negative and hop timing positive");
            }
        }
        public boolean supports(OrderType type) {
            return switch (type) { case REFUEL -> refuel; case REARM -> rearm; case REPAIR -> repair; default -> true; };
        }
        public static ServiceCapability none() { return new ServiceCapability(false, false, false, 0L, 1L); }
    }
}

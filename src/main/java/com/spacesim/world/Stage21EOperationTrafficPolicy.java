package com.spacesim.world;

import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationType;

import java.util.Objects;

/**
 * Stage-21E route/handling availability derived exclusively from physically active warfare forces.
 *
 * <p>This policy deliberately exposes binary physical availability rather than a remote percentage
 * penalty. It composes the Stage-19 {@link PhysicalWarfareOperationService}: a blockade matters only
 * while an ordinary combat-capable fleet is physically present in the blockaded system, and an
 * interdiction matters only while such a fleet is physically present at an endpoint of the exact
 * topology edge being evaluated.</p>
 */
public final class Stage21EOperationTrafficPolicy {
    private final PhysicalWarfareOperationService physicalWarfare;

    /**
     * Creates an operation traffic policy over the existing Stage-19 physical warfare authority.
     *
     * @param physicalWarfare existing read-only physical warfare resolver
     */
    public Stage21EOperationTrafficPolicy(PhysicalWarfareOperationService physicalWarfare) {
        this.physicalWarfare = Objects.requireNonNull(physicalWarfare, "physicalWarfare");
    }

    /**
     * Evaluates whether one concrete topology edge remains physically available to traffic.
     *
     * <p>Friendly traffic is never denied by its own operation through this generic policy. Enemy
     * blockade and interception operations are evaluated in canonical operation/participant order.
     * The method does not consume cargo, modify throughput, invent interception losses or alter the
     * route graph.</p>
     *
     * @param operations current persistent Stage-21E operations
     * @param from first edge endpoint
     * @param to second edge endpoint
     * @param trafficFactionId faction owning the traffic being evaluated
     * @return physical edge availability and the denying operation/fleet when applicable
     */
    public EdgeAvailability edgeAvailability(
            StrategicOperationState operations,
            StarSystemId from,
            StarSystemId to,
            int trafficFactionId) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (trafficFactionId < 0) {
            throw new IllegalArgumentException("trafficFactionId must be non-negative");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("traffic edge endpoints must differ");
        }

        for (OperationState operation : operations.operations()) {
            if (!operation.status().active() || operation.factionId() == trafficFactionId) {
                continue;
            }
            if (operation.type() == OperationType.BLOCKADE
                    && (operation.objectiveSystemId().equals(from) || operation.objectiveSystemId().equals(to))) {
                for (FleetId fleetId : operation.participantFleetIds()) {
                    PhysicalWarfareOperation blockade = PhysicalWarfareOperation.blockade(
                            fleetId, operation.objectiveSystemId());
                    if (physicalWarfare.isPhysicallyActive(blockade)) {
                        return new EdgeAvailability(
                                Availability.DENIED_BY_PHYSICAL_BLOCKADE, operation.id(), fleetId);
                    }
                }
            }
            if (operation.type() == OperationType.INTERCEPTION) {
                for (FleetId fleetId : operation.participantFleetIds()) {
                    PhysicalWarfareOperation interdiction = PhysicalWarfareOperation.interdict(fleetId, from, to);
                    if (physicalWarfare.isPhysicallyActive(interdiction)) {
                        return new EdgeAvailability(
                                Availability.DENIED_BY_PHYSICAL_INTERDICTION, operation.id(), fleetId);
                    }
                }
            }
        }
        return EdgeAvailability.available();
    }

    /**
     * Evaluates physical loading/unloading/service access inside one system.
     *
     * <p>This is the endpoint seam intended for Stage-20 freight/handling adapters. It does not
     * reduce handling by a percentage. An enemy blockade either has a currently physical combat
     * anchor and denies ordinary endpoint handling, or it has no effect.</p>
     *
     * @param operations current operation state
     * @param systemId endpoint system being handled
     * @param trafficFactionId faction owning the handled traffic
     * @return endpoint handling availability
     */
    public HandlingAvailability handlingAvailability(
            StrategicOperationState operations,
            StarSystemId systemId,
            int trafficFactionId) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(systemId, "systemId");
        if (trafficFactionId < 0) {
            throw new IllegalArgumentException("trafficFactionId must be non-negative");
        }
        for (OperationState operation : operations.operations()) {
            if (!operation.status().active()
                    || operation.factionId() == trafficFactionId
                    || operation.type() != OperationType.BLOCKADE
                    || !operation.objectiveSystemId().equals(systemId)) {
                continue;
            }
            for (FleetId fleetId : operation.participantFleetIds()) {
                if (physicalWarfare.isPhysicallyActive(PhysicalWarfareOperation.blockade(fleetId, systemId))) {
                    return new HandlingAvailability(false, operation.id(), fleetId);
                }
            }
        }
        return HandlingAvailability.available();
    }

    /** Physical traffic availability categories; no percentage modifier exists. */
    public enum Availability {
        /** No physically active hostile operation denies the edge. */ AVAILABLE,
        /** A hostile ordinary fleet physically maintains a blockade at an endpoint. */ DENIED_BY_PHYSICAL_BLOCKADE,
        /** A hostile ordinary fleet physically interdicts this exact topology edge. */ DENIED_BY_PHYSICAL_INTERDICTION
    }

    /**
     * Result for one exact topology edge.
     *
     * @param availability physical availability category
     * @param denyingOperationId denying Stage-21E operation, or 0 when available
     * @param denyingFleetId ordinary denying fleet, or null when available
     */
    public record EdgeAvailability(
            Availability availability,
            long denyingOperationId,
            FleetId denyingFleetId) {
        /** Validates available/denied identity invariants. */
        public EdgeAvailability {
            Objects.requireNonNull(availability, "availability");
            if (availability == Availability.AVAILABLE) {
                if (denyingOperationId != 0L || denyingFleetId != null) {
                    throw new IllegalArgumentException("available edge cannot have a denying operation");
                }
            } else if (denyingOperationId <= 0L || denyingFleetId == null) {
                throw new IllegalArgumentException("denied edge requires physical operation and FleetId");
            }
        }

        /** @return true only when ordinary traffic may use the edge */
        public boolean allowsTraffic() {
            return availability == Availability.AVAILABLE;
        }

        /** @return canonical available result */
        public static EdgeAvailability available() {
            return new EdgeAvailability(Availability.AVAILABLE, 0L, null);
        }
    }

    /**
     * Result for physical endpoint handling.
     *
     * @param available whether handling remains physically available
     * @param denyingOperationId denying blockade operation, or 0 when available
     * @param denyingFleetId ordinary denying fleet, or null when available
     */
    public record HandlingAvailability(boolean available, long denyingOperationId, FleetId denyingFleetId) {
        /** Validates available/denied identity invariants. */
        public HandlingAvailability {
            if (available) {
                if (denyingOperationId != 0L || denyingFleetId != null) {
                    throw new IllegalArgumentException("available handling cannot have a denying operation");
                }
            } else if (denyingOperationId <= 0L || denyingFleetId == null) {
                throw new IllegalArgumentException("denied handling requires physical operation and FleetId");
            }
        }

        /** @return canonical available handling result */
        public static HandlingAvailability available() {
            return new HandlingAvailability(true, 0L, null);
        }
    }
}

package com.spacesim.world;

import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationType;

import java.util.Objects;

/**
 * Stage-21E route/handling availability derived only from physically active Stage-19 warfare forces.
 * No remote percentage modifier is authored here.
 */
public final class Stage21EOperationTrafficPolicy {
    private final PhysicalWarfareOperationService physicalWarfare;

    /** @param physicalWarfare existing Stage-19 physical warfare resolver */
    public Stage21EOperationTrafficPolicy(PhysicalWarfareOperationService physicalWarfare) {
        this.physicalWarfare = Objects.requireNonNull(physicalWarfare, "physicalWarfare");
    }

    /**
     * Evaluates one concrete topology edge for ordinary traffic.
     *
     * @param operations current persistent strategic operations
     * @param from first concrete topology-edge endpoint
     * @param to second concrete topology-edge endpoint
     * @param trafficFactionId faction owning the traffic being evaluated
     * @return physical edge availability and denying physical force identity when blocked
     */
    public EdgeAvailability edgeAvailability(
            StrategicOperationState operations,
            StarSystemId from,
            StarSystemId to,
            int trafficFactionId) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (trafficFactionId < 0) throw new IllegalArgumentException("trafficFactionId must be non-negative");
        if (from.equals(to)) throw new IllegalArgumentException("traffic edge endpoints must differ");

        for (OperationState operation : operations.operations()) {
            if (!operation.status().active() || operation.factionId() == trafficFactionId) continue;
            if (operation.type() == OperationType.BLOCKADE
                    && (operation.objectiveSystemId().equals(from) || operation.objectiveSystemId().equals(to))) {
                for (FleetId fleetId : operation.participantFleetIds()) {
                    if (physicalWarfare.isPhysicallyActive(
                            PhysicalWarfareOperation.blockade(fleetId, operation.objectiveSystemId()))) {
                        return new EdgeAvailability(
                                Availability.DENIED_BY_PHYSICAL_BLOCKADE, operation.id(), fleetId);
                    }
                }
            }
            if (operation.type() == OperationType.INTERCEPTION) {
                for (FleetId fleetId : operation.participantFleetIds()) {
                    if (physicalWarfare.isPhysicallyActive(PhysicalWarfareOperation.interdict(fleetId, from, to))) {
                        return new EdgeAvailability(
                                Availability.DENIED_BY_PHYSICAL_INTERDICTION, operation.id(), fleetId);
                    }
                }
            }
        }
        return EdgeAvailability.available();
    }

    /**
     * Evaluates physical loading, unloading and service handling inside one system.
     *
     * @param operations current persistent strategic operations
     * @param systemId concrete endpoint system being evaluated
     * @param trafficFactionId faction owning the traffic being handled
     * @return physical endpoint handling availability and denying blockade identity when blocked
     */
    public HandlingAvailability handlingAvailability(
            StrategicOperationState operations,
            StarSystemId systemId,
            int trafficFactionId) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(systemId, "systemId");
        if (trafficFactionId < 0) throw new IllegalArgumentException("trafficFactionId must be non-negative");
        for (OperationState operation : operations.operations()) {
            if (!operation.status().active()
                    || operation.factionId() == trafficFactionId
                    || operation.type() != OperationType.BLOCKADE
                    || !operation.objectiveSystemId().equals(systemId)) continue;
            for (FleetId fleetId : operation.participantFleetIds()) {
                if (physicalWarfare.isPhysicallyActive(PhysicalWarfareOperation.blockade(fleetId, systemId))) {
                    return new HandlingAvailability(false, operation.id(), fleetId);
                }
            }
        }
        return HandlingAvailability.open();
    }

    /** Physical edge availability; there is intentionally no fractional form. */
    public enum Availability {
        /** No physically active hostile operation denies the edge. */ AVAILABLE,
        /** A hostile physical blockade denies an endpoint. */ DENIED_BY_PHYSICAL_BLOCKADE,
        /** A hostile physical force interdicts the exact edge. */ DENIED_BY_PHYSICAL_INTERDICTION
    }

    /**
     * Physical availability result for one exact topology edge.
     *
     * @param availability physical availability category
     * @param denyingOperationId denying operation identity, or zero when available
     * @param denyingFleetId ordinary denying FleetId, or null when available
     */
    public record EdgeAvailability(Availability availability, long denyingOperationId, FleetId denyingFleetId) {
        /**
         * Validates available/denied identity invariants.
         *
         * @param availability physical availability category
         * @param denyingOperationId denying operation identity, or zero when available
         * @param denyingFleetId ordinary denying FleetId, or null when available
         */
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

        /** @return whether ordinary traffic may use the edge */
        public boolean allowsTraffic() { return availability == Availability.AVAILABLE; }

        /** @return canonical physically available edge result */
        public static EdgeAvailability available() { return new EdgeAvailability(Availability.AVAILABLE, 0L, null); }
    }

    /**
     * Physical handling result for one concrete system endpoint.
     *
     * @param available whether ordinary endpoint handling remains physically available
     * @param denyingOperationId denying blockade operation identity, or zero when available
     * @param denyingFleetId ordinary denying FleetId, or null when available
     */
    public record HandlingAvailability(boolean available, long denyingOperationId, FleetId denyingFleetId) {
        /**
         * Validates available/denied identity invariants.
         *
         * @param available whether ordinary endpoint handling remains physically available
         * @param denyingOperationId denying blockade operation identity, or zero when available
         * @param denyingFleetId ordinary denying FleetId, or null when available
         */
        public HandlingAvailability {
            if (available) {
                if (denyingOperationId != 0L || denyingFleetId != null) {
                    throw new IllegalArgumentException("available handling cannot have a denying operation");
                }
            } else if (denyingOperationId <= 0L || denyingFleetId == null) {
                throw new IllegalArgumentException("denied handling requires physical operation and FleetId");
            }
        }

        /** @return canonical physically open handling result */
        public static HandlingAvailability open() { return new HandlingAvailability(true, 0L, null); }
    }
}

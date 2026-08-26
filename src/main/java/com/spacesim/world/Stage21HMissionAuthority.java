package com.spacesim.world;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;

import java.util.Objects;

/**
 * Read-only Stage-21H mission-objective adapter over ordinary simulation authorities.
 *
 * <p>No caller supplies a boolean completion flag. The adapter reads the same Stage-20 physical
 * freight, fleet, construction, diplomacy, economy, discovery and Stage-21E operation state used by
 * the simulation and returns an observation that the mission lifecycle may consume.</p>
 */
public final class Stage21HMissionAuthority {
    private Stage21HMissionAuthority() {
        throw new AssertionError("No instances");
    }

    /** Objective observation result. */
    public enum Result {
        /** Ordinary authoritative state currently satisfies the predicate. */ SATISFIED,
        /** Predicate remains possible but is not yet satisfied. */ PENDING,
        /** Referenced authoritative target disappeared or became terminal incompatibly. */ FAILED
    }

    /**
     * One read-only authority observation.
     *
     * @param result predicate result
     * @param authorityCode bounded diagnostic code
     * @param observedTick authoritative world tick
     */
    public record Observation(Result result, String authorityCode, long observedTick) {
        /** Validates one authority observation. */
        public Observation {
            Objects.requireNonNull(result, "Mission authority result not set");
            authorityCode = requireText(authorityCode, "Mission authority code");
            if (observedTick < 0L) {
                throw new IllegalArgumentException("Mission authority observation tick cannot be negative");
            }
        }
    }

    /**
     * Evaluates one objective from existing authorities only.
     *
     * @param world ordinary live world authority
     * @param freight Stage-20 physical freight/order authority when required
     * @param discovery owner-local Stage-20 discovery knowledge for the mission issuer
     * @param operations accepted Stage-21E operation registry
     * @param objective declarative mission predicate
     * @return current deterministic objective observation
     */
    public static Observation evaluate(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage20DiscoveryKnowledgeState discovery,
            StrategicOperationState operations,
            MissionObjective objective) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        MissionObjective checked = Objects.requireNonNull(objective, "Mission objective not set");
        long tick = checkedWorld.getAuthoritativeWorldTick();
        return switch (checked.kind()) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST -> freightDelivered(freight, checked, tick);
            case FLEET_PRESENT_IN_SYSTEM -> fleetPresent(checkedWorld, checked, tick);
            case FLEET_ABSENT -> fleetAbsent(checkedWorld, checked, tick);
            case DISCOVERY_AT_LEAST -> discoveryAtLeast(discovery, checked, tick);
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST -> constructionDelivered(checkedWorld, checked, tick);
            case CONSTRUCTION_COMPLETED -> constructionCompleted(checkedWorld, checked, tick);
            case MARKET_ACCESS_ALLOWED -> marketAccess(checkedWorld, checked, tick);
            case OPERATION_STATUS -> operationStatus(operations, checked, tick);
            case FACTION_TREASURY_AT_LEAST -> treasuryAtLeast(checkedWorld, checked, tick);
        };
    }

    private static Observation freightDelivered(
            Stage20FreightPersistentState freight,
            MissionObjective objective,
            long tick) {
        Stage20FreightPersistentState checked = Objects.requireNonNull(
                freight, "Freight state required by physical delivery mission");
        TransportOrderState order = checked.orders().stream()
                .filter(value -> value.orderId().equals(objective.subjectId()))
                .findFirst().orElse(null);
        if (order == null) {
            return new Observation(Result.FAILED, "freight.order-missing", tick);
        }
        if (order.deliveredMassKg() >= objective.threshold()) {
            return new Observation(Result.SATISFIED, "freight.delivery-satisfied", tick);
        }
        FreighterState freighter = checked.freighters().stream()
                .filter(value -> value.fleetId().equals(order.fleetId()))
                .findFirst().orElse(null);
        if (freighter == null || freighter.phase() == FreightPhase.DESTROYED) {
            return new Observation(Result.FAILED, "freight.delivery-capability-lost", tick);
        }
        return new Observation(Result.PENDING, "freight.delivery-pending", tick);
    }

    private static Observation fleetPresent(WorldSimulation world, MissionObjective objective, long tick) {
        FleetId fleetId = new FleetId(parsePositiveLong(objective.subjectId(), "FleetId"));
        FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
        if (placement == null) {
            return new Observation(Result.FAILED, "fleet.missing", tick);
        }
        boolean present = placement.locationKind() == FleetLocationKind.IN_SYSTEM
                && placement.systemId().value() == objective.systemId();
        return new Observation(present ? Result.SATISFIED : Result.PENDING,
                present ? "fleet.present.target-system" : "fleet.present.other-location", tick);
    }

    private static Observation fleetAbsent(WorldSimulation world, MissionObjective objective, long tick) {
        FleetId fleetId = new FleetId(parsePositiveLong(objective.subjectId(), "FleetId"));
        boolean absent = world.findFleet(fleetId).isEmpty();
        return new Observation(absent ? Result.SATISFIED : Result.PENDING,
                absent ? "fleet.absent" : "fleet.still-present", tick);
    }

    private static Observation discoveryAtLeast(
            Stage20DiscoveryKnowledgeState discovery,
            MissionObjective objective,
            long tick) {
        Stage20DiscoveryKnowledgeState checked = Objects.requireNonNull(
                discovery, "Discovery knowledge required by discovery mission");
        String[] stateParts = objective.requiredState().split(":", -1);
        if (stateParts.length != 2) {
            throw new IllegalArgumentException("Discovery objective state must be KIND:STATE");
        }
        StaticObjectKind kind = StaticObjectKind.valueOf(stateParts[0]);
        DiscoveryState required = DiscoveryState.valueOf(stateParts[1]);
        if (required == DiscoveryState.TRACKED) {
            throw new IllegalArgumentException("Static discovery mission cannot request mobile TRACKED state");
        }
        StaticObjectRef ref = new StaticObjectRef(
                new StarSystemId(objective.systemId()), kind, objective.subjectId());
        DiscoveryState actual = checked.discoveryState(ref);
        boolean satisfied = staticRank(actual) >= staticRank(required);
        return new Observation(satisfied ? Result.SATISFIED : Result.PENDING,
                "discovery." + actual.name().toLowerCase(), tick);
    }

    private static Observation constructionDelivered(
            WorldSimulation world,
            MissionObjective objective,
            long tick) {
        ConstructionProjectId id = new ConstructionProjectId(
                parsePositiveLong(objective.subjectId(), "Construction project ID"));
        ConstructionProjectState project = world.findConstructionProject(id).orElse(null);
        if (project == null) {
            return new Observation(Result.FAILED, "construction.missing", tick);
        }
        if (project.status() == ConstructionProjectStatus.CANCELLED
                || project.status() == ConstructionProjectStatus.FAILED) {
            return new Observation(Result.FAILED, "construction.terminal-failure", tick);
        }
        boolean satisfied = project.totalDeliveredUnits() >= objective.threshold();
        return new Observation(satisfied ? Result.SATISFIED : Result.PENDING,
                satisfied ? "construction.delivery-satisfied" : "construction.delivery-pending", tick);
    }

    private static Observation constructionCompleted(
            WorldSimulation world,
            MissionObjective objective,
            long tick) {
        ConstructionProjectId id = new ConstructionProjectId(
                parsePositiveLong(objective.subjectId(), "Construction project ID"));
        ConstructionProjectState project = world.findConstructionProject(id).orElse(null);
        if (project == null) {
            return new Observation(Result.FAILED, "construction.missing", tick);
        }
        if (project.status() == ConstructionProjectStatus.COMPLETED) {
            return new Observation(Result.SATISFIED, "construction.completed", tick);
        }
        if (project.status() == ConstructionProjectStatus.CANCELLED
                || project.status() == ConstructionProjectStatus.FAILED) {
            return new Observation(Result.FAILED, "construction.terminal-failure", tick);
        }
        return new Observation(Result.PENDING, "construction.pending", tick);
    }

    private static Observation marketAccess(WorldSimulation world, MissionObjective objective, long tick) {
        boolean allowed = world.evaluateFactionMarketAccess(
                objective.subjectId(), objective.requiredState()).allowed();
        return new Observation(allowed ? Result.SATISFIED : Result.PENDING,
                allowed ? "diplomacy.market-access-allowed" : "diplomacy.market-access-denied", tick);
    }

    private static Observation operationStatus(
            StrategicOperationState operations,
            MissionObjective objective,
            long tick) {
        StrategicOperationState checked = Objects.requireNonNull(
                operations, "Operation state required by operation mission");
        long operationId = parsePositiveLong(objective.subjectId(), "Operation ID");
        OperationState operation = checked.operations().stream()
                .filter(value -> value.id() == operationId)
                .findFirst().orElse(null);
        if (operation == null) {
            return new Observation(Result.FAILED, "operation.missing", tick);
        }
        OperationStatus required = OperationStatus.valueOf(objective.requiredState());
        if (operation.status() == required) {
            return new Observation(Result.SATISFIED, "operation." + required.name().toLowerCase(), tick);
        }
        if (!operation.status().active()) {
            return new Observation(Result.FAILED, "operation.terminal-" + operation.status().name().toLowerCase(), tick);
        }
        return new Observation(Result.PENDING, "operation." + operation.status().name().toLowerCase(), tick);
    }

    private static Observation treasuryAtLeast(WorldSimulation world, MissionObjective objective, long tick) {
        FactionEconomicState economy = world.findFactionEconomicState(objective.subjectId()).orElse(null);
        if (economy == null) {
            return new Observation(Result.FAILED, "economy.faction-missing", tick);
        }
        boolean satisfied = economy.treasuryMilliCredits() >= objective.threshold();
        return new Observation(satisfied ? Result.SATISFIED : Result.PENDING,
                satisfied ? "economy.treasury-threshold-satisfied" : "economy.treasury-threshold-pending", tick);
    }

    private static int staticRank(DiscoveryState state) {
        return switch (state) {
            case UNKNOWN -> 0;
            case DETECTED -> 1;
            case CLASSIFIED -> 2;
            case KNOWN_STATIC_LOCATION -> 3;
            case TRACKED -> throw new IllegalArgumentException("TRACKED is not static discovery state");
        };
    }

    private static long parsePositiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(requireText(value, label));
            if (parsed <= 0L) {
                throw new IllegalArgumentException(label + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a positive numeric identity", exception);
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}

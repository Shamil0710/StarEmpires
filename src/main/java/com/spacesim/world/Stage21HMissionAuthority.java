package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
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
 * <p>No caller supplies a boolean completion flag. The adapter reads the same Stage-18 finite
 * salvage, Stage-20 physical freight/discovery, ordinary fleet/construction/diplomacy/economy and
 * Stage-21E operation state used by the simulation and returns bounded observations for the mission
 * lifecycle. Issuer validation is likewise read-only and never grants ownership or access.</p>
 */
public final class Stage21HMissionAuthority {
    private static final String REACTION_MASS = "REACTION_MASS";

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
        /**
         * Validates one authority observation.
         *
         * @param result predicate result
         * @param authorityCode bounded diagnostic code
         * @param observedTick authoritative world tick
         */
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
     * @param industry Stage-18 finite industrial/salvage authority when required
     * @param discovery owner-local Stage-20 discovery knowledge for the mission issuer
     * @param operations accepted Stage-21E operation registry
     * @param objective declarative mission predicate
     * @return current deterministic objective observation
     */
    public static Observation evaluate(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage18IndustrialState industry,
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
            case ESCORT_FLEETS_PRESENT_IN_SYSTEM -> escortFleetsPresent(checkedWorld, checked, tick);
            case FLEET_REACTION_MASS_KG_AT_LEAST -> reactionMassAtLeast(checkedWorld, checked, tick);
            case DISCOVERY_AT_LEAST -> discoveryAtLeast(discovery, checked, tick);
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST ->
                    derelictRecovered(industry, discovery, checked, tick);
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST -> constructionDelivered(checkedWorld, checked, tick);
            case CONSTRUCTION_COMPLETED -> constructionCompleted(checkedWorld, checked, tick);
            case MARKET_ACCESS_ALLOWED -> marketAccess(checkedWorld, checked, tick);
            case OPERATION_STATUS -> operationStatus(operations, checked, tick);
            case FACTION_TREASURY_AT_LEAST -> treasuryAtLeast(checkedWorld, checked, tick);
        };
    }

    /**
     * Verifies that a prospective issuer lawfully controls or participates in the target authority.
     *
     * <p>Failure means the NPC may not create the offer. The check never makes an absent target real;
     * it is intended to run immediately before the initial objective observation.</p>
     *
     * @param world ordinary world/faction authority
     * @param freight Stage-20 physical freight authority
     * @param industry Stage-18 finite industrial authority
     * @param discovery issuer-local discovery knowledge
     * @param operations Stage-21E operation state
     * @param issuerFactionId stable issuing faction
     * @param objective proposed objective
     */
    public static void requireIssuerAuthority(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage18IndustrialState industry,
            Stage20DiscoveryKnowledgeState discovery,
            StrategicOperationState operations,
            String issuerFactionId,
            MissionObjective objective) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        String issuer = requireText(issuerFactionId, "Issuer faction");
        MissionObjective checked = Objects.requireNonNull(objective, "Mission objective not set");
        switch (checked.kind()) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST -> {
                TransportOrderState order = requireFreightOrder(freight, checked.subjectId());
                if (!issuer.equals(order.stableFactionId())) {
                    throw new IllegalStateException("Mission issuer does not own referenced freight order");
                }
            }
            case FLEET_PRESENT_IN_SYSTEM, FLEET_ABSENT, FLEET_REACTION_MASS_KG_AT_LEAST ->
                    requireFleetOwner(checkedWorld, checked.subjectId(), issuer);
            case ESCORT_FLEETS_PRESENT_IN_SYSTEM -> {
                requireDistinctEscortFleets(checked);
                requireFleetOwner(checkedWorld, checked.subjectId(), issuer);
            }
            case DISCOVERY_AT_LEAST -> requireDiscoveryOwner(discovery, issuer);
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST -> {
                requireDiscoveryOwner(discovery, issuer);
                String[] subjects = derelictSubjects(checked.subjectId());
                PhysicalSourceSnapshot source = requireIndustrialSource(industry, subjects[1]);
                if (source.sourceKind() != SourceKind.SALVAGE_STREAM) {
                    throw new IllegalStateException("Derelict recovery objective must reference a finite salvage stream");
                }
            }
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST, CONSTRUCTION_COMPLETED -> {
                ConstructionProjectState project = requireConstruction(checkedWorld, checked.subjectId());
                boolean issuerOwns = issuer.equals(project.ownerFactionContentId())
                        || issuer.equals(project.legalFactionContentId());
                if (!issuerOwns) {
                    throw new IllegalStateException("Mission issuer does not control referenced construction project");
                }
            }
            case MARKET_ACCESS_ALLOWED -> {
                if (!issuer.equals(checked.requiredState())) {
                    throw new IllegalStateException("Access mission issuer must be the participant seeking legal access");
                }
            }
            case OPERATION_STATUS -> {
                OperationState operation = requireOperation(operations, checked.subjectId());
                String owner = checkedWorld.findFactionStableId(operation.factionId()).orElseThrow(
                        () -> new IllegalStateException("Referenced operation has unknown stable faction owner"));
                if (!issuer.equals(owner)) {
                    throw new IllegalStateException("Mission issuer does not own referenced strategic operation");
                }
            }
            case FACTION_TREASURY_AT_LEAST -> {
                if (!issuer.equals(checked.subjectId())) {
                    throw new IllegalStateException("Economic mission issuer may inspect only its own treasury objective");
                }
            }
        }
    }

    private static Observation freightDelivered(
            Stage20FreightPersistentState freight,
            MissionObjective objective,
            long tick) {
        TransportOrderState order = findFreightOrder(freight, objective.subjectId());
        if (order == null) {
            return new Observation(Result.FAILED, "freight.order-missing", tick);
        }
        if (order.deliveredMassKg() >= objective.threshold()) {
            return new Observation(Result.SATISFIED, "freight.delivery-satisfied", tick);
        }
        FreighterState freighter = Objects.requireNonNull(freight, "Freight state required by delivery mission")
                .freighters().stream()
                .filter(value -> value.fleetId().equals(order.fleetId()))
                .findFirst().orElse(null);
        if (freighter == null || freighter.phase() == FreightPhase.DESTROYED) {
            return new Observation(Result.FAILED, "freight.delivery-capability-lost", tick);
        }
        return new Observation(Result.PENDING, "freight.delivery-pending", tick);
    }

    private static Observation fleetPresent(WorldSimulation world, MissionObjective objective, long tick) {
        FleetPlacementState placement = world.findFleet(fleetId(objective.subjectId())).orElse(null);
        if (placement == null) {
            return new Observation(Result.FAILED, "fleet.missing", tick);
        }
        boolean present = inSystem(placement, objective.systemId());
        return new Observation(present ? Result.SATISFIED : Result.PENDING,
                present ? "fleet.present.target-system" : "fleet.present.other-location", tick);
    }

    private static Observation fleetAbsent(WorldSimulation world, MissionObjective objective, long tick) {
        boolean absent = world.findFleet(fleetId(objective.subjectId())).isEmpty();
        return new Observation(absent ? Result.SATISFIED : Result.PENDING,
                absent ? "fleet.absent" : "fleet.still-present", tick);
    }

    private static Observation escortFleetsPresent(
            WorldSimulation world,
            MissionObjective objective,
            long tick) {
        requireDistinctEscortFleets(objective);
        FleetPlacementState convoy = world.findFleet(fleetId(objective.subjectId())).orElse(null);
        FleetPlacementState escort = world.findFleet(fleetId(objective.requiredState())).orElse(null);
        if (convoy == null) {
            return new Observation(Result.FAILED, "escort.convoy-missing", tick);
        }
        if (escort == null) {
            return new Observation(Result.FAILED, "escort.contract-fleet-missing", tick);
        }
        boolean present = inSystem(convoy, objective.systemId()) && inSystem(escort, objective.systemId());
        return new Observation(present ? Result.SATISFIED : Result.PENDING,
                present ? "escort.copresence-arrival-satisfied" : "escort.copresence-pending", tick);
    }

    private static Observation reactionMassAtLeast(
            WorldSimulation world,
            MissionObjective objective,
            long tick) {
        EntityState fleet = fleetEntity(world, fleetId(objective.subjectId()));
        if (fleet == null) {
            return new Observation(Result.FAILED, "refuel.fleet-missing", tick);
        }
        if (fleet.engineering() == null || fleet.engineering().consumables() == null) {
            return new Observation(Result.FAILED, "refuel.engineering-state-missing", tick);
        }
        double reactionMassKg = fleet.engineering().consumables().interfaceLoads().stream()
                .filter(load -> REACTION_MASS.equals(load.kindName()))
                .mapToDouble(EntityState.EngineeringConsumableLoadState::massKg)
                .sum();
        boolean satisfied = reactionMassKg + 1e-9d >= objective.threshold();
        return new Observation(satisfied ? Result.SATISFIED : Result.PENDING,
                satisfied ? "refuel.reaction-mass-satisfied" : "refuel.reaction-mass-pending", tick);
    }

    private static Observation discoveryAtLeast(
            Stage20DiscoveryKnowledgeState discovery,
            MissionObjective objective,
            long tick) {
        Stage20DiscoveryKnowledgeState checked = Objects.requireNonNull(
                discovery, "Discovery knowledge required by discovery mission");
        String[] stateParts = discoveryState(objective.requiredState());
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

    private static Observation derelictRecovered(
            Stage18IndustrialState industry,
            Stage20DiscoveryKnowledgeState discovery,
            MissionObjective objective,
            long tick) {
        String[] subjects = derelictSubjects(objective.subjectId());
        String[] stateParts = discoveryState(objective.requiredState());
        StaticObjectKind kind = StaticObjectKind.valueOf(stateParts[0]);
        DiscoveryState required = DiscoveryState.valueOf(stateParts[1]);
        if (kind != StaticObjectKind.SPECIAL_LOCATION || required == DiscoveryState.TRACKED) {
            throw new IllegalArgumentException("Derelict objective requires static SPECIAL_LOCATION knowledge");
        }
        Stage20DiscoveryKnowledgeState checkedDiscovery = Objects.requireNonNull(
                discovery, "Discovery knowledge required by derelict mission");
        DiscoveryState actual = checkedDiscovery.discoveryState(new StaticObjectRef(
                new StarSystemId(objective.systemId()), kind, subjects[0]));
        PhysicalSourceSnapshot source = findIndustrialSource(industry, subjects[1]);
        if (source == null || source.sourceKind() != SourceKind.SALVAGE_STREAM) {
            return new Observation(Result.FAILED, "salvage.source-missing", tick);
        }
        double recoveredKg = Math.max(0d, source.initialAccessibleMassKg() - source.remainingAccessibleMassKg());
        boolean discovered = staticRank(actual) >= staticRank(required);
        boolean recovered = recoveredKg + 1e-9d >= objective.threshold();
        if (discovered && recovered) {
            return new Observation(Result.SATISFIED, "salvage.discovery-and-recovery-satisfied", tick);
        }
        if (source.remainingAccessibleMassKg() <= 1e-9d && !recovered) {
            return new Observation(Result.FAILED, "salvage.required-mass-no-longer-available", tick);
        }
        return new Observation(Result.PENDING,
                discovered ? "salvage.recovery-pending" : "salvage.discovery-pending", tick);
    }

    private static Observation constructionDelivered(
            WorldSimulation world,
            MissionObjective objective,
            long tick) {
        ConstructionProjectState project = findConstruction(world, objective.subjectId());
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
        ConstructionProjectState project = findConstruction(world, objective.subjectId());
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
        OperationState operation = findOperation(operations, objective.subjectId());
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

    private static TransportOrderState requireFreightOrder(Stage20FreightPersistentState freight, String orderId) {
        TransportOrderState order = findFreightOrder(freight, orderId);
        if (order == null) {
            throw new IllegalStateException("Mission references missing physical freight order: " + orderId);
        }
        return order;
    }

    private static TransportOrderState findFreightOrder(Stage20FreightPersistentState freight, String orderId) {
        Stage20FreightPersistentState checked = Objects.requireNonNull(
                freight, "Freight state required by physical delivery mission");
        return checked.orders().stream().filter(value -> value.orderId().equals(orderId)).findFirst().orElse(null);
    }

    private static PhysicalSourceSnapshot requireIndustrialSource(Stage18IndustrialState industry, String sourceId) {
        PhysicalSourceSnapshot source = findIndustrialSource(industry, sourceId);
        if (source == null) {
            throw new IllegalStateException("Mission references missing finite industrial source: " + sourceId);
        }
        return source;
    }

    private static PhysicalSourceSnapshot findIndustrialSource(Stage18IndustrialState industry, String sourceId) {
        Stage18IndustrialState checked = Objects.requireNonNull(
                industry, "Industrial state required by salvage mission");
        return checked.sources().stream().filter(value -> value.sourceId().equals(sourceId)).findFirst().orElse(null);
    }

    private static ConstructionProjectState requireConstruction(WorldSimulation world, String value) {
        ConstructionProjectState project = findConstruction(world, value);
        if (project == null) {
            throw new IllegalStateException("Mission references missing construction project: " + value);
        }
        return project;
    }

    private static ConstructionProjectState findConstruction(WorldSimulation world, String value) {
        return world.findConstructionProject(new ConstructionProjectId(parsePositiveLong(value, "Construction project ID")))
                .orElse(null);
    }

    private static OperationState requireOperation(StrategicOperationState operations, String value) {
        OperationState operation = findOperation(operations, value);
        if (operation == null) {
            throw new IllegalStateException("Mission references missing strategic operation: " + value);
        }
        return operation;
    }

    private static OperationState findOperation(StrategicOperationState operations, String value) {
        StrategicOperationState checked = Objects.requireNonNull(
                operations, "Operation state required by operation mission");
        long operationId = parsePositiveLong(value, "Operation ID");
        return checked.operations().stream().filter(operation -> operation.id() == operationId).findFirst().orElse(null);
    }

    private static void requireFleetOwner(WorldSimulation world, String fleetValue, String issuer) {
        FleetId id = fleetId(fleetValue);
        EntityState entity = fleetEntity(world, id);
        if (entity == null) {
            throw new IllegalStateException("Mission references missing ordinary fleet: " + id);
        }
        if (entity.faction() == null) {
            throw new IllegalStateException("Mission target fleet has no ordinary faction ownership: " + id);
        }
        String owner = world.findFactionStableId(entity.faction().factionId()).orElseThrow(
                () -> new IllegalStateException("Mission target fleet has unknown stable faction ownership: " + id));
        if (!issuer.equals(owner)) {
            throw new IllegalStateException("Mission issuer does not own referenced fleet: " + id);
        }
    }

    private static EntityState fleetEntity(WorldSimulation world, FleetId fleetId) {
        WorldState snapshot = world.snapshot();
        FleetPlacementState placement = snapshot.fleets().stream()
                .filter(value -> value.id().equals(fleetId))
                .findFirst().orElse(null);
        if (placement == null) {
            return null;
        }
        if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
            return placement.transitState() == null ? null : placement.transitState().entityState();
        }
        if (placement.systemId() == null || placement.localEntityId() == null) {
            return null;
        }
        return snapshot.systems().stream()
                .filter(system -> system.systemId().equals(placement.systemId()))
                .flatMap(system -> system.simulationState().entities().stream())
                .filter(entity -> entity.id().equals(placement.localEntityId()))
                .findFirst().orElse(null);
    }

    private static boolean inSystem(FleetPlacementState placement, long systemId) {
        return placement.locationKind() == FleetLocationKind.IN_SYSTEM
                && placement.systemId() != null
                && placement.systemId().value() == systemId;
    }

    private static FleetId fleetId(String value) {
        return new FleetId(parsePositiveLong(value, "FleetId"));
    }

    private static void requireDistinctEscortFleets(MissionObjective objective) {
        if (objective.subjectId().equals(objective.requiredState())) {
            throw new IllegalArgumentException("Escort convoy and contracted escort FleetIds must differ");
        }
    }

    private static void requireDiscoveryOwner(Stage20DiscoveryKnowledgeState discovery, String issuer) {
        Stage20DiscoveryKnowledgeState checked = Objects.requireNonNull(
                discovery, "Discovery knowledge required by discovery mission");
        if (!issuer.equals(checked.ownerId())) {
            throw new IllegalStateException("Mission issuer does not own supplied Stage-20 discovery knowledge");
        }
    }

    private static String[] discoveryState(String value) {
        String[] parts = requireText(value, "Discovery state").split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Discovery objective state must be KIND:STATE");
        }
        return parts;
    }

    private static String[] derelictSubjects(String value) {
        String[] parts = requireText(value, "Derelict objective subject").split("\\|", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Derelict objective subject must be staticObjectId|salvageSourceId");
        }
        return parts;
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

package com.spacesim.world;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.StrategicOperationState.OperationState;

import java.util.Objects;

/**
 * Read-only Stage-21H contractor-participation authority for player-facing mission settlement.
 *
 * <p>The ordinary mission predicate answers whether the living world reached an outcome. That is
 * intentionally insufficient to pay a player contract: an autonomous faction or unrelated actor may
 * have solved the same shortage, escorted the same convoy or completed the same operation. This
 * adapter proves bounded player participation only from existing persistent player ownership,
 * owner-local Stage-20 discovery and ordinary FleetId/construction/operation state. It never accepts
 * a caller-provided completion boolean and never mutates world state.</p>
 *
 * <p>Some physical authorities do not yet retain exclusive per-actor causation for every kilogram or
 * repair unit. For those cases this class requires conservative participation evidence (for example,
 * the assigned freight FleetId must be player-owned, or a player-owned fleet must be physically
 * present at a rescue/salvage site). Unsupported or ambiguous cases fail closed.</p>
 */
public final class Stage21HPlayerMissionAuthority {
    /** Stable Stage-21H social/discovery identity for the human player actor. */
    public static final String PLAYER_ACTOR_ID = "actor.player";

    private Stage21HPlayerMissionAuthority() {
        throw new AssertionError("No instances");
    }

    /** Contractor-participation result. */
    public enum Result {
        /** Existing authorities prove bounded participation by the player actor. */ PARTICIPATED,
        /** Existing authorities cannot prove player participation; payout must fail closed. */ NOT_PROVEN
    }

    /**
     * One bounded participation observation.
     *
     * @param result participation result
     * @param authorityCode stable diagnostic code
     */
    public record Observation(Result result, String authorityCode) {
        /**
         * Validates one participation observation.
         *
         * @param result participation result
         * @param authorityCode stable diagnostic code
         */
        public Observation {
            Objects.requireNonNull(result, "Player mission participation result not set");
            authorityCode = requireText(authorityCode, "Player mission participation code");
        }
    }

    /**
     * Proves bounded player participation for an already satisfied ordinary mission predicate.
     *
     * @param world ordinary live world authority
     * @param freight Stage-20 physical freight authority when required
     * @param contractorDiscovery owner-local Stage-20 discovery for {@link #PLAYER_ACTOR_ID}, when required
     * @param operations Stage-21E operation authority when required
     * @param player persistent authoritative player ownership/affiliation state
     * @param objective satisfied ordinary mission objective
     * @return deterministic participation observation; ambiguous evidence returns {@link Result#NOT_PROVEN}
     */
    public static Observation evaluate(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage20DiscoveryKnowledgeState contractorDiscovery,
            StrategicOperationState operations,
            PlayerState player,
            MissionObjective objective) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        PlayerState checkedPlayer = Objects.requireNonNull(player, "Player contractor state not set");
        MissionObjective checkedObjective = Objects.requireNonNull(objective, "Mission objective not set");
        return switch (checkedObjective.kind()) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST ->
                    freightParticipation(freight, checkedPlayer, checkedObjective);
            case FLEET_PRESENT_IN_SYSTEM ->
                    ownedFleetParticipation(checkedPlayer, checkedObjective.subjectId(), "fleet.player-owned");
            case FLEET_ABSENT -> new Observation(Result.NOT_PROVEN, "fleet.absence-causation-not-proven");
            case ESCORT_FLEETS_PRESENT_IN_SYSTEM ->
                    ownedFleetParticipation(checkedPlayer, checkedObjective.requiredState(), "escort.player-fleet");
            case FLEET_REACTION_MASS_KG_AT_LEAST ->
                    rescueParticipation(checkedWorld, checkedPlayer, checkedObjective);
            case DISCOVERY_AT_LEAST ->
                    discoveryParticipation(contractorDiscovery, checkedObjective, false);
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST ->
                    derelictParticipation(checkedWorld, contractorDiscovery, checkedPlayer, checkedObjective);
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST, CONSTRUCTION_COMPLETED ->
                    constructionParticipation(checkedWorld, checkedPlayer, checkedObjective);
            case MARKET_ACCESS_ALLOWED -> accessParticipation(checkedPlayer, checkedObjective);
            case OPERATION_STATUS -> operationParticipation(operations, checkedPlayer, checkedObjective);
            case FACTION_TREASURY_AT_LEAST -> treasuryParticipation(checkedPlayer, checkedObjective);
        };
    }

    private static Observation freightParticipation(
            Stage20FreightPersistentState freight,
            PlayerState player,
            MissionObjective objective) {
        Stage20FreightPersistentState checked = Objects.requireNonNull(
                freight, "Freight state required by player delivery participation");
        TransportOrderState order = checked.orders().stream()
                .filter(value -> value.orderId().equals(objective.subjectId()))
                .findFirst().orElse(null);
        if (order == null) {
            return new Observation(Result.NOT_PROVEN, "freight.order-missing-for-contractor-proof");
        }
        boolean owned = player.ownedFleetIds().contains(order.fleetId());
        return new Observation(
                owned ? Result.PARTICIPATED : Result.NOT_PROVEN,
                owned ? "freight.assigned-fleet-player-owned" : "freight.assigned-fleet-not-player-owned");
    }

    private static Observation ownedFleetParticipation(
            PlayerState player,
            String fleetValue,
            String successCode) {
        FleetId fleetId = fleetId(fleetValue);
        boolean owned = player.ownedFleetIds().contains(fleetId);
        return new Observation(
                owned ? Result.PARTICIPATED : Result.NOT_PROVEN,
                owned ? successCode : "fleet.contractor-ownership-not-proven");
    }

    private static Observation rescueParticipation(
            WorldSimulation world,
            PlayerState player,
            MissionObjective objective) {
        FleetId target = fleetId(objective.subjectId());
        if (player.ownedFleetIds().contains(target)) {
            return new Observation(Result.PARTICIPATED, "refuel.target-player-owned");
        }
        FleetPlacementState targetPlacement = world.findFleet(target).orElse(null);
        if (targetPlacement == null
                || targetPlacement.locationKind() != FleetLocationKind.IN_SYSTEM
                || targetPlacement.systemId() == null) {
            return new Observation(Result.NOT_PROVEN, "refuel.player-presence-not-proven");
        }
        boolean present = hasOwnedFleetInSystem(world, player, targetPlacement.systemId());
        return new Observation(
                present ? Result.PARTICIPATED : Result.NOT_PROVEN,
                present ? "refuel.player-fleet-copresent" : "refuel.player-presence-not-proven");
    }

    private static Observation discoveryParticipation(
            Stage20DiscoveryKnowledgeState discovery,
            MissionObjective objective,
            boolean requireSpecialLocation) {
        if (discovery == null || !PLAYER_ACTOR_ID.equals(discovery.ownerId())) {
            return new Observation(Result.NOT_PROVEN, "discovery.player-owner-boundary-not-proven");
        }
        String[] parts = discoveryState(objective.requiredState());
        StaticObjectKind kind;
        DiscoveryState required;
        try {
            kind = StaticObjectKind.valueOf(parts[0]);
            required = DiscoveryState.valueOf(parts[1]);
        } catch (IllegalArgumentException exception) {
            return new Observation(Result.NOT_PROVEN, "discovery.required-state-invalid");
        }
        if (required == DiscoveryState.TRACKED
                || requireSpecialLocation && kind != StaticObjectKind.SPECIAL_LOCATION) {
            return new Observation(Result.NOT_PROVEN, "discovery.static-state-not-supported");
        }
        String objectId = objective.subjectId();
        if (requireSpecialLocation) {
            String[] subjects = objectId.split("\\|", -1);
            if (subjects.length != 2 || subjects[0].isBlank()) {
                return new Observation(Result.NOT_PROVEN, "discovery.derelict-subject-invalid");
            }
            objectId = subjects[0];
        }
        DiscoveryState actual = discovery.discoveryState(new StaticObjectRef(
                new StarSystemId(objective.systemId()), kind, objectId));
        boolean sufficient = staticRank(actual) >= staticRank(required);
        return new Observation(
                sufficient ? Result.PARTICIPATED : Result.NOT_PROVEN,
                sufficient ? "discovery.player-evidence-sufficient" : "discovery.player-evidence-insufficient");
    }

    private static Observation derelictParticipation(
            WorldSimulation world,
            Stage20DiscoveryKnowledgeState discovery,
            PlayerState player,
            MissionObjective objective) {
        Observation discovered = discoveryParticipation(discovery, objective, true);
        if (discovered.result() != Result.PARTICIPATED) {
            return discovered;
        }
        StarSystemId system = new StarSystemId(objective.systemId());
        boolean present = hasOwnedFleetInSystem(world, player, system);
        return new Observation(
                present ? Result.PARTICIPATED : Result.NOT_PROVEN,
                present ? "salvage.player-discovery-and-presence" : "salvage.player-fleet-presence-not-proven");
    }

    private static Observation constructionParticipation(
            WorldSimulation world,
            PlayerState player,
            MissionObjective objective) {
        ConstructionProjectId id = new ConstructionProjectId(parsePositiveLong(
                objective.subjectId(), "Construction project ID"));
        ConstructionProjectState project = world.findConstructionProject(id).orElse(null);
        if (project == null) {
            return new Observation(Result.NOT_PROVEN, "construction.project-missing-for-contractor-proof");
        }
        if (player.ownedConstructionProjectIds().contains(id)) {
            return new Observation(Result.PARTICIPATED, "construction.project-player-owned");
        }
        boolean present = hasOwnedFleetInSystem(world, player, project.systemId());
        return new Observation(
                present ? Result.PARTICIPATED : Result.NOT_PROVEN,
                present ? "construction.player-fleet-copresent" : "construction.player-participation-not-proven");
    }

    private static Observation accessParticipation(PlayerState player, MissionObjective objective) {
        boolean participant = player.factionContentId() != null
                && player.factionContentId().equals(objective.subjectId());
        return new Observation(
                participant ? Result.PARTICIPATED : Result.NOT_PROVEN,
                participant ? "diplomacy.player-faction-requester" : "diplomacy.player-faction-not-requester");
    }

    private static Observation operationParticipation(
            StrategicOperationState operations,
            PlayerState player,
            MissionObjective objective) {
        if (operations == null) {
            return new Observation(Result.NOT_PROVEN, "operation.state-missing-for-contractor-proof");
        }
        long id = parsePositiveLong(objective.subjectId(), "Operation ID");
        OperationState operation = operations.operations().stream()
                .filter(value -> value.id() == id)
                .findFirst().orElse(null);
        if (operation == null) {
            return new Observation(Result.NOT_PROVEN, "operation.missing-for-contractor-proof");
        }
        boolean participant = operation.participantFleetIds().stream().anyMatch(player.ownedFleetIds()::contains);
        return new Observation(
                participant ? Result.PARTICIPATED : Result.NOT_PROVEN,
                participant ? "operation.player-fleet-participant" : "operation.player-participation-not-proven");
    }

    private static Observation treasuryParticipation(PlayerState player, MissionObjective objective) {
        boolean participant = player.factionContentId() != null
                && player.factionContentId().equals(objective.subjectId());
        return new Observation(
                participant ? Result.PARTICIPATED : Result.NOT_PROVEN,
                participant ? "economy.player-faction-subject" : "economy.player-faction-not-subject");
    }

    private static boolean hasOwnedFleetInSystem(
            WorldSimulation world,
            PlayerState player,
            StarSystemId systemId) {
        for (FleetId fleetId : player.ownedFleetIds()) {
            FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
            if (placement != null
                    && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && systemId.equals(placement.systemId())) {
                return true;
            }
        }
        return false;
    }

    private static FleetId fleetId(String value) {
        return new FleetId(parsePositiveLong(value, "FleetId"));
    }

    private static String[] discoveryState(String value) {
        String[] parts = requireText(value, "Discovery state").split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Discovery objective state must be KIND:STATE");
        }
        return parts;
    }

    private static int staticRank(DiscoveryState state) {
        return switch (state) {
            case UNKNOWN -> 0;
            case DETECTED -> 1;
            case CLASSIFIED -> 2;
            case KNOWN_STATIC_LOCATION -> 3;
            case TRACKED -> -1;
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

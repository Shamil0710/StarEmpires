package com.spacesim.persistence;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.WorldState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21E generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21D runtime is embedded unchanged. Stage 21E adds only persistent
 * operation/contact/tactical-reference metadata. Physical fleets, damage, ammunition, propellant,
 * freight, economy, territory, diplomacy and combat continue to persist in their earlier owners.</p>
 *
 * @param schemaVersion exact Stage-21E checkpoint schema version
 * @param runtimeVersion exact Stage-21E runtime contract identifier
 * @param stage21DRuntime complete embedded Stage-21D checkpoint
 * @param operationState Stage-21E persistent operation metadata
 */
public record Stage21EGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21DGeneratedWorldRuntimePersistentState stage21DRuntime,
        StrategicOperationState operationState) {

    /** Current Stage-21E checkpoint schema. */
    public static final int CURRENT_VERSION = 8;
    /** Current Stage-21E runtime contract identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21e.generated-world-physical-operations.v8";

    /**
     * Validates operation references against the embedded ordinary world and command state.
     *
     * <p>Active operations must retain a current compatible Stage-21D command group and source order.
     * Terminal operations may retain historical group/order identities after physical annihilation has
     * removed those command objects; when either historical object still exists it remains cross-checked.
     * This preserves loss evidence without weakening any active command invariant.</p>
     *
     * @param schemaVersion exact supported Stage-21E schema version
     * @param runtimeVersion exact supported Stage-21E runtime contract identifier
     * @param stage21DRuntime complete embedded Stage-21D runtime checkpoint
     * @param operationState Stage-21E persistent operation metadata to cross-check
     */
    public Stage21EGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21E checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21E runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21DRuntime, "stage21DRuntime");
        Objects.requireNonNull(operationState, "operationState");

        WorldState world = stage21DRuntime.stage21CRuntime().stage21BRuntime().stage21ARuntime()
                .stage20Runtime().worldState();
        Set<StarSystemId> systems = new HashSet<>();
        world.topology().systems().forEach(system -> systems.add(system.id()));
        Map<Long, CommandGroupState> groups = new HashMap<>();
        for (CommandGroupState group : stage21DRuntime.fleetCommandState().groups()) groups.put(group.id(), group);
        Map<Long, FleetOrderState> orders = new HashMap<>();
        for (FleetOrderState order : stage21DRuntime.fleetCommandState().orders()) orders.put(order.id(), order);
        Map<FleetId, Integer> currentFleetOwners = currentFleetOwners(world);

        for (OperationState operation : operationState.operations()) {
            CommandGroupState group = groups.get(operation.commandGroupId());
            FleetOrderState order = orders.get(operation.sourceOrderId());
            if (operation.status().active()) {
                if (group == null) {
                    throw new IllegalArgumentException(
                            "active Stage-21E operation references unknown command group: " + operation.id());
                }
                if (order == null || order.commandGroupId() != operation.commandGroupId()) {
                    throw new IllegalArgumentException(
                            "active Stage-21E operation references incompatible source order: " + operation.id());
                }
            } else {
                if (order != null && order.commandGroupId() != operation.commandGroupId()) {
                    throw new IllegalArgumentException(
                            "terminal Stage-21E operation references incompatible surviving source order: " + operation.id());
                }
            }
            if (group != null && group.factionId() != operation.factionId()) {
                throw new IllegalArgumentException(
                        "Stage-21E operation faction differs from command group: " + operation.id());
            }
            requireSystem(systems, operation.stagingSystemId(), "staging", operation.id());
            requireSystem(systems, operation.objectiveSystemId(), "objective", operation.id());
            requireSystem(systems, operation.withdrawalPolicy().fallbackSystemId(), "fallback", operation.id());
            for (FleetId fleetId : operation.participantFleetIds()) {
                Integer owner = currentFleetOwners.get(fleetId);
                if (operation.status().active() && owner == null) {
                    throw new IllegalArgumentException(
                            "active Stage-21E participant is absent from ordinary world: " + fleetId);
                }
                if (owner != null && owner != operation.factionId()) {
                    throw new IllegalArgumentException(
                            "Stage-21E participant current owner differs from operation: " + fleetId);
                }
            }
            if (operation.contact() != null) {
                requireSystem(systems, operation.contact().observedSystemId(), "contact", operation.id());
            }
            if (operation.encounter() != null) {
                requireSystem(systems, operation.encounter().systemId(), "encounter", operation.id());
                if (operation.encounter().active()) {
                    throw new IllegalArgumentException(
                            "Stage-21E checkpoint cannot retain an active transient tactical encounter: "
                                    + operation.id());
                }
            }
        }
    }

    /**
     * Composes a current Stage-21E checkpoint.
     *
     * @param stage21D complete accepted Stage-21D generated-world runtime checkpoint
     * @param operations persistent Stage-21E operation metadata
     * @return validated current-version Stage-21E checkpoint wrapper
     */
    public static Stage21EGeneratedWorldRuntimePersistentState compose(
            Stage21DGeneratedWorldRuntimePersistentState stage21D,
            StrategicOperationState operations) {
        return new Stage21EGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21D, operations);
    }

    private static void requireSystem(Set<StarSystemId> systems, StarSystemId systemId, String label, long operationId) {
        if (!systems.contains(systemId)) {
            throw new IllegalArgumentException(
                    "Stage-21E " + label + " system is unknown for operation " + operationId);
        }
    }

    private static Map<FleetId, Integer> currentFleetOwners(WorldState world) {
        Map<StarSystemId, Map<EntityId, EntityState>> local = new HashMap<>();
        for (StarSystemSimulationState system : world.systems()) {
            Map<EntityId, EntityState> entities = new HashMap<>();
            for (EntityState entity : system.simulationState().entities()) entities.put(entity.id(), entity);
            local.put(system.systemId(), entities);
        }
        Map<FleetId, Integer> owners = new HashMap<>();
        for (FleetPlacementState placement : world.fleets()) {
            EntityState entity;
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                entity = placement.transitState() == null ? null : placement.transitState().entityState();
            } else {
                Map<EntityId, EntityState> entities = local.get(placement.systemId());
                entity = entities == null ? null : entities.get(placement.localEntityId());
            }
            if (entity != null && entity.faction() != null) owners.put(placement.id(), entity.faction().factionId());
        }
        return Map.copyOf(owners);
    }
}

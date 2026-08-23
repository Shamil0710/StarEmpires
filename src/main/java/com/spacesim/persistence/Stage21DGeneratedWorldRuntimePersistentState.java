package com.spacesim.persistence;

import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21D generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21C runtime remains embedded unchanged. Stage 21D adds only
 * command-group/order metadata; ordinary FleetId placements, fitted entities, damage, cargo,
 * economy, diplomacy and warfare stay in their previous persistence owners.</p>
 *
 * @param schemaVersion exact Stage-21D checkpoint schema version
 * @param runtimeVersion exact Stage-21D runtime contract identifier
 * @param stage21CRuntime complete embedded Stage-21C runtime checkpoint
 * @param fleetCommandState Stage-21D command-group and strategic-order metadata
 */
public record Stage21DGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21CGeneratedWorldRuntimePersistentState stage21CRuntime,
        FleetCommandState fleetCommandState) {

    /** Current Stage-21D checkpoint schema version. */
    public static final int CURRENT_VERSION = 7;

    /** Current Stage-21D generated-world runtime contract identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21d.generated-world-force-command.v7";

    /**
     * Validates Stage-21D version identity and all cross-layer FleetId/system references.
     *
     * <p>Validation fails closed when command metadata references a fleet or system not present in
     * the embedded physical world, assigns a fleet to a different faction than its authoritative
     * physical payload, or persists a route edge absent from the authoritative galaxy topology.</p>
     *
     * @param schemaVersion exact supported checkpoint schema version
     * @param runtimeVersion exact supported runtime contract identifier
     * @param stage21CRuntime embedded Stage-21C runtime checkpoint
     * @param fleetCommandState Stage-21D command metadata to cross-check against the physical world
     */
    public Stage21DGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21D checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21D runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21CRuntime, "stage21CRuntime");
        Objects.requireNonNull(fleetCommandState, "fleetCommandState");

        WorldState world = stage21CRuntime.stage21BRuntime().stage21ARuntime().stage20Runtime().worldState();
        Map<FleetId, Integer> fleetFactionById = authoritativeFleetFactions(world);
        Set<StarSystemId> knownSystems = new HashSet<>();
        world.topology().systems().forEach(system -> knownSystems.add(system.id()));
        Set<Long> groupIds = new HashSet<>();
        for (CommandGroupState group : fleetCommandState.groups()) {
            groupIds.add(group.id());
            if (!knownSystems.contains(group.homeSystemId())) {
                throw new IllegalArgumentException("Stage-21D group references unknown home system: " + group.id());
            }
            for (FleetId fleetId : group.memberFleetIds()) {
                Integer authoritativeFaction = fleetFactionById.get(fleetId);
                if (authoritativeFaction == null) {
                    throw new IllegalArgumentException("Stage-21D group references missing or unaffiliated FleetId: " + fleetId);
                }
                if (authoritativeFaction != group.factionId()) {
                    throw new IllegalArgumentException("Stage-21D group faction does not match physical FleetId owner: " + fleetId);
                }
            }
        }
        for (FleetOrderState order : fleetCommandState.orders()) {
            if (!groupIds.contains(order.commandGroupId())) {
                throw new IllegalArgumentException("Stage-21D order references missing command group: " + order.id());
            }
            for (StarSystemId systemId : order.route()) {
                if (!knownSystems.contains(systemId)) {
                    throw new IllegalArgumentException("Stage-21D order route references unknown system: " + systemId);
                }
            }
            for (int index = 0; index + 1 < order.route().size(); index++) {
                StarSystemId from = order.route().get(index);
                StarSystemId to = order.route().get(index + 1);
                if (!world.topology().neighbors(from).contains(to)) {
                    throw new IllegalArgumentException("Stage-21D order route contains non-neighbor hop: " + from + " -> " + to);
                }
            }
        }
    }

    /**
     * Composes a current-version Stage-21D checkpoint from an accepted Stage-21C runtime and command state.
     *
     * @param stage21C complete accepted Stage-21C runtime checkpoint
     * @param commandState Stage-21D command-group and strategic-order metadata
     * @return validated current-version Stage-21D persistent runtime wrapper
     */
    public static Stage21DGeneratedWorldRuntimePersistentState compose(
            Stage21CGeneratedWorldRuntimePersistentState stage21C,
            FleetCommandState commandState) {
        return new Stage21DGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                stage21C,
                commandState);
    }

    private static Map<FleetId, Integer> authoritativeFleetFactions(WorldState world) {
        Map<StarSystemId, Map<EntityId, EntityState>> localBySystem = new HashMap<>();
        for (StarSystemSimulationState system : world.systems()) {
            Map<EntityId, EntityState> local = new HashMap<>();
            for (EntityState entity : system.simulationState().entities()) {
                local.put(entity.id(), entity);
            }
            localBySystem.put(system.systemId(), local);
        }
        Map<FleetId, Integer> result = new HashMap<>();
        for (FleetPlacementState placement : world.fleets()) {
            EntityState entity;
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                entity = placement.transitState() == null ? null : placement.transitState().entityState();
            } else {
                Map<EntityId, EntityState> local = localBySystem.get(placement.systemId());
                entity = local == null ? null : local.get(placement.localEntityId());
            }
            if (entity == null || entity.faction() == null) {
                continue;
            }
            result.put(placement.id(), entity.faction().factionId());
        }
        return Map.copyOf(result);
    }
}

package com.spacesim.world;

import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.GameState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only Stage-21D view of ordinary persistent fleets.
 *
 * <p>No placement is owned here. Every entry is reconstructed from {@link WorldState#fleets()} and
 * the exact fitted entity stored either in a local {@link GameState} or the existing transit
 * payload. Consequently the stable FleetId remains the sole fleet identity.</p>
 */
public final class FleetForceRegistry {
    private final List<Entry> entries;
    private final Map<FleetId, Entry> byId;

    private FleetForceRegistry(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<FleetId, Entry> index = new HashMap<>();
        for (Entry entry : this.entries) {
            if (index.putIfAbsent(entry.fleetId(), entry) != null) {
                throw new IllegalArgumentException("duplicate FleetId in reconstructed registry: " + entry.fleetId());
            }
        }
        this.byId = Map.copyOf(index);
    }

    public static FleetForceRegistry reconstruct(
            WorldState world,
            FleetReadinessEvaluator evaluator,
            Map<FleetId, FleetOperationalAvailability> availabilityByFleet) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(evaluator, "evaluator");
        Map<FleetId, FleetOperationalAvailability> availability = availabilityByFleet == null
                ? Map.of()
                : Map.copyOf(availabilityByFleet);
        Map<StarSystemId, Map<com.spacesim.persistence.EntityId, EntityState>> local = localEntities(world);
        List<Entry> result = new ArrayList<>();
        for (FleetPlacementState placement : world.fleets()) {
            EntityState entity;
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                entity = placement.transitState() == null ? null : placement.transitState().entityState();
            } else {
                Map<com.spacesim.persistence.EntityId, EntityState> systemEntities = local.get(placement.systemId());
                entity = systemEntities == null ? null : systemEntities.get(placement.localEntityId());
            }
            if (entity == null) {
                throw new IllegalStateException("fleet placement has no authoritative entity payload: " + placement.id());
            }
            if (entity.identity() == null || !"FLEET".equals(entity.identity().kindName())) {
                throw new IllegalStateException("FleetId points to non-fleet entity: " + placement.id());
            }
            int factionId = entity.faction() == null ? -1 : entity.faction().factionId();
            FleetOperationalAvailability observed = availability.getOrDefault(
                    placement.id(), FleetOperationalAvailability.unavailable());
            result.add(new Entry(
                    placement.id(),
                    factionId,
                    placement.locationKind(),
                    placement.systemId(),
                    placement.transitState() == null ? null : placement.transitState().originSystemId(),
                    placement.transitState() == null ? null : placement.transitState().destinationSystemId(),
                    entity,
                    evaluator.evaluate(entity, observed)));
        }
        result.sort(java.util.Comparator.comparing(Entry::fleetId));
        return new FleetForceRegistry(result);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> find(FleetId fleetId) {
        return Optional.ofNullable(fleetId == null ? null : byId.get(fleetId));
    }

    public List<Entry> ownedBy(int factionId) {
        return entries.stream().filter(entry -> entry.factionId() == factionId).toList();
    }

    private static Map<StarSystemId, Map<com.spacesim.persistence.EntityId, EntityState>> localEntities(WorldState world) {
        Map<StarSystemId, Map<com.spacesim.persistence.EntityId, EntityState>> result = new HashMap<>();
        for (StarSystemSimulationState system : world.systems()) {
            Map<com.spacesim.persistence.EntityId, EntityState> entities = new HashMap<>();
            for (EntityState entity : system.simulationState().entities()) {
                entities.put(entity.id(), entity);
            }
            result.put(system.systemId(), entities);
        }
        return result;
    }

    /** One reconstructed force entry; entityState is the exact existing physical payload. */
    public record Entry(
            FleetId fleetId,
            int factionId,
            FleetLocationKind locationKind,
            StarSystemId systemId,
            StarSystemId transitOriginSystemId,
            StarSystemId transitDestinationSystemId,
            EntityState entityState,
            FleetReadinessState readiness) {
        public Entry {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(locationKind, "locationKind");
            Objects.requireNonNull(entityState, "entityState");
            Objects.requireNonNull(readiness, "readiness");
        }
    }
}

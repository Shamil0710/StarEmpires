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
 * <p>No placement is owned here. Every production entry is reconstructed from
 * {@link WorldState#fleets()} and the exact fitted entity stored either in a local
 * {@link GameState} or the existing transit payload. Consequently the stable FleetId remains the
 * sole fleet identity.</p>
 */
public final class FleetForceRegistry {
    private final List<Entry> entries;
    private final Map<FleetId, Entry> byId;

    /** Package-local for acceptance fixtures; production callers use {@link #reconstruct}. */
    FleetForceRegistry(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<FleetId, Entry> index = new HashMap<>();
        for (Entry entry : this.entries) {
            if (index.putIfAbsent(entry.fleetId(), entry) != null) {
                throw new IllegalArgumentException("duplicate FleetId in reconstructed registry: " + entry.fleetId());
            }
        }
        this.byId = Map.copyOf(index);
    }

    /**
     * Reconstructs the strategic read model from authoritative persistent fleet placements and payloads.
     *
     * @param world persistent world containing ordinary fleet placements and local/transit entity state
     * @param evaluator readiness evaluator derived from existing engineering and consumable authority
     * @param availabilityByFleet external bounded availability observations keyed by stable fleet identity;
     *                            missing entries fail closed to unavailable
     * @return immutable deterministic force registry sorted by {@link FleetId}
     * @throws IllegalStateException when a fleet placement cannot be resolved to its authoritative entity payload
     */
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

    /**
     * Returns all reconstructed fleets in canonical identity order.
     *
     * @return immutable force entries
     */
    public List<Entry> entries() { return entries; }

    /**
     * Looks up a reconstructed fleet by stable ordinary fleet identity.
     *
     * @param fleetId fleet identity, or {@code null}
     * @return matching entry when present
     */
    public Optional<Entry> find(FleetId fleetId) {
        return Optional.ofNullable(fleetId == null ? null : byId.get(fleetId));
    }

    /**
     * Projects all reconstructed fleets currently affiliated with the supplied faction.
     *
     * @param factionId faction identifier
     * @return immutable list preserving canonical registry order
     */
    public List<Entry> ownedBy(int factionId) {
        return entries.stream().filter(entry -> entry.factionId() == factionId).toList();
    }

    private static Map<StarSystemId, Map<com.spacesim.persistence.EntityId, EntityState>> localEntities(WorldState world) {
        Map<StarSystemId, Map<com.spacesim.persistence.EntityId, EntityState>> result = new HashMap<>();
        for (StarSystemSimulationState system : world.systems()) {
            Map<com.spacesim.persistence.EntityId, EntityState> entities = new HashMap<>();
            for (EntityState entity : system.simulationState().entities()) entities.put(entity.id(), entity);
            result.put(system.systemId(), entities);
        }
        return result;
    }

    /**
     * One reconstructed strategic force entry whose {@code entityState} is the exact existing physical payload.
     *
     * @param fleetId stable ordinary fleet identity
     * @param factionId currently affiliated faction identifier, or {@code -1} when unaffiliated
     * @param locationKind existing local/transit placement kind
     * @param systemId current local system identifier when applicable
     * @param transitOriginSystemId transit origin when the fleet is in transit
     * @param transitDestinationSystemId transit destination when the fleet is in transit
     * @param entityState exact persistent physical entity payload
     * @param readiness Stage-21D readiness projection derived from the physical payload and availability seam
     */
    public record Entry(
            FleetId fleetId,
            int factionId,
            FleetLocationKind locationKind,
            StarSystemId systemId,
            StarSystemId transitOriginSystemId,
            StarSystemId transitDestinationSystemId,
            EntityState entityState,
            FleetReadinessState readiness) {
        /**
         * Validates required reconstructed entry fields.
         *
         * @param fleetId stable ordinary fleet identity
         * @param factionId current faction affiliation
         * @param locationKind existing fleet placement kind
         * @param systemId current local system when applicable
         * @param transitOriginSystemId transit origin when applicable
         * @param transitDestinationSystemId transit destination when applicable
         * @param entityState exact persistent physical fleet payload
         * @param readiness readiness projection for this payload
         */
        public Entry {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(locationKind, "locationKind");
            Objects.requireNonNull(entityState, "entityState");
            Objects.requireNonNull(readiness, "readiness");
        }
    }
}

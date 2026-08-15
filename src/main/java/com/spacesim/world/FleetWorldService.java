package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.constants.Constants;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runtime owner of stable FleetIds and mutually-exclusive local/transit fleet placement. */
final class FleetWorldService {
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final FleetIdAllocator idAllocator;
    private final Map<FleetId, FleetPlacementState> placementsById = new LinkedHashMap<>();
    private final Map<LocalFleetKey, FleetId> fleetByLocalLocation = new HashMap<>();

    FleetWorldService(
            Map<StarSystemId, SimulationSession> sessionsById,
            long nextFleetIdValue,
            List<FleetPlacementState> placements) {
        this.sessionsById = Map.copyOf(Objects.requireNonNull(sessionsById, "Fleet sessions не заданы"));
        idAllocator = new FleetIdAllocator(nextFleetIdValue);
        for (FleetPlacementState placement : Objects.requireNonNull(placements, "Fleet placements не заданы")) {
            restorePlacement(placement);
        }
    }

    long nextIdValue() {
        return idAllocator.nextValue();
    }

    List<FleetPlacementState> snapshots() {
        List<FleetPlacementState> result = new ArrayList<>(placementsById.values());
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    Optional<FleetPlacementState> find(FleetId id) {
        return Optional.ofNullable(id == null ? null : placementsById.get(id));
    }

    Optional<FleetId> findByLocal(StarSystemId systemId, EntityId entityId) {
        if (systemId == null || entityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(fleetByLocalLocation.get(new LocalFleetKey(systemId, entityId)));
    }

    FleetId registerLocal(StarSystemId systemId, EntityId entityId) {
        LocalFleetKey key = requireLocalFleet(systemId, entityId);
        if (fleetByLocalLocation.containsKey(key)) {
            throw new IllegalStateException("Local fleet уже имеет world FleetId: " + key);
        }
        FleetId id = idAllocator.allocate();
        FleetPlacementState placement = new FleetPlacementState(
                id, FleetLocationKind.IN_SYSTEM, systemId, entityId, null);
        placementsById.put(id, placement);
        fleetByLocalLocation.put(key, id);
        return id;
    }

    boolean unregisterLocal(StarSystemId systemId, EntityId entityId) {
        if (systemId == null || entityId == null) {
            return false;
        }
        LocalFleetKey key = new LocalFleetKey(systemId, entityId);
        FleetId id = fleetByLocalLocation.remove(key);
        if (id == null) {
            return false;
        }
        FleetPlacementState removed = placementsById.remove(id);
        if (removed == null || removed.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Fleet world index расходится с local index: " + id);
        }
        return true;
    }

    FleetPlacementState beginTransfer(FleetId fleetId, StarSystemId destinationSystemId) {
        FleetPlacementState current = requirePlacement(fleetId);
        if (current.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Fleet уже находится in transit: " + fleetId);
        }
        StarSystemId destination = Objects.requireNonNull(
                destinationSystemId, "Destination StarSystemId не задан");
        if (!sessionsById.containsKey(destination)) {
            throw new IllegalArgumentException("Неизвестная destination StarSystem: " + destination);
        }
        if (destination.equals(current.systemId())) {
            throw new IllegalArgumentException("Fleet transfer должен менять StarSystem");
        }

        SimulationSession origin = sessionsById.get(current.systemId());
        EntityState snapshot = FleetTransferService.detach(origin, current.localEntityId());
        fleetByLocalLocation.remove(new LocalFleetKey(current.systemId(), current.localEntityId()));

        FleetPlacementState transit = new FleetPlacementState(
                current.id(),
                FleetLocationKind.IN_TRANSIT,
                null,
                null,
                new FleetTransitState(current.systemId(), destination, snapshot));
        placementsById.put(current.id(), transit);
        return transit;
    }

    boolean affiliateTransitFaction(FleetId fleetId, int runtimeFactionId) {
        if (runtimeFactionId < 0 || runtimeFactionId >= Constants.FACTION_RUNTIME_CAPACITY) {
            throw new IllegalArgumentException("Transit affiliation runtime faction ID is outside capacity");
        }
        FleetPlacementState current = requirePlacement(fleetId);
        if (current.locationKind() != FleetLocationKind.IN_TRANSIT || current.transitState() == null) {
            throw new IllegalStateException("Transit affiliation requires IN_TRANSIT fleet: " + fleetId);
        }
        FleetTransitState transit = current.transitState();
        EntityState source = transit.entityState();
        EntityState.FactionState faction = source.faction();
        if (faction != null && faction.factionId() == runtimeFactionId) {
            return false;
        }

        EntityState updatedEntity = new EntityState(
                source.id(),
                source.identity(),
                source.transform(),
                source.inventory(),
                source.wallet(),
                source.market(),
                source.production(),
                source.priceHistory(),
                new EntityState.FactionState(runtimeFactionId),
                source.reputation(),
                source.ship(),
                source.tradeAi(),
                source.mining(),
                source.combat(),
                source.asteroid(),
                source.archetype());
        FleetTransitState updatedTransit = new FleetTransitState(
                transit.originSystemId(),
                transit.destinationSystemId(),
                updatedEntity);
        placementsById.put(
                fleetId,
                new FleetPlacementState(
                        fleetId,
                        FleetLocationKind.IN_TRANSIT,
                        null,
                        null,
                        updatedTransit));
        return true;
    }

    FleetPlacementState completeTransfer(FleetId fleetId, float arrivalX, float arrivalY) {
        FleetPlacementState current = requirePlacement(fleetId);
        if (current.locationKind() != FleetLocationKind.IN_TRANSIT) {
            throw new IllegalStateException("Fleet не находится in transit: " + fleetId);
        }
        FleetTransitState transit = current.transitState();
        SimulationSession destination = sessionsById.get(transit.destinationSystemId());
        if (destination == null) {
            throw new IllegalStateException(
                    "Transit destination session отсутствует: " + transit.destinationSystemId());
        }

        EntityId newLocalId = FleetTransferService.attach(
                destination, transit.entityState(), arrivalX, arrivalY);
        LocalFleetKey key = new LocalFleetKey(transit.destinationSystemId(), newLocalId);
        if (fleetByLocalLocation.putIfAbsent(key, fleetId) != null) {
            throw new IllegalStateException("Destination local fleet уже имеет world mapping: " + key);
        }
        FleetPlacementState local = new FleetPlacementState(
                fleetId,
                FleetLocationKind.IN_SYSTEM,
                transit.destinationSystemId(),
                newLocalId,
                null);
        placementsById.put(fleetId, local);
        return local;
    }

    private void restorePlacement(FleetPlacementState placement) {
        FleetPlacementState value = Objects.requireNonNull(placement, "FleetPlacementState не задан");
        if (placementsById.putIfAbsent(value.id(), value) != null) {
            throw new IllegalArgumentException("Duplicate restored FleetId: " + value.id());
        }
        if (value.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return;
        }
        LocalFleetKey key = requireLocalFleet(value.systemId(), value.localEntityId());
        if (fleetByLocalLocation.putIfAbsent(key, value.id()) != null) {
            throw new IllegalArgumentException("Duplicate restored local fleet mapping: " + key);
        }
    }

    private LocalFleetKey requireLocalFleet(StarSystemId systemId, EntityId entityId) {
        StarSystemId system = Objects.requireNonNull(systemId, "Fleet StarSystemId не задан");
        EntityId id = Objects.requireNonNull(entityId, "Fleet local EntityId не задан");
        SimulationSession session = sessionsById.get(system);
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная fleet StarSystem: " + system);
        }
        Entity entity = session.getEntityRegistry().find(id);
        IdentityComponent identity = entity == null ? null : entity.getComponent(IdentityComponent.class);
        if (identity == null || identity.kind != IdentityComponent.Kind.FLEET) {
            throw new IllegalArgumentException("Local placement не указывает на fleet entity: " + system + "/" + id);
        }
        return new LocalFleetKey(system, id);
    }

    private FleetPlacementState requirePlacement(FleetId id) {
        FleetPlacementState placement = placementsById.get(Objects.requireNonNull(id, "FleetId не задан"));
        if (placement == null) {
            throw new IllegalArgumentException("Неизвестный FleetId: " + id);
        }
        return placement;
    }

    private record LocalFleetKey(StarSystemId systemId, EntityId entityId) {
    }
}

package com.spacesim.world;

import com.spacesim.persistence.EntityState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Creates stable fleet placements for saves that predate world fleet IDs. */
final class FleetBootstrap {
    private FleetBootstrap() {
        throw new AssertionError("Utility class");
    }

    static Result create(List<StarSystemSimulationState> systems) {
        List<StarSystemSimulationState> ordered = new ArrayList<>(systems);
        ordered.sort(Comparator.comparing(StarSystemSimulationState::systemId));
        List<FleetPlacementState> placements = new ArrayList<>();
        long value = 1L;
        for (StarSystemSimulationState system : ordered) {
            List<EntityState> entities = new ArrayList<>(system.simulationState().entities());
            entities.sort(Comparator.comparing(EntityState::id));
            for (EntityState entity : entities) {
                if (entity.identity() != null && "FLEET".equals(entity.identity().kindName())) {
                    placements.add(new FleetPlacementState(
                            new FleetId(value++), FleetLocationKind.IN_SYSTEM,
                            system.systemId(), entity.id(), null));
                }
            }
        }
        return new Result(value, List.copyOf(placements));
    }

    record Result(long nextId, List<FleetPlacementState> placements) {
    }
}

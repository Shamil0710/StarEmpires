package com.spacesim.world;

import com.spacesim.persistence.EntityState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic migration/bootstrap of world FleetIds from pre-Stage-10 local fleet entities. */
final class FleetWorldBootstrap {
    private FleetWorldBootstrap() {
        throw new AssertionError("Utility class");
    }

    static Result fromLocalSystems(List<StarSystemSimulationState> systems) {
        List<StarSystemSimulationState> orderedSystems = new ArrayList<>(
                Objects.requireNonNull(systems, "System states не заданы"));
        orderedSystems.sort(Comparator.comparing(StarSystemSimulationState::systemId));

        List<FleetWorldState> fleets = new ArrayList<>();
        long next = 1L;
        for (StarSystemSimulationState system : orderedSystems) {
            List<EntityState> entities = new ArrayList<>(system.simulationState().entities());
            entities.sort(Comparator.comparing(EntityState::id));
            for (EntityState entity : entities) {
                if (!isFleet(entity)) {
                    continue;
                }
                fleets.add(FleetWorldState.inSystem(
                        new FleetId(next++), system.systemId(), entity.id()));
            }
        }
        return new Result(next, List.copyOf(fleets));
    }

    private static boolean isFleet(EntityState state) {
        return state.identity() != null && "FLEET".equals(state.identity().kindName());
    }

    record Result(long nextFleetIdValue, List<FleetWorldState> fleets) {
        Result {
            if (nextFleetIdValue <= 0L) {
                throw new IllegalArgumentException("Fleet allocator watermark должен быть положительным");
            }
            fleets = List.copyOf(fleets);
        }
    }
}

package com.spacesim.world;

import com.spacesim.persistence.EntityState;

import java.util.Objects;

/** Persistent snapshot флота между двумя local StarSystems. */
public record FleetTransitState(
        StarSystemId originSystemId,
        StarSystemId destinationSystemId,
        EntityState entityState) {
    /** Проверяет обязательные поля transit snapshot. */
    public FleetTransitState {
        Objects.requireNonNull(originSystemId, "Transit origin StarSystemId не задан");
        Objects.requireNonNull(destinationSystemId, "Transit destination StarSystemId не задан");
        Objects.requireNonNull(entityState, "Transit EntityState не задан");
        if (originSystemId.equals(destinationSystemId)) {
            throw new IllegalArgumentException("Transit должен менять StarSystem");
        }
        if (entityState.identity() == null || !"FLEET".equals(entityState.identity().kindName())) {
            throw new IllegalArgumentException("Transit state должен описывать fleet");
        }
    }
}

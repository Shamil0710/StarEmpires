package com.spacesim.persistence;

import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.PriceRecorderSystem;

import java.util.List;

/** Deterministic single-entity envelope used by world fleet persistence. */
final class FleetPayloadCodec {
    private FleetPayloadCodec() {
        throw new AssertionError("Utility class");
    }

    static byte[] encode(EntityState entity) {
        long nextId = entity.id().value() == Long.MAX_VALUE ? Long.MAX_VALUE : entity.id().value() + 1L;
        GameState envelope = new GameState(
                GameState.CURRENT_VERSION,
                0L,
                new SimulationClock.State(0.1f, 0L, 0d, 0d, true, 0L),
                nextId,
                1L,
                1L,
                new GlobalEventManager.State(0d, 0L, Double.POSITIVE_INFINITY, 0d, List.of(), List.of()),
                new AsteroidSpawnSystem.State(false, 0d, 0L, 0L),
                new PriceRecorderSystem.State(0f),
                new EconomicLedger.State(1L, List.of()),
                List.of(entity));
        return GameStateCodec.encode(envelope);
    }

    static EntityState decode(byte[] bytes) {
        GameState envelope = GameStateCodec.decode(bytes);
        if (envelope.entities().size() != 1) {
            throw new IllegalArgumentException("Fleet envelope must contain one entity");
        }
        EntityState entity = envelope.entities().get(0);
        if (entity.identity() == null || !"FLEET".equals(entity.identity().kindName())) {
            throw new IllegalArgumentException("Fleet envelope entity has wrong kind");
        }
        return entity;
    }
}

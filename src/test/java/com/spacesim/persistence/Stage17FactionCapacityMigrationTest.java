package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ReputationComponent;
import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17FactionCapacityMigrationTest {
    @Test
    void currentSchemaNormalizesLegacyThreeFactionReputationVector() {
        GameState baseline = SimulationSession.createDemo(0x17A2B5L).snapshot();
        EntityState legacyEntity = entityWithReputation(List.of(12.5f, -7f, 33f));
        GameState legacyShape = replaceEntities(baseline, List.of(legacyEntity));

        GameState decoded = GameStateCodec.decode(GameStateCodec.encode(legacyShape));
        EntityState.ReputationState reputation = decoded.entities().get(0).reputation();

        assertNotNull(reputation);
        assertEquals(Constants.MAX_FACTIONS, reputation.values().size());
        assertEquals(12.5f, reputation.values().get(0), 0f);
        assertEquals(-7f, reputation.values().get(1), 0f);
        assertEquals(33f, reputation.values().get(2), 0f);
        for (int factionId = Constants.LEGACY_FACTION_COUNT;
                factionId < Constants.MAX_FACTIONS;
                factionId++) {
            assertEquals(0f, reputation.values().get(factionId), 0f);
        }
    }

    @Test
    void normalizedStateRestoresAndUsesDynamicFactionSlots() {
        GameState baseline = SimulationSession.createDemo(0x17A2C5L).snapshot();
        GameState decoded = GameStateCodec.decode(GameStateCodec.encode(
                replaceEntities(baseline, List.of(entityWithReputation(List.of(1f, 2f, 3f))))));

        Entity runtime = EntityStateMapper.restore(decoded.entities().get(0));
        ReputationComponent reputation = runtime.getComponent(ReputationComponent.class);

        assertNotNull(reputation);
        int dynamicFactionId = Constants.MAX_FACTIONS - 1;
        assertEquals(0f, reputation.getReputation(dynamicFactionId), 0f);
        reputation.addReputation(dynamicFactionId, 9.5f);
        assertEquals(9.5f, reputation.getReputation(dynamicFactionId), 0f);

        EntityState recaptured = EntityStateMapper.capture(runtime);
        assertEquals(Constants.MAX_FACTIONS, recaptured.reputation().values().size());
        assertEquals(9.5f, recaptured.reputation().values().get(dynamicFactionId), 0f);
    }

    @Test
    void damagedFactionVectorIsRejectedInsteadOfSilentlyPadded() {
        GameState baseline = SimulationSession.createDemo(0x17A2D5L).snapshot();
        GameState damaged = replaceEntities(
                baseline,
                List.of(entityWithReputation(List.of(1f, 2f, 3f, 4f))));

        assertThrows(IllegalArgumentException.class,
                () -> GameStateCodec.decode(GameStateCodec.encode(damaged)));
    }

    private static EntityState entityWithReputation(List<Float> values) {
        return new EntityState(
                new EntityId(1L),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new EntityState.FactionState(Constants.FACTION_TRADE_LEAGUE),
                new EntityState.ReputationState(values),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static GameState replaceEntities(GameState source, List<EntityState> entities) {
        return new GameState(
                GameState.CURRENT_VERSION,
                source.rootSeed(),
                source.clock(),
                source.nextEntityIdValue(),
                source.eventRandomState(),
                source.asteroidRandomState(),
                source.events(),
                source.asteroidSpawner(),
                source.priceRecorder(),
                source.ledger(),
                entities);
    }
}

package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17FactionReputationPersistenceTest {
    @Test
    void currentSchemaNormalizesLegacyThreeFactionVector() {
        GameState baseline = SimulationSession.createDemo(0x17A2D1L).snapshot();
        GameState legacyShape = replaceEntities(
                baseline,
                List.of(entityWithReputation(List.of(12.5f, -7f, 33f))));

        GameState decoded = GameStateCodec.decode(GameStateCodec.encode(legacyShape));
        EntityState.ReputationState reputation = decoded.entities().get(0).reputation();

        assertNotNull(reputation);
        assertEquals(Constants.FACTION_RUNTIME_CAPACITY, reputation.values().size());
        assertEquals(12.5f, reputation.values().get(0), 0f);
        assertEquals(-7f, reputation.values().get(1), 0f);
        assertEquals(33f, reputation.values().get(2), 0f);
        for (int factionId = Constants.LEGACY_FACTION_COUNT;
                factionId < Constants.FACTION_RUNTIME_CAPACITY;
                factionId++) {
            assertEquals(0f, reputation.values().get(factionId), 0f);
        }
    }

    @Test
    void highestDynamicSlotSurvivesCaptureSaveDecodeAndRestore() {
        int dynamicFactionId = Constants.FACTION_RUNTIME_CAPACITY - 1;
        Entity runtime = new Entity();
        runtime.add(new EntityIdComponent(new EntityId(1L)));
        runtime.add(new FactionComponent(dynamicFactionId));
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(dynamicFactionId, 19.75f);
        runtime.add(reputation);

        EntityState captured = EntityStateMapper.capture(runtime);
        assertEquals(Constants.FACTION_RUNTIME_CAPACITY, captured.reputation().values().size());
        assertEquals(19.75f, captured.reputation().values().get(dynamicFactionId), 0f);

        GameState baseline = SimulationSession.createDemo(0x17A2D2L).snapshot();
        GameState decoded = GameStateCodec.decode(GameStateCodec.encode(
                replaceEntities(baseline, List.of(captured))));
        Entity restored = EntityStateMapper.restore(decoded.entities().get(0));
        ReputationComponent restoredReputation = restored.getComponent(ReputationComponent.class);

        assertNotNull(restoredReputation);
        assertEquals(19.75f, restoredReputation.getReputation(dynamicFactionId), 0f);
        EntityState recaptured = EntityStateMapper.capture(restored);
        assertEquals(Constants.FACTION_RUNTIME_CAPACITY, recaptured.reputation().values().size());
        assertEquals(19.75f, recaptured.reputation().values().get(dynamicFactionId), 0f);
    }

    @Test
    void malformedIntermediateFactionVectorIsRejected() {
        GameState baseline = SimulationSession.createDemo(0x17A2D3L).snapshot();
        GameState malformed = replaceEntities(
                baseline,
                List.of(entityWithReputation(List.of(1f, 2f, 3f, 4f))));

        assertThrows(IllegalArgumentException.class,
                () -> GameStateCodec.decode(GameStateCodec.encode(malformed)));
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

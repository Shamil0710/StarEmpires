package com.spacesim.simulation;

import com.spacesim.persistence.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationSessionPersistenceTest {
    private static final long ROOT_SEED = 0x5A7E_2026L;

    @Test
    void snapshotRestoreДаётExactПолноеСостояниеДоПродолжения() {
        SimulationSession original = SimulationSession.createDemo(ROOT_SEED);
        for (int frame = 0; frame < 31; frame++) {
            original.advanceFrame(0.37f);
        }

        GameState saved = original.snapshot();
        SimulationSession restored = SimulationSession.restore(saved);

        assertEquals(saved, restored.snapshot());
        assertEquals(original.getClock().getInterpolationAlpha(),
                restored.getClock().getInterpolationAlpha(), 0d);
    }

    @Test
    void simulateSaveLoadContinueЭквивалентенНепрерывнойСимуляции() {
        SimulationSession uninterrupted = SimulationSession.createDemo(ROOT_SEED);
        SimulationSession saveSource = SimulationSession.createDemo(ROOT_SEED);

        float[] beforeSave = {0.37f, 0.11f, 0.53f, 1f, 0.07f, 0.29f, 0.41f};
        for (int cycle = 0; cycle < 35; cycle++) {
            for (float delta : beforeSave) {
                uninterrupted.advanceFrame(delta);
                saveSource.advanceFrame(delta);
            }
        }
        assertEquals(uninterrupted.snapshot(), saveSource.snapshot());

        SimulationSession loaded = SimulationSession.restore(saveSource.snapshot());
        assertEquals(saveSource.snapshot(), loaded.snapshot());

        float[] afterLoad = {0.13f, 0.87f, 0.2f, 0.44f, 0.31f, 1.2f, 0.05f};
        for (int cycle = 0; cycle < 30; cycle++) {
            for (float delta : afterLoad) {
                uninterrupted.advanceFrame(delta);
                loaded.advanceFrame(delta);
            }
            assertEquals(uninterrupted.snapshot(), loaded.snapshot(),
                    "Состояние разошлось после continuation cycle " + cycle);
        }
    }

    @Test
    void restoreОтклоняетНеподдерживаемуюВерсию() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        GameState state = session.snapshot();
        GameState wrongVersion = new GameState(
                state.schemaVersion() + 1,
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                state.entities());

        assertThrows(IllegalArgumentException.class,
                () -> SimulationSession.restore(wrongVersion));
        assertThrows(NullPointerException.class,
                () -> SimulationSession.restore(null));
    }
}

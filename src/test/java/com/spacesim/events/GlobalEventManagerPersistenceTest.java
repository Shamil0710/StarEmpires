package com.spacesim.events;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;
import com.spacesim.simulation.StatefulRandom;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalEventManagerPersistenceTest {
    @Test
    void snapshotRestoreПродолжаетСобытияRngИТаймерБезСдвига() {
        StatefulRandom originalRandom = new StatefulRandom(123456789L);
        GlobalEventManager original = new GlobalEventManager(originalRandom, 1.25d);
        original.activateEvent(new EconomyEvent(
                "MANUAL",
                Constants.ITEM_ORE,
                1.4f,
                0.75f,
                8f,
                new Vector2(22f, 44f),
                120f));
        original.update(0.7f);

        GlobalEventManager.State savedManager = original.snapshotState();
        long savedRandom = originalRandom.getState();
        StatefulRandom restoredRandom = new StatefulRandom(savedRandom);
        GlobalEventManager restored = new GlobalEventManager(restoredRandom, savedManager);

        assertEquals(savedManager, restored.snapshotState());
        assertEquals(savedRandom, restoredRandom.getState());

        float[] continuation = {0.1f, 0.25f, 1f, 3.5f, 0.05f, 4f};
        for (float delta : continuation) {
            original.update(delta);
            restored.update(delta);
            assertEquals(original.snapshotState(), restored.snapshotState());
            assertEquals(originalRandom.getState(), restoredRandom.getState());
        }

        assertNewsEquivalent(original.consumePendingNews(), restored.consumePendingNews());
        assertEquals(original.snapshotState(), restored.snapshotState());
    }

    @Test
    void zeroRateStateСохраняетБесконечныйCountdown() {
        GlobalEventManager original = new GlobalEventManager(new StatefulRandom(1L), 0d);
        GlobalEventManager.State state = original.snapshotState();

        GlobalEventManager restored = new GlobalEventManager(new StatefulRandom(2L), state);

        assertEquals(Double.POSITIVE_INFINITY, state.secondsUntilNextSpawn());
        assertEquals(state, restored.snapshotState());
    }

    @Test
    void повреждённоеСостояниеМенеджераОтклоняется() {
        assertThrows(IllegalArgumentException.class, () -> new GlobalEventManager.State(
                -1d, 0L, 1d, 0d, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GlobalEventManager.State(
                1d, 0L, Double.POSITIVE_INFINITY, 0d, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GlobalEventManager.State(
                0d, -1L, Double.POSITIVE_INFINITY, 0d, List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new GlobalEventManager(new StatefulRandom(1L), (GlobalEventManager.State) null));
    }

    private void assertNewsEquivalent(List<NewsArticle> first, List<NewsArticle> second) {
        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            NewsArticle expected = first.get(index);
            NewsArticle actual = second.get(index);
            assertEquals(expected.headline, actual.headline);
            assertEquals(expected.content, actual.content);
            assertEquals(expected.timestamp, actual.timestamp);
            assertEquals(expected.color, actual.color);
        }
    }
}

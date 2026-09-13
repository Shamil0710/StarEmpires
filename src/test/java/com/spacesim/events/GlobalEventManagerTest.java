package com.spacesim.events;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalEventManagerTest {
    private static final long RANDOM_SEED = 7_431L;

    @Test
    void unreadPresentationRetentionKeepsNewestArticlesWithoutChangingEconomicEvents() {
        GlobalEventManager manager = managerWithoutAutomaticSpawn();
        EconomyEvent event = createEvent(10f);
        manager.activateEvent(event);
        for (int index = 0; index < GlobalEventManager.MAX_PENDING_NEWS; index++) {
            manager.publishNews(new NewsArticle("article-" + index, "causal detail", null, index));
        }
        assertThrows(NullPointerException.class, () -> manager.publishNews(null));
        assertSame(event, manager.getActiveEvents().get(0));
        assertEquals(1L, manager.getEventRevision());
        assertEquals(0d, manager.getSimulationTimeSeconds());
        List<NewsArticle> retained = manager.consumePendingNews();
        assertEquals(GlobalEventManager.MAX_PENDING_NEWS, retained.size());
        assertEquals("article-0", retained.get(0).headline);
        assertEquals("article-99999", retained.get(retained.size() - 1).headline);
        assertTrue(manager.consumePendingNews().isEmpty());
        manager.update(10f);
        assertTrue(manager.getActiveEvents().isEmpty());
        assertEquals(2L, manager.getEventRevision());
    }

    @Test
    void automaticNewsRetentionDoesNotAffectEventScheduleOrRandomState() {
        var unreadRandom = new com.spacesim.simulation.StatefulRandom(RANDOM_SEED);
        var consumedRandom = new com.spacesim.simulation.StatefulRandom(RANDOM_SEED);
        var unread = new GlobalEventManager(unreadRandom, 1d);
        var consumed = new GlobalEventManager(consumedRandom, 1d);
        long published = 0L;
        for (int interval = 0; interval < 110; interval++) {
            unread.update(1_000f);
            consumed.update(1_000f);
            published += consumed.consumePendingNews().size();
        }
        assertTrue(published > GlobalEventManager.MAX_PENDING_NEWS);
        assertEquals(GlobalEventManager.MAX_PENDING_NEWS, unread.snapshotState().pendingNews().size());
        assertEquals(consumedRandom.getState(), unreadRandom.getState());
        unread.consumePendingNews();
        assertEquals(consumed.snapshotState(), unread.snapshotState());
    }

    @Test
    void activationAndExpirationChangeRevisionOnlyWhenActiveSetChanges() {
        GlobalEventManager manager = managerWithoutAutomaticSpawn();
        EconomyEvent event = createEvent(1f);

        assertEquals(0L, manager.getEventRevision());
        manager.activateEvent(event);

        assertAll(
                () -> assertEquals(1L, manager.getEventRevision()),
                () -> assertEquals(1, manager.getActiveEvents().size()),
                () -> assertSame(event, manager.getActiveEvents().get(0)),
                () -> assertEquals(1, manager.consumePendingNews().size()),
                () -> assertTrue(manager.consumePendingNews().isEmpty())
        );

        manager.update(0.25f);

        assertAll(
                () -> assertEquals(1L, manager.getEventRevision()),
                () -> assertEquals(0.75f, event.getRemainingDurationSeconds(), 0.0001f),
                () -> assertEquals(1, manager.getActiveEvents().size())
        );

        manager.update(0.75f);

        assertAll(
                () -> assertEquals(2L, manager.getEventRevision()),
                () -> assertEquals(0f, event.getRemainingDurationSeconds()),
                () -> assertTrue(manager.getActiveEvents().isEmpty())
        );
    }

    @Test
    void activeEventViewIsReadOnlyAndReflectsManagerChanges() {
        GlobalEventManager manager = managerWithoutAutomaticSpawn();
        List<EconomyEvent> activeView = manager.getActiveEvents();
        EconomyEvent event = createEvent(10f);

        manager.activateEvent(event);

        assertAll(
                () -> assertEquals(1, activeView.size()),
                () -> assertThrows(UnsupportedOperationException.class, activeView::clear),
                () -> assertThrows(IllegalArgumentException.class, () -> manager.activateEvent(event))
        );

        assertTrue(manager.cancelEvent(event));
        assertAll(
                () -> assertTrue(activeView.isEmpty()),
                () -> assertEquals(2L, manager.getEventRevision()),
                () -> assertFalse(manager.cancelEvent(event)),
                () -> assertEquals(2L, manager.getEventRevision())
        );
    }

    @Test
    void eventDefensivelyCopiesLocationAndScopesEffectByItemAndRadius() {
        Vector2 sourceLocation = new Vector2(10f, 20f);
        EconomyEvent event = new EconomyEvent(
                "LOCAL_CRISIS",
                Constants.ITEM_FOOD,
                2f,
                1.5f,
                5f,
                sourceLocation,
                5f);

        sourceLocation.set(1_000f, 1_000f);
        Vector2 returnedLocation = event.getLocation();
        returnedLocation.set(-1_000f, -1_000f);

        assertAll(
                () -> assertEquals(new Vector2(10f, 20f), event.getLocation()),
                () -> assertTrue(event.affects(Constants.ITEM_FOOD, new Vector2(10f, 20f))),
                () -> assertFalse(event.affects(Constants.ITEM_ORE, new Vector2(10f, 20f))),
                () -> assertFalse(event.affects(Constants.ITEM_FOOD, new Vector2(15f, 20f)))
        );
    }

    @Test
    void automaticSpawnScheduleDoesNotDependOnFramePartition() {
        double spawnRatePerSecond = 2d;
        GlobalEventManager singleUpdate = new GlobalEventManager(
                new Random(RANDOM_SEED), spawnRatePerSecond);
        GlobalEventManager sixtyFourFps = new GlobalEventManager(
                new Random(RANDOM_SEED), spawnRatePerSecond);

        singleUpdate.update(10f);
        for (int frame = 0; frame < 640; frame++) {
            sixtyFourFps.update(1f / 64f);
        }

        List<EconomyEvent> singleUpdateEvents = singleUpdate.getActiveEvents();
        List<EconomyEvent> sixtyFourFpsEvents = sixtyFourFps.getActiveEvents();
        int generatedNews = singleUpdate.consumePendingNews().size();

        assertAll(
                () -> assertTrue(generatedNews > 0),
                () -> assertEquals(generatedNews, sixtyFourFps.consumePendingNews().size()),
                () -> assertEquals(singleUpdate.getEventRevision(), sixtyFourFps.getEventRevision()),
                () -> assertEquals(singleUpdateEvents.size(), sixtyFourFpsEvents.size())
        );

        for (int index = 0; index < singleUpdateEvents.size(); index++) {
            EconomyEvent expected = singleUpdateEvents.get(index);
            EconomyEvent actual = sixtyFourFpsEvents.get(index);
            assertAll(
                    () -> assertEquals(expected.getName(), actual.getName()),
                    () -> assertEquals(expected.getTargetItemId(), actual.getTargetItemId()),
                    () -> assertEquals(expected.getRemainingDurationSeconds(),
                            actual.getRemainingDurationSeconds(), 0.002f),
                    () -> assertEquals(expected.getLocation(), actual.getLocation())
            );
        }
    }

    @Test
    void extremeFiniteDeltaUsesBoundedAutomaticSpawnWork() {
        GlobalEventManager manager = new GlobalEventManager(new Random(RANDOM_SEED), 1d);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> manager.update(Float.MAX_VALUE));

        int generatedNews = manager.consumePendingNews().size();
        assertAll(
                () -> assertEquals(1_024, generatedNews),
                () -> assertTrue(manager.getActiveEvents().isEmpty())
        );
    }

    @Test
    void exactSpawnLimitPreservesCountdownWhenNoEventIsSkipped() {
        Random oneSecondIntervals = new Random(0L) {
            @Override
            public double nextDouble() {
                return Math.exp(-1d);
            }
        };
        GlobalEventManager manager = new GlobalEventManager(oneSecondIntervals, 1d);

        manager.update(1_024.5f);

        assertEquals(1_024, manager.consumePendingNews().size());
        manager.update(0.49f);
        assertTrue(manager.consumePendingNews().isEmpty());
        manager.update(0.01f);
        assertEquals(1, manager.consumePendingNews().size());
    }

    @Test
    void invalidConfigurationAndTimeAreRejected() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new GlobalEventManager(null, 0d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GlobalEventManager(new Random(1L), -0.1d)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GlobalEventManager(new Random(1L), Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> managerWithoutAutomaticSpawn().update(-0.1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> managerWithoutAutomaticSpawn().update(Float.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> managerWithoutAutomaticSpawn().update(Float.POSITIVE_INFINITY))
        );
    }

    @Test
    void invalidEventParametersAreRejected() {
        Vector2 location = new Vector2(0f, 0f);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent(" ", Constants.ITEM_FOOD, 1f, 1f, 1f, location, 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.MAX_ITEMS, 1f, 1f, 1f, location, 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.ITEM_FOOD, 0f, 1f, 1f, location, 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.ITEM_FOOD, 1f, -1f, 1f, location, 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.ITEM_FOOD, 1f, 1f, 0f, location, 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.ITEM_FOOD, 1f, 1f, 1f,
                                new Vector2(Float.NaN, 0f), 1f)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EconomyEvent("EVENT", Constants.ITEM_FOOD, 1f, 1f, 1f, location, 0f))
        );
    }

    private GlobalEventManager managerWithoutAutomaticSpawn() {
        return new GlobalEventManager(new Random(RANDOM_SEED), 0d);
    }

    private EconomyEvent createEvent(float durationSeconds) {
        return new EconomyEvent(
                "TEST_EVENT",
                Constants.ITEM_FOOD,
                2f,
                1.5f,
                durationSeconds,
                new Vector2(10f, 20f),
                50f);
    }
}

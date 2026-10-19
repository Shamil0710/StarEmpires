package com.spacesim.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMarketObservationStateTest {
    @Test
    void recordsQuotesPerStationAndCommodityInOrder() {
        var state = PlayerMarketObservationState.empty()
                .record("hub.9", "ore", 10, 48_000, 43_200)
                .record("hub.2", "ore", 12, 50_000, 45_000)
                .record("hub.9", "ore", 20, 49_000, 44_100)
                .record("hub.9", "alloy", 20, 92_000, 82_800);
        assertEquals(List.of("hub.2/ore", "hub.9/alloy", "hub.9/ore"),
                state.series().stream().map(s -> s.stationId() + "/" + s.commodityId()).toList(),
                "series are kept sorted by station then commodity");
        assertEquals(List.of(new PlayerMarketObservationState.Quote(10, 48_000, 43_200),
                new PlayerMarketObservationState.Quote(20, 49_000, 44_100)), state.quotes("hub.9", "ore"));
        assertTrue(state.quotes("hub.5", "ore").isEmpty());
        assertEquals(20, state.latestTick());
        assertEquals(-1, PlayerMarketObservationState.empty().latestTick());
    }

    @Test
    void sameTickReplacesAndIdenticalRepeatIsANoOp() {
        var once = PlayerMarketObservationState.empty().record("hub.9", "ore", 10, 48_000, 43_200);
        assertSame(once, once.record("hub.9", "ore", 10, 48_000, 43_200), "identical same-tick observation changes nothing");
        var replaced = once.record("hub.9", "ore", 10, 47_000, 42_300);
        assertEquals(List.of(new PlayerMarketObservationState.Quote(10, 47_000, 42_300)), replaced.quotes("hub.9", "ore"),
                "docking and a trade on one tick leave the later quote");
        assertThrows(IllegalArgumentException.class, () -> replaced.record("hub.9", "ore", 9, 1, 1),
                "observations never go back in time");
    }

    @Test
    void fullSeriesDropsTheOldestQuote() {
        var state = PlayerMarketObservationState.empty();
        for (int tick = 0; tick < PlayerMarketObservationState.MAX_QUOTES_PER_SERIES + 5; tick++) {
            state = state.record("hub.9", "ore", tick, 1_000 + tick, 900 + tick);
        }
        var quotes = state.quotes("hub.9", "ore");
        assertEquals(PlayerMarketObservationState.MAX_QUOTES_PER_SERIES, quotes.size());
        assertEquals(5, quotes.get(0).tick(), "the five oldest observations were dropped");
        assertEquals(PlayerMarketObservationState.MAX_QUOTES_PER_SERIES + 4, quotes.get(quotes.size() - 1).tick());
    }

    @Test
    void stateIsImmutableAndValidated() {
        var quotes = new ArrayList<PlayerMarketObservationState.Quote>();
        quotes.add(new PlayerMarketObservationState.Quote(1, 10, 9));
        var series = new PlayerMarketObservationState.Series("hub.9", "ore", quotes);
        quotes.clear();
        assertEquals(1, series.quotes().size(), "series copies its quotes");
        assertThrows(UnsupportedOperationException.class, () -> series.quotes().add(new PlayerMarketObservationState.Quote(2, 1, 1)));

        assertThrows(IllegalArgumentException.class, () -> new PlayerMarketObservationState.Quote(1, 0, 9));
        assertThrows(IllegalArgumentException.class, () -> new PlayerMarketObservationState.Series("hub.9", "ore",
                List.of(new PlayerMarketObservationState.Quote(5, 1, 1), new PlayerMarketObservationState.Quote(5, 2, 2))));
        assertThrows(IllegalArgumentException.class, () -> new PlayerMarketObservationState(List.of(
                new PlayerMarketObservationState.Series("hub.9", "ore", List.of(new PlayerMarketObservationState.Quote(1, 1, 1))),
                new PlayerMarketObservationState.Series("hub.2", "ore", List.of(new PlayerMarketObservationState.Quote(1, 1, 1))))),
                "unsorted series are rejected");
        assertThrows(IllegalArgumentException.class, () -> PlayerMarketObservationState.empty().record(" ", "ore", 1, 1, 1));
    }
}

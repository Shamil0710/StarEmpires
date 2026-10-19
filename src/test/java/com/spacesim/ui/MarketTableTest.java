package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTableTest {
    @Test
    void rowShowsPricesHoldAndOneTrendAgainstTheOpeningQuote() {
        // Ore at 48.00 cr/kg against a 42.86 cr/kg opening quote: 12 % above base.
        var ore = new MarketTable.Row("ore", "Металлическая руда", 48_000L, 43_200L, 1_800d, 42_860L);
        assertEquals(List.of("Металлическая руда", "48.00", "43.20", "1,800 кг", "—", "+12 %"), MarketTable.cells(ore));
        assertEquals(MarketTable.Trend.ABOVE, MarketTable.trend(ore));

        var cheap = new MarketTable.Row("alloy", "Сплав", 92_000L, 82_800L, 0d, 100_000L);
        assertEquals("-8 %", MarketTable.cells(cheap).get(5));
        assertEquals(MarketTable.Trend.BELOW, MarketTable.trend(cheap));

        var opening = new MarketTable.Row("water", "Вода", 100_400L, 90_360L, 12.5d, 100_000L);
        assertEquals(MarketTable.Trend.NEAR, MarketTable.trend(opening), "0.4 % counts as the opening quote");
        assertEquals("0 %", MarketTable.cells(opening).get(5));
        assertEquals("13 кг", MarketTable.cells(opening).get(3));
        assertEquals("0.0 кг", MarketTable.cells(cheap).get(3));
    }

    @Test
    void ownStationHasNoPricesOrTrend() {
        var own = new MarketTable.Row("ore", "Металлическая руда", null, null, 300d, 42_860L);
        assertEquals(List.of("Металлическая руда", "—", "—", "300 кг", "—", "—"), MarketTable.cells(own));
        assertEquals(MarketTable.Trend.NONE, MarketTable.trend(own));
        assertEquals(MarketTable.Trend.NONE, MarketTable.trend(new MarketTable.Row("x", "X", 1_000L, 900L, 0d, null)));
    }

    @Test
    void numericColumnsAreRightAlignedAndTheNameTakesTheRest() {
        var spans = MarketTable.columns(10f, 410f, new float[] {60f, 60f, 70f, 40f}, 8f, 0f);
        // Right edges from the right border leftwards, each numeric column keeps its width.
        assertArrayEquals(new float[] {148f, 216f, 284f, 362f, 410f},
                new float[] {spans.get(0).right(), spans.get(1).right(), spans.get(2).right(), spans.get(3).right(),
                        spans.get(4).right()}, 1e-4f);
        assertEquals(10f, spans.get(0).left(), 1e-4f);
        assertEquals(60f, spans.get(1).width(), 1e-4f);
        assertEquals(40f, spans.get(4).width(), 1e-4f);

        // Too narrow for the numbers: they shrink, and every column stays inside the edges in order.
        var narrow = MarketTable.columns(10f, 150f, new float[] {60f, 60f, 70f, 40f}, 8f, 0f);
        assertEquals(10f, narrow.get(0).left(), 1e-4f);
        assertEquals(150f, narrow.get(4).right(), 1e-4f);
        for (int index = 0; index < narrow.size(); index++) {
            assertTrue(narrow.get(index).left() >= 10f - 1e-4f && narrow.get(index).right() <= 150f + 1e-4f,
                    "column " + index + " leaves the table");
            if (index > 0) assertTrue(narrow.get(index).left() >= narrow.get(index - 1).right() - 1e-4f);
        }
    }

    @Test
    void namesStayReadableInTheSmallestSupportedInspector() {
        // 900 x 620 at scale 0.8: a 254.4 px table with five numeric columns including the history line.
        float scale = 0.8f;
        float[] numbers = {48f, 56f, 54f, 48f * scale / 0.8f, 44f};
        var spans = MarketTable.columns(0f, 254.4f, numbers, 9f * scale, 72f * scale);
        assertEquals(6, spans.size());
        assertTrue(spans.get(0).width() >= 72f * scale - 1e-3f, "the name column keeps its minimum width");
        assertEquals(254.4f, spans.get(5).right(), 1e-3f);
        for (int index = 0; index < spans.size(); index++) {
            assertTrue(spans.get(index).left() >= -1e-3f && spans.get(index).right() <= 254.4f + 1e-3f, "column " + index);
            if (index > 0) assertTrue(spans.get(index).left() >= spans.get(index - 1).right() - 1e-3f, "column " + index + " overlaps");
        }
    }

    @Test
    void historyIsDroppedBeforeNumbersWouldBeTruncated() {
        float[] numbers = {48f, 56f, 54f, 48f, 44f};
        // A wide inspector: everything fits.
        assertTrue(MarketTable.historyFits(350f, numbers, 7.2f, 57.6f));
        // A 290 px table: room for names and numbers, not for the history line, which goes first.
        assertFalse(MarketTable.historyFits(290f, numbers, 7.2f, 57.6f));
        float[] withoutHistory = {48f, 56f, 54f, 0f, 44f};
        var spans = MarketTable.columns(0f, 290f, withoutHistory, 7.2f, 57.6f);
        assertEquals(48f, spans.get(1).width(), 1e-3f, "numbers keep their full width once the history is dropped");
        assertEquals(44f, spans.get(5).width(), 1e-3f);
        assertEquals(spans.get(3).right() + 7.2f, spans.get(5).left(), 1e-3f, "the hidden column takes no gap");
        assertEquals(57.6f + 48f + 56f + 54f + 44f + 4 * 7.2f, MarketTable.neededWidth(withoutHistory, 7.2f, 57.6f), 1e-3f);
    }

    @Test
    void rowsAreFrozenAndValidated() {
        var rows = new java.util.ArrayList<MarketTable.Row>();
        rows.add(new MarketTable.Row("ore", "Руда", 1L, 1L, 0d, 1L));
        var table = new MarketTable("station:hub.9", rows);
        rows.clear();
        assertEquals(1, table.rows().size());
        assertThrows(IllegalArgumentException.class, () -> new MarketTable.Row("ore", "Руда", 1L, 1L, -1d, 1L));
        assertThrows(NullPointerException.class, () -> new MarketTable(null, List.of()));
        assertEquals(6, MarketTable.HEADERS.size());
        assertEquals("История", MarketTable.HEADERS.get(MarketTable.HISTORY_COLUMN));
        var observed = new MarketTable.Row("ore", "Руда", 1L, 1L, 0d, 1L, List.of(new MarketTable.HistoryPoint(3, 1L)));
        assertEquals("", MarketTable.cells(observed).get(MarketTable.HISTORY_COLUMN), "an observed history is drawn, not written");
        assertEquals(1, observed.history().size());
    }
}

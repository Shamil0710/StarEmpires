package com.spacesim.ui;

import com.spacesim.economy.Money;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Market quotes of the station the player's ship is docked at, as an inspector table.
 *
 * <p>Quotes are disclosed only while docked. The trend column compares the station's current
 * buy price with the disclosed opening quote of the same commodity; it is not a price history.
 * Pure presentation data and formatting: it never reads or changes simulation state.</p>
 *
 * @param stationStableId system-map id of the docked station ({@code station:<id>})
 * @param rows one row per commodity the ship and the station can handle
 */
public record MarketTable(String stationStableId, List<Row> rows) {
    /** Column headers, left to right. */
    static final List<String> HEADERS = List.of("Товар", "Покупка", "Продажа", "В трюме", "К базе");
    /** Relative deviation below which a price counts as the opening quote. */
    static final double NEAR_BASE = 0.005d;

    /** Deviation of the current buy price from the disclosed opening quote. */
    enum Trend {
        /** No quote (own station or missing base). */ NONE,
        /** Within half a percent of the opening quote. */ NEAR,
        /** More expensive than the opening quote. */ ABOVE,
        /** Cheaper than the opening quote. */ BELOW
    }

    /**
     * One commodity row.
     *
     * @param commodityId exact commodity id
     * @param commodity player-facing commodity name
     * @param buyMilliPerKg price the pilot pays per kilogram, or null at an own station
     * @param sellMilliPerKg price the pilot receives per kilogram, or null at an own station
     * @param holdKg mass of the commodity in the ship's hold
     * @param baseMilliPerKg disclosed opening buy quote per kilogram, or null when unknown
     */
    public record Row(String commodityId, String commodity, Long buyMilliPerKg, Long sellMilliPerKg, double holdKg, Long baseMilliPerKg) {
        /**
         * Validates one row.
         *
         * @param commodityId exact commodity id
         * @param commodity player-facing commodity name
         * @param buyMilliPerKg price the pilot pays per kilogram, or null at an own station
         * @param sellMilliPerKg price the pilot receives per kilogram, or null at an own station
         * @param holdKg mass of the commodity in the ship's hold
         * @param baseMilliPerKg disclosed opening buy quote per kilogram, or null when unknown
         */
        public Row {
            Objects.requireNonNull(commodityId, "commodityId");
            Objects.requireNonNull(commodity, "commodity");
            if (!Double.isFinite(holdKg) || holdKg < 0d) throw new IllegalArgumentException("hold mass must be finite and non-negative");
        }
    }

    /**
     * Validates and freezes the table.
     *
     * @param stationStableId system-map id of the docked station
     * @param rows one row per commodity the ship and the station can handle
     */
    public MarketTable {
        Objects.requireNonNull(stationStableId, "stationStableId");
        rows = List.copyOf(rows);
    }

    /**
     * @param row commodity row
     * @return trend of the current buy price against the opening quote
     */
    static Trend trend(Row row) {
        if (row.buyMilliPerKg() == null || row.baseMilliPerKg() == null || row.baseMilliPerKg() <= 0L) return Trend.NONE;
        double delta = deviation(row);
        return Math.abs(delta) < NEAR_BASE ? Trend.NEAR : delta > 0d ? Trend.ABOVE : Trend.BELOW;
    }

    /**
     * @param row commodity row
     * @return formatted cells in {@link #HEADERS} order
     */
    static List<String> cells(Row row) {
        String trend = switch (trend(row)) {
            case NONE -> "—";
            case NEAR -> "0 %";
            default -> String.format(Locale.ROOT, "%+d %%", Math.round(deviation(row) * 100d));
        };
        return List.of(row.commodity(), price(row.buyMilliPerKg()), price(row.sellMilliPerKg()),
                mass(row.holdKg()), trend);
    }

    /**
     * Horizontal span of one column.
     *
     * @param left left edge
     * @param right right edge
     */
    record Span(float left, float right) {
        float width() {
            return right - left;
        }
    }

    /**
     * Lays out columns between two edges: the first (name) column takes the remaining width and is
     * left-aligned; the numeric columns keep their widths and are right-aligned.
     *
     * @param left left edge
     * @param right right edge
     * @param numericWidths widths of the numeric columns, left to right
     * @param gap gap between columns
     * @return spans, the name column first; when the numeric columns do not fit they shrink
     *         proportionally (their cells are then truncated), so every span stays between the edges
     */
    static List<Span> columns(float left, float right, float[] numericWidths, float gap) {
        float available = Math.max(0f, right - left - numericWidths.length * gap);
        float total = 0f;
        for (float width : numericWidths) total += width;
        float shrink = total > available && total > 0f ? available / total : 1f;
        Span[] spans = new Span[numericWidths.length + 1];
        float x = right;
        for (int index = numericWidths.length - 1; index >= 0; index--) {
            float width = numericWidths[index] * shrink;
            spans[index + 1] = new Span(x - width, x);
            x -= width + gap;
        }
        spans[0] = new Span(left, Math.max(left, x));
        return List.of(spans);
    }

    private static double deviation(Row row) {
        return (double) (row.buyMilliPerKg() - row.baseMilliPerKg()) / row.baseMilliPerKg();
    }

    private static String mass(double kg) {
        return String.format(Locale.ROOT, kg >= 10d ? "%,.0f кг" : "%.1f кг", kg);
    }

    private static String price(Long milli) {
        return milli == null ? "—" : String.format(Locale.ROOT, "%,.2f", Money.toCredits(milli));
    }
}

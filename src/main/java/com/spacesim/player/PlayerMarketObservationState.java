package com.spacesim.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Market quotes the player actually saw while docked: the pilot's own buy and sell quotes at a
 * station, timestamped by the authoritative clock. Nothing is reconstructed between visits, and
 * history of a station that disappeared is kept as past knowledge.
 *
 * @param series one quote series per (station, commodity), sorted by station then commodity
 */
public record PlayerMarketObservationState(List<Series> series) {
    /** Most quotes kept per (station, commodity); the oldest is dropped first. */
    public static final int MAX_QUOTES_PER_SERIES = 128;
    /** Most (station, commodity) series kept in one campaign. */
    public static final int MAX_SERIES = 4096;
    /** Longest station or commodity identity. */
    public static final int MAX_ID_LENGTH = 256;

    /**
     * One observed pair of quotes.
     *
     * @param tick authoritative tick of the observation
     * @param buyMilliPerKg price the pilot would pay per kilogram
     * @param sellMilliPerKg price the pilot would receive per kilogram
     */
    public record Quote(long tick, long buyMilliPerKg, long sellMilliPerKg) {
        /**
         * Validates one observation.
         *
         * @param tick authoritative tick of the observation
         * @param buyMilliPerKg price the pilot would pay per kilogram
         * @param sellMilliPerKg price the pilot would receive per kilogram
         */
        public Quote {
            if (tick < 0 || buyMilliPerKg <= 0 || sellMilliPerKg <= 0) {
                throw new IllegalArgumentException("Invalid market observation");
            }
        }
    }

    /**
     * Quotes of one commodity at one station, oldest first.
     *
     * @param stationId observed station
     * @param commodityId observed commodity
     * @param quotes observations in strictly increasing tick order
     */
    public record Series(String stationId, String commodityId, List<Quote> quotes) {
        /**
         * Validates and freezes one series.
         *
         * @param stationId observed station
         * @param commodityId observed commodity
         * @param quotes observations in strictly increasing tick order
         */
        public Series {
            requireId(stationId, "stationId");
            requireId(commodityId, "commodityId");
            quotes = List.copyOf(quotes);
            if (quotes.isEmpty() || quotes.size() > MAX_QUOTES_PER_SERIES) {
                throw new IllegalArgumentException("Market observation series outside bounds");
            }
            for (int index = 1; index < quotes.size(); index++) {
                if (quotes.get(index).tick() <= quotes.get(index - 1).tick()) {
                    throw new IllegalArgumentException("Market observations must have increasing ticks");
                }
            }
        }

        private int compareKey(String station, String commodity) {
            int byStation = stationId.compareTo(station);
            return byStation != 0 ? byStation : commodityId.compareTo(commodity);
        }
    }

    /**
     * Validates canonical order and bounds.
     *
     * @param series one quote series per (station, commodity), sorted by station then commodity
     */
    public PlayerMarketObservationState {
        series = List.copyOf(series);
        if (series.size() > MAX_SERIES) throw new IllegalArgumentException("Too many market observation series");
        for (int index = 1; index < series.size(); index++) {
            Series previous = series.get(index - 1);
            if (previous.compareKey(series.get(index).stationId(), series.get(index).commodityId()) >= 0) {
                throw new IllegalArgumentException("Market observation series must be unique and sorted");
            }
        }
    }

    /** @return no observations */
    public static PlayerMarketObservationState empty() {
        return new PlayerMarketObservationState(List.of());
    }

    /**
     * Adds one observation. A second observation on the same tick replaces the first, so docking,
     * a trade and periodic sampling on one tick leave one point; the oldest point of a full series
     * is dropped; a new series beyond {@link #MAX_SERIES} is not started.
     *
     * @param stationId observed station
     * @param commodityId observed commodity
     * @param tick authoritative tick, not earlier than the series' last observation
     * @param buyMilliPerKg price the pilot would pay per kilogram
     * @param sellMilliPerKg price the pilot would receive per kilogram
     * @return updated observations
     */
    public PlayerMarketObservationState record(String stationId, String commodityId, long tick,
            long buyMilliPerKg, long sellMilliPerKg) {
        Quote quote = new Quote(tick, buyMilliPerKg, sellMilliPerKg);
        int index = find(stationId, commodityId);
        List<Series> next = new ArrayList<>(series);
        if (index >= 0) {
            List<Quote> quotes = new ArrayList<>(series.get(index).quotes());
            Quote last = quotes.get(quotes.size() - 1);
            if (tick < last.tick()) throw new IllegalArgumentException("Market observation from the past");
            if (tick == last.tick()) {
                if (last.equals(quote)) return this;
                quotes.set(quotes.size() - 1, quote);
            } else {
                quotes.add(quote);
                if (quotes.size() > MAX_QUOTES_PER_SERIES) quotes.remove(0);
            }
            next.set(index, new Series(stationId, commodityId, quotes));
        } else {
            if (series.size() >= MAX_SERIES) return this;
            next.add(-index - 1, new Series(stationId, commodityId, List.of(quote)));
        }
        return new PlayerMarketObservationState(next);
    }

    /**
     * @param stationId station
     * @param commodityId commodity
     * @return observed quotes, oldest first, or an empty list
     */
    public List<Quote> quotes(String stationId, String commodityId) {
        int index = find(stationId, commodityId);
        return index >= 0 ? series.get(index).quotes() : List.of();
    }

    /** @return tick of the latest observation, or -1 without observations */
    public long latestTick() {
        long latest = -1L;
        for (Series value : series) latest = Math.max(latest, value.quotes().get(value.quotes().size() - 1).tick());
        return latest;
    }

    /** Binary search by (station, commodity): index when found, otherwise {@code -(insertion point) - 1}. */
    private int find(String stationId, String commodityId) {
        requireId(stationId, "stationId");
        requireId(commodityId, "commodityId");
        int low = 0;
        int high = series.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int comparison = series.get(middle).compareKey(stationId, commodityId);
            if (comparison < 0) low = middle + 1;
            else if (comparison > 0) high = middle - 1;
            else return middle;
        }
        return -(low + 1);
    }

    private static void requireId(String id, String label) {
        Objects.requireNonNull(id, label);
        if (id.isBlank() || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("Invalid " + label);
    }
}

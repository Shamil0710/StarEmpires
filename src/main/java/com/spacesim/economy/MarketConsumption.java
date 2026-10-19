package com.spacesim.economy;

import java.util.Set;

/**
 * External consumption at civilian trade hubs: the population and industry outside the simulated
 * traffic buy a finite share of a hub's stock every game hour at the hub's own ask, so the operator
 * earns what pilots and freight bring in. The consumed mass leaves the world; the payment is a
 * disclosed money source. Service stock the hub keeps for ships and pilots is never consumed.
 */
public final class MarketConsumption {
    /** Share of each consumable commodity's stock bought by consumers per game hour. */
    public static final double FRACTION_PER_HOUR = 0.05d;
    /** Ledger reason of the consumer payment. */
    public static final String POLICY = "market-consumption.v1";
    /** Commodities a hub stocks as a service (reaction mass, chemical feedstock), never consumed. */
    public static final Set<String> SERVICE_STOCK = Set.of(
            "commodity.material.purified_water", "commodity.feedstock.water_ice", "commodity.feedstock.volatile_feedstock");

    private MarketConsumption() { }

    /**
     * @param storedKg current stock
     * @return whole kilograms consumers buy from it this hour (0 below one kilogram)
     */
    public static double hourlyKg(double storedKg) {
        return Math.floor(Math.max(0d, storedKg) * FRACTION_PER_HOUR);
    }

    /**
     * Removes bought stock from the hub.
     * @param storage actual hub storage
     * @param commodity consumed commodity
     * @param massKg positive whole mass, at most the stored mass
     */
    public static void consume(Stage18StationStorage storage, String commodity, double massKg) {
        storage.removeCommodity(commodity, massKg);
    }
}

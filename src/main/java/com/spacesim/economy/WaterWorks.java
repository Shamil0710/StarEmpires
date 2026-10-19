package com.spacesim.economy;

import com.spacesim.content.Stage18RefiningCatalogLoader;

/**
 * Water works of a volatile-processing depot: ice already in the depot's store is purified into
 * reaction-mass water by the authored recipe. The discarded fraction leaves as process tailings.
 */
public final class WaterWorks {
    /** Authored purification recipe. */
    public static final String RECIPE = "refining.water_purification";
    /** Ice commodity the works consume. */
    public static final String ICE = "commodity.feedstock.water_ice";
    /** Water commodity the works produce. */
    public static final String WATER = "commodity.material.purified_water";
    /** Facility whose presence in a station design makes it a depot with water works. */
    public static final String FACILITY = "facility.processing.volatiles";
    /** Water a depot produces per game hour while below its target. */
    public static final double WATER_PER_HOUR_KG = 250_000d;

    private WaterWorks() { }

    /** @return water per kilogram of ice by the authored recipe */
    public static double waterPerIceKg() {
        return Stage18RefiningCatalogLoader.loadDefault().findRecipe(RECIPE).outputMassFraction();
    }

    /**
     * Purifies stored ice into stored water.
     * @param storage depot store holding at least {@code iceKg} of ice and room for the water
     * @param iceKg positive ice mass
     * @return water produced
     */
    public static double purify(Stage18StationStorage storage, double iceKg) {
        double water = iceKg * waterPerIceKg();
        storage.removeCommodity(ICE, iceKg);
        storage.addCommodity(WATER, water);
        return water;
    }
}

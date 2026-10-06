package com.spacesim.campaign;

import java.util.Map;

/** Authored prices for the ordinary sale of existing civilian industrial stations. */
public final class GeneratedCampaignStationSalePolicy {
    /** Stable pricing contract; does not alter historical stock or ownership on restore. */
    public static final String VERSION = "stage23b.existing-civilian-station-sale.v1";
    private static final Map<String, Long> PRICES = Map.of(
            "station.infrastructure.volatile_depot", 50_000_000L,
            "station.infrastructure.refinery_complex", 180_000_000L,
            "station.infrastructure.industrial_station", 70_000_000L,
            "station.infrastructure.high_tech_hub", 350_000_000L,
            "station.infrastructure.trade_logistics_hub", 150_000_000L,
            "station.infrastructure.frontier_multipurpose", 200_000_000L);
    private GeneratedCampaignStationSalePolicy() { }

    /**
     * Looks up the disclosed whole-object price without valuing hidden inventory for the buyer.
     * @param archetype actual physical station archetype
     * @return price in milli-credits, or zero for protected/non-sale designs
     */
    public static long priceMilliCredits(String archetype) { return PRICES.getOrDefault(archetype, 0L); }
}

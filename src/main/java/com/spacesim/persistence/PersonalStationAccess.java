package com.spacesim.persistence;

import com.spacesim.world.FleetId;

/**
 * Stations a pilot personally owns, as world logistics sees them. The campaign owns the pilot and
 * implements this; the generated world only asks. A pilot's own fleets use own stock without a sale;
 * any other fleet may take stock there only by paying the owner, and never for free.
 */
public interface PersonalStationAccess {
    /** No pilot owns anything: the generated world before or without a campaign. */
    PersonalStationAccess NONE = new PersonalStationAccess() {
        @Override public boolean personallyOwned(String stationId) { return false; }
        @Override public boolean personalFleet(FleetId fleetId) { return false; }
        @Override public double payableKg(String stationId, String buyerFactionId, String commodityId) { return 0d; }
        @Override public long askMilliCreditsPerKg(String stationId, String commodityId) { return 0; }
        @Override public void settle(String stationId, String buyerFactionId, String commodityId, double massKg, long ask) {
            throw new IllegalStateException("No personal station sells stock");
        }
    };

    /**
     * @param stationId existing endpoint
     * @return whether a pilot personally owns it
     */
    boolean personallyOwned(String stationId);

    /**
     * @param fleetId existing fleet
     * @return whether it is one of the pilot's own fleets
     */
    boolean personalFleet(FleetId fleetId);

    /**
     * @param stationId personally owned endpoint
     * @param buyerFactionId stable faction of the buying fleet, or null
     * @param commodityId commodity bought from the station's stock
     * @return kilograms the buyer can pay for now at the station's price (0 when it cannot or will not)
     */
    double payableKg(String stationId, String buyerFactionId, String commodityId);

    /**
     * @param stationId personally owned endpoint
     * @param commodityId commodity offered
     * @return the station's current price per kilogram, quoted before stock leaves (0 when not for sale)
     */
    long askMilliCreditsPerKg(String stationId, String commodityId);

    /**
     * Pays the owner for stock already taken; the caller checked {@link #payableKg} on the same state.
     * @param stationId personally owned endpoint
     * @param buyerFactionId stable paying faction
     * @param commodityId commodity bought
     * @param massKg positive mass taken
     * @param ask price per kilogram quoted before the stock was taken
     * @throws IllegalStateException if the payment cannot settle
     */
    void settle(String stationId, String buyerFactionId, String commodityId, double massKg, long ask);
}

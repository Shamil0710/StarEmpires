package com.spacesim.economy;

/**
 * New-game opening inventory of an existing station: finite stock placed once when a new campaign is
 * confirmed, never on restore. It is a disclosed source of physical mass, like opening market capital.
 */
public final class NewGameOpeningStock {
    private NewGameOpeningStock() { }

    /**
     * @param storage actual station storage
     * @param commodity stocked commodity
     * @param massKg positive opening mass; must fit the storage class's remaining capacity
     */
    public static void stock(Stage18StationStorage storage, String commodity, double massKg) {
        if (!Double.isFinite(massKg) || massKg <= 0) throw new IllegalArgumentException("Opening stock must be positive");
        storage.addCommodity(commodity, massKg);
    }
}

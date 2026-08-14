package com.spacesim.player;

import java.util.List;

/**
 * Read-only market/cargo/wallet snapshot for the currently docked player ship.
 *
 * @param station persistent docking reference
 * @param walletMilliCredits authoritative player wallet balance
 * @param cargoUsed total physical cargo units aboard active ship
 * @param cargoCapacity total ship cargo capacity
 * @param marketAccessAllowed whether current affiliation may trade with this station
 * @param items deterministic item rows in catalog runtime order
 */
public record PlayerMarketView(
        DiscoveredObjectRef station,
        long walletMilliCredits,
        int cargoUsed,
        int cargoCapacity,
        boolean marketAccessAllowed,
        List<PlayerMarketItemView> items) {
    /**
     * Canonicalizes the exposed row list.
     *
     * @param station persistent docking reference
     * @param walletMilliCredits authoritative player wallet balance
     * @param cargoUsed total physical cargo units aboard active ship
     * @param cargoCapacity total ship cargo capacity
     * @param marketAccessAllowed whether current affiliation may trade with this station
     * @param items deterministic item rows in catalog runtime order
     */
    public PlayerMarketView {
        items = List.copyOf(items);
    }
}

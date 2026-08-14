package com.spacesim.player;

/**
 * Read-only one-item row for the docked player market UI.
 *
 * @param itemContentId stable item content ID
 * @param displayName localized/display item name
 * @param runtimeItemId dense runtime item ID
 * @param stationStock current physical station stock
 * @param targetStock station target stock
 * @param playerCargo current physical cargo aboard the active ship
 * @param playerBuyPrice price per unit paid by the player to the station
 * @param playerSellPrice price per unit paid by the station to the player
 * @param tradable whether the market currently enables this item
 */
public record PlayerMarketItemView(
        String itemContentId,
        String displayName,
        int runtimeItemId,
        int stationStock,
        int targetStock,
        int playerCargo,
        float playerBuyPrice,
        float playerSellPrice,
        boolean tradable) {
}

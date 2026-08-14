package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Explicit economic offer to transfer one already-existing physical fleet to the player.
 *
 * <p>The value object does not create or reserve a ship. It identifies a live seller station, a
 * live {@link FleetId} and a positive price. {@link PlayerShipProgressionService} revalidates the
 * seller, fleet placement, faction relationship and player docking state immediately before a
 * purchase, then delegates the actual money/ownership transfer to {@link PlayerOwnershipService}.</p>
 *
 * @param systemId system containing both the seller and offered fleet
 * @param sellerStationId persistent local EntityId of the seller station
 * @param fleetId persistent physical fleet offered for transfer
 * @param priceMilliCredits positive purchase price in authoritative milli-credits
 */
public record PlayerShipSaleOffer(
        StarSystemId systemId,
        EntityId sellerStationId,
        FleetId fleetId,
        long priceMilliCredits) {
    /** Validates stable references and a strictly positive price. */
    public PlayerShipSaleOffer {
        Objects.requireNonNull(systemId, "Sale-offer systemId not set");
        Objects.requireNonNull(sellerStationId, "Sale-offer sellerStationId not set");
        Objects.requireNonNull(fleetId, "Sale-offer fleetId not set");
        if (priceMilliCredits <= 0L) {
            throw new IllegalArgumentException("Sale-offer price must be positive");
        }
    }
}

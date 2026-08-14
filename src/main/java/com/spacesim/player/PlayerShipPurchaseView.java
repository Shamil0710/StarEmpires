package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Read-only evaluation of one explicit ship-sale offer for player UI/tests.
 *
 * @param status current authoritative eligibility result
 * @param systemId offer system
 * @param sellerStationId seller station ID
 * @param fleetId offered persistent FleetId
 * @param shipName human-readable live ship name when resolvable, otherwise an empty string
 * @param archetypeContentId stable ship archetype ID when resolvable, otherwise an empty string
 * @param priceMilliCredits requested transfer price
 * @param playerWalletMilliCredits current player wallet
 */
public record PlayerShipPurchaseView(
        Status status,
        StarSystemId systemId,
        EntityId sellerStationId,
        FleetId fleetId,
        String shipName,
        String archetypeContentId,
        long priceMilliCredits,
        long playerWalletMilliCredits) {

    /** Player-readable purchase eligibility result. */
    public enum Status {
        /** Offer is fully valid and affordable. */
        AVAILABLE("Корабль доступен для покупки"),
        /** Player is not docked at the exact seller station. */
        NOT_DOCKED_AT_SELLER("Необходимо пристыковаться к станции-продавцу"),
        /** Seller station no longer provides the required live economic identity. */
        INVALID_SELLER("Станция-продавец недоступна"),
        /** Offered FleetId is missing, travelling, in another system or not a live ship. */
        FLEET_NOT_AVAILABLE("Корабль больше не доступен для продажи"),
        /** The player already owns this FleetId. */
        ALREADY_OWNED("Корабль уже принадлежит игроку"),
        /** Seller faction does not match the offered ship's current faction. */
        SELLER_MISMATCH("Продавец не уполномочен передать этот корабль"),
        /** Player wallet cannot pay the explicit offer price. */
        INSUFFICIENT_FUNDS("Недостаточно средств для покупки");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        /** @return localized player-readable status text */
        public String getDisplayName() {
            return displayName;
        }
    }

    /** Validates immutable view fields. */
    public PlayerShipPurchaseView {
        Objects.requireNonNull(status, "Purchase status not set");
        Objects.requireNonNull(systemId, "Purchase systemId not set");
        Objects.requireNonNull(sellerStationId, "Purchase sellerStationId not set");
        Objects.requireNonNull(fleetId, "Purchase fleetId not set");
        shipName = shipName == null ? "" : shipName;
        archetypeContentId = archetypeContentId == null ? "" : archetypeContentId;
        if (priceMilliCredits <= 0L || playerWalletMilliCredits < 0L) {
            throw new IllegalArgumentException("Purchase money values are invalid");
        }
    }

    /** @return whether purchase can be submitted immediately */
    public boolean purchasable() {
        return status == Status.AVAILABLE;
    }
}

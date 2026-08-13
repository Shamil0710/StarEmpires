package com.spacesim.trade;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * System-qualified market identity for cross-system planning.
 *
 * @param systemId owning StarSystem
 * @param market immutable local market snapshot
 */
public record SystemMarketRef(
        StarSystemId systemId,
        MarketDirectory.StationMarket market) {

    /**
     * @param systemId owning StarSystem
     * @param market immutable market snapshot
     */
    public SystemMarketRef {
        Objects.requireNonNull(systemId, "Market StarSystemId не задан");
        Objects.requireNonNull(market, "StationMarket не задан");
    }

    /** @return local persistent market EntityId within the owning system */
    public EntityId entityId() {
        return market.id();
    }
}

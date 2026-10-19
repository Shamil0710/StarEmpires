package com.spacesim.campaign;

import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.PersonalStationAccess;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FleetId;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The campaign's answer to world logistics about the pilot's stations: the pilot's own fleets use
 * own stock freely, and an NPC faction buys stock above the pilot's reserve at the pilot's price (or the
 * station's market ask), paid from its treasury into the pilot's wallet. It refuses a price above any
 * market's ceiling and gets nothing it cannot pay for.
 */
final class GeneratedCampaignPersonalStations implements PersonalStationAccess {
    /** Ledger reason of an NPC purchase from a pilot-owned station. */
    static final String POLICY = "personal-station-sale.v1";

    private final Stage228CampaignAuthority campaign;
    private PlayerState ownedFor;
    private Set<String> ownedStations = Set.of();

    GeneratedCampaignPersonalStations(Stage228CampaignAuthority campaign) {
        this.campaign = campaign;
    }

    @Override
    public boolean personallyOwned(String stationId) {
        return owned().contains(stationId);
    }

    /** Derived from the immutable player state and recomputed only when it changes; planning asks often. */
    private Set<String> owned() {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.ownedStations().isEmpty()) return Set.of();
        if (player != ownedFor) {
            ownedStations = campaign.coordinator().runtime().infrastructure().endpoints().stream()
                    .map(e -> e.stationId()).filter(campaign::ownsProductionStation).collect(Collectors.toUnmodifiableSet());
            ownedFor = player;
        }
        return ownedStations;
    }

    @Override
    public boolean personalFleet(FleetId fleetId) {
        return campaign.playerState().map(p -> p.ownedFleetIds().contains(fleetId)).orElse(false);
    }

    @Override
    public double payableKg(String stationId, String buyerFactionId, String commodityId) {
        long ask = askOrZero(stationId, commodityId);
        var player = campaign.playerState().orElse(null);
        // Both sides must be able to settle before any stock moves: the buyer's money and the pilot's wallet room.
        long available = player == null ? 0 : Math.min(spendable(buyerFactionId), Long.MAX_VALUE - player.walletMilliCredits());
        if (ask <= 0 || available <= 0 || ask > acceptableOrZero(commodityId)) return 0d;
        // The pilot's reserve is never offered; one whole kilogram of margin keeps the rounded-up payment within funds.
        double forSale = campaign.coordinator().runtime().infrastructure().endpoint(stationId).storage().commodityMassKg(commodityId)
                - campaign.stationSalePolicy(stationId, commodityId).reserveKg();
        return Math.max(0d, Math.min(Math.floor(forSale), Math.floor((double) available / ask) - 1d));
    }

    private static long acceptableOrZero(String commodityId) {
        try {
            return Stage228CampaignAuthority.npcAcceptableAsk(commodityId);
        } catch (RuntimeException unquoted) {
            return 0;
        }
    }

    @Override
    public long askMilliCreditsPerKg(String stationId, String commodityId) {
        return askOrZero(stationId, commodityId);
    }

    @Override
    public void settle(String stationId, String buyerFactionId, String commodityId, double massKg, long ask) {
        if (ask <= 0 || !(massKg > 0)) throw new IllegalStateException("No payable personal station sale");
        long amount = Math.max(1L, (long) Math.ceil(massKg * ask));
        var player = campaign.playerState().orElseThrow();
        var wallet = new WalletComponent(player.walletMilliCredits());
        if (!campaign.coordinator().runtime().world().transferFromFactionTreasury(buyerFactionId, wallet, "PLAYER", amount,
                POLICY + ':' + stationId + ':' + commodityId))
            throw new IllegalStateException("Buyer treasury or pilot wallet rejected the personal station sale");
        campaign.receivePersonalStationSale(wallet.getBalanceMilliCredits(), stationId, commodityId, massKg, amount);
    }

    private long spendable(String factionId) {
        if (factionId == null) return 0;
        return campaign.coordinator().runtime().world().findFactionEconomicState(factionId)
                .map(e -> e.treasuryMilliCredits() - e.treasuryReserveFloorMilliCredits()).orElse(0L);
    }

    private long askOrZero(String stationId, String commodityId) {
        try {
            return campaign.stationSaleAsk(stationId, commodityId);
        } catch (RuntimeException unquoted) {
            return 0; // no market or no admitted quote: nothing is sold
        }
    }
}

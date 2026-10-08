package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.world.FleetLocationKind;

/** Existing civilian operators finance their actually contacted market from finite treasury money. */
final class GeneratedCampaignMarketWorkingCapital {
    private GeneratedCampaignMarketWorkingCapital() { }

    static void review(Stage228CampaignAuthority campaign) {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.activeFleetId() == null || !player.docked()
                || !player.ownedFleetIds().contains(player.activeFleetId())) return;
        var runtime = campaign.coordinator().runtime(); var world = runtime.world();
        var fleet = world.findFleet(player.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM || world.findFleetJump(fleet.id()).isPresent()
                || !fleet.systemId().equals(player.dockedAt().systemId())) return;
        var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElse(null);
        if (physical == null || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > 1d) return;
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(fleet.systemId()) || campaign.ownsProductionStation(endpoint.stationId())
                    || GeneratedCampaignStationSalePolicy.priceMilliCredits(endpoint.stationArchetypeId()) <= 0
                    || physical.position().distanceTo(endpoint.position()) > 1000d
                    || campaign.pilotMarketReference(endpoint.stationId()).filter(player.dockedAt()::equals).isEmpty()) continue;
            var entity = world.findSession(endpoint.systemId()).orElseThrow().getEntityRegistry().require(player.dockedAt().entityId());
            var legal = entity.getComponent(FactionComponent.class);
            var wallet = entity.getComponent(WalletComponent.class);
            var identity = entity.getComponent(IdentityComponent.class);
            if (legal == null || wallet == null || identity == null
                    || !(identity.name.equals(Stage228CampaignAuthority.PILOT_MARKET_V2_IDENTITY_PREFIX + endpoint.stationId())
                            || identity.name.equals(Stage228CampaignAuthority.PILOT_MARKET_IDENTITY_PREFIX + endpoint.stationId()))) return;
            var faction = world.findFactionStableId(legal.factionId);
            if (faction.isEmpty()) return;
            var economy = world.findFactionEconomicState(faction.orElseThrow());
            if (economy.isEmpty()) return;
            long deficit = Stage228CampaignAuthority.PILOT_MARKET_INITIAL_LIQUIDITY - wallet.getBalanceMilliCredits();
            long available = Math.max(0, economy.orElseThrow().treasuryMilliCredits() - economy.orElseThrow().treasuryReserveFloorMilliCredits());
            long amount = Math.min(deficit, available);
            if (amount > 0) world.transferFromFactionTreasury(faction.orElseThrow(), wallet, identity.name, amount,
                    "civilian-market-working-capital.v1:" + endpoint.stationId());
            return;
        }
    }
}

package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.MarketConsumption;

import java.util.HashSet;
import java.util.TreeMap;

/**
 * Once per game hour, consumers buy a share of every civilian trade hub's stock at the hub's ask
 * and pay its operator's treasury ({@link MarketConsumption}). Stock an NPC freight order is due to
 * load there is left alone. A pure function of the tick and the saved state, so it needs no state.
 */
final class GeneratedCampaignMarketConsumption {
    static final String HUB_ARCHETYPE = "station.infrastructure.trade_logistics_hub";

    private GeneratedCampaignMarketConsumption() { }

    static void review(Stage228CampaignAuthority campaign, long tick) {
        var runtime = campaign.coordinator().runtime();
        long interval = Math.round(3600d / campaign.coordinator().session().fixedStepSeconds());
        if (tick <= 0 || tick % interval != 0) return;
        var world = runtime.world();
        var reserved = new HashSet<String>();
        for (var order : runtime.freight().capture().orders()) reserved.add(order.sourceEndpointId() + '|' + order.commodityId());
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.stationArchetypeId().equals(HUB_ARCHETYPE) || campaign.ownsProductionStation(endpoint.stationId())) continue;
            var ref = campaign.pilotMarketReference(endpoint.stationId()).orElse(null);
            if (ref == null) continue;
            var legal = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                    .getComponent(FactionComponent.class);
            var operator = legal == null ? java.util.Optional.<String>empty() : world.findFactionStableId(legal.factionId);
            if (operator.isEmpty() || world.findFactionEconomicState(operator.orElseThrow()).isEmpty()) continue;
            var bought = new TreeMap<String, Double>();
            long payment = 0;
            for (var stock : endpoint.storage().snapshotCommodityMassByIdKg().entrySet()) {
                if (MarketConsumption.SERVICE_STOCK.contains(stock.getKey())
                        || reserved.contains(endpoint.stationId() + '|' + stock.getKey())) continue;
                double kg = MarketConsumption.hourlyKg(stock.getValue());
                long quote = askOrZero(campaign, endpoint.stationId(), stock.getKey());
                if (kg < 1 || quote <= 0) continue;
                bought.put(stock.getKey(), kg);
                payment = Math.addExact(payment, Math.multiplyExact((long) kg, quote));
            }
            if (payment <= 0) continue;
            pay(campaign, endpoint.stationId(), ref.systemId(), operator.orElseThrow(), payment);
            bought.forEach((commodity, kg) -> MarketConsumption.consume(endpoint.storage(), commodity, kg));
        }
    }

    private static long askOrZero(Stage228CampaignAuthority campaign, String stationId, String commodity) {
        try {
            return campaign.pilotCommodityPrice(stationId, commodity, true);
        } catch (IllegalArgumentException unquoted) {
            return 0; // a commodity the market has no admitted quote for is not sold to consumers
        }
    }

    private static void pay(Stage228CampaignAuthority campaign, String stationId, com.spacesim.world.StarSystemId system,
            String operator, long amount) {
        var world = campaign.coordinator().runtime().world();
        var funding = new WalletComponent();
        if (!funding.creditFromSource(amount)) throw new IllegalStateException("Consumer payment cannot be credited");
        String source = "market-consumers:" + stationId;
        world.findSession(system).orElseThrow().getLedger().recordMoneySource(source, amount, MarketConsumption.POLICY);
        if (!world.transferToFactionTreasury(operator, funding, source, amount, MarketConsumption.POLICY))
            throw new IllegalStateException("Consumer payment cannot settle in the operator treasury");
    }
}

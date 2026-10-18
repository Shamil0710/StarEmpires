package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.economy.MarketConsumption;
import com.spacesim.economy.NewGameOpeningStock;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit stock fixture at one hub; the twin campaign without it isolates the consumer purchase. */
@Tag("slow")
class GeneratedCampaignMarketConsumptionTest {
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final String VOLATILES = "commodity.feedstock.volatile_feedstock";
    private static final double STOCK_KG = 100_000d;

    @Test void consumersBuyAnHourlyShareAtTheAskAndPayTheOperatorButLeaveServiceStock() {
        var stocked = newGame();
        var plain = newGame();
        String hub = "hub." + stocked.coordinator().runtime().world().findFleet(stocked.playerState().orElseThrow().activeFleetId())
                .orElseThrow().systemId().value();
        NewGameOpeningStock.stock(stocked.coordinator().runtime().infrastructure().endpoint(hub).storage(), ORE, STOCK_KG);
        String operator = operator(stocked, hub);
        long ask = stocked.pilotCommodityPrice(hub, ORE, true);
        long interval = Math.round(3600d / stocked.coordinator().session().fixedStepSeconds());

        advance(stocked, interval - 1); advance(plain, interval - 1);
        assertEquals(STOCK_KG + ore(plain, hub), ore(stocked, hub), 1e-6, "nothing is consumed before the hour completes");
        long treasuryBefore = treasury(stocked, operator) - treasury(plain, operator);
        var saved = stocked.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        double volatiles = stock(stocked, hub, VOLATILES);

        advance(stocked, 1); advance(plain, 1); advance(restored, 1);
        assertEquals(STOCK_KG * (1 - MarketConsumption.FRACTION_PER_HOUR), ore(stocked, hub) - ore(plain, hub), 1.0);
        assertEquals((long) (STOCK_KG * MarketConsumption.FRACTION_PER_HOUR) * ask,
                treasury(stocked, operator) - treasury(plain, operator) - treasuryBefore, ask, "the operator receives the consumers' payment");
        assertEquals(volatiles, stock(stocked, hub, VOLATILES), "service stock is not consumed");
        assertEquals(stocked.captureState(), restored.captureState(), "consumption is a pure function of the saved state and the tick");
    }

    private static Stage228CampaignAuthority newGame() {
        var c = Stage228CampaignAuthority.create(2);
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        return c;
    }

    private static void advance(Stage228CampaignAuthority c, long ticks) {
        c.coordinator().setPaused(false);
        for (long i = 0; i < ticks; i++) c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
    }

    private static String operator(Stage228CampaignAuthority c, String station) {
        var world = c.coordinator().runtime().world();
        var ref = c.pilotMarketReference(station).orElseThrow();
        var legal = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId()).getComponent(FactionComponent.class);
        return world.findFactionStableId(legal.factionId).orElseThrow();
    }

    private static long treasury(Stage228CampaignAuthority c, String faction) {
        return c.coordinator().runtime().world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
    }

    private static double ore(Stage228CampaignAuthority c, String station) { return stock(c, station, ORE); }

    private static double stock(Stage228CampaignAuthority c, String station, String commodity) {
        return c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity);
    }
}

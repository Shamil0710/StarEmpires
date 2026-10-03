package com.spacesim.campaign;

import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.LocalPhysicalKinematics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPhysicalMarketTest {
    private static final String WATER = "commodity.material.purified_water";

    @Test void newGlobalMarketsHaveOnlyDisclosedFiniteWorkingCapitalAndRestoreNeverRecommissions() {
        var c = started(); var r = c.coordinator().runtime(); long capital = 0;
        var endpoints = r.infrastructure().endpoints(); assertEquals(72, endpoints.size());
        for (var endpoint : endpoints) {
            var reference = c.pilotMarketReference(endpoint.stationId()).orElseThrow();
            var entity = r.world().findSession(reference.systemId()).orElseThrow().getEntityRegistry().require(reference.entityId());
            assertTrue(entity.getComponent(IdentityComponent.class).name.startsWith(Stage228CampaignAuthority.PILOT_MARKET_V2_IDENTITY_PREFIX));
            assertNull(entity.getComponent(InventoryComponent.class));
            long balance = entity.getComponent(WalletComponent.class).getBalanceMilliCredits();
            assertEquals(10_000_000L, balance); capital += balance;
        }
        assertEquals(720_000_000L, capital);
        long sources = r.world().getTopology().systems().stream().mapToLong(system -> r.world().findSession(system.id()).orElseThrow()
                .getLedger().getEntries().stream().filter(e -> e.reason().equals("new-game-market-working-capital.v2"))
                .mapToLong(e -> e.moneyMilliCredits()).sum()).sum();
        assertEquals(capital, sources); roundtrip(c);
        assertFalse(Stage228CampaignAuthority.restore(c.captureState()).canStartIndependentPilot());
    }

    @Test void genuineV1CheckpointKeepsHomeOnlyLiquidityAndStaticQuotesAfterCurrentRestore() throws Exception {
        byte[] bytes;
        try (var in = new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/campaign/stage23b-pilot-opening-v1.s25.gz"))) { bytes = in.readAllBytes(); }
        var saved = Stage228GeneratedCampaignPersistenceCodec.decode(bytes); var original = bytes.clone();
        var c = Stage228CampaignAuthority.restore(saved); assertEquals(saved, c.captureState());
        var r = c.coordinator().runtime(); var p = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        assertEquals(2, r.infrastructure().endpoints().stream().filter(e -> c.pilotMarketReference(e.stationId()).isPresent()).count());
        var market = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(p.systemId()) && e.storage().commodityMassKg(WATER) > 0).findFirst().orElseThrow();
        assertEquals(5000L, c.pilotCommodityPrice(market.stationId(), WATER, true));
        assertEquals(4500L, c.pilotCommodityPrice(market.stationId(), WATER, false));
        assertEquals(saved, c.captureState()); assertArrayEquals(original, bytes); assertFalse(c.canStartIndependentPilot());
        roundtrip(c);
    }

    @Test void actualNeighborTradeMovesOneExistingKilogramAndMakesConservedProfitAcrossTransitReload() {
        var c = started(); var r = c.coordinator().runtime(); var p = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var source = r.infrastructure().endpoint("hub." + p.systemId().value());
        var destination = r.world().getTopology().neighbors(p.systemId()).stream().filter(system -> r.infrastructure().endpoints().stream()
                .anyMatch(e -> e.systemId().equals(system) && e.stationArchetypeId().equals("station.infrastructure.refinery_complex"))).findFirst().orElseThrow();
        var target = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(destination)
                && e.stationArchetypeId().equals("station.infrastructure.refinery_complex")).findFirst().orElseThrow();
        long ask = c.pilotCommodityPrice(source.stationId(), WATER, true);
        assertEquals(2500L, ask); assertTrue(c.pilotCommodityPrice(target.stationId(), WATER, false) > ask);
        long initialPersonal = c.playerState().orElseThrow().walletMilliCredits();
        long sourceMoney = wallet(c, source.stationId()).getBalanceMilliCredits(); long targetMoney = wallet(c, target.stationId()).getBalanceMilliCredits();
        double sourceStock = source.storage().commodityMassKg(WATER);
        // Explicit dock/departure geometry fixtures; consideration, hold, travel, discovery and sale are real.
        position(c, source.position()); c.submitPilotAction(c.previewPilotAction("DOCK", source.stationId(), "", 0));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.submitPilotAction(c.previewPilotAction("BUY", source.stationId(), WATER, 1));
        assertEquals(sourceStock - 1, source.storage().commodityMassKg(WATER));
        c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        position(c, r.arrival().resolve(destination, p.systemId()).physicalState().position());
        c.submitPilotAction(c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0));
        boolean restoredTransit = false;
        for (int i = 0; i < 2000 && c.coordinator().runtime().world().findFleetJump(p.id()).isPresent(); i++) {
            c.advanceFrame(0.25f);
            var jump = c.coordinator().runtime().world().findFleetJump(p.id()).orElse(null);
            if (!restoredTransit && jump != null && jump.phase() == FleetJumpPhase.IN_TRANSIT) { c = roundtrip(c); restoredTransit = true; }
        }
        assertTrue(restoredTransit); assertEquals(destination, c.coordinator().runtime().world().findFleet(p.id()).orElseThrow().systemId());
        var actualTarget = c.coordinator().runtime().infrastructure().endpoint(target.stationId());
        double targetStock = actualTarget.storage().commodityMassKg(WATER);
        long bid = c.pilotCommodityPrice(target.stationId(), WATER, false); assertTrue(bid > ask);
        position(c, actualTarget.position()); c.submitPilotAction(c.previewPilotAction("DOCK", target.stationId(), "", 0));
        var preview = c.previewPilotAction("SELL", target.stationId(), WATER, 1); assertTrue(preview.allowed()); assertEquals(bid, preview.walletChangeMilliCredits());
        c.submitPilotAction(preview);
        assertEquals(targetStock + 1, actualTarget.storage().commodityMassKg(WATER));
        assertEquals(0d, c.coordinator().runtime().freight().findFreighter(p.id()).orElseThrow().cargoMassKg());
        long finalPersonal = c.playerState().orElseThrow().walletMilliCredits(); assertEquals(initialPersonal + bid - ask, finalPersonal);
        assertEquals(sourceMoney + ask, wallet(c, source.stationId()).getBalanceMilliCredits());
        assertEquals(targetMoney - bid, wallet(c, target.stationId()).getBalanceMilliCredits());
        assertEquals(initialPersonal + sourceMoney + targetMoney, finalPersonal + wallet(c, source.stationId()).getBalanceMilliCredits() + wallet(c, target.stationId()).getBalanceMilliCredits());
        roundtrip(c);
    }
    private static WalletComponent wallet(Stage228CampaignAuthority c, String station) { var ref = c.pilotMarketReference(station).orElseThrow(); return c.coordinator().runtime().world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId()).getComponent(WalletComponent.class); }
    private static Stage228CampaignAuthority started() { var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart()); return c; }
    private static void position(Stage228CampaignAuthority c, com.spacesim.world.LocalPhysicalPosition position) { var r = c.coordinator().runtime(); var p = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow(); r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(), LocalPhysicalKinematics.stationary(position)); }
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) { var before = c.captureState(); var result = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(before))); assertEquals(before, result.captureState()); return result; }
}

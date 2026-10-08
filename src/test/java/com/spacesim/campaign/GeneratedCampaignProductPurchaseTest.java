package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.*;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.PlayerState;
import com.spacesim.ui.GeneratedCampaignProductTransportUi;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignProductPurchaseTest {
    private static final String PRODUCT = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;

    @Test void purchaseReservesRefundsResumesAndSettlesFiniteStockAndMoneyExactlyOnce() throws Exception {
        var c = fixture();
        if (Boolean.getBoolean("stage23b.writeProductPurchaseFixture")) Stage228GeneratedCampaignPersistenceCodec.write(
                java.nio.file.Path.of("target/stage23b-product-purchase-fixture.s25"), c.captureState());
        String station = c.dockedModuleStationId().orElseThrow();
        var runtime = c.coordinator().runtime();
        String seller = runtime.industry().industrial().station(station).stableFactionId();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        long treasury = runtime.world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        long fee = FinishedProductPurchasePayment.quote(PRODUCT, 1);
        var fleet = c.playerState().orElseThrow().activeFleetId();
        assertFalse(c.previewPilotAction("LOAD_PRODUCT", station, PRODUCT, 1).allowed());
        assertTrue(GeneratedCampaignProductTransportUi.rows(c).stream()
                .anyMatch(r -> r.selection().stableId().equals("pilot-product-buy|" + station + '|' + PRODUCT)));
        var before = c.captureState();
        var preview = c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 1);
        assertTrue(preview.allowed()); assertEquals(-fee, preview.walletChangeMilliCredits());
        assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(wallet - fee, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury, runtime.world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(1, runtime.infrastructure().endpoint(station).storage().snapshotProductCountById().get(PRODUCT));
        assertEquals(0, runtime.infrastructure().endpoint(station).storage().productCount(PRODUCT));
        assertEquals(0, runtime.freight().moduleCargoStorage(fleet).productCount(PRODUCT));
        assertFalse(c.previewPilotAction("UNDOCK", "", "", 0).allowed());
        assertFalse(c.previewPilotAction("PURCHASE_STATION", station, "", 0).allowed(), "The seller cannot change during pending delivery");
        assertTrue(GeneratedCampaignProductTransportUi.rows(c).stream().anyMatch(r -> r.selection().stableId().startsWith("product-transfer-cancel|")));
        c.coordinator().setPaused(true); c.advanceFrame(1);
        assertEquals(0, c.productTransfers().orders().get(0).completedHandlingKg());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.productTransfers().orders().get(0).completedHandlingKg() > 0);
        var pending = c.captureState();
        byte[] historical = HistoricalCampaignTimeControlFixture.withoutIdleTimeControl(Stage228GeneratedCampaignPersistenceCodec.encode(pending));
        java.nio.ByteBuffer.wrap(historical).putInt(4, 16);
        java.nio.ByteBuffer.wrap(historical).putInt(8, 16);
        byte[] runtimeV16 = "m22.8.generated-campaign.v16".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(runtimeV16, 0, historical, 14, runtimeV16.length);
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(historical));
        c = roundTrip(c);
        var restored = c;
        assertThrows(IllegalStateException.class, () -> restored.submitPilotAction(preview));
        c.submitPilotAction(c.previewPilotAction("CANCEL_PRODUCT_TRANSFER", station, c.productTransfers().orders().get(0).orderId(), 0));
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(PRODUCT));
        assertEquals(treasury, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        c.submitPilotAction(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 1));
        c = roundTrip(c);
        c.coordinator().setPaused(false);
        for (int tick = 0; tick < 1000 && !c.productTransfers().orders().isEmpty(); tick++) c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.productTransfers().orders().isEmpty());
        assertEquals(wallet - fee, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + fee, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(0, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(PRODUCT));
        assertEquals(1, c.coordinator().runtime().freight().moduleCargoStorage(fleet).productCount(PRODUCT));
        assertEquals(station, c.coordinator().runtime().freight().capture().productLots().get(0).sourceEndpointId());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("PRODUCT_PURCHASE_SETTLED")).count());
        c = roundTrip(c); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(treasury + fee, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertFalse(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 1).allowed());
        roundTrip(c);
    }

    @Test void insufficientFundsAndAbsentStockCannotCreateAPurchaseReservation() {
        var c = fixture(); var p = c.playerState().orElseThrow();
        String station = c.dockedModuleStationId().orElseThrow();
        long fee = FinishedProductPurchasePayment.quote(PRODUCT, 1);
        var stocked = c.captureState();
        assertFalse(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 2).allowed());
        assertEquals(stocked, c.captureState());
        var poor = player(p, fee - 1);
        c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                c.captureState(), c.coordinator().runtime().captureState(), poor));
        var before = c.captureState();
        assertFalse(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 1).allowed());
        assertFalse(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 2).allowed());
        assertEquals(before, c.captureState());
        assertTrue(c.productTransfers().orders().isEmpty());
    }

    @Test void destroyedBuyerReleasesUndeliveredSellerStockAndRefundsHeldMoney() {
        var c = fixture(); String station = c.dockedModuleStationId().orElseThrow();
        var fleet = c.playerState().orElseThrow().activeFleetId();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        String seller = c.coordinator().runtime().industry().industrial().station(station).stableFactionId();
        long treasury = c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        c.submitPilotAction(c.previewPilotAction("BUY_PRODUCT", station, PRODUCT, 1));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertFalse(c.productTransfers().orders().isEmpty());
        c.coordinator().runtime().destroyLocalFreighter(fleet, com.spacesim.world.DestructionPolicy.destroyAll());
        var afterLoss = c.captureState();
        assertEquals(afterLoss, c.captureState(), "Refund ledger and wallet must be captured together even before another frame");
        assertTrue(c.productTransfers().orders().isEmpty());
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(1, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(PRODUCT));
        var refund = c.playerJournal().entries().stream().filter(e -> e.action().equals("PRODUCT_PURCHASE_REFUNDED")).toList();
        assertEquals(1, refund.size());
        assertEquals(FinishedProductPurchasePayment.quote(PRODUCT, 1), refund.get(0).walletDeltaMilliCredits());
        assertEquals(1, refund.get(0).quantity());
        assertTrue(c.coordinator().runtime().freight().capture().productLots().isEmpty());
        roundTrip(c);
    }

    private static Stage228CampaignAuthority fixture() {
        // Explicit berth, stock and money fixtures isolate trade; they are not ordinary acquisition evidence.
        var c = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .99, FoundedCampaignFixture.restore());
        String station = c.dockedModuleStationId().orElseThrow();
        var products = Stage22CivilianMiningProductionPath.loadProducts(); var product = products.findProduct(PRODUCT);
        var supply = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), products, "fixture.product.sale",
                Map.of(product.storageClassId(), product.unitMassKg()), Map.of(), Map.of(PRODUCT, 1));
        var handler = new Stage18LogisticsRuntime.HandlingCapability("fixture.product.sale", Set.of(product.storageClassId()), product.unitMassKg(), product.unitMassKg());
        assertTrue(new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), products).transferProduct(supply,
                c.coordinator().runtime().infrastructure().endpoint(station).storage(), PRODUCT, 1, handler, handler.openInterval(1)).transferred());
        var p = c.playerState().orElseThrow(); long balance = Math.max(p.walletMilliCredits(), Math.multiplyExact(3, FinishedProductPurchasePayment.quote(PRODUCT, 1)));
        if (balance > p.walletMilliCredits()) c.coordinator().runtime().world().findSession(p.dockedAt().systemId()).orElseThrow()
                .getLedger().recordMoneySource("PLAYER", balance - p.walletMilliCredits(), "fixture.product.purchase-funding");
        return Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                c.captureState(), c.coordinator().runtime().captureState(), player(p, balance)));
    }

    private static PlayerState player(PlayerState p, long wallet) {
        return new PlayerState(wallet, p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(), p.discoveredSystemIds(),
                p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), List.of());
    }

    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        var state = c.captureState(); var result = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(state)));
        assertEquals(state, result.captureState()); return result;
    }
}

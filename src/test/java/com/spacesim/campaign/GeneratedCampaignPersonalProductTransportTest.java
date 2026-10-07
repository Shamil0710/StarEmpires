package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignProductTransportUi;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPersonalProductTransportTest {
    @Test void foreignServiceAccessDoesNotGrantFinishedProductStockOrUnloadingRights() {
        // Explicit berth and finite product stock fixtures isolate legal admission, not acquisition.
        var c = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .99, FoundedCampaignFixture.restore());
        var own = c;
        String station = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> own.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        String productId = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var product = products.findProduct(productId);
        var supply = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), products,
                "fixture.foreign.product", Map.of(product.storageClassId(), product.unitMassKg() * 2),
                Map.of(), Map.of(productId, 2));
        var handler = new Stage18LogisticsRuntime.HandlingCapability("fixture.foreign.product",
                Set.of(product.storageClassId()), product.unitMassKg() * 2, product.unitMassKg());
        assertTrue(new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), products).transferProduct(
                supply, c.coordinator().runtime().infrastructure().endpoint(station).storage(), productId, 2,
                handler, handler.openInterval(1)).transferred());
        c.submitPilotAction(c.previewPilotAction("LOAD_PRODUCT", station, productId, 1));
        c = finish(c);
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var saved = c.captureState();
        var p = saved.playerState();
        var visitor = new com.spacesim.player.PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(),
                p.ownedFleetIds(), p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(),
                p.dockedAt(), p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), java.util.List.of());
        c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(
                saved, c.coordinator().runtime().captureState(), visitor));
        assertFalse(c.ownsProductionStation(station));
        assertTrue(c.dockedModuleStationId().filter(station::equals).isPresent(), "Actual foreign service contact remains valid");
        assertEquals(1, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(productId));
        assertEquals(1, c.coordinator().runtime().freight().moduleCargoStorage(p.activeFleetId()).productCount(productId));
        var before = c.captureState();
        assertFalse(c.previewPilotAction("LOAD_PRODUCT", station, productId, 1).allowed());
        assertFalse(c.previewPilotAction("UNLOAD_PRODUCT", station, productId, 1).allowed());
        assertTrue(GeneratedCampaignProductTransportUi.rows(c).stream()
                .noneMatch(r -> r.selection().stableId().startsWith("pilot-product-load|" + station + '|')
                        || r.selection().stableId().startsWith("pilot-product-unload|" + station + '|')));
        assertEquals(before, c.captureState(), "Refused access and projection must preserve every physical and financial owner");
        roundTrip(c);
    }

    @Test void actualCommandsReserveCancelResumeAndPublishPhysicalProductExactlyOnce() {
        // Explicit ownership/docking/stock fixtures isolate handling, without claiming ordinary acquisition.
        var c = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .99, FoundedCampaignFixture.restore());
        var initialCampaign = c;
        String station = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> initialCampaign.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        String productId = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        var products = Stage22CivilianMiningProductionPath.loadProducts(); var product = products.findProduct(productId);
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var supply = new Stage18StationStorage(ontology, products, "fixture.product.transport",
                Map.of(product.storageClassId(), product.unitMassKg()), Map.of(), Map.of(productId, 1));
        var store = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        var handler = new Stage18LogisticsRuntime.HandlingCapability("fixture.product.transport", Set.of(product.storageClassId()),
                product.unitMassKg(), product.unitMassKg());
        assertTrue(new Stage18LogisticsRuntime(ontology, products).transferProduct(supply, store, productId, 1,
                handler, handler.openInterval(1)).transferred());
        var before = c.captureState(); var preview = c.previewPilotAction("LOAD_PRODUCT", station, productId, 1);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(0, store.productCount(productId)); assertEquals(1, store.snapshot().productCountById().get(productId));
        assertFalse(c.previewPilotAction("UNDOCK", "", "", 0).allowed());
        assertTrue(GeneratedCampaignProductTransportUi.rows(c).stream().anyMatch(r -> r.selection().stableId().startsWith("product-transfer-cancel|")));
        c.coordinator().setPaused(true); c.advanceFrame(1); assertEquals(0, c.productTransfers().orders().get(0).completedHandlingKg());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertFalse(c.productTransfers().orders().isEmpty()); assertTrue(c.productTransfers().orders().get(0).completedHandlingKg() > 0);
        c = roundTrip(c);
        c.submitPilotAction(c.previewPilotAction("CANCEL_PRODUCT_TRANSFER", station, c.productTransfers().orders().get(0).orderId(), 0));
        assertEquals(1, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(productId));
        c.submitPilotAction(c.previewPilotAction("LOAD_PRODUCT", station, productId, 1));
        c = finish(c);
        var fleet = c.playerState().orElseThrow().activeFleetId();
        assertEquals(1, c.coordinator().runtime().freight().moduleCargoStorage(fleet).productCount(productId));
        assertEquals(1, c.coordinator().runtime().freight().capture().productLots().size());
        assertEquals(station, c.coordinator().runtime().freight().capture().productLots().get(0).sourceEndpointId());
        c = roundTrip(c);
        // A completed transfer consumes this tick's endpoint budget; a new job starts on the next tick.
        assertFalse(c.previewPilotAction("UNLOAD_PRODUCT", station, productId, 1).allowed());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var unload = c.previewPilotAction("UNLOAD_PRODUCT", station, productId, 1); assertTrue(unload.allowed()); c.submitPilotAction(unload);
        c = finish(roundTrip(c));
        assertTrue(c.coordinator().runtime().freight().capture().productLots().isEmpty());
        assertEquals(1, c.coordinator().runtime().infrastructure().endpoint(station).storage().productCount(productId));
        assertEquals(before.playerState().walletMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        roundTrip(c);
    }

    private static Stage228CampaignAuthority finish(Stage228CampaignAuthority c) {
        c.coordinator().setPaused(false);
        for (int tick = 0; tick < 1000 && !c.productTransfers().orders().isEmpty(); tick++)
            c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.productTransfers().orders().isEmpty(), "Actual handling must finish within bounded physical ticks"); return c;
    }

    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        var saved = c.captureState(); var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState()); return restored;
    }
}

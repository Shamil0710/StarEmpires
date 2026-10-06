package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignFinishedProductPersistenceTest {
    @Test void historicalNativeV11AddsEmptyYardConstructionWithoutGrantingStructures() throws Exception {
        var saved = FoundedCampaignFixture.restore().captureState();
        var buffer = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(buffer)) {
            out.writeInt(0x53323843); out.writeInt(11); out.writeInt(11); out.writeUTF("m22.8.generated-campaign.v11");
            for (var payload : java.util.List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(saved.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(saved.smallCraft()), Stage228HangarPersistenceCodec.encode(saved.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(saved.flightDeck()), Stage228OperationsPersistenceCodec.encode(saved.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(saved.playerState()), PlayerJournalPersistenceCodec.encode(saved.playerJournal()),
                    ShipyardModuleCustodyPersistenceCodec.encode(saved.moduleCustody()), ShipyardRepairQueuePersistenceCodec.encode(saved.repairQueue()),
                    ShipyardRefitQueuePersistenceCodec.encode(saved.refitQueue()), ShipyardModuleTransferQueuePersistenceCodec.encode(saved.moduleTransfers()),
                    FinishedProductTransferQueuePersistenceCodec.encode(saved.productTransfers()))) {
                out.writeInt(payload.length); out.write(payload);
            }
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(buffer.toByteArray());
        assertEquals(saved, migrated);
        assertEquals(com.spacesim.economy.Stage23YardConstructionWorkQueue.State.empty(), migrated.yardConstruction());
    }
    @Test void historicalNativeV10AddsEmptyHandlingWithoutGrantingStockOrProgress() throws Exception {
        var saved = FoundedCampaignFixture.restore().captureState();
        var buffer = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(buffer)) {
            out.writeInt(0x53323843); out.writeInt(10); out.writeInt(10); out.writeUTF("m22.8.generated-campaign.v10");
            for (var payload : java.util.List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(saved.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(saved.smallCraft()), Stage228HangarPersistenceCodec.encode(saved.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(saved.flightDeck()), Stage228OperationsPersistenceCodec.encode(saved.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(saved.playerState()), PlayerJournalPersistenceCodec.encode(saved.playerJournal()),
                    ShipyardModuleCustodyPersistenceCodec.encode(saved.moduleCustody()), ShipyardRepairQueuePersistenceCodec.encode(saved.repairQueue()),
                    ShipyardRefitQueuePersistenceCodec.encode(saved.refitQueue()), ShipyardModuleTransferQueuePersistenceCodec.encode(saved.moduleTransfers()))) {
                out.writeInt(payload.length); out.write(payload);
            }
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(buffer.toByteArray());
        assertEquals(saved, migrated);
        assertEquals(com.spacesim.economy.FinishedProductTransferWorkQueue.State.empty(), migrated.productTransfers());
        assertEquals(saved, Stage228CampaignAuthority.restore(migrated).captureState());
    }

    @Test void nativeCampaignRetainsFinishedGoodsOwnerMassSourceAndPhysicalUnloading() {
        var campaign = FoundedCampaignFixture.restore();
        var runtime = campaign.coordinator().runtime();
        var fleet = campaign.playerState().orElseThrow().activeFleetId();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        String productId = com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        var product = products.findProduct(productId);
        double mass = product.unitMassKg();
        assertTrue(runtime.freight().findFreighter(fleet).orElseThrow().cargoCapacityKg() >= mass);
        var station = runtime.industry().industrial().stations().stream()
                .filter(s -> s.storage().remainingCapacityKg(product.storageClassId()) >= mass).findFirst().orElseThrow();
        var store = runtime.infrastructure().endpoint(station.stationId()).storage();
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.product-handler",
                Set.of(product.storageClassId()), mass, mass);
        // Explicit finite finished-product fixture verifies persistence, not manufacture or ordinary player delivery.
        var supply = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), products, "fixture.finished-product",
                Map.of(product.storageClassId(), mass), Map.of(), Map.of(productId, 1));
        assertTrue(new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), products)
                .transferProduct(supply, store, productId, 1, handling, handling.openInterval(1)).transferred());
        double seconds = runtime.world().getAuthoritativeWorldTick() * (double) campaign.coordinator().session().fixedStepSeconds();
        assertTrue(runtime.freight().exchangePersonalProduct(fleet, store, productId, 1, true, seconds, handling, handling.openInterval(1)));
        runtime.synchronizeFreightEngineeringCargo(fleet);
        var saved = campaign.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(
                saved.stage21Runtime(), saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), null));
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, loaded.captureState(), "Native campaign must retain product lots and physical fitted mass");
        var loadedRuntime = loaded.coordinator().runtime();
        var lot = loadedRuntime.freight().capture().productLots().get(0);
        assertEquals(station.stationId(), lot.sourceEndpointId());
        assertEquals(productId, lot.productId());
        assertEquals(mass, loadedRuntime.freight().findFreighter(fleet).orElseThrow().cargoMassKg());
        var destination = loadedRuntime.infrastructure().endpoint(station.stationId()).storage();
        assertTrue(loadedRuntime.freight().exchangePersonalProduct(fleet, destination, productId, 1, false, seconds,
                handling, handling.openInterval(1)));
        loadedRuntime.synchronizeFreightEngineeringCargo(fleet);
        assertTrue(loadedRuntime.freight().capture().productLots().isEmpty());
        assertEquals(1, destination.productCount(productId));
        var unloaded = loaded.captureState();
        assertEquals(unloaded, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(unloaded))).captureState());
        assertEquals(saved, campaign.captureState(), "Loaded/unloaded owners cannot mutate the initial campaign");
        assertTrue(loadedRuntime.freight().exchangePersonalProduct(fleet, destination, productId, 1, true, seconds + 100,
                handling, handling.openInterval(1)));
        loadedRuntime.synchronizeFreightEngineeringCargo(fleet);
        assertThrows(IllegalArgumentException.class, loaded::captureState,
                "The composed checkpoint must reject product provenance from a future physical tick");
    }
}

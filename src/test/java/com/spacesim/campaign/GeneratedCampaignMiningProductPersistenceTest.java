package com.spacesim.campaign;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignMiningProductPersistenceTest {
    @Test void ordinaryCampaignRetainsFiniteFinishedMiningProductWithoutGrantingOpeningStock() {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        String module = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        double mass = products.findProduct(module).unitMassKg();
        var stations = r.industry().industrial().stations();
        assertTrue(stations.stream().allMatch(s -> s.storage().productCount(module) == 0));
        var station = stations.stream().filter(s -> s.storage().remainingCapacityKg("storage.oversized") >= mass).findFirst().orElseThrow();
        // Explicit finished-product inventory fixture proves admission/save; it is not manufacture or purchase.
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        var supplied = new com.spacesim.economy.Stage18StationStorage(ontology, products, "fixture.supplied-module",
                java.util.Map.of("storage.oversized", mass), java.util.Map.of(), java.util.Map.of(module, 1));
        var handling = new com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability("fixture.handling",
                java.util.Set.of("storage.oversized"), mass, mass);
        assertTrue(new com.spacesim.economy.Stage18LogisticsRuntime(ontology, products)
                .transferProduct(supplied, station.storage(), module, 1, handling, handling.openInterval(1)).transferred());
        assertEquals(0, supplied.productCount(module));
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertEquals(1, restored.coordinator().runtime().industry().industrial().station(station.stationId()).storage().productCount(module));
        assertEquals(c.playerState(), restored.playerState());
    }
}

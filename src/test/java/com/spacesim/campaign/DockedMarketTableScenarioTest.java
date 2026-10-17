package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ui.MarketTable;
import com.spacesim.ui.ProductionUiProjector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The system-map market table appears only while the pilot's ship is docked, on the ordinary path:
 * a new pilot flies to the hub, docks, saves and reloads, and undocks. Every price comes from the
 * station's own quote and every hold mass from the ship's hold.
 */
@Tag("scenario")
class DockedMarketTableScenarioTest {
    private static final long SEED = 2L;

    @Test
    void dockedMarketTableFollowsDockingAndSurvivesSaveLoad() {
        var campaign = Stage228CampaignAuthority.create(SEED);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(campaign, 400_000L);
        assertNull(ProductionUiProjector.dockedMarket(pilot.campaign()), "no quotes before docking");

        var miner = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        String hub = "hub." + miner.currentSystemId().value();
        pilot.fly(pilot.campaign().coordinator().runtime().infrastructure().endpoint(hub).position(), 500, 3000d);
        pilot.action("DOCK", hub, "", 0);

        MarketTable table = ProductionUiProjector.dockedMarket(pilot.campaign());
        assertNotNull(table, "docked pilot sees the station's quotes");
        assertEquals("station:" + hub, table.stationStableId());
        assertEquals(expectedCommodities(pilot, hub), table.rows().stream().map(MarketTable.Row::commodityId).toList(),
                "one row per commodity both the hold and the station handle");

        // Buy a little of the first tradeable commodity so the hold column is not trivially empty;
        // one physical ship operation per completed tick, so finish the docking tick first.
        pilot.tick();
        String bought = table.rows().stream().map(MarketTable.Row::commodityId)
                .filter(id -> pilot.allowed("BUY", hub, id, 10)).findFirst().orElseThrow();
        pilot.action("BUY", hub, bought, 10);
        pilot.tick();
        table = ProductionUiProjector.dockedMarket(pilot.campaign());
        assertNotNull(table);
        assertExactRows(pilot, hub, table);
        assertTrue(table.rows().stream().anyMatch(row -> row.commodityId().equals(bought) && row.holdKg() >= 10d - 1e-6),
                "the bought cargo shows in the hold column");

        pilot.saveAndReload();
        assertEquals(table, ProductionUiProjector.dockedMarket(pilot.campaign()), "same table after save/load");

        pilot.action("UNDOCK", "", "", 0);
        assertNull(ProductionUiProjector.dockedMarket(pilot.campaign()), "quotes disappear after undocking");
    }

    /** Every cell equals the authoritative quote, opening quote and hold mass of its commodity. */
    private static void assertExactRows(ScenarioPilot pilot, String hub, MarketTable table) {
        var hold = pilot.campaign().coordinator().runtime().freight().findFreighter(pilot.fleetId()).orElseThrow();
        for (var row : table.rows()) {
            String id = row.commodityId();
            assertEquals(pilot.campaign().pilotCommodityPrice(hub, id, true), row.buyMilliPerKg(), id + " buy");
            assertEquals(pilot.campaign().pilotCommodityPrice(hub, id, false), row.sellMilliPerKg(), id + " sell");
            assertEquals(Stage228CampaignAuthority.pilotCommodityPrice(id, true), row.baseMilliPerKg(), id + " opening");
            assertEquals(hold.cargoStorage().commodityMassByIdKg().getOrDefault(id, 0d), row.holdKg(), 1e-9, id + " hold");
        }
    }

    private static java.util.List<String> expectedCommodities(ScenarioPilot pilot, String hub) {
        var runtime = pilot.campaign().coordinator().runtime();
        var endpoint = runtime.infrastructure().endpoint(hub);
        var hold = runtime.freight().findFreighter(pilot.fleetId()).orElseThrow();
        return com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault().getCommodities().stream()
                .filter(c -> hold.cargoStorage().capacityByStorageClassKg().containsKey(c.storageClassId())
                        && endpoint.handlingCapability().supportedStorageClassIds().contains(c.storageClassId())
                        && endpoint.storage().snapshot().capacityByStorageClassKg().containsKey(c.storageClassId()))
                .map(c -> c.id()).toList();
    }
}

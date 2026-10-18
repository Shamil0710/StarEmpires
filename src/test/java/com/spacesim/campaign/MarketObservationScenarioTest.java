package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.UnsupportedSaveVersionException;
import com.spacesim.ui.ProductionUiProjector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player's market history on the ordinary path: docking records the station's quotes, an
 * hour docked adds a point, a purchase adds another, save/load keeps the history exactly, and
 * nothing is recorded once the ship has undocked. The game loads only the current save format.
 */
@Tag("scenario")
class MarketObservationScenarioTest {
    private static final long SEED = 2L;

    @Test
    void observedQuotesFollowDockingTimeAndTradesAcrossSaveLoad() throws Exception {
        var campaign = Stage228CampaignAuthority.create(SEED);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(campaign, 600_000L);
        assertTrue(pilot.campaign().marketObservations().series().isEmpty(), "nothing is known before docking");

        var miner = pilot.campaign().coordinator().runtime().freight().capture().freighters().stream()
                .filter(f -> f.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
                .findFirst().orElseThrow();
        String hub = "hub." + miner.currentSystemId().value();
        pilot.fly(pilot.campaign().coordinator().runtime().infrastructure().endpoint(hub).position(), 500, 3000d);
        long beforeDock = pilot.world().getAuthoritativeWorldTick();
        pilot.action("DOCK", hub, "", 0);

        // Docking records one quote per commodity the docked table shows, equal to the live quotes.
        var table = ProductionUiProjector.dockedMarket(pilot.campaign());
        var observations = pilot.campaign().marketObservations();
        assertEquals(table.rows().size(), observations.series().size(), "one series per disclosed commodity");
        for (var row : table.rows()) {
            var quotes = observations.quotes(hub, row.commodityId());
            assertEquals(1, quotes.size(), row.commodityId());
            assertEquals(row.buyMilliPerKg(), quotes.get(0).buyMilliPerKg());
            assertEquals(row.sellMilliPerKg(), quotes.get(0).sellMilliPerKg());
            assertTrue(quotes.get(0).tick() >= beforeDock);
        }

        // An hour docked adds a periodic observation.
        String commodity = table.rows().get(0).commodityId();
        long hourTicks = Math.round(3_600d / pilot.campaign().coordinator().session().fixedStepSeconds());
        pilot.ticks(Math.toIntExact(hourTicks + 1));
        assertTrue(observations.quotes(hub, commodity).size() < pilot.campaign().marketObservations().quotes(hub, commodity).size(),
                "time spent docked is observed");

        // A purchase records the quotes after the trade.
        pilot.tick();
        String bought = table.rows().stream().map(r -> r.commodityId()).filter(id -> pilot.allowed("BUY", hub, id, 10))
                .findFirst().orElseThrow();
        int before = pilot.campaign().marketObservations().quotes(hub, bought).size();
        pilot.action("BUY", hub, bought, 10);
        var afterTrade = pilot.campaign().marketObservations().quotes(hub, bought);
        assertEquals(before + 1, afterTrade.size(), "the trade adds one observation");
        assertEquals(pilot.campaign().pilotCommodityPrice(hub, bought, true), afterTrade.get(afterTrade.size() - 1).buyMilliPerKg());

        // The docked table's history column shows exactly the observed buy quotes.
        var shown = ProductionUiProjector.dockedMarket(pilot.campaign()).rows().stream()
                .filter(r -> r.commodityId().equals(bought)).findFirst().orElseThrow().history();
        assertEquals(afterTrade.stream().map(q -> new com.spacesim.ui.MarketTable.HistoryPoint(q.tick(), q.buyMilliPerKg())).toList(), shown);

        // Save/load keeps the history exactly, and recording continues after the load.
        var history = pilot.campaign().marketObservations();
        pilot.saveAndReload();
        assertEquals(history, pilot.campaign().marketObservations());
        pilot.ticks(Math.toIntExact(hourTicks + 1));
        assertTrue(pilot.campaign().marketObservations().quotes(hub, commodity).size() > history.quotes(hub, commodity).size(),
                "the loaded campaign keeps observing while docked");

        // A checkpoint cannot claim quotes from the future.
        var state = pilot.campaign().captureState();
        long future = pilot.world().getAuthoritativeWorldTick() + 1_000;
        var forged = state.marketObservations().record(hub, commodity, future, 1_000, 900);
        assertThrows(IllegalArgumentException.class, () -> HistoricalCampaignContentFixture.withObservations(state, forged));

        // After undocking nothing more is recorded, even over hours.
        pilot.tick();
        pilot.action("UNDOCK", "", "", 0);
        var undocked = pilot.campaign().marketObservations();
        pilot.ticks(Math.toIntExact(2 * hourTicks + 1));
        assertEquals(undocked, pilot.campaign().marketObservations(), "no observations away from the dock");

        // The game's loader accepts the current file and rejects an older dev format from its header.
        var file = Files.createTempFile("market-observations-", ".s25");
        try {
            Stage228GeneratedCampaignPersistenceCodec.write(file, pilot.campaign().captureState());
            assertEquals(pilot.campaign().captureState(), Stage228GeneratedCampaignPersistenceCodec.readCurrent(file));
            byte[] bytes = Files.readAllBytes(file);
            java.nio.ByteBuffer.wrap(bytes).putInt(Integer.BYTES, 29);
            Files.write(file, bytes);
            var rejected = assertThrows(UnsupportedSaveVersionException.class, () -> Stage228GeneratedCampaignPersistenceCodec.readCurrent(file));
            assertEquals(29, rejected.fileVersion());
            assertEquals(30, rejected.currentVersion());
            Files.write(file, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
            assertEquals(-1, assertThrows(UnsupportedSaveVersionException.class,
                    () -> Stage228GeneratedCampaignPersistenceCodec.readCurrent(file)).fileVersion());
        } finally {
            Files.deleteIfExists(file);
        }
        assertFalse(undocked.series().isEmpty());
    }
}

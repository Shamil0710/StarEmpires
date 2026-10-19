package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit ownership fixture of the local industrial station; terms are set through ordinary commands. */
@Tag("slow")
class GeneratedCampaignStationSaleUiTest {
    private static final String STATION = "industry.1";
    private static final String WATER = GeneratedCampaignStationSaleUi.WATER;

    @Test void ownStationShowsPriceAndReserveRowsThatStartFromTheCurrentTerms() {
        var start = Stage228CampaignAuthority.create(2);
        start.submitIndependentPilotStart(start.previewIndependentPilotStart());
        assertTrue(GeneratedCampaignStationSaleUi.rows(start).isEmpty(), "no own station, no sale terms");

        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(com.spacesim.campaign.GeneratedCampaignOwnedStationFixture.owning(start, STATION))));
        var rows = GeneratedCampaignStationSaleUi.rows(c);
        String price = GeneratedCampaignStationSaleUi.PRICE_PREFIX + STATION + '|' + WATER;
        String reserve = GeneratedCampaignStationSaleUi.RESERVE_PREFIX + STATION + '|' + WATER;
        assertEquals(java.util.List.of(price, reserve), rows.stream().map(r -> r.selection().stableId()).toList());
        assertEquals("SET_STATION_PRICE", GeneratedCampaignStationSaleUi.action(price));
        assertEquals("SET_STATION_RESERVE", GeneratedCampaignStationSaleUi.action(reserve));
        assertEquals("", GeneratedCampaignStationSaleUi.action("pilot-refining|x|y"));
        assertEquals(0, GeneratedCampaignStationSaleUi.currentValue(c, price));

        c.submitPilotAction(c.previewPilotAction("SET_STATION_PRICE", STATION, WATER, 80));
        c.submitPilotAction(c.previewPilotAction("SET_STATION_RESERVE", STATION, WATER, 5_000_000));
        assertEquals(80, GeneratedCampaignStationSaleUi.currentValue(c, price));
        assertEquals(5_000_000, GeneratedCampaignStationSaleUi.currentValue(c, reserve));
        var fields = GeneratedCampaignStationSaleUi.rows(c).get(0).sections().get(0).lines();
        assertTrue(fields.stream().anyMatch(l -> l.label().equals("Цена для NPC") && l.value().equals("0.080 кр./кг")));
        assertTrue(fields.stream().anyMatch(l -> l.label().equals("Неприкосновенный запас") && l.value().equals("5,000,000 кг")));
    }

    @Test void confirmButtonsNameTheTerms() {
        assertEquals("ЦЕНА ВОДЫ ДЛЯ NPC: РЫНОЧНАЯ", ConfirmButton.physicalOutcome("SET_STATION_PRICE", 0, 0));
        assertEquals("ЦЕНА ВОДЫ ДЛЯ NPC 0.080 КР/КГ", ConfirmButton.physicalOutcome("SET_STATION_PRICE", 80, 0));
        assertEquals("ЗАПАС ВОДЫ 5000000 КГ", ConfirmButton.physicalOutcome("SET_STATION_RESERVE", 5_000_000, 0));
    }
}

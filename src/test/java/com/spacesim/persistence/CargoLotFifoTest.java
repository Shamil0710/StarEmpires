package com.spacesim.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CargoLotFifoTest {
    @Test void completeRepresentableSeriesMassConsumesTheFinalReceiptWithoutAZeroMassSuffix() {
        var fleet = new com.spacesim.world.FleetId(26);
        for (var input : new double[][]{{.1, 5}, {.3, 3}, {.1216, 100003}}) {
            var series = new ExtractionReceiptSeries(41, 900, .1, input[0], (long) input[1], input[0]);
            var lot = new Stage20FreightPersistentState.CargoLotState("freight-lot:41", fleet,
                    Stage20FreightPersistentState.personalExtractionOrderId(fleet), "commodity.feedstock.metallic_ore",
                    series.remainingMassKg(), "source.actual", "player-extraction:source.actual", series.receipt(0).loadedAtSeconds(), series);
            var split = CargoLotFifo.split(lot, CargoLotFifo.availableMassKg(lot));
            assertNull(split.retained()); assertEquals(1, split.delivered().size());
            assertEquals(lot, split.delivered().get(0), "All actual receipts, their identities, timestamps and physical mass survive the transfer");
        }
    }
    @Test void aTransferCrossingReceiptBoundariesKeepsOriginalIdsTimesAndPartialTail() {
        var series = new ExtractionReceiptSeries(41, 900, .1, 2.5, 4, 2.5);
        var fleet = new com.spacesim.world.FleetId(26);
        var original = new Stage20FreightPersistentState.CargoLotState("freight-lot:41", fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), "commodity.feedstock.metallic_ore", 10,
                "source.actual", "player-extraction:source.actual", 90, series);
        var split = CargoLotFifo.split(original, 7);
        assertEquals(2, split.delivered().size());
        assertEquals(2, split.delivered().get(0).receiptSeries().receiptCount());
        assertEquals(series.receipt(0), split.delivered().get(0).receiptSeries().receipt(0));
        assertEquals(series.receipt(1), split.delivered().get(0).receiptSeries().receipt(1));
        var partial = split.delivered().get(1);
        assertEquals("freight-lot:43", partial.lotId()); assertEquals(2, partial.massKg());
        assertEquals(series.receipt(2).loadedAtSeconds(), partial.loadedAtSimulationSeconds());
        var tail = split.retained(); assertNotNull(tail);
        assertEquals("freight-lot:43", tail.lotId()); assertEquals(3, tail.massKg());
        assertEquals(.5, tail.receiptSeries().firstReceiptRemainingKg());
        assertEquals(series.receipt(3), tail.receiptSeries().receipt(1));
        var tiny = CargoLotFifo.split(tail, .25);
        assertEquals(partial.loadedAtSimulationSeconds(), tiny.delivered().get(0).loadedAtSimulationSeconds());
        assertEquals(.25, tiny.retained().receiptSeries().firstReceiptRemainingKg());
        assertEquals(41, tiny.retained().receiptSeries().originalFirstOrdinal());
        assertNull(CargoLotFifo.split(tiny.retained(), 2.75).retained());
    }
    @Test void historicalPhysicalRunningSumCannotEraseATrulyPartialReceipt() {
        var series = new ExtractionReceiptSeries(225898, 13370860, .10000000149011612,
                .733236945363057, 996, .733236945363057);
        var fleet = new com.spacesim.world.FleetId(26);
        var lot = new Stage20FreightPersistentState.CargoLotState("freight-lot:225898", fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), "commodity.feedstock.metallic_ore",
                730.3039975815965, "source.actual", "player-extraction:source.actual", series.receipt(0).loadedAtSeconds(), series);
        var full = CargoLotFifo.split(lot, CargoLotFifo.availableMassKg(lot));
        assertNull(full.retained()); assertEquals(series, full.delivered().get(0).receiptSeries());
        assertEquals(CargoLotFifo.availableMassKg(lot), full.delivered().get(0).massKg());
        var partial = CargoLotFifo.split(lot, Math.nextDown(CargoLotFifo.availableMassKg(lot)));
        assertNotNull(partial.retained()); assertTrue(partial.retained().massKg() > 0);
        assertEquals(1, partial.retained().receiptSeries().receiptCount());
        assertEquals(series.receipt(995).ordinal(), partial.retained().receiptSeries().firstOrdinal());
        assertEquals(series.receipt(995).loadedAtSeconds(), partial.retained().loadedAtSimulationSeconds());
        assertEquals(series.remainingMassKg(), partial.delivered().stream().mapToDouble(v -> v.massKg()).sum()
                + partial.retained().massKg(), series.massRoundoffBoundKg());
        var simple = new ExtractionReceiptSeries(1, 0, .1, .1, 5, .1);
        var simpleLot = new Stage20FreightPersistentState.CargoLotState("freight-lot:1", fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), lot.commodityId(), .5,
                lot.sourceEndpointId(), lot.sourceProvenanceId(), 0d, simple);
        assertNotNull(CargoLotFifo.split(simpleLot, Math.nextDown(.5)).retained(), "No epsilon may drop a genuinely partial receipt");
    }
    @Test void largePrefixHasBoundedDescriptionsAndLegacyPointsRetainTheirDate() {
        var fleet = new com.spacesim.world.FleetId(26);
        var series = new ExtractionReceiptSeries(1, 10, .1, 2.5, 200_003, 2.5);
        var lot = new Stage20FreightPersistentState.CargoLotState("freight-lot:1", fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), "commodity.feedstock.metallic_ore", series.remainingMassKg(),
                "source.actual", "player-extraction:source.actual", 1, series);
        var prefix = CargoLotFifo.split(lot, 400_001);
        assertEquals(2, prefix.delivered().size());
        assertEquals(160_000, prefix.delivered().get(0).receiptSeries().receiptCount());
        assertEquals(160_001, prefix.retained().receiptSeries().firstOrdinal());
        var point = new Stage20FreightPersistentState.CargoLotState("freight-lot:3", fleet,
                Stage20FreightPersistentState.manualCargoOrderId(fleet), lot.commodityId(), 5, "hub.actual", "player-market:hub.actual", 12.345);
        var old = CargoLotFifo.split(point, 2);
        assertEquals(12.345, old.delivered().get(0).loadedAtSimulationSeconds());
        assertEquals(12.345, old.retained().loadedAtSimulationSeconds());
        assertEquals(3, old.retained().massKg()); assertNull(old.retained().receiptSeries());
        assertThrows(IllegalArgumentException.class, () -> CargoLotFifo.split(lot, lot.massKg() + 1));
    }
}

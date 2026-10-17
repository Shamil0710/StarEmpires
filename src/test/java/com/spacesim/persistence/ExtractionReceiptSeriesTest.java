package com.spacesim.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionReceiptSeriesTest {
    @Test void cargoCannotClaimAnotherSourceIdentityTimestampOrAdditionalMass() {
        var fleet = new com.spacesim.world.FleetId(26);
        var series = new ExtractionReceiptSeries(41, 900, .1, 2.5, 4, 2.5);
        String order = Stage20FreightPersistentState.personalExtractionOrderId(fleet);
        var lot = new Stage20FreightPersistentState.CargoLotState("freight-lot:41", fleet, order, "commodity.feedstock.metallic_ore",
                10, "source.actual", "player-extraction:source.actual", 90, series);
        assertEquals(series, lot.receiptSeries());
        assertThrows(IllegalArgumentException.class, () -> new Stage20FreightPersistentState.CargoLotState("freight-lot:42", fleet,
                order, lot.commodityId(), 10, lot.sourceEndpointId(), lot.sourceProvenanceId(), 90, series));
        assertThrows(IllegalArgumentException.class, () -> new Stage20FreightPersistentState.CargoLotState(lot.lotId(), fleet,
                order, lot.commodityId(), 10, lot.sourceEndpointId(), lot.sourceProvenanceId(), 90.1, series));
        assertThrows(IllegalArgumentException.class, () -> new Stage20FreightPersistentState.CargoLotState(lot.lotId(), fleet,
                order, lot.commodityId(), 11, lot.sourceEndpointId(), lot.sourceProvenanceId(), 90, series));
        assertThrows(IllegalArgumentException.class, () -> new Stage20FreightPersistentState.CargoLotState(lot.lotId(), fleet,
                Stage20FreightPersistentState.manualCargoOrderId(fleet), lot.commodityId(), 10,
                lot.sourceEndpointId(), "player-market:source.actual", 90, series));
    }
    @Test void largeSeriesRetainsEachOriginalIdentityAndFloatingPointTimestamp() {
        double step = (double) .1f;
        long start = 8_000_000_123L;
        var series = new ExtractionReceiptSeries(41, start, step, .733236945, 200_003, .733236945);
        boolean naiveTimestampDiffers = false;
        for (long index = 0; index < series.receiptCount(); index++) {
            var receipt = series.receipt(index);
            assertEquals(41 + index, receipt.ordinal());
            assertEquals(start + index, receipt.tick());
            assertEquals(Double.doubleToLongBits((start + index) * step), Double.doubleToLongBits(receipt.loadedAtSeconds()));
            assertEquals(.733236945, receipt.remainingMassKg());
            naiveTimestampDiffers |= Double.doubleToLongBits(start * step + index * step)
                    != Double.doubleToLongBits(receipt.loadedAtSeconds());
        }
        assertTrue(naiveTimestampDiffers, "The regression must exercise timestamps that additive reconstruction changes");
    }

    @Test void partialFifoAndAppendingAfterPartialConsumptionPreserveReceiptHistory() {
        var original = new ExtractionReceiptSeries(100, 900, .1, 2.5, 4, 2.5);
        var tail = original.consumeHead(.75).orElseThrow();
        assertEquals(original.receipt(0).ordinal(), tail.receipt(0).ordinal());
        assertEquals(original.receipt(0).loadedAtSeconds(), tail.receipt(0).loadedAtSeconds());
        assertEquals(1.75, tail.receipt(0).remainingMassKg());
        tail = tail.consumeHead(1.75).orElseThrow().dropReceipts(1).orElseThrow();
        assertEquals(original.receipt(2), tail.receipt(0));
        assertEquals(original.receipt(3), tail.receipt(1));
        var extended = tail.append(104, 904, .1, 2.5);
        assertEquals(tail.receipt(0), extended.receipt(0));
        assertEquals(104, extended.receipt(2).ordinal());
        assertEquals(100, extended.originalFirstOrdinal());
        assertEquals(900, extended.originalFirstTick());
        assertEquals(5, extended.producedReceiptCount());
        assertTrue(extended.dropReceipts(3).isEmpty());
    }

    @Test void gapsChangedRatesAndOverflowCannotCreateVirtualReceipts() {
        var series = new ExtractionReceiptSeries(10, 100, .1, 2, 3, 2);
        assertFalse(series.canAppend(14, 103, .1, 2));
        assertFalse(series.canAppend(13, 104, .1, 2));
        assertFalse(series.canAppend(13, 103, Math.nextUp(.1), 2));
        assertFalse(series.canAppend(13, 103, .1, Math.nextUp(2d)));
        assertThrows(IllegalArgumentException.class, () -> series.consumeHead(3));
        assertThrows(IllegalArgumentException.class, () -> series.dropReceipts(4));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionReceiptSeries(Long.MAX_VALUE - 1, 0, .1, 1, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionReceiptSeries(1, Long.MAX_VALUE, .1, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionReceiptSeries(1, 1, Double.MAX_VALUE, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionReceiptSeries(10, 100, .1, 2, 3, 2, 9, 98));
    }
}

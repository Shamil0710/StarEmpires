package com.spacesim.persistence;

import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Constant-size FIFO slicing of a point lot or a consecutive extraction series. */
public final class CargoLotFifo {
    private CargoLotFifo() { }
    /**
     * @param lot actual remaining lot
     * @return mass described by its actual receipts
     */
    public static double availableMassKg(CargoLotState lot) {
        return lot.receiptSeries() == null ? lot.massKg() : lot.receiptSeries().remainingMassKg();
    }
    /**
     * Splits a physically requested FIFO quantity into at most two exact chronological descriptions.
     * @param lot current actual lot
     * @param amount positive transferred kilograms
     * @return delivered descriptions and remaining custody, without allocator/clock changes
     */
    public static Split split(CargoLotState lot, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > availableMassKg(lot) + 1e-9)
            throw new IllegalArgumentException("FIFO request exceeds actual receipt mass");
        if (lot.receiptSeries() == null) {
            var prefix = copy(lot, lot.lotId(), amount, lot.loadedAtSimulationSeconds(), null);
            double left = lot.massKg() - amount;
            return new Split(List.of(prefix), left <= 1e-9 ? null : copy(lot, lot.lotId(), left, lot.loadedAtSimulationSeconds(), null));
        }
        var series = lot.receiptSeries();
        // A complete transfer is expressed in the same representable mass as availableMassKg.
        // Its exact binary-rational expansion may exceed that double by a fraction of an ulp;
        // that is not an unconsumed receipt and must not manufacture a zero-mass suffix.
        if (amount == availableMassKg(lot))
            return new Split(List.of(copy(lot, lot.lotId(), amount, lot.loadedAtSimulationSeconds(), series)), null);
        var head = new BigDecimal(series.firstReceiptRemainingKg());
        var unit = new BigDecimal(series.receiptMassKg());
        var total = head.add(unit.multiply(BigDecimal.valueOf(series.receiptCount() - 1)));
        var requested = new BigDecimal(amount).min(total);
        var delivered = new ArrayList<CargoLotState>(2);
        ExtractionReceiptSeries retained;
        if (requested.compareTo(head) < 0) {
            delivered.add(copy(lot, lot.lotId(), amount, lot.loadedAtSimulationSeconds(), null));
            retained = series.consumeHead(requested.doubleValue()).orElseThrow();
        } else {
            var afterHead = requested.subtract(head);
            long whole = 1 + afterHead.divideToIntegralValue(unit).longValueExact();
            var partial = afterHead.subtract(unit.multiply(BigDecimal.valueOf(whole - 1)));
            var prefix = new ExtractionReceiptSeries(series.firstOrdinal(), series.firstTick(), series.fixedStepSeconds(),
                    series.receiptMassKg(), whole, series.firstReceiptRemainingKg(), series.originalFirstOrdinal(), series.originalFirstTick());
            delivered.add(copy(lot, lot.lotId(), prefix.remainingMassKg(), prefix.receipt(0).loadedAtSeconds(), prefix));
            retained = series.dropReceipts(whole).orElse(null);
            if (partial.signum() > 0 && retained != null) {
                var receipt = retained.receipt(0);
                double portion = Math.min(receipt.remainingMassKg(), partial.doubleValue());
                delivered.add(copy(lot, "freight-lot:" + receipt.ordinal(), portion, receipt.loadedAtSeconds(), null));
                retained = retained.consumeHead(portion).orElse(null);
            }
        }
        double retainedMass = lot.massKg() - amount;
        // Historical running sums may differ from receipt mass within the validated roundoff
        // bound. Preserve a genuinely partial receipt even when that mirror subtraction is
        // non-positive; this changes no warehouse stock or physical transfer amount.
        if (retained != null && retainedMass <= 0d) retainedMass = retained.remainingMassKg();
        var remaining = retained == null ? null : copy(lot, "freight-lot:" + retained.firstOrdinal(), retainedMass,
                retained.receipt(0).loadedAtSeconds(), retained);
        return new Split(delivered, remaining);
    }
    private static CargoLotState copy(CargoLotState lot, String id, double mass, double seconds, ExtractionReceiptSeries series) {
        return new CargoLotState(id, lot.fleetId(), lot.orderId(), lot.commodityId(), mass,
                lot.sourceEndpointId(), lot.sourceProvenanceId(), seconds, series);
    }
    /**
     * @param delivered exact FIFO descriptions
     * @param retained remaining custody, or null
     */
    public record Split(List<CargoLotState> delivered, CargoLotState retained) {
        /**
         * @param delivered immutable prefix
         * @param retained nullable suffix
         */
        public Split { delivered = List.copyOf(delivered); }
    }
}

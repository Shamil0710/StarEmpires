package com.spacesim.persistence;

import java.util.Optional;

/** Lossless ordinal/time description of equal consecutive physical extraction receipts. */
public record ExtractionReceiptSeries(long firstOrdinal, long firstTick, double fixedStepSeconds,
        double receiptMassKg, long receiptCount, double firstReceiptRemainingKg,
        long originalFirstOrdinal, long originalFirstTick) {
    /** Starts a series from the first actual newly produced receipt.
     * @param firstOrdinal first allocated identity
     * @param firstTick first producing tick
     * @param fixedStepSeconds actual simulation interval
     * @param receiptMassKg unchanged per-receipt output
     * @param receiptCount actual receipt count
     * @param firstReceiptRemainingKg current oldest-receipt portion
     */
    public ExtractionReceiptSeries(long firstOrdinal, long firstTick, double fixedStepSeconds,
            double receiptMassKg, long receiptCount, double firstReceiptRemainingKg) {
        this(firstOrdinal, firstTick, fixedStepSeconds, receiptMassKg, receiptCount,
                firstReceiptRemainingKg, firstOrdinal, firstTick);
    }
    /**
     * Validates bounded arithmetic and the remaining mass of the oldest receipt.
     * @param firstOrdinal allocated identity of the oldest remaining receipt
     * @param firstTick actual world tick that produced that receipt
     * @param fixedStepSeconds original simulation interval
     * @param receiptMassKg unchanged measured output of each whole receipt
     * @param receiptCount number of remaining receipts, not serialized rows
     * @param firstReceiptRemainingKg remaining portion of the oldest receipt
     * @param originalFirstOrdinal first identity allocated when the series began
     * @param originalFirstTick first producing tick before any FIFO consumption
     */
    public ExtractionReceiptSeries {
        if (firstOrdinal <= 0 || firstTick < 0 || receiptCount <= 0
                || originalFirstOrdinal <= 0 || originalFirstOrdinal > firstOrdinal
                || originalFirstTick < 0 || originalFirstTick > firstTick
                || firstOrdinal - originalFirstOrdinal != firstTick - originalFirstTick
                || !Double.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0
                || !Double.isFinite(receiptMassKg) || receiptMassKg <= 0
                || !Double.isFinite(firstReceiptRemainingKg) || firstReceiptRemainingKg <= 0
                || firstReceiptRemainingKg > receiptMassKg) throw new IllegalArgumentException("Invalid extraction receipt series");
        try {
            long lastOrdinal = Math.addExact(firstOrdinal, receiptCount - 1);
            long lastTick = Math.addExact(firstTick, receiptCount - 1);
            if (lastOrdinal == Long.MAX_VALUE || !Double.isFinite(lastTick * fixedStepSeconds)
                    || !Double.isFinite((lastOrdinal - originalFirstOrdinal + 1d) * receiptMassKg)
                    || !Double.isFinite(firstReceiptRemainingKg + (receiptCount - 1d) * receiptMassKg))
                throw new IllegalArgumentException("Extraction receipt series exceeds finite bounds");
        } catch (ArithmeticException overflow) { throw new IllegalArgumentException("Extraction receipt series overflows", overflow); }
    }

    /** @return last actual receipt ordinal */
    public long lastOrdinal() { return firstOrdinal + receiptCount - 1; }
    /** @return last actual producing tick */
    public long lastTick() { return firstTick + receiptCount - 1; }
    /** @return actual total allocations in this series, including receipts already consumed */
    public long producedReceiptCount() { return lastOrdinal() - originalFirstOrdinal + 1; }
    /** @return mathematical remaining receipt mass rounded once to the storage representation */
    public double remainingMassKg() { return Math.fma(receiptCount - 1d, receiptMassKg, firstReceiptRemainingKg); }
    /** @return conservative rounding bound for the original sequence of positive double additions */
    public double massRoundoffBoundKg() {
        double peak = producedReceiptCount() * receiptMassKg;
        double bound = producedReceiptCount() * Math.ulp(peak);
        if (!Double.isFinite(bound)) throw new IllegalArgumentException("Unbounded receipt mass roundoff");
        return Math.max(1e-9, bound);
    }

    /**
     * Tests whether one newly committed physical receipt can extend this sequence.
     * Source/order/commodity/provenance equality must additionally be checked by its owner.
     * @param ordinal newly allocated identity
     * @param tick actual producing tick
     * @param step actual fixed-step duration
     * @param mass measured new output
     * @return true only for exact consecutive equal receipts
     */
    public boolean canAppend(long ordinal, long tick, double step, double mass) {
        return lastOrdinal() < Long.MAX_VALUE - 1 && lastTick() < Long.MAX_VALUE
                && ordinal == lastOrdinal() + 1 && tick == lastTick() + 1
                && Double.doubleToLongBits(step) == Double.doubleToLongBits(fixedStepSeconds)
                && Double.doubleToLongBits(mass) == Double.doubleToLongBits(receiptMassKg);
    }

    /**
     * Adds precisely one receipt already produced and allocated by the physical authority.
     * @param ordinal new identity
     * @param tick new producing tick
     * @param step unchanged simulation interval
     * @param mass unchanged actual output
     * @return extended immutable description
     */
    public ExtractionReceiptSeries append(long ordinal, long tick, double step, double mass) {
        if (!canAppend(ordinal, tick, step, mass)) throw new IllegalArgumentException("Extraction receipt is not contiguous");
        return new ExtractionReceiptSeries(firstOrdinal, firstTick, fixedStepSeconds, receiptMassKg,
                Math.addExact(receiptCount, 1), firstReceiptRemainingKg, originalFirstOrdinal, originalFirstTick);
    }

    /**
     * Reads one original physical receipt without accumulating or averaging timestamps.
     * @param index zero-based index among remaining receipts
     * @return original allocated identity, producing tick, timestamp and current mass
     */
    public Receipt receipt(long index) {
        if (index < 0 || index >= receiptCount) throw new IllegalArgumentException("Receipt index outside series");
        long tick = firstTick + index;
        return new Receipt(firstOrdinal + index, tick, tick * fixedStepSeconds,
                index == 0 ? firstReceiptRemainingKg : receiptMassKg);
    }

    /**
     * Removes exactly identified whole FIFO receipts, without advancing clocks or allocator.
     * @param count number already physically consumed
     * @return remaining description, or empty after complete consumption
     */
    public Optional<ExtractionReceiptSeries> dropReceipts(long count) {
        if (count < 0 || count > receiptCount) throw new IllegalArgumentException("Invalid consumed receipt count");
        if (count == receiptCount) return Optional.empty();
        if (count == 0) return Optional.of(this);
        return Optional.of(new ExtractionReceiptSeries(firstOrdinal + count, firstTick + count, fixedStepSeconds,
                receiptMassKg, receiptCount - count, receiptMassKg, originalFirstOrdinal, originalFirstTick));
    }

    /**
     * Removes an already transferred portion of the oldest receipt and preserves its original time.
     * @param mass physically consumed positive mass
     * @return remaining description, or empty when the last receipt was fully consumed
     */
    public Optional<ExtractionReceiptSeries> consumeHead(double mass) {
        if (!Double.isFinite(mass) || mass <= 0 || mass > firstReceiptRemainingKg)
            throw new IllegalArgumentException("Consumption exceeds oldest receipt");
        if (mass == firstReceiptRemainingKg) return dropReceipts(1);
        return Optional.of(new ExtractionReceiptSeries(firstOrdinal, firstTick, fixedStepSeconds, receiptMassKg,
                receiptCount, firstReceiptRemainingKg - mass, originalFirstOrdinal, originalFirstTick));
    }

    /** Exact identity/time projection of one actual receipt.
     * @param ordinal original allocated identity
     * @param tick original producing tick
     * @param loadedAtSeconds original tick multiplied by original fixed step
     * @param remainingMassKg remaining physical mass
     */
    public record Receipt(long ordinal, long tick, double loadedAtSeconds, double remainingMassKg) { }
}

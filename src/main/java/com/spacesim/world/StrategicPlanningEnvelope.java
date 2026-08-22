package com.spacesim.world;

import java.util.Objects;

/**
 * Abstract multidimensional Stage-21B planning capacity.
 *
 * <p>Units are deliberately normalized strategic pressure rather than credits, tonnes, build slots
 * or fleet points. Adapters owned by treasury, logistics, construction and fleet-readiness
 * authorities may project their current capacity into this record without transferring mutation
 * authority to the strategic planner.</p>
 *
 * @param treasuryUnits normalized treasury capacity
 * @param logisticsUnits normalized logistics capacity
 * @param constructionUnits normalized construction capacity
 * @param readinessUnits normalized fleet/readiness capacity
 */
public record StrategicPlanningEnvelope(
        long treasuryUnits,
        long logisticsUnits,
        long constructionUnits,
        long readinessUnits) {
    /** Zero capacity/request. */
    public static final StrategicPlanningEnvelope ZERO = new StrategicPlanningEnvelope(0L, 0L, 0L, 0L);

    /** Validates non-negative planning dimensions. */
    public StrategicPlanningEnvelope {
        requireNonNegative(treasuryUnits, "Treasury planning units");
        requireNonNegative(logisticsUnits, "Logistics planning units");
        requireNonNegative(constructionUnits, "Construction planning units");
        requireNonNegative(readinessUnits, "Readiness planning units");
    }

    /**
     * Convenience factory for a balanced request/capacity across all four dimensions.
     *
     * @param units normalized units for every dimension
     * @return balanced planning envelope
     */
    public static StrategicPlanningEnvelope balanced(long units) {
        return new StrategicPlanningEnvelope(units, units, units, units);
    }

    /**
     * Checks whether this request fits completely inside the supplied capacity.
     *
     * @param capacity available capacity
     * @return whether every dimension fits
     */
    public boolean fitsWithin(StrategicPlanningEnvelope capacity) {
        StrategicPlanningEnvelope checked = Objects.requireNonNull(capacity, "Planning capacity not set");
        return treasuryUnits <= checked.treasuryUnits
                && logisticsUnits <= checked.logisticsUnits
                && constructionUnits <= checked.constructionUnits
                && readinessUnits <= checked.readinessUnits;
    }

    /**
     * Subtracts a fully fitting request from this capacity.
     *
     * @param request request that must fit in this envelope
     * @return remaining capacity
     */
    public StrategicPlanningEnvelope minus(StrategicPlanningEnvelope request) {
        StrategicPlanningEnvelope checked = Objects.requireNonNull(request, "Planning request not set");
        if (!checked.fitsWithin(this)) {
            throw new IllegalArgumentException("Strategic planning request exceeds available capacity");
        }
        return new StrategicPlanningEnvelope(
                treasuryUnits - checked.treasuryUnits,
                logisticsUnits - checked.logisticsUnits,
                constructionUnits - checked.constructionUnits,
                readinessUnits - checked.readinessUnits);
    }

    /**
     * Adds two planning envelopes exactly.
     *
     * @param other envelope to add
     * @return summed envelope
     */
    public StrategicPlanningEnvelope plus(StrategicPlanningEnvelope other) {
        StrategicPlanningEnvelope checked = Objects.requireNonNull(other, "Planning envelope not set");
        return new StrategicPlanningEnvelope(
                Math.addExact(treasuryUnits, checked.treasuryUnits),
                Math.addExact(logisticsUnits, checked.logisticsUnits),
                Math.addExact(constructionUnits, checked.constructionUnits),
                Math.addExact(readinessUnits, checked.readinessUnits));
    }

    /**
     * Returns a ceiling-rounded basis-point fraction of each dimension.
     *
     * @param basisPoints fraction in {@code [0,10000]}
     * @return dimension-wise ceiling-rounded fraction
     */
    public StrategicPlanningEnvelope fractionCeil(int basisPoints) {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Planning-envelope fraction must be in [0,10000]");
        }
        return new StrategicPlanningEnvelope(
                fractionCeil(treasuryUnits, basisPoints),
                fractionCeil(logisticsUnits, basisPoints),
                fractionCeil(constructionUnits, basisPoints),
                fractionCeil(readinessUnits, basisPoints));
    }

    /**
     * Reports whether no strategic capacity is requested/available.
     *
     * @return true only when every dimension is zero
     */
    public boolean isZero() {
        return treasuryUnits == 0L && logisticsUnits == 0L && constructionUnits == 0L && readinessUnits == 0L;
    }

    private static long fractionCeil(long value, int basisPoints) {
        if (value == 0L || basisPoints == 0) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / 10_000L, basisPoints);
        long remainderProduct = (value % 10_000L) * (long) basisPoints;
        long remainder = remainderProduct / 10_000L;
        if (remainderProduct % 10_000L != 0L) {
            remainder = Math.addExact(remainder, 1L);
        }
        return Math.addExact(whole, remainder);
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}

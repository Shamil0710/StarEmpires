package com.spacesim.world;

import java.util.Objects;

/**
 * Authoritative Stage-20 local-system position using a hierarchical cell plus a double-precision
 * local offset.
 *
 * <p>The cell partition is a numerical representation detail only. It is not a sector, a generated
 * region, a materialization radius or a gameplay boundary. Keeping the local offset normalized near
 * zero prevents loss of meter-scale precision when an entity travels to very large local-system
 * coordinates.</p>
 *
 * @param cellX signed numerical cell coordinate on the local-system X axis
 * @param cellY signed numerical cell coordinate on the local-system Y axis
 * @param offsetXM normalized local X offset in meters
 * @param offsetYM normalized local Y offset in meters
 */
public record LocalPhysicalPosition(long cellX, long cellY, double offsetXM, double offsetYM) {
    /** Numerical cell width in meters: exactly {@code 2^30}. */
    public static final double CELL_SIZE_M = 0x1.0p30;
    /** Maximum normalized offset magnitude before crossing a numerical cell boundary. */
    public static final double HALF_CELL_SIZE_M = 0x1.0p29;
    private static final long MAX_EXACT_DOUBLE_INTEGER = 1L << 53;

    /**
     * Validates an already-normalized hierarchical position.
     *
     * @param cellX signed numerical cell coordinate on X
     * @param cellY signed numerical cell coordinate on Y
     * @param offsetXM normalized local X offset in meters
     * @param offsetYM normalized local Y offset in meters
     */
    public LocalPhysicalPosition {
        requireNormalized(offsetXM, "offsetXM");
        requireNormalized(offsetYM, "offsetYM");
    }

    /**
     * Creates the physical local-system origin.
     *
     * @return zero-cell, zero-offset position
     */
    public static LocalPhysicalPosition origin() {
        return new LocalPhysicalPosition(0L, 0L, 0d, 0d);
    }

    /**
     * Applies a finite local displacement and renormalizes across numerical cell boundaries.
     *
     * <p>This operation changes physical position; presentation/camera rebasing never calls it.
     * The displacement is expressed in authoritative SI meters.</p>
     *
     * @param deltaXM local X displacement in meters
     * @param deltaYM local Y displacement in meters
     * @return translated normalized physical position
     */
    public LocalPhysicalPosition translated(double deltaXM, double deltaYM) {
        requireFinite(deltaXM, "deltaXM");
        requireFinite(deltaYM, "deltaYM");
        Axis x = normalize(cellX, offsetXM + deltaXM);
        Axis y = normalize(cellY, offsetYM + deltaYM);
        return new LocalPhysicalPosition(x.cell(), y.cell(), x.offsetM(), y.offsetM());
    }

    /**
     * Computes an SI displacement from this physical position to another position.
     *
     * <p>The calculation keeps local offset arithmetic in double precision. Cell deltas are exact
     * while they remain within the exactly representable integer domain of IEEE-754 double. This is
     * vastly larger than any intended local interaction/materialization neighborhood and is an
     * operation precision guard, not a world edge: both positions remain valid outside that domain.</p>
     *
     * @param other destination physical position
     * @return destination minus source displacement in meters
     */
    public Displacement displacementTo(LocalPhysicalPosition other) {
        LocalPhysicalPosition checked = Objects.requireNonNull(other, "other");
        long cellDeltaX = Math.subtractExact(checked.cellX, cellX);
        long cellDeltaY = Math.subtractExact(checked.cellY, cellY);
        requireExactlyRepresentableCellDelta(cellDeltaX, "cellDeltaX");
        requireExactlyRepresentableCellDelta(cellDeltaY, "cellDeltaY");
        double deltaX = Math.fma((double) cellDeltaX, CELL_SIZE_M, checked.offsetXM - offsetXM);
        double deltaY = Math.fma((double) cellDeltaY, CELL_SIZE_M, checked.offsetYM - offsetYM);
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            throw new ArithmeticException("Local physical displacement is outside finite double range");
        }
        return new Displacement(deltaX, deltaY);
    }

    /**
     * Computes Euclidean SI separation from this position to another position.
     *
     * @param other destination physical position
     * @return physical separation in meters
     */
    public double distanceTo(LocalPhysicalPosition other) {
        Displacement displacement = displacementTo(other);
        return Math.hypot(displacement.deltaXM(), displacement.deltaYM());
    }

    private static Axis normalize(long cell, double rawOffsetM) {
        requireFinite(rawOffsetM, "rawOffsetM");
        double shift = Math.floor((rawOffsetM + HALF_CELL_SIZE_M) / CELL_SIZE_M);
        if (!Double.isFinite(shift) || shift < Long.MIN_VALUE || shift > Long.MAX_VALUE) {
            throw new ArithmeticException("Local displacement exceeds hierarchical cell range");
        }
        long cellShift = (long) shift;
        long normalizedCell = Math.addExact(cell, cellShift);
        double normalizedOffset = rawOffsetM - cellShift * CELL_SIZE_M;

        // Correct the rare rounding-at-boundary case without changing the physical coordinate.
        if (normalizedOffset >= HALF_CELL_SIZE_M) {
            normalizedCell = Math.addExact(normalizedCell, 1L);
            normalizedOffset -= CELL_SIZE_M;
        } else if (normalizedOffset < -HALF_CELL_SIZE_M) {
            normalizedCell = Math.addExact(normalizedCell, -1L);
            normalizedOffset += CELL_SIZE_M;
        }
        requireNormalized(normalizedOffset, "normalizedOffset");
        return new Axis(normalizedCell, normalizedOffset);
    }

    private static void requireExactlyRepresentableCellDelta(long value, String field) {
        if (value < -MAX_EXACT_DOUBLE_INTEGER || value > MAX_EXACT_DOUBLE_INTEGER) {
            throw new ArithmeticException(field + " exceeds exact local displacement domain");
        }
    }

    private static void requireNormalized(double value, String field) {
        requireFinite(value, field);
        if (value < -HALF_CELL_SIZE_M || value >= HALF_CELL_SIZE_M) {
            throw new IllegalArgumentException(field + " must be normalized inside the numerical cell");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    /**
     * Local SI displacement between two hierarchical physical positions.
     *
     * @param deltaXM X displacement in meters
     * @param deltaYM Y displacement in meters
     */
    public record Displacement(double deltaXM, double deltaYM) {
        /**
         * Validates a finite local displacement.
         *
         * @param deltaXM X displacement in meters
         * @param deltaYM Y displacement in meters
         */
        public Displacement {
            requireFinite(deltaXM, "deltaXM");
            requireFinite(deltaYM, "deltaYM");
        }
    }

    private record Axis(long cell, double offsetM) {
    }
}

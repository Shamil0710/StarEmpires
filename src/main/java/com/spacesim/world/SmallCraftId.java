package com.spacesim.world;

/**
 * Stable persistent identity of one physical embarked small craft.
 *
 * <p>The value is campaign-global inside the M22.8 small-craft authority. IDs are never inferred
 * from carrier slot, squadron position, faction, design or current mission, so the same physical
 * craft keeps its identity while parked, deployed, damaged, recovered and saved/loaded.</p>
 *
 * @param value positive monotonic campaign identity
 */
public record SmallCraftId(long value) implements Comparable<SmallCraftId> {
    /** Validates one persistent identity. */
    public SmallCraftId {
        if (value <= 0L) {
            throw new IllegalArgumentException("SmallCraftId must be positive");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(SmallCraftId other) {
        return Long.compare(value, other.value);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "small-craft:" + value;
    }
}

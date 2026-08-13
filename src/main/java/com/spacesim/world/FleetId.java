package com.spacesim.world;

/** @param value numeric identifier value */
public record FleetId(long value) implements Comparable<FleetId> {
    /** @param value numeric identifier value */
    public FleetId {
        if (value <= 0L) {
            throw new IllegalArgumentException("FleetId must be positive");
        }
    }

    @Override
    public int compareTo(FleetId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "fleet:" + value;
    }
}

package com.spacesim.world;

/** Allocator world-level FleetId values. */
final class FleetIdAllocator {
    private long nextValue;

    FleetIdAllocator(long nextValue) {
        if (nextValue <= 0L) {
            throw new IllegalArgumentException("Fleet allocator value must be positive");
        }
        this.nextValue = nextValue;
    }

    FleetId allocate() {
        if (nextValue == Long.MAX_VALUE) {
            throw new IllegalStateException("FleetId range exhausted");
        }
        return new FleetId(nextValue++);
    }

    long nextValue() {
        return nextValue;
    }
}

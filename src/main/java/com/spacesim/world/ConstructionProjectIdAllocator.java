package com.spacesim.world;

/** Deterministic persistent allocator of world-level {@link ConstructionProjectId}. */
final class ConstructionProjectIdAllocator {
    private long nextValue;

    ConstructionProjectIdAllocator(long nextValue) {
        if (nextValue <= 0L) {
            throw new IllegalArgumentException("Следующий ConstructionProjectId должен быть положительным");
        }
        this.nextValue = nextValue;
    }

    ConstructionProjectId allocate() {
        if (nextValue == Long.MAX_VALUE) {
            throw new IllegalStateException("Диапазон ConstructionProjectId исчерпан");
        }
        ConstructionProjectId id = new ConstructionProjectId(nextValue);
        nextValue++;
        return id;
    }

    long peekNextValue() {
        return nextValue;
    }
}

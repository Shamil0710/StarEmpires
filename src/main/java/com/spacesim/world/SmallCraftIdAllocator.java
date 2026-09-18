package com.spacesim.world;

/**
 * Collision-proof monotonic identity allocator for physical small craft.
 *
 * <p>The allocator assigns identity only. It does not manufacture a craft, grant a hull, ammunition,
 * reaction mass or repairs. A caller may consume an ID only after the relevant physical-production
 * authority has established that the craft exists.</p>
 */
public final class SmallCraftIdAllocator {
    private long nextValue;

    private SmallCraftIdAllocator(long nextValue) {
        if (nextValue <= 0L) {
            throw new IllegalArgumentException("nextValue must be positive");
        }
        this.nextValue = nextValue;
    }

    /** @return allocator for a new campaign, beginning at identity one */
    public static SmallCraftIdAllocator empty() {
        return new SmallCraftIdAllocator(1L);
    }

    /**
     * Restores the persisted next-ID watermark.
     *
     * @param nextValue exact next unused positive identity
     * @return independent allocator continuing from the saved watermark
     */
    public static SmallCraftIdAllocator restore(long nextValue) {
        return new SmallCraftIdAllocator(nextValue);
    }

    /**
     * Reserves the next identity for one already-authorized physical production result.
     *
     * @return newly reserved stable craft identity
     */
    public SmallCraftId allocate() {
        if (nextValue == Long.MAX_VALUE) {
            throw new IllegalStateException("Small-craft identity space exhausted");
        }
        return new SmallCraftId(nextValue++);
    }

    /** @return exact next unused value that must be persisted */
    public long nextValue() {
        return nextValue;
    }
}

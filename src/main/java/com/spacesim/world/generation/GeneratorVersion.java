package com.spacesim.world.generation;

/**
 * Version of the deterministic physical-world generator contract.
 *
 * <p>Changing generated output for an existing seed requires a new enum value rather than silently
 * changing the meaning of persisted seed data.</p>
 */
public enum GeneratorVersion {
    /** First Stage-20B star-system physical-geometry contract. */
    STAGE_20_B_V1("20B-v1", 0x4d595df4d0f33173L);

    private final String stableId;
    private final long seedSalt;

    GeneratorVersion(String stableId, long seedSalt) {
        this.stableId = stableId;
        this.seedSalt = seedSalt;
    }

    /** Returns the stable persistence/debug identifier for this generator contract. */
    public String stableId() {
        return stableId;
    }

    /** Derives a version-scoped deterministic state from a caller-owned world seed. */
    public long versionedSeed(long worldSeed) {
        return mix64(worldSeed ^ seedSalt);
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}

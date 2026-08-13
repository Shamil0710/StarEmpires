package com.spacesim.world;

/**
 * Stable world-level identity of one construction project.
 *
 * <p>Unlike {@link com.spacesim.persistence.EntityId}, construction-project IDs are allocated at
 * world level because the project owns a target StarSystem but is not itself a local ECS entity.</p>
 *
 * @param value strictly positive persistent value
 */
public record ConstructionProjectId(long value) implements Comparable<ConstructionProjectId> {
    /** Validates the persistent ID. */
    public ConstructionProjectId {
        if (value <= 0L) {
            throw new IllegalArgumentException("ConstructionProjectId должен быть положительным");
        }
    }

    @Override
    public int compareTo(ConstructionProjectId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "construction-project:" + value;
    }
}

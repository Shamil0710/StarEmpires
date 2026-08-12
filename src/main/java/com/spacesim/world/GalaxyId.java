package com.spacesim.world;

/**
 * Устойчивый идентификатор галактики в persistent topology.
 *
 * @param value положительное числовое значение ID
 */
public record GalaxyId(long value) implements Comparable<GalaxyId> {
    /**
     * Проверяет допустимость ID.
     *
     * @param value положительное числовое значение ID
     * @throws IllegalArgumentException если значение не положительное
     */
    public GalaxyId {
        if (value <= 0L) {
            throw new IllegalArgumentException("GalaxyId должен быть положительным");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(GalaxyId other) {
        return Long.compare(value, other.value);
    }
}

package com.spacesim.world;

/**
 * Устойчивый ID планеты strategic world-layer.
 *
 * @param value положительное стабильное значение
 */
public record PlanetId(long value) implements Comparable<PlanetId> {
    /**
     * Проверяет ID.
     *
     * @param value положительное стабильное значение
     */
    public PlanetId {
        if (value <= 0L) {
            throw new IllegalArgumentException("PlanetId должен быть положительным");
        }
    }

    @Override
    public int compareTo(PlanetId other) {
        return Long.compare(value, other.value);
    }
}

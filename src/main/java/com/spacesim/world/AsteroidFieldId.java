package com.spacesim.world;

/**
 * Устойчивый ID стратегического астероидного поля.
 *
 * @param value положительное стабильное значение
 */
public record AsteroidFieldId(long value) implements Comparable<AsteroidFieldId> {
    /**
     * Проверяет ID.
     *
     * @param value положительное стабильное значение
     */
    public AsteroidFieldId {
        if (value <= 0L) {
            throw new IllegalArgumentException("AsteroidFieldId должен быть положительным");
        }
    }

    @Override
    public int compareTo(AsteroidFieldId other) {
        return Long.compare(value, other.value);
    }
}

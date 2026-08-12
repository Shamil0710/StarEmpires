package com.spacesim.world;

/**
 * Устойчивый идентификатор звёздной системы.
 *
 * @param value положительное числовое значение ID
 */
public record StarSystemId(long value) implements Comparable<StarSystemId> {
    /**
     * Проверяет допустимость ID.
     *
     * @throws IllegalArgumentException если значение не положительное
     */
    public StarSystemId {
        if (value <= 0L) {
            throw new IllegalArgumentException("StarSystemId должен быть положительным");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(StarSystemId other) {
        return Long.compare(value, other.value);
    }
}

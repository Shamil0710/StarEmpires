package com.spacesim.world;

/**
 * Устойчивый идентификатор сектора галактики.
 *
 * @param value положительное числовое значение ID
 */
public record SectorId(long value) implements Comparable<SectorId> {
    /**
     * Проверяет допустимость ID.
     *
     * @throws IllegalArgumentException если значение не положительное
     */
    public SectorId {
        if (value <= 0L) {
            throw new IllegalArgumentException("SectorId должен быть положительным");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(SectorId other) {
        return Long.compare(value, other.value);
    }
}

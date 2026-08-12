package com.spacesim.persistence;

/**
 * Детерминированно выдаёт последовательные устойчивые идентификаторы сущностей.
 *
 * <p>Аллокатор не использует wall-clock или случайность. Его {@link #getNextValue()} является
 * частью будущего save-state: после загрузки достаточно восстановить следующее значение, чтобы
 * вновь создаваемые объекты не пересекались с уже существующими ID и одинаковое продолжение
 * симуляции создавало одинаковую последовательность идентификаторов.</p>
 */
public final class EntityIdAllocator {
    private long nextValue;

    /** Создаёт новый аллокатор, начинающий с {@code entity:1}. */
    public EntityIdAllocator() {
        this(1L);
    }

    /**
     * Восстанавливает аллокатор с указанного следующего значения.
     *
     * @param nextValue положительное значение, которое будет выдано следующим
     * @throws IllegalArgumentException если значение не положительно
     */
    public EntityIdAllocator(long nextValue) {
        if (nextValue <= 0L) {
            throw new IllegalArgumentException("Следующий EntityId должен быть положительным");
        }
        this.nextValue = nextValue;
    }

    /**
     * Выдаёт следующий ID и сдвигает последовательность.
     *
     * @return новый уникальный в рамках этой последовательности ID
     * @throws IllegalStateException если диапазон положительных {@code long} исчерпан
     */
    public EntityId allocate() {
        if (nextValue == Long.MAX_VALUE) {
            EntityId last = new EntityId(nextValue);
            nextValue = 0L;
            return last;
        }
        if (nextValue <= 0L) {
            throw new IllegalStateException("Диапазон EntityId исчерпан");
        }
        return new EntityId(nextValue++);
    }

    /**
     * Возвращает значение, которое будет выдано следующим, для save-state.
     *
     * @return положительное следующее значение либо {@code 0}, если диапазон исчерпан
     */
    public long getNextValue() {
        return nextValue;
    }
}

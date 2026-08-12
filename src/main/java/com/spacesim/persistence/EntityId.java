package com.spacesim.persistence;

/**
 * Устойчивый идентификатор одной сущности игрового мира.
 *
 * <p>Значение не зависит от Ashley {@code Entity}, позиции объекта в коллекции или адреса в памяти.
 * Оно предназначено для persistent-ссылок, save/load и восстановления связей между сущностями.
 * Ноль зарезервирован как отсутствие идентификатора, поэтому допустимы только положительные
 * значения {@code long}.</p>
 *
 * @param value положительное числовое значение идентификатора
 */
public record EntityId(long value) implements Comparable<EntityId> {
    /**
     * Проверяет допустимость persistent ID.
     *
     * @throws IllegalArgumentException если значение не положительно
     */
    public EntityId {
        if (value <= 0L) {
            throw new IllegalArgumentException("EntityId должен быть положительным");
        }
    }

    /**
     * Сравнивает идентификаторы по их числовому значению.
     *
     * @param other другой ненулевой идентификатор
     * @return результат сравнения значений
     */
    @Override
    public int compareTo(EntityId other) {
        return Long.compare(value, other.value);
    }

    /**
     * Возвращает компактное стабильное текстовое представление для диагностики.
     *
     * @return строка вида {@code entity:42}
     */
    @Override
    public String toString() {
        return "entity:" + value;
    }
}

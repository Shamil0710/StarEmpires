package com.spacesim.world;

import java.util.Objects;

/**
 * Каноническое неориентированное jump-соединение двух звёздных систем.
 *
 * <p>Конструктор всегда упорядочивает концы по {@link StarSystemId}, поэтому соединения A-B и B-A
 * равны и дают одинаковый persistent representation.</p>
 *
 * @param first меньший по ID конец после канонизации
 * @param second больший по ID конец после канонизации
 */
public record JumpConnection(StarSystemId first, StarSystemId second)
        implements Comparable<JumpConnection> {
    /**
     * Канонизирует порядок концов и запрещает self-jump.
     *
     * @throws NullPointerException если конец не задан
     * @throws IllegalArgumentException если оба конца совпадают
     */
    public JumpConnection {
        Objects.requireNonNull(first, "Первый StarSystemId jump не задан");
        Objects.requireNonNull(second, "Второй StarSystemId jump не задан");
        if (first.equals(second)) {
            throw new IllegalArgumentException("Jump connection не может вести систему в саму себя");
        }
        if (first.compareTo(second) > 0) {
            StarSystemId swap = first;
            first = second;
            second = swap;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(JumpConnection other) {
        int firstCompare = first.compareTo(other.first);
        return firstCompare != 0 ? firstCompare : second.compareTo(other.second);
    }

    /**
     * Возвращает противоположный конец соединения.
     *
     * @param system один из концов connection
     * @return противоположная система
     * @throws IllegalArgumentException если система не принадлежит connection
     */
    public StarSystemId other(StarSystemId system) {
        Objects.requireNonNull(system, "StarSystemId не задан");
        if (first.equals(system)) {
            return second;
        }
        if (second.equals(system)) {
            return first;
        }
        throw new IllegalArgumentException("Система не принадлежит jump connection: " + system);
    }
}

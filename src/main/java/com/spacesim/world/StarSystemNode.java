package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable topology-описание одной звёздной системы.
 *
 * <p>Координаты задают положение системы на стратегической карте и не являются локальными
 * координатами объектов внутри системы.</p>
 *
 * @param id устойчивый ID системы
 * @param name отображаемое имя
 * @param x стратегическая координата X
 * @param y стратегическая координата Y
 */
public record StarSystemNode(StarSystemId id, String name, double x, double y) {
    /**
     * Нормализует имя и валидирует topology-данные.
     *
     * @throws NullPointerException если ID или имя не заданы
     * @throws IllegalArgumentException если имя пустое или координаты неконечны
     */
    public StarSystemNode {
        Objects.requireNonNull(id, "StarSystemId не задан");
        name = Objects.requireNonNull(name, "Имя системы не задано").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Имя системы не может быть пустым");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Координаты системы должны быть конечными");
        }
    }
}

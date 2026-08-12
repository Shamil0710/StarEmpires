package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable strategic область астероидного поля внутри StarSystem.
 *
 * @param id устойчивый ID поля
 * @param name отображаемое имя
 * @param x system-level координата X центра
 * @param y system-level координата Y центра
 * @param radius положительный радиус области
 */
public record AsteroidFieldNode(
        AsteroidFieldId id,
        String name,
        double x,
        double y,
        double radius) {
    /**
     * Валидирует strategic field data.
     *
     * @param id устойчивый ID поля
     * @param name отображаемое имя
     * @param x конечная координата X
     * @param y конечная координата Y
     * @param radius конечный положительный радиус
     */
    public AsteroidFieldNode {
        Objects.requireNonNull(id, "AsteroidFieldId не задан");
        name = normalizedName(name);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Координаты asteroid field должны быть конечными");
        }
        if (!Double.isFinite(radius) || radius <= 0d) {
            throw new IllegalArgumentException("Радиус asteroid field должен быть конечным и положительным");
        }
    }

    private static String normalizedName(String value) {
        String result = Objects.requireNonNull(value, "Имя asteroid field не задано").trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Имя asteroid field не может быть пустым");
        }
        return result;
    }
}

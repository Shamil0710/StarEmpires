package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable strategic описание планеты внутри StarSystem.
 *
 * @param id устойчивый ID планеты
 * @param name отображаемое имя
 * @param orbitRadius условный радиус орбиты в system-level координатах
 */
public record PlanetNode(
        PlanetId id,
        String name,
        double orbitRadius) {
    /**
     * Валидирует strategic planet data.
     *
     * @param id устойчивый ID планеты
     * @param name отображаемое имя
     * @param orbitRadius конечный неотрицательный радиус орбиты
     */
    public PlanetNode {
        Objects.requireNonNull(id, "PlanetId не задан");
        name = normalizedName(name);
        if (!Double.isFinite(orbitRadius) || orbitRadius < 0d) {
            throw new IllegalArgumentException("Радиус орбиты планеты должен быть конечным и неотрицательным");
        }
    }

    private static String normalizedName(String value) {
        String result = Objects.requireNonNull(value, "Имя планеты не задано").trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Имя планеты не может быть пустым");
        }
        return result;
    }
}

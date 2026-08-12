package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable strategic узел звёздной системы.
 *
 * @param id устойчивый ID системы
 * @param name отображаемое имя
 * @param x координата X на карте галактики
 * @param y координата Y на карте галактики
 * @param planets стратегические планеты системы
 * @param asteroidFields стратегические астероидные поля системы
 */
public record StarSystemNode(
        StarSystemId id,
        String name,
        double x,
        double y,
        List<PlanetNode> planets,
        List<AsteroidFieldNode> asteroidFields) {
    /**
     * Совместимый shorthand для системы без strategic landmarks.
     *
     * @param id устойчивый ID системы
     * @param name отображаемое имя
     * @param x координата X
     * @param y координата Y
     */
    public StarSystemNode(StarSystemId id, String name, double x, double y) {
        this(id, name, x, y, List.of(), List.of());
    }

    /**
     * Валидирует и канонизирует system-level strategic objects.
     *
     * @param id устойчивый ID системы
     * @param name отображаемое имя
     * @param x конечная координата X
     * @param y конечная координата Y
     * @param planets планеты системы
     * @param asteroidFields астероидные поля системы
     */
    public StarSystemNode {
        Objects.requireNonNull(id, "StarSystemId не задан");
        name = normalizedName(name);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Координаты StarSystem должны быть конечными");
        }
        planets = canonicalPlanets(planets);
        asteroidFields = canonicalFields(asteroidFields);
    }

    private static List<PlanetNode> canonicalPlanets(List<PlanetNode> source) {
        Objects.requireNonNull(source, "Список планет StarSystem не задан");
        List<PlanetNode> result = new ArrayList<>(source.size());
        Set<PlanetId> ids = new HashSet<>();
        for (PlanetNode planet : source) {
            PlanetNode value = Objects.requireNonNull(planet, "PlanetNode не задан");
            if (!ids.add(value.id())) {
                throw new IllegalArgumentException("Дублирующий PlanetId внутри StarSystem: " + value.id());
            }
            result.add(value);
        }
        result.sort(Comparator.comparing(PlanetNode::id));
        return List.copyOf(result);
    }

    private static List<AsteroidFieldNode> canonicalFields(List<AsteroidFieldNode> source) {
        Objects.requireNonNull(source, "Список asteroid fields StarSystem не задан");
        List<AsteroidFieldNode> result = new ArrayList<>(source.size());
        Set<AsteroidFieldId> ids = new HashSet<>();
        for (AsteroidFieldNode field : source) {
            AsteroidFieldNode value = Objects.requireNonNull(field, "AsteroidFieldNode не задан");
            if (!ids.add(value.id())) {
                throw new IllegalArgumentException(
                        "Дублирующий AsteroidFieldId внутри StarSystem: " + value.id());
            }
            result.add(value);
        }
        result.sort(Comparator.comparing(AsteroidFieldNode::id));
        return List.copyOf(result);
    }

    private static String normalizedName(String value) {
        String result = Objects.requireNonNull(value, "Имя StarSystem не задано").trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Имя StarSystem не может быть пустым");
        }
        return result;
    }
}

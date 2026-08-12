package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable topology-описание сектора и входящих в него звёздных систем.
 *
 * @param id устойчивый ID сектора
 * @param name отображаемое имя
 * @param systems системы сектора в произвольном входном порядке
 */
public record SectorNode(SectorId id, String name, List<StarSystemNode> systems) {
    /**
     * Нормализует имя, защищает коллекцию и сортирует системы по persistent ID.
     *
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException если имя пустое или ID системы повторяется внутри сектора
     */
    public SectorNode {
        Objects.requireNonNull(id, "SectorId не задан");
        name = Objects.requireNonNull(name, "Имя сектора не задано").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Имя сектора не может быть пустым");
        }
        Objects.requireNonNull(systems, "Список систем сектора не задан");

        List<StarSystemNode> sorted = new ArrayList<>(systems.size());
        Set<StarSystemId> ids = new HashSet<>();
        for (StarSystemNode system : systems) {
            StarSystemNode value = Objects.requireNonNull(system, "Система сектора не задана");
            if (!ids.add(value.id())) {
                throw new IllegalArgumentException(
                        "Дублирующий StarSystemId внутри сектора: " + value.id());
            }
            sorted.add(value);
        }
        sorted.sort(Comparator.comparing(StarSystemNode::id));
        systems = List.copyOf(sorted);
    }
}

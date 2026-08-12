package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent active strategic goal faction, создающий demand floors в существующей экономике.
 *
 * @param goalId stable ID цели внутри faction state
 * @param type тип стратегической цели
 * @param demandFloors demand floors по stable item IDs
 */
public record FactionStrategicGoalState(
        String goalId,
        GoalType type,
        List<FactionStockPolicyState> demandFloors) implements Comparable<FactionStrategicGoalState> {

    /** Поддерживаемые типы strategic demand текущего Stage 8. */
    public enum GoalType {
        /** Военный спрос на вооружение/материалы/энергию. */
        MILITARY,
        /** Спрос расширения на снабжение новых активов и территорий. */
        EXPANSION
    }

    /**
     * Валидирует goal и canonical demand ordering.
     *
     * @param goalId stable ID цели
     * @param type goal type
     * @param demandFloors demand floors
     */
    public FactionStrategicGoalState {
        goalId = Objects.requireNonNull(goalId, "Strategic goal ID не задан").strip();
        if (goalId.isEmpty()) {
            throw new IllegalArgumentException("Strategic goal ID не может быть пустым");
        }
        Objects.requireNonNull(type, "Strategic goal type не задан");
        Objects.requireNonNull(demandFloors, "Strategic goal demand floors не заданы");
        if (demandFloors.isEmpty()) {
            throw new IllegalArgumentException("Strategic goal должен создавать хотя бы один demand floor");
        }
        List<FactionStockPolicyState> sorted = new ArrayList<>(demandFloors.size());
        Set<String> seenItems = new HashSet<>();
        for (FactionStockPolicyState demand : demandFloors) {
            FactionStockPolicyState value = Objects.requireNonNull(demand, "Strategic goal demand не задан");
            if (!seenItems.add(value.itemContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующий item demand внутри strategic goal: " + value.itemContentId());
            }
            sorted.add(value);
        }
        sorted.sort(Comparator.naturalOrder());
        demandFloors = List.copyOf(sorted);
    }

    /** @param other другая goal @return deterministic lexical ordering по stable goal ID */
    @Override
    public int compareTo(FactionStrategicGoalState other) {
        return goalId.compareTo(
                Objects.requireNonNull(other, "FactionStrategicGoalState не задан").goalId);
    }
}

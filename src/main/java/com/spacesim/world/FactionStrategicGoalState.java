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
 * @param growthPlan optional Stage-11 persistent spatial expansion plan; допустим только для EXPANSION
 */
public record FactionStrategicGoalState(
        String goalId,
        GoalType type,
        List<FactionStockPolicyState> demandFloors,
        StrategicGrowthState.Plan growthPlan) implements Comparable<FactionStrategicGoalState> {

    /** Поддерживаемые типы strategic demand. */
    public enum GoalType {
        /** Военный спрос на вооружение/материалы/энергию. */
        MILITARY,
        /** Спрос расширения и optional physical Stage-11 expansion plan. */
        EXPANSION
    }

    /**
     * Source-compatible constructor для Stage-8 goal без physical expansion plan.
     *
     * @param goalId stable ID цели
     * @param type goal type
     * @param demandFloors demand floors
     */
    public FactionStrategicGoalState(
            String goalId,
            GoalType type,
            List<FactionStockPolicyState> demandFloors) {
        this(goalId, type, demandFloors, null);
    }

    /**
     * Валидирует goal и canonical demand ordering.
     *
     * @param goalId stable ID цели
     * @param type goal type
     * @param demandFloors demand floors
     * @param growthPlan optional Stage-11 expansion plan
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
        if (growthPlan != null && type != GoalType.EXPANSION) {
            throw new IllegalArgumentException("Physical growth plan допустим только для EXPANSION goal");
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

    /** @return true when this goal carries a persistent Stage-11 spatial plan */
    public boolean hasGrowthPlan() {
        return growthPlan != null;
    }

    /** @param other другая goal @return deterministic lexical ordering по stable goal ID */
    @Override
    public int compareTo(FactionStrategicGoalState other) {
        return goalId.compareTo(
                Objects.requireNonNull(other, "FactionStrategicGoalState не задан").goalId);
    }
}

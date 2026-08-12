package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent strategic policy одной faction: diplomacy, territory, stock/production policy и goals.
 *
 * @param factionContentId stable owner faction content ID
 * @param minimumMarketAccessRelation минимальное directed relation для доступа к рынкам faction
 * @param relations directed relations к другим factions
 * @param controlledSystems стратегически контролируемые StarSystem IDs
 * @param stockPolicies базовые target-stock floors faction
 * @param productionPolicies desired recipe по station archetype
 * @param strategicGoals active military/expansion goals, создающие дополнительные demand floors
 */
public record FactionStrategicState(
        String factionContentId,
        int minimumMarketAccessRelation,
        List<FactionRelationState> relations,
        List<StarSystemId> controlledSystems,
        List<FactionStockPolicyState> stockPolicies,
        List<FactionProductionPolicyState> productionPolicies,
        List<FactionStrategicGoalState> strategicGoals) implements Comparable<FactionStrategicState> {

    /**
     * Source-compatible constructor diplomacy/territory state без economic production goals.
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold
     * @param relations directed relations
     * @param controlledSystems controlled systems
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems) {
        this(
                factionContentId,
                minimumMarketAccessRelation,
                relations,
                controlledSystems,
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * Валидирует state и нормализует canonical ordering.
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold в диапазоне [-100, 100]
     * @param relations directed relation list
     * @param controlledSystems controlled system IDs
     * @param stockPolicies базовые stock floors
     * @param productionPolicies production policies
     * @param strategicGoals active strategic goals
     */
    public FactionStrategicState {
        factionContentId = requireId(factionContentId, "Faction content ID");
        if (minimumMarketAccessRelation < -100 || minimumMarketAccessRelation > 100) {
            throw new IllegalArgumentException("Market-access threshold должен быть в диапазоне [-100, 100]");
        }
        Objects.requireNonNull(relations, "Faction relations не заданы");
        Objects.requireNonNull(controlledSystems, "Controlled systems не заданы");
        Objects.requireNonNull(stockPolicies, "Faction stock policies не заданы");
        Objects.requireNonNull(productionPolicies, "Faction production policies не заданы");
        Objects.requireNonNull(strategicGoals, "Faction strategic goals не заданы");

        List<FactionRelationState> sortedRelations = new ArrayList<>(relations.size());
        Set<String> relationTargets = new HashSet<>();
        for (FactionRelationState relation : relations) {
            FactionRelationState value = Objects.requireNonNull(relation, "FactionRelationState не задан");
            if (value.targetFactionContentId().equals(factionContentId)) {
                throw new IllegalArgumentException("Self relation не хранится явно");
            }
            if (!relationTargets.add(value.targetFactionContentId())) {
                throw new IllegalArgumentException("Дублирующая faction relation: " + value.targetFactionContentId());
            }
            sortedRelations.add(value);
        }
        sortedRelations.sort(Comparator.naturalOrder());
        relations = List.copyOf(sortedRelations);

        List<StarSystemId> sortedSystems = new ArrayList<>(controlledSystems.size());
        Set<StarSystemId> seenSystems = new HashSet<>();
        for (StarSystemId systemId : controlledSystems) {
            StarSystemId value = Objects.requireNonNull(systemId, "Controlled StarSystemId не задан");
            if (!seenSystems.add(value)) {
                throw new IllegalArgumentException("Дублирующая controlled StarSystem: " + value);
            }
            sortedSystems.add(value);
        }
        sortedSystems.sort(Comparator.naturalOrder());
        controlledSystems = List.copyOf(sortedSystems);

        List<FactionStockPolicyState> sortedStock = new ArrayList<>(stockPolicies.size());
        Set<String> stockItems = new HashSet<>();
        for (FactionStockPolicyState policy : stockPolicies) {
            FactionStockPolicyState value = Objects.requireNonNull(policy, "FactionStockPolicyState не задан");
            if (!stockItems.add(value.itemContentId())) {
                throw new IllegalArgumentException("Дублирующая stock policy: " + value.itemContentId());
            }
            sortedStock.add(value);
        }
        sortedStock.sort(Comparator.naturalOrder());
        stockPolicies = List.copyOf(sortedStock);

        List<FactionProductionPolicyState> sortedProduction = new ArrayList<>(productionPolicies.size());
        Set<String> productionArchetypes = new HashSet<>();
        for (FactionProductionPolicyState policy : productionPolicies) {
            FactionProductionPolicyState value = Objects.requireNonNull(
                    policy,
                    "FactionProductionPolicyState не задан");
            if (!productionArchetypes.add(value.stationArchetypeContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующая production policy для archetype: " + value.stationArchetypeContentId());
            }
            sortedProduction.add(value);
        }
        sortedProduction.sort(Comparator.naturalOrder());
        productionPolicies = List.copyOf(sortedProduction);

        List<FactionStrategicGoalState> sortedGoals = new ArrayList<>(strategicGoals.size());
        Set<String> goalIds = new HashSet<>();
        for (FactionStrategicGoalState goal : strategicGoals) {
            FactionStrategicGoalState value = Objects.requireNonNull(goal, "FactionStrategicGoalState не задан");
            if (!goalIds.add(value.goalId())) {
                throw new IllegalArgumentException("Дублирующий strategic goal ID: " + value.goalId());
            }
            sortedGoals.add(value);
        }
        sortedGoals.sort(Comparator.naturalOrder());
        strategicGoals = List.copyOf(sortedGoals);
    }

    /**
     * Возвращает directed relation к faction; self всегда 100, отсутствующая relation — 0.
     *
     * @param targetFactionContentId target content ID
     * @return relation в диапазоне [-100, 100]
     */
    public int relationTo(String targetFactionContentId) {
        if (targetFactionContentId == null) {
            return 0;
        }
        String target = targetFactionContentId.strip();
        if (target.equals(factionContentId)) {
            return 100;
        }
        for (FactionRelationState relation : relations) {
            if (relation.targetFactionContentId().equals(target)) {
                return relation.relation();
            }
        }
        return 0;
    }

    /**
     * Проверяет strategic владение системой.
     *
     * @param systemId stable system ID
     * @return {@code true}, если system входит в controlled territory
     */
    public boolean controls(StarSystemId systemId) {
        return systemId != null && controlledSystems.contains(systemId);
    }

    /**
     * Вычисляет effective target-stock floor товара из базовой policy и всех active goals.
     *
     * @param itemContentId stable item ID
     * @return максимальный floor или 0, если спрос не задан
     */
    public int effectiveTargetStockFloor(String itemContentId) {
        if (itemContentId == null) {
            return 0;
        }
        int floor = 0;
        for (FactionStockPolicyState policy : stockPolicies) {
            if (policy.itemContentId().equals(itemContentId)) {
                floor = Math.max(floor, policy.targetStockFloor());
            }
        }
        for (FactionStrategicGoalState goal : strategicGoals) {
            for (FactionStockPolicyState demand : goal.demandFloors()) {
                if (demand.itemContentId().equals(itemContentId)) {
                    floor = Math.max(floor, demand.targetStockFloor());
                }
            }
        }
        return floor;
    }

    /**
     * Ищет desired production recipe для station archetype.
     *
     * @param stationArchetypeContentId stable station archetype ID
     * @return stable recipe ID или null
     */
    public String productionRecipeFor(String stationArchetypeContentId) {
        if (stationArchetypeContentId == null) {
            return null;
        }
        for (FactionProductionPolicyState policy : productionPolicies) {
            if (policy.stationArchetypeContentId().equals(stationArchetypeContentId)) {
                return policy.recipeContentId();
            }
        }
        return null;
    }

    /** @param other другой strategic state @return lexical comparison по owner content ID */
    @Override
    public int compareTo(FactionStrategicState other) {
        return factionContentId.compareTo(
                Objects.requireNonNull(other, "FactionStrategicState не задан").factionContentId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " не задан").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " не может быть пустым");
        }
        return normalized;
    }
}

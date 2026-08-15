package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent strategic policy одной faction: diplomacy, territory, fiscal, stock/production и goals.
 *
 * @param factionContentId stable owner faction content ID
 * @param minimumMarketAccessRelation минимальное directed relation для доступа к рынкам faction
 * @param relations directed relations к другим factions
 * @param controlledSystems стратегически контролируемые StarSystem IDs
 * @param stationTaxBasisPoints налог со surplus собственных station wallets, 0..10000 bps
 * @param foreignTerritoryTariffBasisPoints levy со surplus чужих markets в controlled territory
 * @param stockPolicies базовые target-stock floors faction
 * @param productionPolicies desired recipe по station archetype
 * @param strategicGoals active military/expansion goals, создающие дополнительные demand floors
 * @param territorialClaims explicit political claims with deterministic stabilization progress
 * @param territorialControlStates maintenance clocks for systems in {@code controlledSystems}
 * @param territorialRecognitions directed recognition of other factions' claims/control
 * @param constructionRightsGranted explicit foreign construction concessions granted by this faction
 */
public record FactionStrategicState(
        String factionContentId,
        int minimumMarketAccessRelation,
        List<FactionRelationState> relations,
        List<StarSystemId> controlledSystems,
        int stationTaxBasisPoints,
        int foreignTerritoryTariffBasisPoints,
        List<FactionStockPolicyState> stockPolicies,
        List<FactionProductionPolicyState> productionPolicies,
        List<FactionStrategicGoalState> strategicGoals,
        List<TerritorialClaimState> territorialClaims,
        List<TerritorialControlState> territorialControlStates,
        List<TerritorialRecognitionState> territorialRecognitions,
        List<TerritorialConstructionRightState> constructionRightsGranted)
        implements Comparable<FactionStrategicState> {

    /**
     * Source-compatible diplomacy/territory constructor с нулевой fiscal/economic policy.
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
                0,
                0,
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * Source-compatible policy constructor с нулевыми tax/tariff rates.
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold
     * @param relations directed relations
     * @param controlledSystems controlled systems
     * @param stockPolicies stock floors
     * @param productionPolicies production policies
     * @param strategicGoals active goals
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems,
            List<FactionStockPolicyState> stockPolicies,
            List<FactionProductionPolicyState> productionPolicies,
            List<FactionStrategicGoalState> strategicGoals) {
        this(
                factionContentId,
                minimumMarketAccessRelation,
                relations,
                controlledSystems,
                0,
                0,
                stockPolicies,
                productionPolicies,
                strategicGoals);
    }

    /**
     * Source-compatible pre-Stage-17D strategic constructor.
     *
     * <p>Existing controlled systems receive neutral maintenance clocks. No political claim,
     * recognition or foreign construction right is invented by this compatibility boundary.</p>
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold
     * @param relations directed relations
     * @param controlledSystems controlled systems
     * @param stationTaxBasisPoints own-station tax rate
     * @param foreignTerritoryTariffBasisPoints foreign-market tariff rate
     * @param stockPolicies base stock floors
     * @param productionPolicies production policies
     * @param strategicGoals active strategic goals
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems,
            int stationTaxBasisPoints,
            int foreignTerritoryTariffBasisPoints,
            List<FactionStockPolicyState> stockPolicies,
            List<FactionProductionPolicyState> productionPolicies,
            List<FactionStrategicGoalState> strategicGoals) {
        this(
                factionContentId,
                minimumMarketAccessRelation,
                relations,
                controlledSystems,
                stationTaxBasisPoints,
                foreignTerritoryTariffBasisPoints,
                stockPolicies,
                productionPolicies,
                strategicGoals,
                List.of(),
                legacyControlStates(controlledSystems),
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
     * @param stationTaxBasisPoints own-station tax rate
     * @param foreignTerritoryTariffBasisPoints foreign-market tariff rate
     * @param stockPolicies базовые stock floors
     * @param productionPolicies production policies
     * @param strategicGoals active strategic goals
     * @param territorialClaims political claim states
     * @param territorialControlStates maintenance state for every controlled system
     * @param territorialRecognitions directed territorial recognition states
     * @param constructionRightsGranted foreign construction concessions granted by this faction
     */
    public FactionStrategicState {
        factionContentId = requireId(factionContentId, "Faction content ID");
        if (minimumMarketAccessRelation < -100 || minimumMarketAccessRelation > 100) {
            throw new IllegalArgumentException("Market-access threshold должен быть в диапазоне [-100, 100]");
        }
        validateBasisPoints(stationTaxBasisPoints, "Station tax");
        validateBasisPoints(foreignTerritoryTariffBasisPoints, "Foreign territory tariff");
        Objects.requireNonNull(relations, "Faction relations не заданы");
        Objects.requireNonNull(controlledSystems, "Controlled systems не заданы");
        Objects.requireNonNull(stockPolicies, "Faction stock policies не заданы");
        Objects.requireNonNull(productionPolicies, "Faction production policies не заданы");
        Objects.requireNonNull(strategicGoals, "Faction strategic goals не заданы");
        Objects.requireNonNull(territorialClaims, "Territorial claims not set");
        Objects.requireNonNull(territorialControlStates, "Territorial control states not set");
        Objects.requireNonNull(territorialRecognitions, "Territorial recognitions not set");
        Objects.requireNonNull(constructionRightsGranted, "Construction rights not set");

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

        List<TerritorialClaimState> sortedClaims = new ArrayList<>(territorialClaims.size());
        Set<StarSystemId> claimedSystems = new HashSet<>();
        for (TerritorialClaimState claim : territorialClaims) {
            TerritorialClaimState value = Objects.requireNonNull(claim, "Territorial claim not set");
            if (!claimedSystems.add(value.systemId())) {
                throw new IllegalArgumentException("Duplicate territorial claim: " + value.systemId());
            }
            sortedClaims.add(value);
        }
        sortedClaims.sort(Comparator.naturalOrder());
        territorialClaims = List.copyOf(sortedClaims);

        List<TerritorialControlState> sortedControl = new ArrayList<>(territorialControlStates.size());
        Set<StarSystemId> maintainedSystems = new HashSet<>();
        for (TerritorialControlState control : territorialControlStates) {
            TerritorialControlState value = Objects.requireNonNull(control, "Territorial control state not set");
            if (!maintainedSystems.add(value.systemId())) {
                throw new IllegalArgumentException("Duplicate territorial control state: " + value.systemId());
            }
            sortedControl.add(value);
        }
        if (!maintainedSystems.equals(seenSystems)) {
            throw new IllegalArgumentException(
                    "Territorial control states must exactly cover controlledSystems");
        }
        sortedControl.sort(Comparator.naturalOrder());
        territorialControlStates = List.copyOf(sortedControl);

        List<TerritorialRecognitionState> sortedRecognitions = new ArrayList<>(territorialRecognitions.size());
        Set<String> recognitionKeys = new HashSet<>();
        for (TerritorialRecognitionState recognition : territorialRecognitions) {
            TerritorialRecognitionState value = Objects.requireNonNull(
                    recognition, "Territorial recognition not set");
            if (value.targetFactionContentId().equals(factionContentId)) {
                throw new IllegalArgumentException("Faction cannot recognize its own territorial position");
            }
            String key = value.systemId().value() + "\u0000" + value.targetFactionContentId()
                    + "\u0000" + value.kind();
            if (!recognitionKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate territorial recognition: " + key);
            }
            sortedRecognitions.add(value);
        }
        sortedRecognitions.sort(Comparator.naturalOrder());
        territorialRecognitions = List.copyOf(sortedRecognitions);

        List<TerritorialConstructionRightState> sortedRights = new ArrayList<>(constructionRightsGranted.size());
        Set<String> rightKeys = new HashSet<>();
        for (TerritorialConstructionRightState right : constructionRightsGranted) {
            TerritorialConstructionRightState value = Objects.requireNonNull(right, "Construction right not set");
            if (value.granteeFactionContentId().equals(factionContentId)) {
                throw new IllegalArgumentException("Faction does not need a construction concession from itself");
            }
            String key = value.systemId().value() + "\u0000" + value.granteeFactionContentId();
            if (!rightKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate territorial construction right: " + key);
            }
            sortedRights.add(value);
        }
        sortedRights.sort(Comparator.naturalOrder());
        constructionRightsGranted = List.copyOf(sortedRights);
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
     * Finds this faction's political claim to one system.
     *
     * @param systemId system to inspect
     * @return claim or {@code null}
     */
    public TerritorialClaimState claimFor(StarSystemId systemId) {
        if (systemId == null) {
            return null;
        }
        for (TerritorialClaimState claim : territorialClaims) {
            if (claim.systemId().equals(systemId)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Finds this faction's maintenance state for established control.
     *
     * @param systemId system to inspect
     * @return control state or {@code null}
     */
    public TerritorialControlState controlStateFor(StarSystemId systemId) {
        if (systemId == null) {
            return null;
        }
        for (TerritorialControlState control : territorialControlStates) {
            if (control.systemId().equals(systemId)) {
                return control;
            }
        }
        return null;
    }

    /**
     * Checks an explicit construction concession granted by this faction.
     *
     * @param granteeFactionContentId proposed foreign builder
     * @param systemId target controlled system
     * @param worldTick authoritative world tick
     * @return true when an unexpired matching right exists
     */
    public boolean grantsConstructionRightTo(
            String granteeFactionContentId,
            StarSystemId systemId,
            long worldTick) {
        if (granteeFactionContentId == null || systemId == null || worldTick < 0L) {
            return false;
        }
        String grantee = granteeFactionContentId.strip();
        for (TerritorialConstructionRightState right : constructionRightsGranted) {
            if (right.granteeFactionContentId().equals(grantee)
                    && right.systemId().equals(systemId)
                    && right.activeAt(worldTick)) {
                return true;
            }
        }
        return false;
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

    private static List<TerritorialControlState> legacyControlStates(List<StarSystemId> controlledSystems) {
        List<TerritorialControlState> result = new ArrayList<>();
        for (StarSystemId systemId : Objects.requireNonNull(controlledSystems, "Controlled systems not set")) {
            result.add(new TerritorialControlState(
                    Objects.requireNonNull(systemId, "Controlled StarSystemId not set"),
                    0L,
                    0L,
                    0L));
        }
        return List.copyOf(result);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " не задан").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " не может быть пустым");
        }
        return normalized;
    }

    private static void validateBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " должен быть в диапазоне [0, 10000] bps");
        }
    }
}

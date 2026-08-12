package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent strategic policy одной faction: directed relations, market-access threshold и territory.
 *
 * @param factionContentId stable owner faction content ID
 * @param minimumMarketAccessRelation минимальное directed relation для доступа к рынкам faction
 * @param relations directed relations к другим factions
 * @param controlledSystems стратегически контролируемые StarSystem IDs
 */
public record FactionStrategicState(
        String factionContentId,
        int minimumMarketAccessRelation,
        List<FactionRelationState> relations,
        List<StarSystemId> controlledSystems) implements Comparable<FactionStrategicState> {

    /**
     * Валидирует state и нормализует canonical ordering.
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation threshold в диапазоне [-100, 100]
     * @param relations directed relation list
     * @param controlledSystems controlled system IDs
     */
    public FactionStrategicState {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        if (minimumMarketAccessRelation < -100 || minimumMarketAccessRelation > 100) {
            throw new IllegalArgumentException("Market-access threshold должен быть в диапазоне [-100, 100]");
        }
        Objects.requireNonNull(relations, "Faction relations не заданы");
        Objects.requireNonNull(controlledSystems, "Controlled systems не заданы");

        List<FactionRelationState> sortedRelations = new ArrayList<>(relations.size());
        Set<String> relationTargets = new HashSet<>();
        for (FactionRelationState relation : relations) {
            FactionRelationState value = Objects.requireNonNull(relation, "FactionRelationState не задан");
            if (value.targetFactionContentId().equals(factionContentId)) {
                throw new IllegalArgumentException("Self relation не хранится явно");
            }
            if (!relationTargets.add(value.targetFactionContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующая faction relation: " + value.targetFactionContentId());
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
     * Проверяет стратегическое владение системой.
     *
     * @param systemId stable system ID
     * @return true, если system входит в controlled territory
     */
    public boolean controls(StarSystemId systemId) {
        return systemId != null && controlledSystems.contains(systemId);
    }

    /**
     * Сравнивает по stable owner content ID.
     *
     * @param other другой strategic state
     * @return lexical comparison
     */
    @Override
    public int compareTo(FactionStrategicState other) {
        return factionContentId.compareTo(
                Objects.requireNonNull(other, "FactionStrategicState не задан").factionContentId);
    }
}

package com.spacesim.world;

import java.util.Objects;

/**
 * Directed relation одной faction к другой.
 *
 * @param targetFactionContentId stable content ID target faction
 * @param relation отношение в диапазоне [-100, 100]
 */
public record FactionRelationState(
        String targetFactionContentId,
        int relation) implements Comparable<FactionRelationState> {

    /**
     * Нормализует content ID и валидирует relation.
     *
     * @param targetFactionContentId stable target faction content ID
     * @param relation отношение в диапазоне [-100, 100]
     */
    public FactionRelationState {
        targetFactionContentId = Objects.requireNonNull(
                targetFactionContentId,
                "Target faction content ID не задан").strip();
        if (targetFactionContentId.isEmpty()) {
            throw new IllegalArgumentException("Target faction content ID не может быть пустым");
        }
        if (relation < -100 || relation > 100) {
            throw new IllegalArgumentException("Faction relation должна быть в диапазоне [-100, 100]");
        }
    }

    /**
     * Сравнивает по stable target content ID для deterministic persistence.
     *
     * @param other другая relation
     * @return результат lexical сравнения target content ID
     */
    @Override
    public int compareTo(FactionRelationState other) {
        return targetFactionContentId.compareTo(
                Objects.requireNonNull(other, "FactionRelationState не задан").targetFactionContentId);
    }
}

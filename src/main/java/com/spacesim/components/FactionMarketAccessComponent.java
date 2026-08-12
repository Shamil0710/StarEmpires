package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

import java.util.Arrays;

/**
 * Transient runtime market-access rules одной станции.
 *
 * <p>Компонент материализуется из persistent faction diplomacy при восстановлении WorldSimulation
 * и намеренно не входит в EntityState: source of truth остаётся world-level strategic policy.
 * Отсутствие компонента означает unrestricted legacy market.</p>
 */
public final class FactionMarketAccessComponent implements Component {
    private final boolean[] allowedFactionIds = new boolean[Constants.MAX_FACTIONS];
    private boolean allowUnfactioned = true;

    /** Создаёт policy, изначально запрещающую все named factions. */
    public FactionMarketAccessComponent() {
    }

    /**
     * Разрешает или запрещает доступ сущностям без faction membership.
     *
     * @param allowed новое состояние доступа
     * @return этот компонент
     */
    public FactionMarketAccessComponent allowUnfactioned(boolean allowed) {
        allowUnfactioned = allowed;
        return this;
    }

    /**
     * Явно задаёт доступ runtime faction.
     *
     * @param factionId dense runtime faction ID
     * @param allowed разрешён ли доступ
     * @return этот компонент
     */
    public FactionMarketAccessComponent setFactionAllowed(int factionId, boolean allowed) {
        requireFactionId(factionId);
        allowedFactionIds[factionId] = allowed;
        return this;
    }

    /**
     * Проверяет доступ участника.
     *
     * @param participantFactionId runtime faction ID либо {@code -1}
     * @return {@code true}, если market trade разрешён
     */
    public boolean canTrade(int participantFactionId) {
        if (participantFactionId < 0) {
            return allowUnfactioned;
        }
        return participantFactionId < allowedFactionIds.length
                && allowedFactionIds[participantFactionId];
    }

    /** @return defensive copy маски доступа для тестовой диагностики */
    public boolean[] copyAllowedFactionIds() {
        return Arrays.copyOf(allowedFactionIds, allowedFactionIds.length);
    }

    private static void requireFactionId(int factionId) {
        if (factionId < 0 || factionId >= Constants.MAX_FACTIONS) {
            throw new IllegalArgumentException("Некорректный runtime faction ID market access");
        }
    }
}

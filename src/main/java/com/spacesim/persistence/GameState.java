package com.spacesim.persistence;

import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.PriceRecorderSystem;

import java.util.List;

/**
 * Версионированный value-based снимок authoritative состояния игровой симуляции.
 *
 * <p>Формат содержит clock, точные RNG states, системные таймеры, ledger, следующий EntityId и
 * полный список {@link EntityState}. Schema v2 расширила item-indexed arrays и добавила stable
 * archetype IDs. Schema v3 сохраняет configured market target отдельно от effective target, чтобы
 * strategic demand можно было безопасно повышать и снижать без потери station baseline.</p>
 *
 * @param schemaVersion версия бинарной/логической схемы
 * @param rootSeed корневой seed игровой сессии
 * @param clock полное состояние fixed-step часов
 * @param nextEntityIdValue следующее значение общего ID allocator
 * @param eventRandomState состояние RNG событий
 * @param asteroidRandomState состояние RNG астероидов
 * @param events состояние менеджера событий
 * @param asteroidSpawner внутренние таймеры asteroid spawner
 * @param priceRecorder внутренний таймер истории цен
 * @param ledger экономический журнал и его sequence
 * @param entities полный набор persistent-сущностей
 */
public record GameState(
        int schemaVersion,
        long rootSeed,
        SimulationClock.State clock,
        long nextEntityIdValue,
        long eventRandomState,
        long asteroidRandomState,
        GlobalEventManager.State events,
        AsteroidSpawnSystem.State asteroidSpawner,
        PriceRecorderSystem.State priceRecorder,
        EconomicLedger.State ledger,
        List<EntityState> entities) {
    /** Текущая версия persistent schema с configured market target provenance. */
    public static final int CURRENT_VERSION = 3;

    /** Schema с расширяемыми item slots и stable entity archetype, но без market target provenance. */
    public static final int ITEM_CAPACITY_ARCHETYPE_VERSION = 2;

    /** Историческая Stage-3 schema с пятью item slots и без stable archetype ID. */
    public static final int LEGACY_STAGE3_VERSION = 1;
}

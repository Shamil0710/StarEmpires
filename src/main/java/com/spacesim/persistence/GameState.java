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
 * полный список {@link EntityState}. Начиная со schema v2 snapshot также фиксирует semantic
 * fingerprint content catalog, чтобы сохранение нельзя было продолжить на несовместимых данных.</p>
 *
 * @param schemaVersion версия бинарной/логической схемы
 * @param contentFingerprint SHA-256 semantic fingerprint каталога текущей сессии
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
        String contentFingerprint,
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
    /** Текущая версия persistent schema. */
    public static final int CURRENT_VERSION = 2;

    /** Последняя schema до введения content fingerprint и расширяемой item slot-capacity. */
    public static final int LEGACY_STAGE3_VERSION = 1;
}

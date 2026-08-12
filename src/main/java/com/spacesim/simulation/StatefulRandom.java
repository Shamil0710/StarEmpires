package com.spacesim.simulation;

import java.util.random.RandomGenerator;

/**
 * Компактный детерминированный генератор SplitMix64 с явно сохраняемым внутренним состоянием.
 *
 * <p>В отличие от {@link java.util.Random}, состояние этого генератора является частью публичного
 * контракта симуляции: {@link #getState()} можно записать в save-state, а новый экземпляр с тем же
 * значением продолжит последовательность ровно со следующего случайного числа. Алгоритм не зависит
 * от wall-clock, потоков или реализации RNG конкретной JVM.</p>
 */
public final class StatefulRandom implements RandomGenerator {
    private static final long GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_MULTIPLIER_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_MULTIPLIER_2 = 0x94D049BB133111EBL;

    private long state;

    /**
     * Создаёт генератор с указанным начальным или восстановленным внутренним состоянием.
     *
     * @param state произвольные 64 бита состояния; все значения допустимы
     */
    public StatefulRandom(long state) {
        this.state = state;
    }

    /**
     * Возвращает состояние, из которого будет вычислено следующее случайное значение.
     *
     * @return точное сериализуемое состояние генератора
     */
    public long getState() {
        return state;
    }

    /**
     * Генерирует следующие 64 псевдослучайных бита и продвигает сохраняемое состояние.
     *
     * @return очередное псевдослучайное значение
     */
    @Override
    public long nextLong() {
        long mixed = state += GAMMA;
        mixed = (mixed ^ (mixed >>> 30)) * MIX_MULTIPLIER_1;
        mixed = (mixed ^ (mixed >>> 27)) * MIX_MULTIPLIER_2;
        return mixed ^ (mixed >>> 31);
    }
}

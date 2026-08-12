package com.spacesim.simulation;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Фабрика воспроизводимых независимых потоков случайности одной игровой сессии.
 *
 * <p>Каждое имя потока детерминированно смешивается с корневым seed. Поэтому добавление вызовов RNG
 * в одной подсистеме не сдвигает последовательность другой подсистемы, если им назначены разные
 * имена потоков.</p>
 */
public final class SimulationRandom {
    private final long rootSeed;

    /**
     * @param rootSeed корневой seed игровой сессии
     */
    public SimulationRandom(long rootSeed) {
        this.rootSeed = rootSeed;
    }

    /** @return корневой seed игровой сессии */
    public long getRootSeed() {
        return rootSeed;
    }

    /**
     * Создаёт новый генератор, воспроизводимый по паре root seed + stream name.
     *
     * @param streamName устойчивое непустое имя подсистемы
     * @return новый независимый генератор в начальном состоянии соответствующего потока
     * @throws IllegalArgumentException если имя отсутствует или пусто
     */
    public RandomGenerator createStream(String streamName) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalArgumentException("Имя RNG-потока не должно быть пустым");
        }
        long streamHash = fnv1a64(streamName);
        return new Random(mix64(rootSeed ^ streamHash));
    }

    private long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return mixed;
    }
}

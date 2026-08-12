package com.spacesim.simulation;

import java.nio.charset.StandardCharsets;

/**
 * Детерминированный корневой источник независимых RNG-потоков симуляции.
 *
 * <p>Каждый именованный поток получает seed только из {@code rootSeed} и имени через стабильный
 * FNV-1a + avalanche mix. Поэтому добавление случайного вызова в одной подсистеме не сдвигает
 * последовательности остальных. Возвращаемый {@link StatefulRandom} имеет явно сериализуемое
 * состояние и пригоден для точного продолжения save/load.</p>
 */
public final class SimulationRandom {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final long rootSeed;

    /**
     * Создаёт сервис от произвольного корневого seed.
     *
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
     * Создаёт новый независимый stateful RNG-поток для подсистемы.
     *
     * <p>Повторный вызов с тем же именем создаёт новый генератор в том же начальном состоянии, а не
     * возвращает ранее продвинутый экземпляр. Владельцу потока следует хранить сам экземпляр.</p>
     *
     * @param streamName непустое стабильное имя подсистемы
     * @return новый генератор в детерминированном начальном состоянии
     * @throws IllegalArgumentException если имя {@code null} или пустое
     */
    public StatefulRandom createStream(String streamName) {
        return new StatefulRandom(deriveSeed(streamName));
    }

    /**
     * Восстанавливает поток из ранее сохранённого внутреннего состояния.
     *
     * @param state точное значение {@link StatefulRandom#getState()}
     * @return новый генератор, продолжающий последовательность с этого состояния
     */
    public StatefulRandom restoreStream(long state) {
        return new StatefulRandom(state);
    }

    /**
     * Вычисляет стабильный seed именованного потока без создания генератора.
     *
     * @param streamName непустое стабильное имя подсистемы
     * @return seed потока
     * @throws IllegalArgumentException если имя {@code null} или пустое
     */
    public long deriveSeed(String streamName) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalArgumentException("Имя RNG-потока не должно быть пустым");
        }

        long hash = FNV_OFFSET_BASIS ^ rootSeed;
        byte[] bytes = streamName.getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= FNV_PRIME;
        }
        hash ^= hash >>> 30;
        hash *= 0xbf58476d1ce4e5b9L;
        hash ^= hash >>> 27;
        hash *= 0x94d049bb133111ebL;
        hash ^= hash >>> 31;
        return hash;
    }
}

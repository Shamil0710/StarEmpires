package com.spacesim.model;

import com.spacesim.constants.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Неизменяемая конфигурация поддерживаемого набора конечных астероидных источников.
 *
 * <p>Конфигурация задаёт добываемый товар, разрешённые точки появления, начальное и целевое
 * количество источников, период пополнения, диапазон запаса и seed. Один экземпляр можно безопасно
 * использовать для создания нескольких независимых {@link com.spacesim.systems.AsteroidSpawnSystem}
 * с одинаковым воспроизводимым расписанием.</p>
 */
public final class AsteroidSpawnConfig {
    private final int resourceItem;
    private final List<AsteroidSpawnPoint> spawnPoints;
    private final int initialCount;
    private final int targetCount;
    private final float refillIntervalSeconds;
    private final long minResource;
    private final long maxResource;
    private final long seed;

    /**
     * Создаёт и проверяет конфигурацию ресурсного пояса.
     *
     * @param resourceItem идентификатор добываемого товара
     * @param spawnPoints непустой список уникально именованных разрешённых точек появления
     * @param initialCount число источников, создаваемых при первой обработке системы
     * @param targetCount максимальное число одновременно поддерживаемых активных источников
     * @param refillIntervalSeconds положительный период проверки пополнения в игровых секундах
     * @param minResource минимальный положительный запас одного источника
     * @param maxResource максимальный запас одного источника, не меньше минимального
     * @param seed seed генератора, определяющего выбор точек и размер запасов
     * @throws IllegalArgumentException если товар не добываемый, список точек некорректен, счётчики
     *                                  противоречат друг другу, интервал неположителен или диапазон
     *                                  ресурса недопустим
     */
    public AsteroidSpawnConfig(
            int resourceItem,
            List<AsteroidSpawnPoint> spawnPoints,
            int initialCount,
            int targetCount,
            float refillIntervalSeconds,
            long minResource,
            long maxResource,
            long seed) {
        ItemType item = ItemType.fromId(resourceItem);
        if (item == null || !item.isMineable()) {
            throw new IllegalArgumentException("Конфигурация должна использовать добываемый товар");
        }
        if (spawnPoints == null || spawnPoints.isEmpty()) {
            throw new IllegalArgumentException("Нужно задать хотя бы одну точку астероидного пояса");
        }
        if (spawnPoints.stream().anyMatch(point -> point == null)) {
            throw new IllegalArgumentException("Точка астероидного пояса не должна быть null");
        }
        Set<String> pointIds = new HashSet<>();
        for (AsteroidSpawnPoint point : spawnPoints) {
            if (!pointIds.add(point.id())) {
                throw new IllegalArgumentException("Повторяющийся идентификатор точки: " + point.id());
            }
        }
        if (initialCount < 0 || targetCount < initialCount || targetCount > spawnPoints.size()) {
            throw new IllegalArgumentException("Некорректное начальное или целевое число астероидов");
        }
        if (!Float.isFinite(refillIntervalSeconds) || refillIntervalSeconds <= 0f) {
            throw new IllegalArgumentException("Интервал пополнения должен быть конечным и положительным");
        }
        if (minResource <= 0L || maxResource < minResource || maxResource == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Некорректный диапазон запаса астероида");
        }

        this.resourceItem = resourceItem;
        this.spawnPoints = List.copyOf(spawnPoints);
        this.initialCount = initialCount;
        this.targetCount = targetCount;
        this.refillIntervalSeconds = refillIntervalSeconds;
        this.minResource = minResource;
        this.maxResource = maxResource;
        this.seed = seed;
    }

    /** @return идентификатор товара, содержащегося в создаваемых источниках */
    public int getResourceItem() {
        return resourceItem;
    }

    /** @return неизменяемый список разрешённых точек появления */
    public List<AsteroidSpawnPoint> getSpawnPoints() {
        return spawnPoints;
    }

    /** @return число астероидов, создаваемых при первом обновлении */
    public int getInitialCount() {
        return initialCount;
    }

    /** @return целевое максимальное число одновременно активных астероидов */
    public int getTargetCount() {
        return targetCount;
    }

    /**
     * Совместимое доменное имя максимального числа одновременно активных астероидов.
     *
     * @return то же значение, что и {@link #getTargetCount()}
     */
    public int maxActiveAsteroids() {
        return targetCount;
    }

    /** @return период проверки пополнения в игровых секундах */
    public float getRefillIntervalSeconds() {
        return refillIntervalSeconds;
    }

    /** @return минимальный запас ресурса одного нового астероида */
    public long getMinResource() {
        return minResource;
    }

    /** @return максимальный запас ресурса одного нового астероида */
    public long getMaxResource() {
        return maxResource;
    }

    /** @return seed воспроизводимого генератора появления */
    public long getSeed() {
        return seed;
    }

    /**
     * Создаёт конфигурацию демонстрационного мира: четыре исходных источника, пополнение до шести
     * раз в двадцать секунд, по 36–84 единицы руды на источник.
     *
     * @return новая валидная конфигурация демонстрационного астероидного пояса
     */
    public static AsteroidSpawnConfig demoWorld() {
        return new AsteroidSpawnConfig(
                Constants.ITEM_ORE,
                List.of(
                        new AsteroidSpawnPoint("NW-1", 150f, 1_230f),
                        new AsteroidSpawnPoint("NW-2", 310f, 1_145f),
                        new AsteroidSpawnPoint("N-1", 610f, 1_260f),
                        new AsteroidSpawnPoint("NE-1", 1_020f, 1_210f),
                        new AsteroidSpawnPoint("NE-2", 1_310f, 1_245f),
                        new AsteroidSpawnPoint("E-1", 1_720f, 1_120f),
                        new AsteroidSpawnPoint("SW-1", 240f, 1_020f),
                        new AsteroidSpawnPoint("E-2", 1_860f, 900f)
                ),
                4,
                6,
                20f,
                36L,
                84L,
                0x5EED_2026L);
    }
}

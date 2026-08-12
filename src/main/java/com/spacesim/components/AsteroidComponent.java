package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.model.ItemType;

/**
 * Конечный природный источник одного добываемого ресурса.
 *
 * <p>Компонент хранит устойчивый идентификатор точки астероидного пояса, тип ресурса,
 * первоначальный объём и изменяемый остаток. {@link com.spacesim.systems.MiningSystem}
 * уменьшает {@link #remainingResource} и удаляет сущность после истощения.</p>
 */
public final class AsteroidComponent implements Component {
    /** Идентификатор разрешённой точки появления в конфигурации пояса. */
    public final String spawnPointId;

    /** Числовой идентификатор добываемого товара. */
    public final int resourceItem;

    /** Первоначальный объём ресурса в целых единицах. */
    public final long initialResource;

    /** Текущий остаток ресурса в целых единицах. */
    public long remainingResource;

    /**
     * Создаёт конечный ресурсный источник.
     *
     * @param spawnPointId непустой идентификатор точки пояса
     * @param resourceItem идентификатор товара, помеченного как добываемый
     * @param resourceAmount строго положительный первоначальный объём
     */
    public AsteroidComponent(String spawnPointId, int resourceItem, long resourceAmount) {
        if (spawnPointId == null || spawnPointId.strip().isEmpty()) {
            throw new IllegalArgumentException("Идентификатор точки астероида не должен быть пустым");
        }
        ItemType item = ItemType.fromId(resourceItem);
        if (item == null || !item.isMineable()) {
            throw new IllegalArgumentException("Астероид должен содержать добываемый товар");
        }
        if (resourceAmount <= 0L) {
            throw new IllegalArgumentException("Запас астероида должен быть положительным");
        }

        this.spawnPointId = spawnPointId.strip();
        this.resourceItem = resourceItem;
        this.initialResource = resourceAmount;
        this.remainingResource = resourceAmount;
    }

    /** @return {@code true}, если ресурс полностью исчерпан или состояние повреждено отрицательным остатком. */
    public boolean isDepleted() {
        return remainingResource <= 0L;
    }

    /**
     * Возвращает нормированную заполненность источника.
     *
     * @return значение в диапазоне {@code [0, 1]}; повреждённые значения безопасно ограничиваются
     */
    public float getRemainingRatio() {
        if (remainingResource <= 0L || initialResource <= 0L) {
            return 0f;
        }
        if (remainingResource >= initialResource) {
            return 1f;
        }
        return (float) ((double) remainingResource / (double) initialResource);
    }
}

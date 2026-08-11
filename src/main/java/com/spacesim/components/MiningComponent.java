package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;
import com.spacesim.model.ItemType;

/**
 * Конфигурация и накопленное состояние абстрактной корабельной добычи.
 *
 * <p>Добыча не привязана к координатам или конечному месторождению: активный добывающий корабль
 * непрерывно переводит дробную производительность в целые единицы собственного
 * {@link InventoryComponent инвентаря}. {@link com.spacesim.systems.MiningSystem} проверяет роль
 * корабля, добываемость ресурса, доступную вместимость и численные инварианты перед каждым
 * изменением склада.</p>
 */
public class MiningComponent implements Component {
    /** Идентификатор извлекаемого товара; штатно указывает на добываемый ресурс. */
    public int resourceItem = Constants.ITEM_ORE;

    /** Производительность в единицах ресурса за секунду игрового времени. */
    public float extractionPerSecond = 0.5f;

    /** Накопленная дробная единица добычи; штатный диапазон {@code [0, 1)}. */
    public double extractionRemainder = 0d;

    /** Общее фактически помещённое в трюм количество; при переполнении насыщается на максимуме. */
    public long totalMined = 0L;

    /** Признак включённого добывающего оборудования. */
    public boolean active = true;

    /**
     * Создаёт активное оборудование для добычи руды со скоростью {@code 0.5} единицы в секунду.
     */
    public MiningComponent() {
    }

    /**
     * Создаёт активное оборудование с заданным ресурсом и производительностью.
     *
     * @param resourceItem идентификатор добываемого товара
     * @param extractionPerSecond конечная строго положительная производительность
     * @throws IllegalArgumentException если товар отсутствует, не является добываемым либо
     *                                  производительность неположительна или неконечна
     */
    public MiningComponent(int resourceItem, float extractionPerSecond) {
        ItemType item = ItemType.fromId(resourceItem);
        if (item == null || !item.isMineable()) {
            throw new IllegalArgumentException("Корабль не может добывать товар: " + resourceItem);
        }
        if (!Float.isFinite(extractionPerSecond) || extractionPerSecond <= 0f) {
            throw new IllegalArgumentException(
                    "Скорость добычи должна быть конечной и строго положительной");
        }
        this.resourceItem = resourceItem;
        this.extractionPerSecond = extractionPerSecond;
    }
}

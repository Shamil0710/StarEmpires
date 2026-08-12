package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;
import com.spacesim.model.ItemType;
import com.spacesim.persistence.EntityId;

/**
 * Конфигурация и сериализуемое состояние автономного цикла добывающего корабля.
 *
 * <p>{@link com.spacesim.systems.MiningSystem} выбирает совместимый
 * {@link AsteroidComponent астероид}, перемещает корабль к нему, извлекает конечный запас в
 * собственный {@link InventoryComponent трюм}, возвращает груз на рынок и продаёт его. Persistent-
 * ссылки на цель и базу хранятся как устойчивые {@link EntityId}; runtime-система разрешает их
 * через registry.</p>
 *
 * <p>Компонент не хранит деньги: authoritative баланс добывающего корабля находится только в
 * {@link WalletComponent}. Производительность выражена в единицах товара за секунду, скорости и
 * радиусы — в мировых единицах.</p>
 */
public class MiningComponent implements Component {
    /** Состояние полного автономного цикла добычи и доставки. */
    public enum State {
        /** Корабль ищет ближайший совместимый источник ресурса. */
        SEARCHING("Поиск астероида"),
        /** Корабль летит к выбранному источнику. */
        TRAVEL_TO_ASTEROID("Полёт к астероиду"),
        /** Корабль находится у источника и заполняет трюм. */
        MINING("Добыча"),
        /** Корабль с грузом направляется к рынку разгрузки. */
        RETURNING_TO_BASE("Возврат на базу"),
        /** Корабль пытается продать добытый ресурс выбранному рынку. */
        UNLOADING("Разгрузка"),
        /** Оборудование выключено; состояние сохраняется до повторного включения. */
        PAUSED("Оборудование выключено");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        /** @return непустое русское имя состояния для интерфейса */
        public String getDisplayName() {
            return displayName;
        }
    }

    /** Идентификатор извлекаемого товара; штатно указывает на добываемый ресурс. */
    public int resourceItem = Constants.ITEM_ORE;
    /** Производительность в единицах ресурса за секунду игрового времени. */
    public float extractionPerSecond = 0.5f;
    /** Линейная скорость полёта в мировых единицах в секунду. */
    public float movementSpeed = 85f;
    /** Расстояние до астероида, на котором разрешается добыча. */
    public float extractionRange = 14f;
    /** Расстояние до станции, на котором разрешается разгрузка. */
    public float dockingRange = 10f;
    /** Накопленная дробная единица добычи; штатный диапазон {@code [0, 1)}. */
    public double extractionRemainder;
    /** Общее фактически помещённое в трюм количество; при переполнении насыщается на максимуме. */
    public long totalMined;
    /** Общее успешно проданное рынкам количество; при переполнении насыщается на максимуме. */
    public long totalDelivered;
    /** Признак включённого добывающего оборудования и автономного цикла. */
    public boolean active = true;
    /** Текущий этап автономного цикла; повреждённый {@code null} безопасно восстанавливается. */
    public State state = State.SEARCHING;
    /** Persistent ID выбранного астероида либо {@code null}, если источник отсутствует. */
    public EntityId targetAsteroidId;

    /**
     * Persistent ID предпочтительного рынка разгрузки либо {@code null} для автоматического выбора.
     * Если рынок исчез, перестал торговать ресурсом, заполнился или не может оплатить хотя бы одну
     * единицу груза, система выбирает другую станцию.
     */
    public EntityId homeBaseId;

    /** Создаёт активное оборудование для добычи руды со скоростью {@code 0.5} единицы в секунду. */
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

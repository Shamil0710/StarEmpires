package com.spacesim.constants;

import com.spacesim.model.ItemType;

/**
 * Единый каталог идентификаторов и числовых параметров экономической модели.
 *
 * <p>Идентификаторы товаров и фракций используются как индексы в массивах
 * компонентов Ashley. Поэтому порядок элементов в массивах имён и базовых цен
 * должен оставаться согласованным с соответствующими константами
 * {@code ITEM_*} и {@code FACTION_*}. Публичные массивы предназначены только
 * для чтения: изменение их содержимого во время работы меняет глобальные
 * правила сразу для всех сущностей.</p>
 */
public class Constants {
    /**
     * Создаёт экземпляр пространства констант без собственного состояния.
     * Конструктор сохранён публичным для совместимости; обычно достаточно
     * обращаться к статическим полям класса.
     */
    public Constants() {
    }

    /**
     * Число поддерживаемых типов товара и обязательная длина товарных массивов.
     * Допустимый идентификатор товара лежит в диапазоне
     * {@code [0, MAX_ITEMS)}.
     */
    public static final int MAX_ITEMS = 5;

    /** Идентификатор руды. */
    public static final int ITEM_ORE = 0;

    /** Идентификатор энергии. */
    public static final int ITEM_ENERGY = 1;

    /** Идентификатор продовольствия. */
    public static final int ITEM_FOOD = 2;

    /** Идентификатор стали. */
    public static final int ITEM_STEEL = 3;

    /** Идентификатор вооружения. */
    public static final int ITEM_WEAPONS = 4;

    /**
     * Отображаемые имена товаров, индексированные идентификатором товара.
     * Длина массива равна {@link #MAX_ITEMS}; массив следует считать
     * неизменяемым справочником.
     */
    public static final String[] ITEM_NAMES = createItemNames();

    /**
     * Базовые цены товаров в кредитах за одну целую единицу товара.
     * {@link com.spacesim.systems.MarketSystem} масштабирует эти положительные
     * значения в зависимости от запасов и экономических событий.
     */
    public static final float[] BASE_PRICES = createBasePrices();

    /**
     * Безопасно возвращает объектное описание товара по его числовому идентификатору.
     *
     * <p>Метод служит мостом от исторического API констант и массивов к типизированному каталогу.
     * Для некорректного идентификатора он не обращается к массиву и не выбрасывает исключение.</p>
     *
     * @param itemId проверяемый идентификатор товара
     * @return соответствующий товар либо {@code null}, если идентификатор вне каталога
     */
    public static ItemType getItemType(int itemId) {
        return ItemType.fromId(itemId);
    }

    /**
     * Число фракций и обязательная длина массива репутации.
     * Допустимый идентификатор фракции лежит в диапазоне
     * {@code [0, MAX_FACTIONS)}.
     */
    public static final int MAX_FACTIONS = 3;

    /** Идентификатор нейтральной фракции. */
    public static final int FACTION_NEUTRAL = 0;

    /** Идентификатор Торговой лиги. */
    public static final int FACTION_TRADE_LEAGUE = 1;

    /** Идентификатор фракции шахтёров. */
    public static final int FACTION_MINERS = 2;

    /**
     * Русские отображаемые имена фракций, индексированные идентификатором
     * фракции. Длина массива равна {@link #MAX_FACTIONS}.
     */
    public static final String[] FACTION_NAMES = {"Нейтралы", "Торговая лига", "Шахтёры"};

    /** Минимальная репутация у одной фракции, в пунктах репутации. */
    public static final float MIN_REPUTATION = -100f;

    /** Максимальная репутация у одной фракции, в пунктах репутации. */
    public static final float MAX_REPUTATION = 100f;

    /** Прирост репутации за одну успешную торговую операцию, в пунктах. */
    public static final float REPUTATION_TRADE_GAIN = 1f;

    /**
     * Максимальная относительная поправка цены от репутации.
     * Значение {@code 0.15} соответствует пятнадцати процентам.
     */
    public static final float MAX_REPUTATION_PRICE_BONUS = 0.15f;

    /**
     * Размер стороны ячейки пространственного хеша в единицах координат мира.
     */
    public static final int CELL_SIZE = 200;

    /** Ширина расширенного игрового мира в условных координатных единицах. */
    public static final float WORLD_WIDTH = 2_000f;

    /** Высота расширенного игрового мира в условных координатных единицах. */
    public static final float WORLD_HEIGHT = 1_400f;

    private static String[] createItemNames() {
        String[] names = new String[MAX_ITEMS];
        for (ItemType item : ItemType.values()) {
            names[item.getId()] = item.getCodeName();
        }
        return names;
    }

    private static float[] createBasePrices() {
        float[] prices = new float[MAX_ITEMS];
        for (ItemType item : ItemType.values()) {
            prices[item.getId()] = item.getBasePrice();
        }
        return prices;
    }
}

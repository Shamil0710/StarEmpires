package com.spacesim.constants;

import com.spacesim.model.ItemType;

/**
 * Совместимые числовые параметры и исторические идентификаторы экономической модели.
 *
 * <p>Authoritative metadata товаров постепенно перенесена в versioned content catalog. Числовые
 * {@code ITEM_*} здесь сохраняются как стабильные runtime IDs core-контента и для совместимости
 * старого кода. {@link #MAX_ITEMS} теперь означает вместимость плотных hot-path массивов, а не
 * фактическое число определённых товаров.</p>
 */
public class Constants {
    /**
     * Создаёт экземпляр пространства констант без собственного состояния.
     * Конструктор сохранён публичным для совместимости.
     */
    public Constants() {
    }

    /**
     * Максимальное число плотных runtime item slots текущей persistent/simulation schema.
     *
     * <p>Фактическое число активных товаров задаёт {@code ContentCatalog}; допустимые runtime ID
     * каталога должны быть плотными от нуля и меньше этой capacity.</p>
     */
    public static final int MAX_ITEMS = 64;

    /** Идентификатор руды core-контента. */
    public static final int ITEM_ORE = 0;

    /** Идентификатор энергии core-контента. */
    public static final int ITEM_ENERGY = 1;

    /** Идентификатор продовольствия core-контента. */
    public static final int ITEM_FOOD = 2;

    /** Идентификатор стали core-контента. */
    public static final int ITEM_STEEL = 3;

    /** Идентификатор вооружения core-контента. */
    public static final int ITEM_WEAPONS = 4;

    /**
     * Legacy-кодовые имена известных Java {@link ItemType}; незанятые data-driven slots равны null.
     * Новый simulation/UI-код должен использовать content catalog.
     */
    public static final String[] ITEM_NAMES = createItemNames();

    /**
     * Legacy-базовые цены известных Java {@link ItemType}; незанятые slots равны нулю.
     * {@link com.spacesim.systems.MarketSystem} больше не использует этот массив.
     */
    public static final float[] BASE_PRICES = createBasePrices();

    /**
     * Возвращает историческое enum-описание core-товара, если оно существует.
     *
     * @param itemId проверяемый runtime ID
     * @return legacy enum либо {@code null} для data-only/некорректного ID
     */
    public static ItemType getItemType(int itemId) {
        return ItemType.fromId(itemId);
    }

    /** Число legacy-фракций и текущая длина массива репутации. */
    public static final int MAX_FACTIONS = 3;

    /** Идентификатор нейтральной фракции. */
    public static final int FACTION_NEUTRAL = 0;

    /** Идентификатор Торговой лиги. */
    public static final int FACTION_TRADE_LEAGUE = 1;

    /** Идентификатор фракции шахтёров. */
    public static final int FACTION_MINERS = 2;

    /** Legacy-имена фракций; будут перенесены в content catalog в Stage 4. */
    public static final String[] FACTION_NAMES = {"Нейтралы", "Торговая лига", "Шахтёры"};

    /** Минимальная репутация у одной фракции, в пунктах репутации. */
    public static final float MIN_REPUTATION = -100f;

    /** Максимальная репутация у одной фракции, в пунктах репутации. */
    public static final float MAX_REPUTATION = 100f;

    /** Прирост репутации за одну успешную торговую операцию, в пунктах. */
    public static final float REPUTATION_TRADE_GAIN = 1f;

    /** Максимальная относительная поправка цены от репутации. */
    public static final float MAX_REPUTATION_PRICE_BONUS = 0.15f;

    /** Размер стороны ячейки пространственного хеша в единицах координат мира. */
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

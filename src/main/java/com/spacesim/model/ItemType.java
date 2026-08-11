package com.spacesim.model;

import com.spacesim.constants.Constants;

/**
 * Неизменяемое описание одного товара экономической модели.
 *
 * <p>Числовой идентификатор сохраняет совместимость с плотными товарными массивами ECS-компонентов.
 * Он задаётся явно и не зависит от {@link #ordinal()}, поэтому изменение порядка констант enum не
 * должно менять формат инвентарей, рынков и рецептов. Английское кодовое имя и базовая цена
 * соответствуют историческим массивам {@link Constants#ITEM_NAMES} и
 * {@link Constants#BASE_PRICES}; русское имя предназначено для пользовательского интерфейса.</p>
 */
public enum ItemType {
    /** Добываемая руда — первичный твёрдый материал. */
    ORE(Constants.ITEM_ORE, "Ore", "Руда", ItemCategory.MATERIAL, 10f, true),

    /** Перевозимый энергоноситель, требующий герметичного газожидкостного отсека. */
    ENERGY(Constants.ITEM_ENERGY, "Energy", "Энергия", ItemCategory.GAS_LIQUID, 5f, false),

    /** Готовое продовольствие в контейнерной упаковке. */
    FOOD(Constants.ITEM_FOOD, "Food", "Продовольствие", ItemCategory.FINISHED_GOODS, 20f, false),

    /** Выплавленная сталь как конструкционный материал. */
    STEEL(Constants.ITEM_STEEL, "Steel", "Сталь", ItemCategory.MATERIAL, 50f, false),

    /** Готовое вооружение как конечная промышленная продукция. */
    WEAPONS(Constants.ITEM_WEAPONS, "Weapons", "Вооружение", ItemCategory.FINISHED_GOODS, 150f, false);

    private static final ItemType[] BY_ID = createIdIndex();

    private final int id;
    private final String codeName;
    private final String displayName;
    private final ItemCategory category;
    private final float basePrice;
    private final boolean mineable;

    ItemType(int id, String codeName, String displayName, ItemCategory category,
             float basePrice, boolean mineable) {
        this.id = id;
        this.codeName = codeName;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice;
        this.mineable = mineable;
    }

    /**
     * Возвращает устойчивый числовой идентификатор товара.
     *
     * @return индекс товара в диапазоне {@code [0, Constants.MAX_ITEMS)}
     */
    public int getId() {
        return id;
    }

    /**
     * Возвращает историческое английское кодовое имя товара.
     *
     * @return непустое имя, совпадающее с элементом {@link Constants#ITEM_NAMES}
     */
    public String getCodeName() {
        return codeName;
    }

    /**
     * Возвращает русское отображаемое имя товара.
     *
     * @return непустое имя для пользовательского интерфейса
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Возвращает категорию грузового хранения товара.
     *
     * @return обязательная категория товара
     */
    public ItemCategory getCategory() {
        return category;
    }

    /**
     * Возвращает базовую цену одной единицы товара.
     *
     * @return конечная строго положительная цена в кредитах
     */
    public float getBasePrice() {
        return basePrice;
    }

    /**
     * Проверяет, может ли товар непосредственно извлекаться добывающим кораблём.
     *
     * @return {@code true} только для первичного добываемого ресурса
     */
    public boolean isMineable() {
        return mineable;
    }

    /**
     * Безопасно находит описание товара по числовому идентификатору.
     *
     * <p>Метод не использует {@link #ordinal()} и не выбрасывает исключение для внешних либо
     * повреждённых данных. Это позволяет применять его в ECS-системах перед обращением к товарным
     * массивам.</p>
     *
     * @param itemId проверяемый числовой идентификатор
     * @return описание товара либо {@code null}, если идентификатор находится вне каталога
     */
    public static ItemType fromId(int itemId) {
        if (itemId < 0 || itemId >= BY_ID.length) {
            return null;
        }
        return BY_ID[itemId];
    }

    private static ItemType[] createIdIndex() {
        ItemType[] values = values();
        if (values.length != Constants.MAX_ITEMS) {
            throw new IllegalStateException(
                    "Число описаний товаров не совпадает с Constants.MAX_ITEMS");
        }

        ItemType[] result = new ItemType[Constants.MAX_ITEMS];
        for (ItemType item : values) {
            if (item.id < 0 || item.id >= result.length) {
                throw new IllegalStateException("Идентификатор товара вне диапазона: " + item.id);
            }
            if (result[item.id] != null) {
                throw new IllegalStateException("Повторяющийся идентификатор товара: " + item.id);
            }
            if (item.codeName == null || item.codeName.isBlank()
                    || item.displayName == null || item.displayName.isBlank()
                    || item.category == null
                    || !Float.isFinite(item.basePrice) || item.basePrice <= 0f) {
                throw new IllegalStateException("Некорректное описание товара: " + item.name());
            }
            if (item.mineable && item.category != ItemCategory.MATERIAL) {
                throw new IllegalStateException(
                        "Добываемый товар должен относиться к материалам: " + item.name());
            }
            result[item.id] = item;
        }
        return result;
    }
}

package com.spacesim.model;

import com.spacesim.constants.Constants;

/**
 * Legacy enum core-товаров, сохранённый как compatibility facade.
 *
 * <p>Authoritative metadata теперь находится в versioned content catalog. Этот enum описывает
 * только пять исторических core runtime ID и больше не обязан заполнять всю
 * {@link Constants#MAX_ITEMS slot-capacity}. Новый товар может существовать только в данных.</p>
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

    /** @return устойчивый legacy runtime ID товара */
    public int getId() {
        return id;
    }

    /** @return историческое английское кодовое имя */
    public String getCodeName() {
        return codeName;
    }

    /** @return русское отображаемое имя */
    public String getDisplayName() {
        return displayName;
    }

    /** @return категория грузового хранения */
    public ItemCategory getCategory() {
        return category;
    }

    /** @return legacy-базовая цена одной единицы */
    public float getBasePrice() {
        return basePrice;
    }

    /** @return признак непосредственной добываемости */
    public boolean isMineable() {
        return mineable;
    }

    /**
     * Безопасно находит legacy enum по runtime ID.
     *
     * @param itemId проверяемый runtime ID
     * @return enum либо {@code null}; data-only ID намеренно возвращает null
     */
    public static ItemType fromId(int itemId) {
        if (itemId < 0 || itemId >= BY_ID.length) {
            return null;
        }
        return BY_ID[itemId];
    }

    private static ItemType[] createIdIndex() {
        ItemType[] values = values();
        int highestId = -1;
        for (ItemType item : values) {
            highestId = Math.max(highestId, item.id);
        }
        ItemType[] result = new ItemType[highestId + 1];
        for (ItemType item : values) {
            if (item.id < 0 || item.id >= Constants.MAX_ITEMS) {
                throw new IllegalStateException("Идентификатор товара вне slot-capacity: " + item.id);
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

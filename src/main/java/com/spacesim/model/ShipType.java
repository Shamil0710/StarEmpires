package com.spacesim.model;

/**
 * Функциональный тип корабля и его политика работы с коммерческим грузом.
 *
 * <p>Тип задаёт только роль и совместимость грузового отсека. Индивидуальная вместимость,
 * скорость, состояние добычи и боевые характеристики принадлежат соответствующим ECS-компонентам.
 * Основная политика совместимости принимает категорию и признак добываемости напрямую, поэтому
 * data-driven товары не обязаны существовать как Java enum-константы. Методы с {@link ItemType}
 * остаются временным compatibility facade.</p>
 */
public enum ShipType {
    /** Контейнеровоз продовольствия, вооружения и другой готовой продукции. */
    FINISHED_GOODS_CARRIER("Перевозчик готовых товаров", ItemCategory.FINISHED_GOODS, true),

    /** Сухогруз для руды, стали и других твёрдых материалов. */
    MATERIAL_CARRIER("Перевозчик материалов", ItemCategory.MATERIAL, true),

    /** Танкер с герметичными резервуарами для газов, жидкостей и энергоносителей. */
    GAS_LIQUID_CARRIER("Перевозчик газа и жидкостей", ItemCategory.GAS_LIQUID, true),

    /** Корабль, способный добывать и хранить только добываемые первичные ресурсы. */
    MINING_SHIP("Добывающий корабль", null, false),

    /** Боевой корабль без коммерческого грузового назначения. */
    COMBAT_SHIP("Боевой корабль", null, false);

    private final String displayName;
    private final ItemCategory cargoCategory;
    private final boolean carrier;

    ShipType(String displayName, ItemCategory cargoCategory, boolean carrier) {
        this.displayName = displayName;
        this.cargoCategory = cargoCategory;
        this.carrier = carrier;
    }

    /**
     * Возвращает русское имя типа корабля.
     *
     * @return непустое имя для пользовательского интерфейса
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Проверяет физическую совместимость грузового отсека с data-driven описанием товара.
     *
     * @param category категория хранения товара; {@code null} всегда отклоняется
     * @param mineable можно ли добывать товар напрямую
     * @return {@code true}, если товар допустимо хранить в корабле этого типа
     */
    public boolean canCarry(ItemCategory category, boolean mineable) {
        if (category == null) {
            return false;
        }
        if (this == MINING_SHIP) {
            return mineable;
        }
        return carrier && category == cargoCategory;
    }

    /**
     * Проверяет, разрешено ли покупать data-driven товар для нового торгового маршрута.
     *
     * @param category категория хранения товара
     * @param mineable можно ли добывать товар напрямую
     * @return {@code true}, если товар совместим и тип является коммерческим перевозчиком
     */
    public boolean canPurchase(ItemCategory category, boolean mineable) {
        return carrier && canCarry(category, mineable);
    }

    /**
     * Compatibility-проверка физической совместимости с историческим {@link ItemType}.
     *
     * @param item проверяемый товар либо {@code null}
     * @return {@code true}, если товар допустимо хранить в корабле этого типа
     */
    public boolean canCarry(ItemType item) {
        return item != null && canCarry(item.getCategory(), item.isMineable());
    }

    /**
     * Compatibility-проверка покупки исторического {@link ItemType}.
     *
     * @param item проверяемый товар либо {@code null}
     * @return {@code true}, если товар совместим и тип является коммерческим перевозчиком
     */
    public boolean canPurchase(ItemType item) {
        return item != null && canPurchase(item.getCategory(), item.isMineable());
    }

    /**
     * Проверяет, относится ли тип к одному из коммерческих перевозчиков.
     *
     * @return {@code true} для трёх типов перевозчиков
     */
    public boolean isCarrier() {
        return carrier;
    }

    /**
     * Проверяет добывающую роль корабля.
     *
     * @return {@code true} только для {@link #MINING_SHIP}
     */
    public boolean isMining() {
        return this == MINING_SHIP;
    }

    /**
     * Проверяет боевую роль корабля.
     *
     * @return {@code true} только для {@link #COMBAT_SHIP}
     */
    public boolean isCombat() {
        return this == COMBAT_SHIP;
    }
}

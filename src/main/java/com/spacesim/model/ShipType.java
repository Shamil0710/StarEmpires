package com.spacesim.model;

/**
 * Функциональный тип корабля и его политика работы с коммерческим грузом.
 *
 * <p>Тип задаёт только роль и совместимость грузового отсека. Индивидуальная вместимость,
 * скорость, состояние добычи и боевые характеристики принадлежат соответствующим ECS-компонентам.
 * Раздельные методы {@link #canCarry(ItemType)} и {@link #canPurchase(ItemType)} позволяют
 * добывающему кораблю хранить извлечённую руду, не превращая его в обычного торговца.</p>
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
     * Проверяет физическую совместимость грузового отсека с товаром.
     *
     * <p>Три транспортных типа принимают всю свою категорию. Добывающий корабль принимает только
     * товары с признаком {@link ItemType#isMineable()}, а боевой корабль не принимает коммерческий
     * груз. Значение {@code null} всегда отклоняется.</p>
     *
     * @param item проверяемый товар либо {@code null}
     * @return {@code true}, если товар допустимо хранить в корабле этого типа
     */
    public boolean canCarry(ItemType item) {
        if (item == null) {
            return false;
        }
        if (this == MINING_SHIP) {
            return item.isMineable();
        }
        return carrier && item.getCategory() == cargoCategory;
    }

    /**
     * Проверяет, разрешено ли кораблю покупать товар для нового торгового маршрута.
     *
     * <p>Покупку выполняют только три транспортных типа. Добывающий корабль получает ресурс через
     * добычу, а боевой корабль не участвует в коммерческой перевозке.</p>
     *
     * @param item проверяемый товар либо {@code null}
     * @return {@code true}, если товар совместим и тип является коммерческим перевозчиком
     */
    public boolean canPurchase(ItemType item) {
        return carrier && canCarry(item);
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

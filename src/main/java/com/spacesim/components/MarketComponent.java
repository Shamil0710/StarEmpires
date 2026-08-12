package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Рыночная конфигурация, рассчитанные цены и состояние потребления станции.
 *
 * <p>Все массивы индексируются идентификатором товара и в штатном состоянии
 * имеют длину {@link Constants#MAX_ITEMS}. Код построения мира настраивает
 * торгуемые товары через {@link #configureTradableItem(int, int, float)},
 * {@link com.spacesim.systems.MarketSystem} рассчитывает цены,
 * {@link com.spacesim.systems.ConsumptionSystem} расходует запасы и ведёт
 * дробный остаток, а торговый контроллер читает цены и изменяет склад станции.
 * Компонент изменяется на игровом потоке и не является потокобезопасным.</p>
 *
 * <p>Цена выражена в кредитах за одну единицу товара, запасы — в целых
 * единицах, скорость потребления — в единицах товара в секунду игрового
 * времени.</p>
 */
public class MarketComponent implements Component {
    /**
     * Цена, по которой станция продаёт товар участнику, в кредитах за единицу.
     * Значения рассчитывает рыночная система; торговый контроллер и система
     * записи истории читают их. Для неторгуемого товара значение равно нулю.
     */
    public float[] sellPrices = new float[Constants.MAX_ITEMS];

    /**
     * Цена, по которой станция покупает товар у участника, в кредитах за
     * единицу. Значения рассчитывает рыночная система и читает торговый
     * контроллер; для неторгуемого товара значение равно нулю.
     */
    public float[] buyPrices = new float[Constants.MAX_ITEMS];

    /**
     * Целевой запас станции по каждому товару, в целых единицах.
     * Для торгуемого товара значение строго положительно и определяет дефицит,
     * используемый при расчёте цены и выборе маршрута торговым ИИ; ноль
     * обозначает отключённый рынок.
     */
    public int[] targetStock = new int[Constants.MAX_ITEMS];

    /**
     * Базовая непрерывная скорость потребления по товарам, в единицах в
     * секунду. Система потребления умножает её на эффекты событий; штатные
     * значения конечны и неотрицательны.
     */
    public float[] baseConsumption = new float[Constants.MAX_ITEMS];

    /**
     * Накопленная дробная часть потребления по каждому товару.
     * Полем владеет система потребления; при штатной работе каждый остаток
     * находится в диапазоне {@code [0, 1)} и превращается в списание после
     * накопления целой единицы.
     */
    public double[] consumptionRemainder = new double[Constants.MAX_ITEMS];

    /**
     * Флаги явного разрешения торговли по товарам.
     * Итоговая доступность дополнительно требует положительного значения в
     * {@link #targetStock}; проверять её следует через {@link #isTradable(int)}.
     */
    public boolean[] tradableItems = new boolean[Constants.MAX_ITEMS];

    /**
     * Признак необходимости пересчитать цены из текущих запасов.
     *
     * <p>Его устанавливают методы конфигурации, торговый контроллер, системы
     * потребления и производства после изменения склада. Рыночная система
     * сбрасывает флаг после пересчёта; изменение ревизии глобальных событий
     * также инициирует пересчёт независимо от значения поля.</p>
     */
    public boolean isDirty = true;

    /**
     * Создаёт рынок без разрешённых товаров и рассчитанных цен.
     * Все массивы заполнены нулями, а {@link #isDirty} установлен, чтобы первая
     * обработка рыночной системой сформировала актуальное состояние.
     */
    public MarketComponent() {
    }

    /**
     * Включает рынок одного товара и задаёт его экономические параметры.
     *
     * <p>Метод устанавливает флаг торговли, целевой запас, базовое потребление
     * и помечает цены устаревшими. Уже накопленный дробный остаток и текущие
     * цены не очищаются: они будут обработаны соответствующими системами.</p>
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @param desiredStock строго положительный целевой запас в целых единицах
     * @param consumptionPerSecond конечная неотрицательная скорость
     *                             потребления в единицах в секунду
     * @throws IllegalArgumentException если идентификатор выходит за диапазон,
     *                                  целевой запас неположителен либо скорость
     *                                  не является конечной и неотрицательной
     */
    public void configureTradableItem(int itemId, int desiredStock, float consumptionPerSecond) {
        validateItemId(itemId);
        if (desiredStock <= 0) {
            throw new IllegalArgumentException("Целевой запас торгуемого товара должен быть положительным");
        }
        if (!Float.isFinite(consumptionPerSecond) || consumptionPerSecond < 0f) {
            throw new IllegalArgumentException("Потребление должно быть конечным и неотрицательным");
        }

        tradableItems[itemId] = true;
        targetStock[itemId] = desiredStock;
        baseConsumption[itemId] = consumptionPerSecond;
        isDirty = true;
    }

    /**
     * Полностью отключает торговлю и потребление одного товара.
     *
     * <p>Целевой запас, скорость потребления, дробный остаток и обе цены
     * обнуляются, после чего компонент помечается для пересчёта остальных
     * рыночных данных.</p>
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @throws IllegalArgumentException если идентификатор выходит за диапазон
     */
    public void disableItemTrading(int itemId) {
        validateItemId(itemId);
        tradableItems[itemId] = false;
        targetStock[itemId] = 0;
        baseConsumption[itemId] = 0f;
        consumptionRemainder[itemId] = 0d;
        sellPrices[itemId] = 0f;
        buyPrices[itemId] = 0f;
        isDirty = true;
    }

    /**
     * Проверяет, разрешены ли сделки с товаром в текущей конфигурации.
     *
     * @param itemId проверяемый идентификатор товара
     * @return {@code true}, только если идентификатор допустим, соответствующий
     *         флаг {@link #tradableItems} установлен и целевой запас строго
     *         положителен; для недопустимого идентификатора возвращает
     *         {@code false}
     */
    public boolean isTradable(int itemId) {
        return itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && tradableItems[itemId]
                && targetStock[itemId] > 0;
    }

    private void validateItemId(int itemId) {
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Некорректный идентификатор товара: " + itemId);
        }
    }
}

package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Целочисленный склад ECS-сущности с общей ограниченной вместимостью.
 *
 * <p>{@link com.spacesim.controllers.TradeController} переносит товары между
 * складами, системы потребления и производства уменьшают и увеличивают запасы,
 * рыночная система читает их для расчёта цен, а торговый ИИ использует методы
 * свободной вместимости при планировании маршрута. Все количества измеряются
 * в целых единицах товара.</p>
 *
 * <p>Рабочий инвариант: каждый элемент {@link #stock} и {@link #capacity}
 * неотрицателен, а сумма запасов не превышает вместимость. Массив открыт ради
 * дешёвого доступа ECS-систем; код, меняющий его напрямую, отвечает за
 * сохранение этого инварианта и за установку
 * {@link MarketComponent#isDirty}, если изменение влияет на рынок.</p>
 */
public class InventoryComponent implements Component {
    /**
     * Запасы по идентификатору товара, в целых единицах.
     * Длина массива равна {@link Constants#MAX_ITEMS}; ссылка на массив
     * неизменна, его элементы изменяются торговыми и экономическими системами.
     */
    public final int[] stock = new int[Constants.MAX_ITEMS];

    /**
     * Общая вместимость склада по всем товарам, в целых единицах.
     * Значение по умолчанию — {@code 1000}; штатное значение неотрицательно.
     */
    public int capacity = 1000;

    /**
     * Создаёт пустой склад вместимостью {@code 1000} единиц.
     * Все товарные остатки инициализируются нулями.
     */
    public InventoryComponent() {
    }

    /**
     * Суммирует запасы всех типов товара.
     *
     * <p>Суммирование выполняется в {@code long}. Как только промежуточная сумма
     * достигает границы {@code int}, метод немедленно возвращает эту границу,
     * не продолжая обход. Для штатных неотрицательных запасов это эквивалентно
     * насыщению точной итоговой суммы; при повреждённых смешанных знаках результат
     * отражает первый переполненный префикс.</p>
     *
     * @return сумма элементов {@link #stock} либо первая достигнутая граница
     *         диапазона {@code int}
     */
    public int getTotalStock() {
        long total = 0L;
        for (int amount : stock) {
            total += amount;
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (total <= Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
        }
        return (int) total;
    }

    /**
     * Вычисляет доступную общую вместимость склада.
     *
     * @return {@code capacity - getTotalStock()} для корректного непереполненного
     *         склада; {@code 0}, если вместимость неположительна, сумма запасов
     *         отрицательна либо склад уже заполнен или переполнен
     */
    public int getFreeCapacity() {
        int totalStock = getTotalStock();
        if (capacity <= 0 || totalStock < 0 || totalStock >= capacity) {
            return 0;
        }
        return capacity - totalStock;
    }
}

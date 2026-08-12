package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.FloatArray;
import com.spacesim.constants.Constants;

/**
 * Ограниченная история рыночной цены продажи для построения графиков.
 *
 * <p>{@link com.spacesim.systems.PriceRecorderSystem} периодически добавляет
 * текущие цены и удаляет самые старые точки сверх лимита, а UI читает ряды для
 * визуализации. Каждый элемент хранит цену в кредитах за единицу товара;
 * порядок точек — от самой старой к самой новой.</p>
 */
public class PriceHistoryComponent implements Component {
    /**
     * Временные ряды, индексированные идентификатором товара.
     * Массив имеет длину {@link Constants#MAX_ITEMS}; конструктор заполняет
     * каждый элемент отдельным ненулевым {@link FloatArray}.
     */
    public FloatArray[] history = new FloatArray[Constants.MAX_ITEMS];

    /**
     * Желаемое максимальное число сохранённых точек на товар.
     * Значение читает и применяет система записи; штатная конфигурация должна
     * быть положительной.
     */
    public int maxPoints = 50;

    /**
     * Создаёт пустой ряд для каждого зарегистрированного типа товара.
     * Начальная ёмкость каждого ряда равна текущему {@link #maxPoints}.
     */
    public PriceHistoryComponent() {
        for(int i=0; i<Constants.MAX_ITEMS; i++) history[i] = new FloatArray(maxPoints);
    }
}

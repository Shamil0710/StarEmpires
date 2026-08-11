package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class MarketComponent implements Component {
    public float[] sellPrices = new float[Constants.MAX_ITEMS];
    public float[] buyPrices = new float[Constants.MAX_ITEMS];
    public int[] targetStock = new int[Constants.MAX_ITEMS]; // "Идеальное" кол-во
    public float[] baseConsumption = new float[Constants.MAX_ITEMS];
    public double[] consumptionRemainder = new double[Constants.MAX_ITEMS];
    public boolean[] tradableItems = new boolean[Constants.MAX_ITEMS];
    public boolean isDirty = true;

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

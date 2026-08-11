package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class MarketComponent implements Component {
    public float[] sellPrices = new float[Constants.MAX_ITEMS];
    public float[] buyPrices = new float[Constants.MAX_ITEMS];
    public int[] targetStock = new int[Constants.MAX_ITEMS]; // "Идеальное" кол-во
    public float[] baseConsumption = new float[Constants.MAX_ITEMS];
    public boolean isDirty = true;
}

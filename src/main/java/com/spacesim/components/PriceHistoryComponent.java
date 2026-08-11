package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.FloatArray;
import com.spacesim.constants.Constants;

public class PriceHistoryComponent implements Component {
    public FloatArray[] history = new FloatArray[Constants.MAX_ITEMS];
    public int maxPoints = 50;

    public PriceHistoryComponent() {
        for(int i=0; i<Constants.MAX_ITEMS; i++) history[i] = new FloatArray(maxPoints);
    }
}

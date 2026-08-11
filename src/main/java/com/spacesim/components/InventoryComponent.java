package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class InventoryComponent implements Component {
    public int[] stock = new int[Constants.MAX_ITEMS];
    public int capacity = 1000;
}
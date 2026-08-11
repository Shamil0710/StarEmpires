package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class InventoryComponent implements Component {
    public final int[] stock = new int[Constants.MAX_ITEMS];
    public int capacity = 1000;

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

    public int getFreeCapacity() {
        int totalStock = getTotalStock();
        if (capacity <= 0 || totalStock < 0 || totalStock >= capacity) {
            return 0;
        }
        return capacity - totalStock;
    }
}

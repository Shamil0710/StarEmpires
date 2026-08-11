package com.spacesim.model;

import com.spacesim.constants.Constants;

public class Recipe {
    public final String name;
    public final int[] inputItems = new int[Constants.MAX_ITEMS];
    public final int[] outputItems = new int[Constants.MAX_ITEMS];
    public final float durationSeconds;

    public Recipe(String name, float durationSeconds) {
        this.name = name;
        this.durationSeconds = durationSeconds;
    }

    public Recipe input(int itemId, int amount) {
        if (isValidItem(itemId) && amount > 0) {
            inputItems[itemId] = amount;
        }
        return this;
    }

    public Recipe output(int itemId, int amount) {
        if (isValidItem(itemId) && amount > 0) {
            outputItems[itemId] = amount;
        }
        return this;
    }

    private boolean isValidItem(int itemId) {
        return itemId >= 0 && itemId < Constants.MAX_ITEMS;
    }
}

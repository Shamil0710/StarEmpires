package com.spacesim.model;

import com.spacesim.constants.Constants;

public class Recipe {
    public final String name;
    public final float durationSeconds;
    private final int[] inputItems = new int[Constants.MAX_ITEMS];
    private final int[] outputItems = new int[Constants.MAX_ITEMS];

    public Recipe(String name, float durationSeconds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank");
        }
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) {
            throw new IllegalArgumentException("Recipe duration must be finite and positive");
        }

        this.name = name;
        this.durationSeconds = durationSeconds;
    }

    public Recipe input(int itemId, int amount) {
        validateItemId(itemId);
        validateAmount(amount);
        inputItems[itemId] = amount;
        return this;
    }

    public Recipe output(int itemId, int amount) {
        validateItemId(itemId);
        validateAmount(amount);
        outputItems[itemId] = amount;
        return this;
    }

    public int getInputAmount(int itemId) {
        validateItemId(itemId);
        return inputItems[itemId];
    }

    public int getOutputAmount(int itemId) {
        validateItemId(itemId);
        return outputItems[itemId];
    }

    private void validateItemId(int itemId) {
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Item amount must be positive");
        }
    }
}

package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.model.Recipe;
import java.util.ArrayList;
import java.util.List;

public class ProductionComponent implements Component {
    public final List<Recipe> recipes = new ArrayList<>();
    public int activeRecipeIndex = 0;
    public float progressSeconds = 0f;

    public Recipe getActiveRecipe() {
        if (activeRecipeIndex < 0 || activeRecipeIndex >= recipes.size()) {
            return null;
        }
        return recipes.get(activeRecipeIndex);
    }
}

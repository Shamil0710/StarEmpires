package com.spacesim.world;

import com.spacesim.content.ContentCatalog;

import java.util.Map;
import java.util.Objects;

final class ConstructionBidPolicy {
    private static final double PROCUREMENT_BUDGET_FRACTION = 0.80d;

    private ConstructionBidPolicy() {
        throw new AssertionError("Utility class");
    }

    static float buyPriceMultiplier(
            ContentCatalog catalog,
            ContentCatalog.StationArchetypeDefinition station) {
        ContentCatalog checked = Objects.requireNonNull(catalog, "ContentCatalog не задан");
        ContentCatalog.StationArchetypeDefinition target = Objects.requireNonNull(station, "Station не задана");
        ContentCatalog.ConstructionDefinition construction = target.construction();
        if (construction == null) {
            throw new IllegalArgumentException("Station is not constructible: " + target.id());
        }
        double baseMaterialCost = 0d;
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checked.findItem(requirement.getKey());
            if (item == null) {
                throw new IllegalStateException("Unknown construction item: " + requirement.getKey());
            }
            baseMaterialCost += item.basePrice() * requirement.getValue();
        }
        double multiplier = construction.fundingCredits() * PROCUREMENT_BUDGET_FRACTION / baseMaterialCost;
        if (!Double.isFinite(multiplier) || multiplier < 1d || multiplier > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Construction funding cannot cover bounded procurement: " + target.id());
        }
        return (float) multiplier;
    }
}

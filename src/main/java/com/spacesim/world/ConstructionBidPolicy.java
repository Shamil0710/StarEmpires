package com.spacesim.world;

import com.spacesim.components.ProcurementPolicyComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;

import java.util.Map;
import java.util.Objects;

final class ConstructionBidPolicy {
    private static final double PROCUREMENT_BUDGET_FRACTION = 0.80d;

    private ConstructionBidPolicy() {
        throw new AssertionError("Utility class");
    }

    static ProcurementPolicyComponent create(
            ContentCatalog catalog,
            ContentCatalog.StationArchetypeDefinition station) {
        ContentCatalog checked = Objects.requireNonNull(catalog, "ContentCatalog не задан");
        ContentCatalog.StationArchetypeDefinition target = Objects.requireNonNull(station, "Station не задана");
        ContentCatalog.ConstructionDefinition construction = target.construction();
        if (construction == null) {
            throw new IllegalArgumentException("Station is not constructible: " + target.id());
        }
        if (PROCUREMENT_BUDGET_FRACTION * (1d + Constants.MAX_REPUTATION_PRICE_BONUS) > 1d) {
            throw new IllegalStateException("Procurement reserve does not cover maximum reputation markup");
        }

        double baseMaterialCost = 0d;
        long requiredUnits = 0L;
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checked.findItem(requirement.getKey());
            if (item == null) {
                throw new IllegalStateException("Unknown construction item: " + requirement.getKey());
            }
            baseMaterialCost += item.basePrice() * requirement.getValue();
            requiredUnits = Math.addExact(requiredUnits, requirement.getValue());
        }
        double procurementBudget = construction.fundingCredits() * PROCUREMENT_BUDGET_FRACTION;
        double premiumPool = procurementBudget - baseMaterialCost;
        if (!Double.isFinite(premiumPool) || premiumPool < 0d || requiredUnits <= 0L) {
            throw new IllegalArgumentException("Construction funding cannot cover bounded procurement: " + target.id());
        }
        double premiumPerUnit = premiumPool / requiredUnits;

        ProcurementPolicyComponent policy = new ProcurementPolicyComponent();
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checked.findItem(requirement.getKey());
            double bid = item.basePrice() + premiumPerUnit;
            if (!Double.isFinite(bid) || bid <= 0d || bid > Float.MAX_VALUE) {
                throw new IllegalArgumentException("Construction procurement bid is not representable: " + target.id());
            }
            policy.configureBuyPrice(item.runtimeId(), (float) bid);
        }
        return policy;
    }
}

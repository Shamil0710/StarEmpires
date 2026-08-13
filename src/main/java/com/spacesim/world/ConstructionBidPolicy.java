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
        int materialLines = construction.materials().size();
        if (materialLines <= 0) {
            throw new IllegalArgumentException("Construction project has no physical materials: " + target.id());
        }

        double lineBudget = construction.fundingCredits() * PROCUREMENT_BUDGET_FRACTION / materialLines;
        ProcurementPolicyComponent policy = new ProcurementPolicyComponent();
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checked.findItem(requirement.getKey());
            if (item == null) {
                throw new IllegalStateException("Unknown construction item: " + requirement.getKey());
            }
            int amount = requirement.getValue();
            double bid = lineBudget / amount;
            if (!Double.isFinite(bid) || bid < item.basePrice() || bid > Float.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Construction funding cannot cover bounded material line: " + target.id() + " -> " + item.id());
            }
            policy.configureBuyPrice(item.runtimeId(), (float) bid);
        }
        return policy;
    }
}

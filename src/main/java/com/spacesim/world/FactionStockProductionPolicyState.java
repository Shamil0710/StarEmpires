package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Common player/AI authoring contract for strategic stock floors and production recipe preferences.
 *
 * <p>The value contains policy only. Installing it does not create demand, cargo, production output,
 * money or assets. Physical consequences occur only when the ordinary strategic policy engine is
 * explicitly applied and then flow through existing market, logistics and production systems.</p>
 *
 * @param stockPolicies canonical per-item minimum stock targets
 * @param productionPolicies canonical per-station-archetype recipe preferences
 */
public record FactionStockProductionPolicyState(
        List<FactionStockPolicyState> stockPolicies,
        List<FactionProductionPolicyState> productionPolicies) {

    /**
     * Canonicalizes and validates an immutable stock/production policy value.
     *
     * @param stockPolicies per-item minimum stock targets
     * @param productionPolicies per-station-archetype recipe preferences
     */
    public FactionStockProductionPolicyState {
        List<FactionStockPolicyState> stocks = new ArrayList<>(
                Objects.requireNonNull(stockPolicies, "Stock policies not set"));
        List<FactionProductionPolicyState> production = new ArrayList<>(
                Objects.requireNonNull(productionPolicies, "Production policies not set"));
        stocks.sort(null);
        production.sort(null);

        Set<String> stockItems = new HashSet<>();
        for (FactionStockPolicyState policy : stocks) {
            FactionStockPolicyState checked = Objects.requireNonNull(policy, "Stock policy not set");
            if (!stockItems.add(checked.itemContentId())) {
                throw new IllegalArgumentException("Duplicate stock policy item: " + checked.itemContentId());
            }
        }
        Set<String> productionArchetypes = new HashSet<>();
        for (FactionProductionPolicyState policy : production) {
            FactionProductionPolicyState checked = Objects.requireNonNull(policy, "Production policy not set");
            if (!productionArchetypes.add(checked.stationArchetypeContentId())) {
                throw new IllegalArgumentException(
                        "Duplicate production policy archetype: " + checked.stationArchetypeContentId());
            }
        }
        stockPolicies = List.copyOf(stocks);
        productionPolicies = List.copyOf(production);
    }

    /** @return policy with no explicit stock or production overrides */
    public static FactionStockProductionPolicyState empty() {
        return new FactionStockProductionPolicyState(List.of(), List.of());
    }
}

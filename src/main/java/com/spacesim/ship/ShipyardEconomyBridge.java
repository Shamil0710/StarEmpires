package com.spacesim.ship;

import com.spacesim.components.InventoryComponent;
import com.spacesim.ship.ShipyardEngineeringService.IndustrialInputRequirement;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Thin Stage-17.5G bridge from shipyard work requirements to the existing physical inventory.
 *
 * <p>The bridge deliberately does not create a second warehouse or commodity registry. Stage 18
 * supplies authoritative content-ID to runtime-item bindings after its commodity/component ontology
 * is implemented. Until then this adapter proves that shipyard completion can consume ordinary
 * {@link InventoryComponent} stock atomically. Money/pricing remains on the existing wallet/trade/
 * ledger path and is not reimplemented here.</p>
 *
 * <p>The current shared inventory stores integer item units. Fractional engineering requirements are
 * therefore conservatively rounded up at this compatibility boundary; Stage 18 owns final commodity
 * unit granularity.</p>
 */
public final class ShipyardEconomyBridge {
    private ShipyardEconomyBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * Stage-18-resolvable mapping from engineering input IDs to existing inventory runtime IDs.
     *
     * @param runtimeItemIdByContentId content ID to ordinary inventory index
     */
    public record PhysicalInputBinding(Map<String, Integer> runtimeItemIdByContentId) {
        /**
         * Validates and freezes one binding map.
         *
         * @param runtimeItemIdByContentId content ID to inventory index
         */
        public PhysicalInputBinding {
            Objects.requireNonNull(runtimeItemIdByContentId, "runtimeItemIdByContentId");
            TreeMap<String, Integer> copy = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : runtimeItemIdByContentId.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("physical input content ID must be non-blank");
                }
                Integer runtimeId = Objects.requireNonNull(entry.getValue(), "runtime item ID");
                if (runtimeId < 0) {
                    throw new IllegalArgumentException("runtime item ID must be non-negative");
                }
                copy.put(entry.getKey(), runtimeId);
            }
            runtimeItemIdByContentId = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Atomically consumes all physical inputs required by one feasible work plan.
     *
     * <p>All mappings and stock sufficiency are checked before any inventory mutation. The returned
     * settlement can then be combined with independently accumulated engineering work and passed to
     * {@link ShipyardEngineeringService} completion.</p>
     *
     * @param plan feasible shipyard work plan
     * @param inventory ordinary authoritative inventory holding delivered yard inputs
     * @param binding Stage-18-facing content-to-runtime binding
     * @param completedWorkSeconds engineering work already completed
     * @return settlement proving consumed inputs and reported completed work
     */
    public static WorkSettlement consumeRequiredInputs(
            WorkPlan plan,
            InventoryComponent inventory,
            PhysicalInputBinding binding,
            double completedWorkSeconds) {
        WorkPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        InventoryComponent checkedInventory = Objects.requireNonNull(inventory, "inventory");
        PhysicalInputBinding checkedBinding = Objects.requireNonNull(binding, "binding");
        if (!checkedPlan.feasibility().feasible()) {
            throw new IllegalStateException("Cannot consume inputs for infeasible shipyard work");
        }
        if (!Double.isFinite(completedWorkSeconds) || completedWorkSeconds < 0d) {
            throw new IllegalArgumentException("completedWorkSeconds must be finite and non-negative");
        }

        TreeMap<Integer, Integer> requiredByRuntimeId = new TreeMap<>();
        TreeMap<String, Double> deliveredByContentId = new TreeMap<>();
        for (IndustrialInputRequirement required : checkedPlan.requirements().inputs()) {
            Integer runtimeId = checkedBinding.runtimeItemIdByContentId().get(required.contentId());
            if (runtimeId == null) {
                throw new IllegalStateException("No physical inventory binding for: " + required.contentId());
            }
            if (runtimeId >= checkedInventory.stock.length) {
                throw new IllegalStateException("Inventory runtime ID out of range: " + runtimeId);
            }
            long rounded = (long) Math.ceil(required.amount());
            if (rounded <= 0L || rounded > Integer.MAX_VALUE) {
                throw new IllegalStateException("Physical shipyard input amount is not representable: "
                        + required.contentId());
            }
            int units = (int) rounded;
            requiredByRuntimeId.merge(runtimeId, units, ShipyardEconomyBridge::addExact);
            deliveredByContentId.put(required.contentId(), (double) units);
        }

        for (Map.Entry<Integer, Integer> entry : requiredByRuntimeId.entrySet()) {
            int available = checkedInventory.stock[entry.getKey()];
            if (available < entry.getValue()) {
                throw new IllegalStateException("Insufficient physical shipyard stock at runtime item "
                        + entry.getKey() + ": required=" + entry.getValue() + ",available=" + available);
            }
        }
        for (Map.Entry<Integer, Integer> entry : requiredByRuntimeId.entrySet()) {
            checkedInventory.stock[entry.getKey()] -= entry.getValue();
        }
        return new WorkSettlement(deliveredByContentId, completedWorkSeconds);
    }

    private static int addExact(int left, int right) {
        return Math.addExact(left, right);
    }
}

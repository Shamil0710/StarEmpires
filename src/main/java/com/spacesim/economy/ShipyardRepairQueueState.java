package com.spacesim.economy;

import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import java.util.*;

/** Exact prepaid-material repair progress on the campaign's completed-tick clock. */
public record ShipyardRepairQueueState(long lastProcessedTick, List<RepairOrder> orders) {
    /** Maximum simultaneous physical repair jobs. */
    public static final int CAPACITY = 128;
    /**
     * Validates unique job/fleet identity and bounded original timing.
     * @param lastProcessedTick last completed interval processed by this queue
     * @param orders exact pending jobs; completed jobs are removed
     */
    public ShipyardRepairQueueState {
        if (lastProcessedTick < 0 || orders == null || orders.size() > CAPACITY)
            throw new IllegalArgumentException("Invalid repair queue bounds");
        var sorted = new TreeMap<String, RepairOrder>(); var fleets = new HashSet<Long>();
        for (var order : orders) {
            Objects.requireNonNull(order, "order");
            if (order.startedAtTick() > lastProcessedTick || sorted.putIfAbsent(order.orderId(), order) != null
                    || !fleets.add(order.fleetId())) throw new IllegalArgumentException("Invalid repair job identity or time");
        }
        orders = List.copyOf(sorted.values());
    }
    /** @return no jobs and no historical work credit */
    public static ShipyardRepairQueueState empty() { return new ShipyardRepairQueueState(0, List.of()); }

    /**
     * One physical repair job; source fitting/condition are evidence, not replacement live authority.
     * @param orderId stable physical job identity
     * @param fleetId original personal fleet
     * @param assetId original persistent ship entity
     * @param stationId canonical station custody owner
     * @param yardInstanceId actual installed yard instance
     * @param yardDefinitionId authored yard design
     * @param startedAtTick actual reservation tick
     * @param sourceFit original physical fitting
     * @param sourceDamage exact condition requiring this work
     * @param requiredWorkSeconds authored total engineering work
     * @param completedWorkSeconds actual completed finite engineering work
     * @param reservedCommodityMassByIdKg exact reserved physical recipe
     * @param servicePayment actual money held for a foreign operator, or null for owner work
     */
    public record RepairOrder(String orderId, long fleetId, long assetId, String stationId,
            String yardInstanceId, String yardDefinitionId, long startedAtTick, InstalledFit sourceFit,
            Snapshot sourceDamage, double requiredWorkSeconds, double completedWorkSeconds,
            Map<String, Double> reservedCommodityMassByIdKg, ShipyardRepairServicePayment servicePayment) {
        /**
         * Preserves historical owner-funded material work without issuing a paid contract.
         * @param orderId job identity
         * @param fleetId original fleet
         * @param assetId original asset
         * @param stationId station custody
         * @param yardInstanceId actual yard
         * @param yardDefinitionId authored yard
         * @param startedAtTick reservation tick
         * @param sourceFit original fitting
         * @param sourceDamage original damage
         * @param requiredWorkSeconds required work
         * @param completedWorkSeconds completed work
         * @param reservedCommodityMassByIdKg actual materials
         */
        public RepairOrder(String orderId, long fleetId, long assetId, String stationId,
                String yardInstanceId, String yardDefinitionId, long startedAtTick, InstalledFit sourceFit,
                Snapshot sourceDamage, double requiredWorkSeconds, double completedWorkSeconds,
                Map<String, Double> reservedCommodityMassByIdKg) {
            this(orderId, fleetId, assetId, stationId, yardInstanceId, yardDefinitionId, startedAtTick,
                    sourceFit, sourceDamage, requiredWorkSeconds, completedWorkSeconds, reservedCommodityMassByIdKg, null);
        }
        /**
         * Validates an incomplete job with finite physical custody.
         * @param orderId job identity
         * @param fleetId original fleet
         * @param assetId persistent ship entity
         * @param stationId storage owner
         * @param yardInstanceId installed yard
         * @param yardDefinitionId authored design
         * @param startedAtTick reservation tick
         * @param sourceFit original fitting
         * @param sourceDamage original condition
         * @param requiredWorkSeconds total real work
         * @param completedWorkSeconds completed work
         * @param reservedCommodityMassByIdKg reserved recipe
         * @param servicePayment held money for a foreign operator, or null for owner work
         */
        public RepairOrder {
            text(orderId); text(stationId); text(yardInstanceId); text(yardDefinitionId);
            if (fleetId <= 0 || assetId <= 0 || startedAtTick < 0 || !Double.isFinite(requiredWorkSeconds)
                    || requiredWorkSeconds <= 0 || !Double.isFinite(completedWorkSeconds)
                    || completedWorkSeconds < 0 || completedWorkSeconds >= requiredWorkSeconds)
                throw new IllegalArgumentException("Invalid physical repair progress");
            Objects.requireNonNull(sourceFit); Objects.requireNonNull(sourceDamage);
            text(sourceFit.hullId());
            if (sourceFit.installedModules().size() > 4096 || sourceDamage.compartmentIntegrityById().size() > 4096
                    || sourceDamage.moduleDamage().moduleIntegrityByMount().size() > 4096)
                throw new IllegalArgumentException("Repair evidence exceeds bounds");
            sourceFit.installedModules().forEach(m -> { text(m.mountId()); text(m.moduleId()); });
            sourceDamage.compartmentIntegrityById().keySet().forEach(ShipyardRepairQueueState::text);
            sourceDamage.moduleDamage().moduleIntegrityByMount().keySet().forEach(ShipyardRepairQueueState::text);
            if (reservedCommodityMassByIdKg == null || reservedCommodityMassByIdKg.isEmpty()
                    || reservedCommodityMassByIdKg.size() > 4096) throw new IllegalArgumentException("Missing repair custody");
            var masses = new TreeMap<String, Double>();
            reservedCommodityMassByIdKg.forEach((id, mass) -> {
                text(id);
                if (mass == null || !Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("Invalid repair material mass");
                masses.put(id, mass);
            });
            reservedCommodityMassByIdKg = Collections.unmodifiableMap(masses);
            if (servicePayment != null && servicePayment.reservedMilliCredits()
                    != ShipyardRepairServicePayment.quote(requiredWorkSeconds, reservedCommodityMassByIdKg))
                throw new IllegalArgumentException("Paid repair reservation does not match its physical tariff");
        }
        /**
         * Retains evidence and custody while advancing actual work.
         * @param work exact accumulated incomplete work
         * @return updated pending job
         */
        public RepairOrder withWork(double work) {
            return new RepairOrder(orderId, fleetId, assetId, stationId, yardInstanceId, yardDefinitionId,
                    startedAtTick, sourceFit, sourceDamage, requiredWorkSeconds, work, reservedCommodityMassByIdKg, servicePayment);
        }
    }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("Invalid repair identity");
    }
}

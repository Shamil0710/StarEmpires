package com.spacesim.economy;

import java.util.*;

/** Durable paid hull/module custody, including completed work awaiting physical publication. */
public record ShipyardSmallCraftBuildQueueState(long lastProcessedTick, List<Order> orders) {
    /** Maximum retained jobs; every installed yard has at most one occupied build slot. */
    public static final int CAPACITY = 128;
    /**
     * Validates bounded identities and elapsed-time evidence.
     * @param lastProcessedTick last actual completed interval
     * @param orders retained build custody
     */
    public ShipyardSmallCraftBuildQueueState {
        if (lastProcessedTick < 0 || orders == null || orders.size() > CAPACITY)
            throw new IllegalArgumentException("Invalid build queue bounds");
        var sorted = new TreeMap<String, Order>(); var yards = new HashSet<List<String>>();
        for (var o : orders) if (o == null || o.startedAtTick() > lastProcessedTick
                || sorted.putIfAbsent(o.orderId(), o) != null
                || !yards.add(List.of(o.stationId(), o.yardInstanceId())))
            throw new IllegalArgumentException("Duplicate build identity/yard or future start");
        orders = List.copyOf(sorted.values());
    }
    /** @return empty non-granting historical state */
    public static ShipyardSmallCraftBuildQueueState empty() { return new ShipyardSmallCraftBuildQueueState(0, List.of()); }

    /**
     * Exact immutable bill and performed work; no craft identity exists yet.
     * @param orderId unique order identity
     * @param ownerActorId personal property owner
     * @param builderFactionId actual legal operator
     * @param stationId canonical custody owner
     * @param yardInstanceId actual installed yard
     * @param yardDefinitionId authored yard definition
     * @param designId exact authored craft design
     * @param startedAtTick start interval
     * @param requiredWorkSeconds authored engineering work
     * @param completedWorkSeconds actual paid engineering work
     * @param reservedCommodityMassKg bare-hull materials
     * @param reservedProductCounts finished modules, one per installed mount
     */
    public record Order(String orderId, String ownerActorId, String builderFactionId, String stationId,
            String yardInstanceId, String yardDefinitionId, String designId, long startedAtTick,
            double requiredWorkSeconds, double completedWorkSeconds,
            Map<String, Double> reservedCommodityMassKg, Map<String, Integer> reservedProductCounts) {
        /**
         * Validates structural custody; the queue also recomputes its authored bill and work.
         * @param orderId unique order identity
         * @param ownerActorId personal property owner
         * @param builderFactionId actual legal operator
         * @param stationId canonical custody owner
         * @param yardInstanceId installed yard identity
         * @param yardDefinitionId authored yard definition
         * @param designId exact authored fit
         * @param startedAtTick actual start tick
         * @param requiredWorkSeconds authored work
         * @param completedWorkSeconds actual paid work
         * @param reservedCommodityMassKg bare-hull materials
         * @param reservedProductCounts finished installed modules
         */
        public Order {
            for (var s : List.of(orderId, ownerActorId, builderFactionId, stationId, yardInstanceId, yardDefinitionId, designId))
                if (s.isBlank() || s.length() > 512) throw new IllegalArgumentException("Invalid build identity");
            if (startedAtTick < 0 || !Double.isFinite(requiredWorkSeconds) || requiredWorkSeconds <= 0
                    || !Double.isFinite(completedWorkSeconds) || completedWorkSeconds < 0 || completedWorkSeconds > requiredWorkSeconds)
                throw new IllegalArgumentException("Invalid build work");
            var raw = new TreeMap<String, Double>();
            Objects.requireNonNull(reservedCommodityMassKg).forEach((id, kg) -> {
                if (id == null || id.isBlank() || kg == null || !Double.isFinite(kg) || kg <= 0)
                    throw new IllegalArgumentException("Invalid build material");
                raw.put(id, kg);
            });
            var stock = new TreeMap<String, Integer>();
            Objects.requireNonNull(reservedProductCounts).forEach((id, count) -> {
                if (id == null || id.isBlank() || count == null || count <= 0) throw new IllegalArgumentException("Invalid build equipment");
                stock.put(id, count);
            });
            if (raw.isEmpty() || stock.isEmpty() || raw.size() > 128 || stock.size() > 128)
                throw new IllegalArgumentException("Build custody outside bounds");
            reservedCommodityMassKg = Collections.unmodifiableMap(raw); reservedProductCounts = Collections.unmodifiableMap(stock);
        }
        /** @return whether all finite engineering work has actually been paid */
        public boolean ready() { return completedWorkSeconds == requiredWorkSeconds; }
        /**
         * Retains a newly paid amount without changing custody or identity.
         * @param work cumulative performed work
         * @return new immutable order
         */
        public Order withWork(double work) { return new Order(orderId, ownerActorId, builderFactionId, stationId,
                yardInstanceId, yardDefinitionId, designId, startedAtTick, requiredWorkSeconds, work,
                reservedCommodityMassKg, reservedProductCounts); }
    }
}

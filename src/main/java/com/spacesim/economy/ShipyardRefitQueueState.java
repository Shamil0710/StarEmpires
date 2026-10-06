package com.spacesim.economy;

import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import java.util.*;

/** Exact pending physical refits; campaign integration must retain this alongside all custody owners. */
public record ShipyardRefitQueueState(long lastProcessedTick, List<Order> orders) {
    /** Maximum concurrent jobs. */
    public static final int CAPACITY = 128;
    /**
     * Validates bounded unique jobs and original timing.
     * @param lastProcessedTick last actual interval processed
     * @param orders pending incomplete jobs
     */
    public ShipyardRefitQueueState {
        if (lastProcessedTick < 0 || orders == null || orders.size() > CAPACITY) throw new IllegalArgumentException("Invalid refit queue bounds");
        var sorted = new TreeMap<String, Order>(); var fleets = new HashSet<Long>();
        var reservedModules = new HashSet<String>();
        for (var o : orders) if (o == null || o.startedAtTick() > lastProcessedTick
                || sorted.putIfAbsent(o.orderId(), o) != null || !fleets.add(o.fleetId())) throw new IllegalArgumentException("Invalid refit identity/time");
        orders = List.copyOf(sorted.values());
        for (var order : orders) for (var module : order.reservedUsedModulesByTargetMount().values())
            if (!reservedModules.add(module.custodyId())) throw new IllegalArgumentException("Used module reserved twice");
    }
    /** @return no equipment or historical work grants */
    public static ShipyardRefitQueueState empty() { return new ShipyardRefitQueueState(0, List.of()); }
    /**
     * One prepaid-equipment refit; original fitting/condition is evidence, not live authority.
     * @param orderId unique job identity
     * @param fleetId stable fleet identity
     * @param assetId original ship entity
     * @param stationId canonical custody owner
     * @param yardInstanceId installed physical yard
     * @param yardDefinitionId authored yard design
     * @param startedAtTick actual start tick
     * @param sourceFit original fitted hardware
     * @param targetFit intended same-hull fitting
     * @param sourceDamage original damage
     * @param requiredWorkSeconds total authored engineering work
     * @param completedWorkSeconds performed finite work
     * @param reservedProductCounts pristine incoming physical modules
     * @param reservedUsedModulesByTargetMount exact existing used equipment reserved for changed target mounts
     * @param servicePayment held foreign service money, or null for owner work
     */
    public record Order(String orderId, long fleetId, long assetId, String stationId, String yardInstanceId,
            String yardDefinitionId, long startedAtTick, InstalledFit sourceFit, InstalledFit targetFit,
            Snapshot sourceDamage, double requiredWorkSeconds, double completedWorkSeconds, Map<String, Integer> reservedProductCounts,
            Map<String, ShipyardModuleCustodyState.StoredModule> reservedUsedModulesByTargetMount,
            ShipyardRefitServicePayment servicePayment) {
        /**
         * Retains historical work without creating a financial obligation.
         * @param orderId job identity
         * @param fleetId fleet
         * @param assetId actual ship
         * @param stationId custody station
         * @param yardInstanceId installed yard
         * @param yardDefinitionId yard definition
         * @param startedAtTick start tick
         * @param sourceFit original fit
         * @param targetFit target fit
         * @param sourceDamage original condition
         * @param requiredWorkSeconds required work
         * @param completedWorkSeconds actual work
         * @param reservedProductCounts fresh equipment
         * @param reservedUsedModulesByTargetMount existing individual equipment
         */
        public Order(String orderId, long fleetId, long assetId, String stationId, String yardInstanceId,
                String yardDefinitionId, long startedAtTick, InstalledFit sourceFit, InstalledFit targetFit,
                Snapshot sourceDamage, double requiredWorkSeconds, double completedWorkSeconds, Map<String, Integer> reservedProductCounts,
                Map<String, ShipyardModuleCustodyState.StoredModule> reservedUsedModulesByTargetMount) {
            this(orderId, fleetId, assetId, stationId, yardInstanceId, yardDefinitionId, startedAtTick, sourceFit,
                    targetFit, sourceDamage, requiredWorkSeconds, completedWorkSeconds, reservedProductCounts,
                    reservedUsedModulesByTargetMount, null);
        }
        /**
         * Source-compatible pristine-equipment job; grants no used modules.
         * @param orderId job identity
         * @param fleetId fleet identity
         * @param assetId ship identity
         * @param stationId custody station
         * @param yardInstanceId installed yard
         * @param yardDefinitionId yard design
         * @param startedAtTick original tick
         * @param sourceFit original fitting
         * @param targetFit intended fitting
         * @param sourceDamage original condition
         * @param requiredWorkSeconds total work
         * @param completedWorkSeconds performed work
         * @param reservedProductCounts pristine equipment escrow
         */
        public Order(String orderId, long fleetId, long assetId, String stationId, String yardInstanceId,
                String yardDefinitionId, long startedAtTick, InstalledFit sourceFit, InstalledFit targetFit,
                Snapshot sourceDamage, double requiredWorkSeconds, double completedWorkSeconds, Map<String, Integer> reservedProductCounts) {
            this(orderId, fleetId, assetId, stationId, yardInstanceId, yardDefinitionId, startedAtTick, sourceFit,
                    targetFit, sourceDamage, requiredWorkSeconds, completedWorkSeconds, reservedProductCounts, Map.of());
        }
        /**
         * Validates bounded same-hull evidence and incomplete progress.
         * @param orderId job identity
         * @param fleetId fleet identity
         * @param assetId original ship
         * @param stationId actual storage
         * @param yardInstanceId installed yard
         * @param yardDefinitionId authored design
         * @param startedAtTick start tick
         * @param sourceFit original fitting
         * @param targetFit intended fitting
         * @param sourceDamage original damage
         * @param requiredWorkSeconds total work
         * @param completedWorkSeconds actual progress
         * @param reservedProductCounts physical equipment escrow
         * @param reservedUsedModulesByTargetMount reserved individual equipment, still occupying its original custody
         * @param servicePayment actual money held for the operator, or null for owner work
         */
        public Order {
            text(orderId); text(stationId); text(yardInstanceId); text(yardDefinitionId);
            if (fleetId <= 0 || assetId <= 0 || startedAtTick < 0 || !Double.isFinite(requiredWorkSeconds) || requiredWorkSeconds <= 0
                    || !Double.isFinite(completedWorkSeconds) || completedWorkSeconds < 0 || completedWorkSeconds >= requiredWorkSeconds)
                throw new IllegalArgumentException("Invalid refit progress");
            Objects.requireNonNull(sourceDamage); fit(sourceFit); fit(targetFit);
            if (!sourceFit.hullId().equals(targetFit.hullId()) || sourceFit.equals(targetFit)) throw new IllegalArgumentException("Refit requires changed same-hull hardware");
            var targetModules = new HashMap<String, String>(); targetFit.installedModules().forEach(m -> targetModules.put(m.mountId(), m.moduleId()));
            for (var module : sourceFit.installedModules())
                if (!module.moduleId().equals(targetModules.get(module.mountId())) && orderId.length() + 1 + module.mountId().length() > 512)
                    throw new IllegalArgumentException("Refit removal identity exceeds custody bounds");
            if (sourceDamage.compartmentIntegrityById().size() > 4096 || sourceDamage.moduleDamage().moduleIntegrityByMount().size() > 4096)
                throw new IllegalArgumentException("Refit damage exceeds bounds");
            sourceDamage.compartmentIntegrityById().keySet().forEach(ShipyardRefitQueueState::text);
            sourceDamage.moduleDamage().moduleIntegrityByMount().keySet().forEach(ShipyardRefitQueueState::text);
            if (reservedProductCounts == null || reservedProductCounts.size() > 4096) throw new IllegalArgumentException("Invalid refit escrow");
            var counts = new TreeMap<String, Integer>();
            reservedProductCounts.forEach((id, count) -> { text(id); if (count == null || count <= 0 || count > 4096) throw new IllegalArgumentException("Invalid refit count"); counts.put(id, count); });
            reservedProductCounts = Collections.unmodifiableMap(counts);
            if (reservedUsedModulesByTargetMount == null || reservedUsedModulesByTargetMount.size() > 4096)
                throw new IllegalArgumentException("Invalid individual refit reservation");
            var used = new TreeMap<String, ShipyardModuleCustodyState.StoredModule>(); var ids = new HashSet<String>();
            var sourceModules = new HashMap<String, String>(); sourceFit.installedModules().forEach(m -> sourceModules.put(m.mountId(), m.moduleId()));
            reservedUsedModulesByTargetMount.forEach((mount, module) -> {
                text(mount); Objects.requireNonNull(module);
                if (!stationId.equals(module.stationId()) || module.removedAtTick() > startedAtTick
                        || !module.condition().assignment().moduleId().equals(targetModules.get(mount))
                        || module.condition().assignment().moduleId().equals(sourceModules.get(mount)) || !ids.add(module.custodyId()))
                    throw new IllegalArgumentException("Used module differs from changed target mount or actual station/time");
                used.put(mount, module);
            });
            reservedUsedModulesByTargetMount = Collections.unmodifiableMap(used);
            if (servicePayment != null && servicePayment.reservedMilliCredits()
                    != ShipyardRefitServicePayment.quote(requiredWorkSeconds, reservedProductCounts))
                throw new IllegalArgumentException("Refit payment differs from physical work and fresh equipment tariff");
        }
        /**
         * Retains custody/evidence while advancing actual work.
         * @param work new incomplete accumulated work
         * @return updated job
         */
        public Order withWork(double work) { return new Order(orderId, fleetId, assetId, stationId, yardInstanceId, yardDefinitionId,
                startedAtTick, sourceFit, targetFit, sourceDamage, requiredWorkSeconds, work, reservedProductCounts, reservedUsedModulesByTargetMount, servicePayment); }
    }
    private static void fit(InstalledFit fit) {
        Objects.requireNonNull(fit); text(fit.hullId());
        if (fit.installedModules().size() > 4096) throw new IllegalArgumentException("Refit fitting exceeds bounds");
        var mounts = new HashSet<String>();
        fit.installedModules().forEach(m -> { text(m.mountId()); text(m.moduleId()); if (!mounts.add(m.mountId())) throw new IllegalArgumentException("Duplicate refit mount"); });
    }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid refit identity");
    }
}

package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipObservationService.EngineeringGrant;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Common Stage-17.5H physical power/heat grant boundary for incremental ship operations.
 *
 * <p>Sensors, beams, shield recharge and later fitted capability actions may plan incremental loads,
 * but they do not receive free power or heat capacity. This service resolves the request against the
 * same fitted/damaged ship, uses surviving continuous power margin first, then the physical shared
 * ENERGY_STORAGE discharge envelope, and commits storage draw plus mount-local heat atomically to
 * the existing {@link EngineeringComponent}. It contains no player/AI distinction.</p>
 *
 * <p>Stage 17.5I adds {@link IntervalBudget} for operations that overlap the same deterministic
 * engineering interval. The budget reserves continuous reactor margin and storage discharge power
 * once for the interval so independent sensor/beam/shield calls cannot each spend the same power
 * headroom. Sequential non-overlapping legacy calls retain the original single-operation API.</p>
 */
public final class ShipEngineeringGrantService {
    private static final double EPSILON = 1e-9d;

    private final ShipEngineeringCatalog catalog;
    private final ShipEngineeringRuntime runtime;

    /**
     * Creates the common grant service over one production engineering catalog.
     *
     * @param catalog production engineering definitions
     */
    public ShipEngineeringGrantService(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.runtime = new ShipEngineeringRuntime(catalog);
    }

    /**
     * Mutable-by-service reservation ledger for one overlapping engineering interval.
     *
     * <p>The object exposes read-only counters to callers. Only this service can reserve from it.
     * A budget is bound to one {@link EngineeringComponent}; passing it to another ship is rejected.
     * It is deliberately not persisted because it represents scheduling state inside one simulation
     * interval, not physical ship inventory.</p>
     */
    public static final class IntervalBudget {
        private final EngineeringComponent component;
        private final double intervalSeconds;
        private final double initialContinuousPowerW;
        private final double initialStorageDischargePowerW;
        private double remainingContinuousPowerW;
        private double remainingStorageDischargePowerW;
        private double committedStorageDrawJ;
        private double committedHeatJ;
        private int committedOperations;

        private IntervalBudget(
                EngineeringComponent component,
                double intervalSeconds,
                double continuousPowerW,
                double storageDischargePowerW) {
            this.component = Objects.requireNonNull(component, "component");
            this.intervalSeconds = intervalSeconds;
            this.initialContinuousPowerW = continuousPowerW;
            this.initialStorageDischargePowerW = storageDischargePowerW;
            this.remainingContinuousPowerW = continuousPowerW;
            this.remainingStorageDischargePowerW = storageDischargePowerW;
        }

        /** @return scheduling interval duration in seconds */
        public double intervalSeconds() {
            return intervalSeconds;
        }

        /** @return continuous reactor margin available when the interval opened */
        public double initialContinuousPowerW() {
            return initialContinuousPowerW;
        }

        /** @return surviving shared-storage discharge power available when the interval opened */
        public double initialStorageDischargePowerW() {
            return initialStorageDischargePowerW;
        }

        /** @return continuous reactor margin not yet reserved by overlapping operations */
        public double remainingContinuousPowerW() {
            return remainingContinuousPowerW;
        }

        /** @return shared-storage discharge power not yet reserved by overlapping operations */
        public double remainingStorageDischargePowerW() {
            return remainingStorageDischargePowerW;
        }

        /** @return physical shared-storage energy committed by operations admitted in this interval */
        public double committedStorageDrawJ() {
            return committedStorageDrawJ;
        }

        /** @return physical local heat committed by operations admitted in this interval */
        public double committedHeatJ() {
            return committedHeatJ;
        }

        /** @return number of operations successfully admitted through this interval budget */
        public int committedOperations() {
            return committedOperations;
        }

        private void commit(
                double continuousPowerW,
                double storageDischargePowerW,
                double storageDrawJ,
                double generatedHeatJ) {
            remainingContinuousPowerW = canonicalZero(remainingContinuousPowerW - continuousPowerW);
            remainingStorageDischargePowerW = canonicalZero(
                    remainingStorageDischargePowerW - storageDischargePowerW);
            committedStorageDrawJ += storageDrawJ;
            committedHeatJ += generatedHeatJ;
            committedOperations = Math.addExact(committedOperations, 1);
        }
    }

    /**
     * Opens one reservation ledger for operations that overlap the same simulation interval.
     *
     * @param engineering authoritative physical ship component
     * @param intervalSeconds positive deterministic interval duration
     * @return interval budget initialized from current damage-aware reactor/storage capability
     */
    public IntervalBudget beginInterval(
            EngineeringComponent engineering,
            double intervalSeconds) {
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        requirePositiveFinite(intervalSeconds, "intervalSeconds");
        Objects.requireNonNull(component.fit, "engineering.fit");
        RuntimeState state = Objects.requireNonNull(component.runtimeState, "engineering.runtimeState");
        ShipInstanceRuntimeState instance = Objects.requireNonNull(component.instanceState, "engineering.instanceState");
        DamageState damage = instance.damage().moduleDamage();
        DerivedShipState derived = runtime.derive(component.fit, state, damage);
        return new IntervalBudget(
                component,
                intervalSeconds,
                Math.max(0d, derived.continuousPowerMarginW()),
                survivingStorageDischargeW(component, damage));
    }

    /**
     * Attempts and, when feasible, commits one non-overlapping incremental operation load.
     *
     * <p>This compatibility method opens a fresh one-operation interval budget. Callers scheduling
     * multiple operations that overlap the same simulation interval must instead open one budget via
     * {@link #beginInterval(EngineeringComponent, double)} and pass it to the overload below.</p>
     *
     * @param engineering authoritative physical ship component
     * @param mountId physical mount receiving the operation heat
     * @param requiredPowerW incremental electrical power demand
     * @param generatedHeatW incremental local waste heat
     * @param durationSeconds positive operation duration
     * @return physical grant and committed storage/heat accounting
     */
    public GrantResult grantAndCommit(
            EngineeringComponent engineering,
            String mountId,
            double requiredPowerW,
            double generatedHeatW,
            double durationSeconds) {
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        IntervalBudget budget = beginInterval(component, durationSeconds);
        return grantAndCommit(
                component,
                mountId,
                requiredPowerW,
                generatedHeatW,
                durationSeconds,
                budget);
    }

    /**
     * Attempts and commits one operation against a shared same-interval reservation budget.
     *
     * <p>A denied request leaves both the engineering component and interval budget unchanged.
     * Continuous power is reserved before physical storage is considered. Any residual demand may
     * use only the still-unreserved storage discharge power and actually remaining shared energy.
     * Local operation heat is committed in joules to the fitted mount.</p>
     *
     * @param engineering authoritative physical ship component
     * @param mountId physical mount receiving operation heat
     * @param requiredPowerW incremental electrical power demand
     * @param generatedHeatW incremental local waste heat
     * @param durationSeconds positive operation duration not exceeding the shared interval
     * @param budget shared reservation ledger created for this ship and interval
     * @return physical grant and committed storage/heat accounting
     */
    public GrantResult grantAndCommit(
            EngineeringComponent engineering,
            String mountId,
            double requiredPowerW,
            double generatedHeatW,
            double durationSeconds,
            IntervalBudget budget) {
        EngineeringComponent component = Objects.requireNonNull(engineering, "engineering");
        IntervalBudget interval = Objects.requireNonNull(budget, "budget");
        requireNonBlank(mountId, "mountId");
        requireNonNegativeFinite(requiredPowerW, "requiredPowerW");
        requireNonNegativeFinite(generatedHeatW, "generatedHeatW");
        requirePositiveFinite(durationSeconds, "durationSeconds");
        if (interval.component != component) {
            throw new IllegalArgumentException("interval budget belongs to a different engineering component");
        }
        if (durationSeconds > interval.intervalSeconds + EPSILON) {
            throw new IllegalArgumentException("operation duration exceeds interval budget duration");
        }
        Objects.requireNonNull(component.fit, "engineering.fit");
        RuntimeState state = Objects.requireNonNull(component.runtimeState, "engineering.runtimeState");
        ShipInstanceRuntimeState instance = Objects.requireNonNull(component.instanceState, "engineering.instanceState");
        DamageState damage = instance.damage().moduleDamage();
        ModuleDefinition mountedModule = moduleOnMount(component, mountId);
        if (mountedModule == null || integrity(damage, mountId) <= EPSILON) {
            return GrantResult.denied(state);
        }

        double continuousContributionW = Math.min(requiredPowerW, interval.remainingContinuousPowerW);
        double storageNeedW = Math.max(0d, requiredPowerW - continuousContributionW);
        double survivingDischargeW = survivingStorageDischargeW(component, damage);
        double storageDischargeLimitW = Math.min(
                survivingDischargeW,
                interval.remainingStorageDischargePowerW);
        double survivingStorageCapacityJ = survivingStorageCapacityJ(component, damage);
        double usableStoredEnergyJ = Math.min(state.sharedBusEnergyJ(), survivingStorageCapacityJ);
        double energyLimitedDischargeW = usableStoredEnergyJ / durationSeconds;
        double availableStorageW = Math.min(storageDischargeLimitW, energyLimitedDischargeW);
        if (storageNeedW > availableStorageW + EPSILON) {
            return GrantResult.denied(state);
        }

        double mountIntegrity = integrity(damage, mountId);
        double localCapacityJ = mountedModule.localThermalCapacityJ() * mountIntegrity;
        double currentLocalHeatJ = state.localHeatJByMount().getOrDefault(mountId, 0d);
        double generatedHeatJ = generatedHeatW * durationSeconds;
        if (generatedHeatJ > Math.max(0d, localCapacityJ - currentLocalHeatJ) + EPSILON) {
            return GrantResult.denied(state);
        }

        double storageDrawJ = storageNeedW * durationSeconds;
        TreeMap<String, Double> localHeat = new TreeMap<>(state.localHeatJByMount());
        if (generatedHeatJ > 0d) {
            localHeat.put(mountId, currentLocalHeatJ + generatedHeatJ);
        }
        RuntimeState next = new RuntimeState(
                state.consumables(),
                Math.max(0d, usableStoredEnergyJ - storageDrawJ),
                state.shipHeatStoredJ(),
                localHeat,
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount());
        component.setRuntimeState(next);
        interval.commit(
                continuousContributionW,
                storageNeedW,
                storageDrawJ,
                generatedHeatJ);
        return new GrantResult(
                new EngineeringGrant(requiredPowerW, generatedHeatW),
                next,
                storageDrawJ,
                generatedHeatJ,
                true);
    }

    private ModuleDefinition moduleOnMount(EngineeringComponent engineering, String mountId) {
        for (InstalledModuleDefinition assignment : engineering.fit.installedModules()) {
            if (assignment.mountId().equals(mountId)) {
                return catalog.findModule(assignment.moduleId());
            }
        }
        return null;
    }

    private double survivingStorageDischargeW(EngineeringComponent engineering, DamageState damage) {
        double total = 0d;
        for (InstalledModuleDefinition assignment : engineering.fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module != null && module.family() == ModuleFamily.ENERGY_STORAGE) {
                total += parameter(module, ShipEngineeringRuntime.MAX_DISCHARGE_POWER_W)
                        * integrity(damage, assignment.mountId());
            }
        }
        return total;
    }

    private double survivingStorageCapacityJ(EngineeringComponent engineering, DamageState damage) {
        double total = 0d;
        for (InstalledModuleDefinition assignment : engineering.fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module != null && module.family() == ModuleFamily.ENERGY_STORAGE) {
                total += module.storedEnergyCapacityJ() * integrity(damage, assignment.mountId());
            }
        }
        return total;
    }

    private static double parameter(ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null) {
            return 0d;
        }
        requireNonNegativeFinite(value, module.id() + "." + key);
        return value;
    }

    private static double integrity(DamageState damage, String mountId) {
        return damage.moduleIntegrityByMount().getOrDefault(mountId, 1d);
    }

    /**
     * Result of one atomic engineering admission attempt.
     *
     * @param grant grant consumable by a fitted capability service
     * @param state resulting physical operating state
     * @param storageDrawJ electrical energy removed from shared storage
     * @param generatedHeatJ heat committed to the physical mount
     * @param committed whether state and grant were physically committed
     */
    public record GrantResult(
            EngineeringGrant grant,
            RuntimeState state,
            double storageDrawJ,
            double generatedHeatJ,
            boolean committed) {
        /**
         * Validates one grant result.
         *
         * @param grant grant consumable by a fitted capability service
         * @param state resulting physical operating state
         * @param storageDrawJ electrical energy removed from shared storage
         * @param generatedHeatJ heat committed to the physical mount
         * @param committed whether state and grant were physically committed
         */
        public GrantResult {
            Objects.requireNonNull(grant, "grant");
            Objects.requireNonNull(state, "state");
            requireNonNegativeFinite(storageDrawJ, "storageDrawJ");
            requireNonNegativeFinite(generatedHeatJ, "generatedHeatJ");
            if (!committed && (storageDrawJ > 0d || generatedHeatJ > 0d)) {
                throw new IllegalArgumentException("denied grant cannot consume energy or create heat");
            }
        }

        private static GrantResult denied(RuntimeState state) {
            return new GrantResult(EngineeringGrant.denied(), state, 0d, 0d, false);
        }
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) <= EPSILON ? 0d : value;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}

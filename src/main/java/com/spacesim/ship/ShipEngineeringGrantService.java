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
     * Attempts and, when feasible, commits one incremental operation load.
     *
     * <p>A denied request leaves the engineering component bit-for-bit unchanged. Heat is authored as
     * incremental watts and committed as joules over {@code durationSeconds}. The local thermal
     * buffer is a hard admission boundary; ordinary coolant/radiator processing remains in the
     * engineering tick and can make future grants available again.</p>
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
        requireNonBlank(mountId, "mountId");
        requireNonNegativeFinite(requiredPowerW, "requiredPowerW");
        requireNonNegativeFinite(generatedHeatW, "generatedHeatW");
        requirePositiveFinite(durationSeconds, "durationSeconds");
        Objects.requireNonNull(component.fit, "engineering.fit");
        RuntimeState state = Objects.requireNonNull(component.runtimeState, "engineering.runtimeState");
        ShipInstanceRuntimeState instance = Objects.requireNonNull(component.instanceState, "engineering.instanceState");
        DamageState damage = instance.damage().moduleDamage();
        ModuleDefinition mountedModule = moduleOnMount(component, mountId);
        if (mountedModule == null || integrity(damage, mountId) <= EPSILON) {
            return GrantResult.denied(state);
        }

        DerivedShipState derived = runtime.derive(component.fit, state, damage);
        double continuousMarginW = Math.max(0d, derived.continuousPowerMarginW());
        double storageNeedW = Math.max(0d, requiredPowerW - continuousMarginW);
        double storageDischargeLimitW = survivingStorageDischargeW(component, damage);
        double survivingStorageCapacityJ = survivingStorageCapacityJ(component, damage);
        double usableStoredEnergyJ = Math.min(state.sharedBusEnergyJ(), survivingStorageCapacityJ);
        double energyLimitedDischargeW = usableStoredEnergyJ / durationSeconds;
        double availableStorageW = Math.min(storageDischargeLimitW, energyLimitedDischargeW);
        if (storageNeedW > availableStorageW + EPSILON) {
            return GrantResult.denied(state);
        }

        double integrity = integrity(damage, mountId);
        double localCapacityJ = mountedModule.localThermalCapacityJ() * integrity;
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

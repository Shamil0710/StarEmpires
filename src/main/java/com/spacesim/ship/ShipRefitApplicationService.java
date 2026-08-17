package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipShieldEngineeringAdapter.FittedShield;
import com.spacesim.ship.ShipyardRefitContinuity.Completion;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-17.5H live application boundary for a settled Stage-17.5G refit completion.
 *
 * <p>The same physical ship entity receives the target fit. Retained modules keep local heat,
 * launcher/FTL cycles, shield reserve and ammunition-feed identity; replaced modules do not transfer
 * those states to new hardware. Shared electrical energy may only be clamped downward to the new
 * surviving capacity. New shield hardware starts empty/collapsed and therefore cannot be installed
 * as a free charged battery.</p>
 */
public final class ShipRefitApplicationService {
    private final ShipEngineeringCatalog catalog;
    private final ShipEngineeringRuntime runtime;
    private final ShipShieldEngineeringAdapter shields = new ShipShieldEngineeringAdapter();
    private final ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();

    /**
     * Creates a refit application service over the same production catalog as fitting/runtime.
     *
     * @param catalog production engineering catalog
     */
    public ShipRefitApplicationService(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.runtime = new ShipEngineeringRuntime(catalog);
    }

    /**
     * Applies one completed refit to the existing authoritative engineering component.
     *
     * @param liveAssetId persistent ID of the entity owning {@code component}
     * @param component existing physical engineering component
     * @param completion settled Stage-17.5G continuity result
     * @return same mutated component reference for command-pipeline convenience
     */
    public EngineeringComponent apply(
            EntityId liveAssetId,
            EngineeringComponent component,
            Completion completion) {
        EntityId checkedId = Objects.requireNonNull(liveAssetId, "liveAssetId");
        EngineeringComponent checked = Objects.requireNonNull(component, "component");
        Completion result = Objects.requireNonNull(completion, "completion");
        if (!checkedId.equals(result.assetId())) {
            throw new IllegalArgumentException("refit completion belongs to a different physical asset");
        }
        InstalledFit oldFit = Objects.requireNonNull(checked.fit, "component.fit");
        RuntimeState oldRuntime = Objects.requireNonNull(checked.runtimeState, "component.runtimeState");
        ShipInstanceRuntimeState oldInstance = Objects.requireNonNull(checked.instanceState, "component.instanceState");
        InstalledFit targetFit = result.fit();
        DamageState targetDamage = result.installedDamage().moduleDamage();
        Map<String, String> oldModules = moduleIds(oldFit);
        Map<String, String> newModules = moduleIds(targetFit);

        RuntimeState baseline = runtime.initialize(targetFit, oldRuntime.consumables(), targetDamage);
        TreeMap<String, Double> localHeat = new TreeMap<>();
        TreeMap<String, Double> thrustLimits = new TreeMap<>(baseline.thrustLimitNByMount());
        TreeMap<String, Double> ftlCooldowns = new TreeMap<>();
        for (Map.Entry<String, String> entry : newModules.entrySet()) {
            String mount = entry.getKey();
            boolean retained = samePhysicalModule(oldModules, newModules, mount);
            localHeat.put(mount, retained ? oldRuntime.localHeatJByMount().getOrDefault(mount, 0d) : 0d);
            if (retained && oldRuntime.thrustLimitNByMount().containsKey(mount)
                    && thrustLimits.containsKey(mount)) {
                thrustLimits.put(mount, Math.min(
                        oldRuntime.thrustLimitNByMount().get(mount), thrustLimits.get(mount)));
            }
            if (retained && oldRuntime.ftlCooldownSecondsByMount().containsKey(mount)) {
                ftlCooldowns.put(mount, oldRuntime.ftlCooldownSecondsByMount().get(mount));
            } else if (baseline.ftlCooldownSecondsByMount().containsKey(mount)) {
                ftlCooldowns.put(mount, 0d);
            }
        }
        RuntimeState targetRuntime = new RuntimeState(
                oldRuntime.consumables(),
                Math.min(oldRuntime.sharedBusEnergyJ(), baseline.sharedBusEnergyJ()),
                oldRuntime.shipHeatStoredJ(),
                localHeat,
                thrustLimits,
                baseline.coolantBusCapacityW(),
                ftlCooldowns);

        TreeMap<String, State> targetShields = new TreeMap<>();
        var targetDerived = runtime.derive(targetFit, targetRuntime, targetDamage);
        for (FittedShield fitted : shields.derive(targetDerived)) {
            State previous = oldInstance.shieldStatesByMount().get(fitted.mountId());
            if (previous != null && samePhysicalModule(oldModules, newModules, fitted.mountId())) {
                targetShields.put(fitted.mountId(), shieldRuntime.withEmitterIntegrity(
                        fitted.definition(), previous, fitted.emitterIntegrity()));
            } else {
                State empty = new State(0d, 0d, true, 0d, fitted.emitterIntegrity());
                targetShields.put(fitted.mountId(), shieldRuntime.withEmitterIntegrity(
                        fitted.definition(), empty, fitted.emitterIntegrity()));
            }
        }

        List<FeedBinding> retainedFeeds = new ArrayList<>();
        for (FeedBinding binding : oldInstance.weaponLoadout().feeds()) {
            if (samePhysicalModule(oldModules, newModules, binding.mountId())) {
                retainedFeeds.add(binding);
            }
        }
        TreeMap<String, Double> weaponCooldowns = new TreeMap<>();
        for (Map.Entry<String, Double> entry
                : oldInstance.weaponMountRuntime().cooldownSecondsByMount().entrySet()) {
            if (samePhysicalModule(oldModules, newModules, entry.getKey())) {
                weaponCooldowns.put(entry.getKey(), entry.getValue());
            }
        }

        checked.fit = targetFit;
        checked.setRuntimeState(targetRuntime);
        checked.setInstanceState(new ShipInstanceRuntimeState(
                result.installedDamage(),
                targetShields,
                result.installedMaintenance(),
                new WeaponLoadoutState(retainedFeeds),
                new WeaponMountRuntime.RuntimeState(weaponCooldowns)));
        return checked;
    }

    private static Map<String, String> moduleIds(InstalledFit fit) {
        TreeMap<String, String> result = new TreeMap<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            result.put(assignment.mountId(), assignment.moduleId());
        }
        return result;
    }

    private static boolean samePhysicalModule(
            Map<String, String> oldModules,
            Map<String, String> newModules,
            String mountId) {
        String before = oldModules.get(mountId);
        return before != null && before.equals(newModules.get(mountId));
    }
}

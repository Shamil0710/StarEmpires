package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardEngineeringService.RefitCompletion;
import com.spacesim.ship.ShipyardEngineeringService.WorkKind;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-17.5G continuity boundary for physical modules removed or retained by a refit.
 *
 * <p>A damaged or overdue module must not become pristine merely because it leaves a mount. This
 * helper captures removed-module condition for the ordinary Stage-18 inventory/asset boundary and
 * reconciles installed damage/service age without changing the ship's persistent EntityId. Stage
 * 17.5H is expected to persist the resulting state together with the live engineering component.</p>
 */
public final class ShipyardRefitContinuity {
    private ShipyardRefitContinuity() {
        throw new AssertionError("utility class");
    }

    /**
     * Physical condition of one module removed from the fitted ship.
     *
     * @param assignment original mount/module assignment
     * @param integrity retained subsystem integrity in [0,1]
     * @param secondsSinceService retained scheduled-maintenance age
     */
    public record RemovedModuleState(
            InstalledModuleDefinition assignment,
            double integrity,
            double secondsSinceService) {
        /**
         * Validates one removed physical module state.
         *
         * @param assignment original assignment
         * @param integrity retained integrity
         * @param secondsSinceService retained maintenance age
         */
        public RemovedModuleState {
            Objects.requireNonNull(assignment, "assignment");
            if (!Double.isFinite(integrity) || integrity < 0d || integrity > 1d) {
                throw new IllegalArgumentException("removed module integrity must be in [0,1]");
            }
            if (!Double.isFinite(secondsSinceService) || secondsSinceService < 0d) {
                throw new IllegalArgumentException("removed module service age must be finite and non-negative");
            }
        }
    }

    /**
     * Full identity-preserving authoritative refit handoff.
     *
     * @param assetId unchanged persistent ship ID
     * @param fit completed target fit
     * @param installedDamage damage retained by modules that physically remain installed
     * @param installedMaintenance service age retained by modules that physically remain installed
     * @param removedModules physical removed modules with retained condition
     */
    public record Completion(
            EntityId assetId,
            InstalledFit fit,
            Snapshot installedDamage,
            MaintenanceState installedMaintenance,
            List<RemovedModuleState> removedModules) {
        /**
         * Freezes removed-module ordering.
         *
         * @param assetId unchanged ship ID
         * @param fit target fit
         * @param installedDamage installed damage state
         * @param installedMaintenance installed maintenance state
         * @param removedModules removed physical module states
         */
        public Completion {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(installedDamage, "installedDamage");
            Objects.requireNonNull(installedMaintenance, "installedMaintenance");
            Objects.requireNonNull(removedModules, "removedModules");
            List<RemovedModuleState> copy = new ArrayList<>(removedModules);
            copy.sort(Comparator.comparing((RemovedModuleState value) -> value.assignment().mountId())
                    .thenComparing(value -> value.assignment().moduleId()));
            removedModules = List.copyOf(copy);
        }
    }

    /**
     * Completes a settled refit and preserves damage/service state of installed and removed modules.
     *
     * @param service common shipyard engineering service
     * @param plan settled REFIT plan
     * @param settlement physical input/work settlement
     * @param sourceDamage damage state captured when the work order was executed
     * @param sourceMaintenance maintenance state captured when the work order was executed
     * @return complete Stage-17.5G refit continuity handoff
     */
    public static Completion complete(
            ShipyardEngineeringService service,
            WorkPlan plan,
            WorkSettlement settlement,
            Snapshot sourceDamage,
            MaintenanceState sourceMaintenance) {
        ShipyardEngineeringService checkedService = Objects.requireNonNull(service, "service");
        WorkPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        Snapshot checkedDamage = Objects.requireNonNull(sourceDamage, "sourceDamage");
        MaintenanceState checkedMaintenance = Objects.requireNonNull(sourceMaintenance, "sourceMaintenance");
        if (checkedPlan.kind() != WorkKind.REFIT) {
            throw new IllegalArgumentException("Refit continuity requires a REFIT plan");
        }
        RefitCompletion base = checkedService.completeRefit(checkedPlan, settlement);
        ReconciledState reconciled = reconcile(
                checkedPlan.sourceFit(), checkedPlan.targetFit(), checkedDamage, checkedMaintenance);
        if (!reconciled.installedDamage().equals(base.damage())) {
            throw new IllegalStateException("Refit damage continuity differs from planned completion state");
        }
        return new Completion(
                base.assetId(), base.fit(), reconciled.installedDamage(),
                reconciled.installedMaintenance(), reconciled.removedModules());
    }

    private static ReconciledState reconcile(
            InstalledFit sourceFit,
            InstalledFit targetFit,
            Snapshot sourceDamage,
            MaintenanceState sourceMaintenance) {
        Map<String, InstalledModuleDefinition> oldByMount = byMount(sourceFit);
        Map<String, InstalledModuleDefinition> newByMount = byMount(targetFit);
        TreeMap<String, Double> retainedDamage = new TreeMap<>();
        TreeMap<String, Double> retainedMaintenance = new TreeMap<>();
        List<RemovedModuleState> removed = new ArrayList<>();

        for (Map.Entry<String, InstalledModuleDefinition> oldEntry : oldByMount.entrySet()) {
            InstalledModuleDefinition newValue = newByMount.get(oldEntry.getKey());
            boolean samePhysicalDefinition = newValue != null
                    && oldEntry.getValue().moduleId().equals(newValue.moduleId());
            double integrity = sourceDamage.moduleDamage().moduleIntegrityByMount()
                    .getOrDefault(oldEntry.getKey(), 1d);
            double serviceAge = sourceMaintenance.secondsSinceServiceByMount()
                    .getOrDefault(oldEntry.getKey(), 0d);
            if (samePhysicalDefinition) {
                if (integrity < 1d) {
                    retainedDamage.put(oldEntry.getKey(), integrity);
                }
                retainedMaintenance.put(oldEntry.getKey(), serviceAge);
            } else {
                removed.add(new RemovedModuleState(oldEntry.getValue(), integrity, serviceAge));
            }
        }
        for (Map.Entry<String, InstalledModuleDefinition> newEntry : newByMount.entrySet()) {
            InstalledModuleDefinition oldValue = oldByMount.get(newEntry.getKey());
            if (oldValue == null || !oldValue.moduleId().equals(newEntry.getValue().moduleId())) {
                retainedMaintenance.put(newEntry.getKey(), 0d);
            }
        }
        Snapshot installedDamage = new Snapshot(
                sourceDamage.compartmentIntegrityById(), new DamageState(retainedDamage));
        return new ReconciledState(
                installedDamage, new MaintenanceState(retainedMaintenance), removed);
    }

    private static Map<String, InstalledModuleDefinition> byMount(InstalledFit fit) {
        TreeMap<String, InstalledModuleDefinition> result = new TreeMap<>();
        for (InstalledModuleDefinition assignment : Objects.requireNonNull(fit, "fit").installedModules()) {
            result.put(assignment.mountId(), assignment);
        }
        return result;
    }

    private record ReconciledState(
            Snapshot installedDamage,
            MaintenanceState installedMaintenance,
            List<RemovedModuleState> removedModules) { }
}

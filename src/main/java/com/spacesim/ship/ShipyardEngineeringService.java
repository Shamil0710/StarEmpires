package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.CompartmentRepairProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipEngineeringState.ValidationIssue;
import com.spacesim.ship.ShipEngineeringState.ValidationResult;
import com.spacesim.ship.ShipEngineeringState.ValidationSeverity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Shared Stage-17.5G engineering boundary for build, refit, repair and maintenance work.
 *
 * <p>The service plans physical inputs, facility capabilities and engineering work. It does not own
 * an inventory, wallet, factory graph or ship Entity allocator. Completion is possible only after a
 * caller supplies a settlement proving that the required physical inputs and work were provided.
 * Stage 18 binds those requirement IDs to ordinary inventory/logistics/industry; Stage 17.5H binds
 * completion to live ECS/persistence surfaces.</p>
 *
 * <p>No method inspects player/AI ownership or doctrine class. Refit and repair completion preserve
 * the supplied persistent {@link EntityId}; replacing an existing ship with a newly spawned entity is
 * intentionally outside this API.</p>
 */
public final class ShipyardEngineeringService {
    private static final double EPSILON = 1e-9d;

    private final ShipEngineeringCatalog engineering;
    private final ShipyardIndustrialCatalog industrial;
    private final ShipFittingValidator fittingValidator;

    /**
     * Creates the common shipyard engineering boundary.
     *
     * @param engineering authoritative ship engineering catalog
     * @param industrial Stage-17.5G industrial requirement catalog
     */
    public ShipyardEngineeringService(
            ShipEngineeringCatalog engineering,
            ShipyardIndustrialCatalog industrial) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.industrial = Objects.requireNonNull(industrial, "industrial");
        this.fittingValidator = new ShipFittingValidator(engineering);
    }

    /** Type of physical shipyard work. */
    public enum WorkKind {
        /** Produce a new fitted hull. */ BUILD,
        /** Change modules on the same physical hull. */ REFIT,
        /** Restore local structure and installed subsystem integrity. */ REPAIR,
        /** Perform scheduled module service work. */ MAINTENANCE
    }

    /** Stable reason why a yard cannot execute a requested work plan. */
    public enum FeasibilityCode {
        /** Target fit is not physically valid. */ INVALID_TARGET_FIT,
        /** A refit attempted to replace the hull rather than modify the same asset. */ HULL_CHANGE_REQUIRES_BUILD,
        /** No industrial profile exists for the referenced hull. */ MISSING_HULL_PROFILE,
        /** No industrial profile exists for a referenced module. */ MISSING_MODULE_PROFILE,
        /** Hull dimensions exceed the yard berth envelope. */ BERTH_ENVELOPE_EXCEEDED,
        /** Current/target physical service mass exceeds berth capacity. */ BERTH_MASS_EXCEEDED,
        /** Required fabrication capability is absent. */ FABRICATION_CAPABILITY_MISSING,
        /** Yard cannot handle one required physical input. */ MATERIAL_HANDLING_MISSING,
        /** Required tooling is absent. */ TOOLING_MISSING,
        /** Yard precision capability is below the authored requirement. */ PRECISION_CAPABILITY_INSUFFICIENT,
        /** Yard industrial power is below the authored requirement. */ INDUSTRIAL_POWER_INSUFFICIENT,
        /** Yard labor capacity is below the authored requirement. */ LABOR_CAPACITY_INSUFFICIENT,
        /** Yard automation capacity is below the authored requirement. */ AUTOMATION_CAPACITY_INSUFFICIENT
    }

    /**
     * Facility capability projection supplied by the physical station/shipyard layer.
     *
     * @param yardId stable facility/capability ID
     * @param berthDimensionsM maximum physical berth envelope
     * @param maxServiceMassKg maximum supported physical ship mass
     * @param fabricationCapabilities available fabrication capabilities
     * @param handledInputContentIds physical input families/items the yard can receive/process
     * @param toolingTags installed tooling
     * @param precisionCapability normalized precision capability in [0,1]
     * @param workRate engineering work-seconds completed per simulation second
     * @param laborCapacity simultaneous labor capacity
     * @param automationCapacity simultaneous automation capacity
     * @param industrialPowerW available industrial power
     */
    public record ShipyardCapability(
            String yardId,
            Dimensions3d berthDimensionsM,
            double maxServiceMassKg,
            Set<String> fabricationCapabilities,
            Set<String> handledInputContentIds,
            Set<String> toolingTags,
            double precisionCapability,
            double workRate,
            int laborCapacity,
            int automationCapacity,
            double industrialPowerW) {
        /**
         * Validates and freezes one facility capability projection.
         *
         * @param yardId stable facility ID
         * @param berthDimensionsM berth dimensions
         * @param maxServiceMassKg mass capacity
         * @param fabricationCapabilities fabrication capabilities
         * @param handledInputContentIds handled physical inputs
         * @param toolingTags tooling tags
         * @param precisionCapability normalized precision capability
         * @param workRate work rate
         * @param laborCapacity labor capacity
         * @param automationCapacity automation capacity
         * @param industrialPowerW industrial power
         */
        public ShipyardCapability {
            requireNonBlank(yardId, "yardId");
            Objects.requireNonNull(berthDimensionsM, "berthDimensionsM");
            requirePositive(berthDimensionsM.lengthM(), "berth length");
            requirePositive(berthDimensionsM.widthM(), "berth width");
            requirePositive(berthDimensionsM.heightM(), "berth height");
            requirePositive(maxServiceMassKg, "maxServiceMassKg");
            fabricationCapabilities = immutableSet(fabricationCapabilities, "fabricationCapabilities");
            handledInputContentIds = immutableSet(handledInputContentIds, "handledInputContentIds");
            toolingTags = immutableSet(toolingTags, "toolingTags");
            requireUnitInterval(precisionCapability, "precisionCapability");
            requirePositive(workRate, "workRate");
            if (laborCapacity < 0 || automationCapacity < 0) {
                throw new IllegalArgumentException("yard labor/automation capacity must be non-negative");
            }
            requireNonNegative(industrialPowerW, "industrialPowerW");
        }
    }

    /**
     * One aggregated physical input requirement.
     *
     * @param contentId Stage-18-resolvable input ID
     * @param amount positive required amount in that content definition's native unit
     */
    public record IndustrialInputRequirement(String contentId, double amount) {
        /**
         * Validates one physical input requirement.
         *
         * @param contentId input ID
         * @param amount required amount
         */
        public IndustrialInputRequirement {
            requireNonBlank(contentId, "contentId");
            requirePositive(amount, "amount");
        }
    }

    /**
     * Aggregate engineering requirements for one work order.
     *
     * @param inputs physical material/component inputs
     * @param fabricationCapabilities required fabrication capabilities
     * @param toolingTags required tooling
     * @param precisionRequirement minimum precision capability
     * @param industrialPowerW minimum industrial power
     * @param laborRequirement minimum simultaneous labor capacity
     * @param automationRequirement minimum simultaneous automation capacity
     * @param totalWorkSeconds total engineering work at unit work rate
     * @param berthDimensionsM required physical berth envelope
     * @param serviceMassKg physical mass supported during the work
     */
    public record WorkRequirements(
            List<IndustrialInputRequirement> inputs,
            Set<String> fabricationCapabilities,
            Set<String> toolingTags,
            double precisionRequirement,
            double industrialPowerW,
            int laborRequirement,
            int automationRequirement,
            double totalWorkSeconds,
            Dimensions3d berthDimensionsM,
            double serviceMassKg) {
        /**
         * Validates and freezes deterministic work requirements.
         *
         * @param inputs physical inputs
         * @param fabricationCapabilities fabrication capabilities
         * @param toolingTags tooling tags
         * @param precisionRequirement precision requirement
         * @param industrialPowerW industrial power
         * @param laborRequirement labor requirement
         * @param automationRequirement automation requirement
         * @param totalWorkSeconds total work
         * @param berthDimensionsM berth envelope
         * @param serviceMassKg service mass
         */
        public WorkRequirements {
            Objects.requireNonNull(inputs, "inputs");
            List<IndustrialInputRequirement> inputCopy = new ArrayList<>(inputs);
            inputCopy.sort(Comparator.comparing(IndustrialInputRequirement::contentId));
            inputs = List.copyOf(inputCopy);
            fabricationCapabilities = immutableSet(fabricationCapabilities, "fabricationCapabilities");
            toolingTags = immutableSet(toolingTags, "toolingTags");
            requireUnitInterval(precisionRequirement, "precisionRequirement");
            requireNonNegative(industrialPowerW, "industrialPowerW");
            if (laborRequirement < 0 || automationRequirement < 0) {
                throw new IllegalArgumentException("work labor/automation requirement must be non-negative");
            }
            requireNonNegative(totalWorkSeconds, "totalWorkSeconds");
            Objects.requireNonNull(berthDimensionsM, "berthDimensionsM");
            requirePositive(serviceMassKg, "serviceMassKg");
        }

        /**
         * Returns real elapsed time at one facility work rate.
         *
         * @param yard facility capability
         * @return required simulation seconds
         */
        public double durationSeconds(ShipyardCapability yard) {
            return totalWorkSeconds / Objects.requireNonNull(yard, "yard").workRate();
        }
    }

    /**
     * One deterministic feasibility diagnostic.
     *
     * @param code stable diagnostic code
     * @param subject failing capability/input/fit subject
     * @param detail deterministic diagnostic detail
     */
    public record FeasibilityIssue(FeasibilityCode code, String subject, String detail) {
        /**
         * Normalizes one diagnostic.
         *
         * @param code diagnostic code
         * @param subject subject
         * @param detail detail
         */
        public FeasibilityIssue {
            Objects.requireNonNull(code, "code");
            subject = subject == null ? "" : subject;
            detail = detail == null ? "" : detail;
        }
    }

    /**
     * Deterministic facility/fit feasibility result.
     *
     * @param issues sorted blocking issues
     */
    public record Feasibility(List<FeasibilityIssue> issues) {
        /**
         * Sorts and freezes blocking issues.
         *
         * @param issues feasibility issues
         */
        public Feasibility {
            Objects.requireNonNull(issues, "issues");
            List<FeasibilityIssue> copy = new ArrayList<>(issues);
            copy.sort(Comparator.comparing((FeasibilityIssue issue) -> issue.code().name())
                    .thenComparing(FeasibilityIssue::subject)
                    .thenComparing(FeasibilityIssue::detail));
            issues = List.copyOf(copy);
        }

        /** @return whether the facility can execute the work */
        public boolean feasible() {
            return issues.isEmpty();
        }
    }

    /**
     * Immutable plan for build/refit/repair/maintenance.
     *
     * @param kind work type
     * @param assetId existing physical asset ID; null only for BUILD
     * @param sourceFit existing fit for refit/service work; null only for BUILD
     * @param targetFit fit that exists after work
     * @param requirements physical/facility/work requirements
     * @param feasibility blocking feasibility result
     * @param removedModules non-destructively removed modules during refit
     * @param affectedMounts mounts serviced/repaired/replaced by this order
     * @param completionDamage resulting local damage state for refit/repair, otherwise null
     */
    public record WorkPlan(
            WorkKind kind,
            EntityId assetId,
            InstalledFit sourceFit,
            InstalledFit targetFit,
            WorkRequirements requirements,
            Feasibility feasibility,
            List<InstalledModuleDefinition> removedModules,
            List<String> affectedMounts,
            Snapshot completionDamage) {
        /**
         * Freezes deterministic plan collections.
         *
         * @param kind work type
         * @param assetId existing asset ID or null for build
         * @param sourceFit source fit or null for build
         * @param targetFit target fit
         * @param requirements work requirements
         * @param feasibility feasibility result
         * @param removedModules removed modules
         * @param affectedMounts affected mounts
         * @param completionDamage resulting damage state where applicable
         */
        public WorkPlan {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(targetFit, "targetFit");
            Objects.requireNonNull(requirements, "requirements");
            Objects.requireNonNull(feasibility, "feasibility");
            Objects.requireNonNull(removedModules, "removedModules");
            List<InstalledModuleDefinition> removedCopy = new ArrayList<>(removedModules);
            removedCopy.sort(Comparator.comparing(InstalledModuleDefinition::mountId)
                    .thenComparing(InstalledModuleDefinition::moduleId));
            removedModules = List.copyOf(removedCopy);
            Objects.requireNonNull(affectedMounts, "affectedMounts");
            TreeSet<String> mountCopy = new TreeSet<>(affectedMounts);
            affectedMounts = List.copyOf(mountCopy);
            if (kind == WorkKind.BUILD) {
                if (assetId != null || sourceFit != null) {
                    throw new IllegalArgumentException("BUILD plan must not reference an existing asset");
                }
            } else if (assetId == null || sourceFit == null) {
                throw new IllegalArgumentException("Existing-asset work requires assetId and sourceFit");
            }
        }
    }

    /**
     * Physical settlement supplied by the ordinary economy/work simulation.
     *
     * @param deliveredInputs delivered/consumed physical input amounts by content ID
     * @param completedWorkSeconds engineering work completed at unit work-rate scale
     */
    public record WorkSettlement(Map<String, Double> deliveredInputs, double completedWorkSeconds) {
        /**
         * Validates and freezes one settlement snapshot.
         *
         * @param deliveredInputs delivered inputs
         * @param completedWorkSeconds completed work
         */
        public WorkSettlement {
            Objects.requireNonNull(deliveredInputs, "deliveredInputs");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : deliveredInputs.entrySet()) {
                requireNonBlank(entry.getKey(), "delivered input ID");
                Double amount = Objects.requireNonNull(entry.getValue(), "delivered input amount");
                requireNonNegative(amount, "delivered input amount");
                copy.put(entry.getKey(), amount);
            }
            deliveredInputs = Collections.unmodifiableMap(copy);
            requireNonNegative(completedWorkSeconds, "completedWorkSeconds");
        }

        /** @return empty, unsettled work state */
        public static WorkSettlement empty() {
            return new WorkSettlement(Map.of(), 0d);
        }
    }

    /**
     * Persistent-ready scheduled-maintenance age state.
     *
     * @param secondsSinceServiceByMount elapsed operating/service age by installed mount
     */
    public record MaintenanceState(Map<String, Double> secondsSinceServiceByMount) {
        /**
         * Validates and freezes service ages.
         *
         * @param secondsSinceServiceByMount service ages by mount
         */
        public MaintenanceState {
            Objects.requireNonNull(secondsSinceServiceByMount, "secondsSinceServiceByMount");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : secondsSinceServiceByMount.entrySet()) {
                requireNonBlank(entry.getKey(), "maintenance mount ID");
                Double seconds = Objects.requireNonNull(entry.getValue(), "maintenance age");
                requireNonNegative(seconds, "maintenance age");
                copy.put(entry.getKey(), seconds);
            }
            secondsSinceServiceByMount = Collections.unmodifiableMap(copy);
        }

        /** @return initial maintenance state */
        public static MaintenanceState initial() {
            return new MaintenanceState(Map.of());
        }
    }

    /**
     * New-ship completion result; entity creation itself stays in the ordinary world layer.
     *
     * @param assetId newly allocated persistent asset ID
     * @param fit completed installed fit
     */
    public record BuildCompletion(EntityId assetId, InstalledFit fit) { }

    /**
     * Refit completion preserving persistent identity and removed-module fate.
     *
     * @param assetId unchanged persistent asset ID
     * @param fit new installed fit
     * @param damage reconciled local damage state
     * @param removedModules physical modules removed from the fit and returned to the economy boundary
     */
    public record RefitCompletion(
            EntityId assetId,
            InstalledFit fit,
            Snapshot damage,
            List<InstalledModuleDefinition> removedModules) {
        /**
         * Freezes removed-module ordering.
         *
         * @param assetId unchanged asset ID
         * @param fit new fit
         * @param damage reconciled damage
         * @param removedModules removed modules
         */
        public RefitCompletion {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(damage, "damage");
            removedModules = List.copyOf(Objects.requireNonNull(removedModules, "removedModules"));
        }
    }

    /**
     * Repair completion preserving persistent identity.
     *
     * @param assetId unchanged persistent asset ID
     * @param damage repaired local damage state
     */
    public record RepairCompletion(EntityId assetId, Snapshot damage) { }

    /**
     * Maintenance completion preserving persistent identity.
     *
     * @param assetId unchanged persistent asset ID
     * @param maintenance updated service-age state
     */
    public record MaintenanceCompletion(EntityId assetId, MaintenanceState maintenance) { }

    /**
     * Plans production of one new fitted ship.
     *
     * @param targetFit target production fit
     * @param yard candidate shipyard capability
     * @return deterministic build plan
     */
    public WorkPlan planBuild(InstalledFit targetFit, ShipyardCapability yard) {
        InstalledFit checkedTarget = Objects.requireNonNull(targetFit, "targetFit");
        ShipyardCapability checkedYard = Objects.requireNonNull(yard, "yard");
        HullDefinition hull = requireHull(checkedTarget.hullId());
        List<FeasibilityIssue> issues = new ArrayList<>();
        validateFit(hull, checkedTarget, ConsumableState.empty(), DamageState.pristine(), issues);

        RequirementAccumulator accumulator = new RequirementAccumulator();
        HullIndustrialProfile hullProfile = industrial.findHullProfile(hull.id());
        if (hullProfile == null) {
            issue(issues, FeasibilityCode.MISSING_HULL_PROFILE, hull.id(), "build");
        } else {
            accumulator.addHull(hullProfile, true);
        }
        for (InstalledModuleDefinition assignment : checkedTarget.installedModules()) {
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            if (module == null) {
                continue;
            }
            ModuleIndustrialProfile profile = requireModuleProfile(module.id(), issues);
            if (profile != null) {
                accumulator.addModule(profile, module, true, true, false);
            }
        }
        WorkRequirements requirements = accumulator.toRequirements(
                hull.boundingDimensionsM(), serviceMassKg(hull, checkedTarget, ConsumableState.empty()));
        appendYardIssues(requirements, checkedYard, issues);
        return new WorkPlan(
                WorkKind.BUILD, null, null, checkedTarget, requirements, new Feasibility(issues),
                List.of(), mountIds(checkedTarget), null);
    }

    /**
     * Plans module changes on the same persistent hull.
     *
     * @param assetId existing physical ship ID
     * @param sourceFit current installed fit
     * @param targetFit requested installed fit
     * @param consumables current physical loads; incompatible loads must be unloaded before refit
     * @param damage current local damage state
     * @param yard candidate shipyard
     * @return deterministic refit plan
     */
    public WorkPlan planRefit(
            EntityId assetId,
            InstalledFit sourceFit,
            InstalledFit targetFit,
            ConsumableState consumables,
            Snapshot damage,
            ShipyardCapability yard) {
        EntityId checkedAsset = Objects.requireNonNull(assetId, "assetId");
        InstalledFit checkedSource = Objects.requireNonNull(sourceFit, "sourceFit");
        InstalledFit checkedTarget = Objects.requireNonNull(targetFit, "targetFit");
        ConsumableState checkedConsumables = Objects.requireNonNull(consumables, "consumables");
        Snapshot checkedDamage = Objects.requireNonNull(damage, "damage");
        ShipyardCapability checkedYard = Objects.requireNonNull(yard, "yard");
        HullDefinition hull = requireHull(checkedSource.hullId());
        List<FeasibilityIssue> issues = new ArrayList<>();
        if (!checkedSource.hullId().equals(checkedTarget.hullId())) {
            issue(issues, FeasibilityCode.HULL_CHANGE_REQUIRES_BUILD, checkedTarget.hullId(),
                    "sourceHull=" + checkedSource.hullId());
        }

        Snapshot completionDamage = reconcileRefitDamage(checkedSource, checkedTarget, checkedDamage);
        validateFit(hull, checkedTarget, checkedConsumables, completionDamage.moduleDamage(), issues);

        Map<String, InstalledModuleDefinition> oldByMount = installedByMount(checkedSource);
        Map<String, InstalledModuleDefinition> newByMount = installedByMount(checkedTarget);
        TreeSet<String> changedMounts = new TreeSet<>();
        changedMounts.addAll(oldByMount.keySet());
        changedMounts.addAll(newByMount.keySet());
        RequirementAccumulator accumulator = new RequirementAccumulator();
        List<InstalledModuleDefinition> removed = new ArrayList<>();
        List<String> affected = new ArrayList<>();
        for (String mountId : changedMounts) {
            InstalledModuleDefinition oldValue = oldByMount.get(mountId);
            InstalledModuleDefinition newValue = newByMount.get(mountId);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            affected.add(mountId);
            if (oldValue != null) {
                removed.add(oldValue);
                ModuleIndustrialProfile oldProfile = requireModuleProfile(oldValue.moduleId(), issues);
                ModuleDefinition oldModule = engineering.findModule(oldValue.moduleId());
                if (oldProfile != null && oldModule != null) {
                    accumulator.addModule(oldProfile, oldModule, false, false, true);
                }
            }
            if (newValue != null) {
                ModuleDefinition newModule = engineering.findModule(newValue.moduleId());
                ModuleIndustrialProfile newProfile = requireModuleProfile(newValue.moduleId(), issues);
                if (newProfile != null && newModule != null) {
                    accumulator.addModule(newProfile, newModule, true, true, false);
                }
            }
        }
        WorkRequirements requirements = accumulator.toRequirements(
                hull.boundingDimensionsM(), serviceMassKg(hull, checkedTarget, checkedConsumables));
        appendYardIssues(requirements, checkedYard, issues);
        return new WorkPlan(
                WorkKind.REFIT, checkedAsset, checkedSource, checkedTarget, requirements,
                new Feasibility(issues), removed, affected, completionDamage);
    }

    /**
     * Plans full restoration of current compartment and installed-module damage.
     *
     * @param assetId existing physical ship ID
     * @param fit current installed fit
     * @param consumables current physical loads
     * @param damage current local damage state
     * @param yard candidate shipyard
     * @return deterministic repair plan
     */
    public WorkPlan planRepair(
            EntityId assetId,
            InstalledFit fit,
            ConsumableState consumables,
            Snapshot damage,
            ShipyardCapability yard) {
        EntityId checkedAsset = Objects.requireNonNull(assetId, "assetId");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedConsumables = Objects.requireNonNull(consumables, "consumables");
        Snapshot checkedDamage = Objects.requireNonNull(damage, "damage");
        ShipyardCapability checkedYard = Objects.requireNonNull(yard, "yard");
        HullDefinition hull = requireHull(checkedFit.hullId());
        List<FeasibilityIssue> issues = new ArrayList<>();
        validateFit(hull, checkedFit, checkedConsumables, checkedDamage.moduleDamage(), issues);
        RequirementAccumulator accumulator = new RequirementAccumulator();
        List<String> affected = new ArrayList<>();

        HullIndustrialProfile hullProfile = industrial.findHullProfile(hull.id());
        boolean structuralRepair = false;
        for (Map.Entry<String, Double> entry : checkedDamage.compartmentIntegrityById().entrySet()) {
            double loss = 1d - entry.getValue();
            if (loss <= EPSILON) {
                continue;
            }
            affected.add("compartment:" + entry.getKey());
            structuralRepair = true;
            if (hullProfile == null) {
                issue(issues, FeasibilityCode.MISSING_HULL_PROFILE, hull.id(), "repair");
                continue;
            }
            CompartmentRepairProfile profile = hullProfile.findCompartmentRepair(entry.getKey());
            if (profile == null) {
                issue(issues, FeasibilityCode.MISSING_HULL_PROFILE, entry.getKey(), "compartment repair");
                continue;
            }
            accumulator.addInputs(profile.repairInputsAtFullLoss(), loss);
            accumulator.workSeconds += profile.repairWorkSecondsAtFullLoss() * loss;
        }
        if (structuralRepair && hullProfile != null) {
            accumulator.addHullCapabilities(hullProfile);
        }

        Map<String, InstalledModuleDefinition> installed = installedByMount(checkedFit);
        for (Map.Entry<String, Double> entry : checkedDamage.moduleDamage().moduleIntegrityByMount().entrySet()) {
            double loss = 1d - entry.getValue();
            if (loss <= EPSILON) {
                continue;
            }
            InstalledModuleDefinition assignment = installed.get(entry.getKey());
            if (assignment == null) {
                continue;
            }
            affected.add(entry.getKey());
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            ModuleIndustrialProfile profile = requireModuleProfile(assignment.moduleId(), issues);
            if (module == null || profile == null) {
                continue;
            }
            accumulator.addModuleCapabilities(profile);
            accumulator.addInputs(module.constructionInputs(), loss);
            accumulator.workSeconds += module.maintenance().maintenanceWorkSeconds()
                    * module.maintenance().repairComplexity() * loss;
        }

        WorkRequirements requirements = accumulator.toRequirements(
                hull.boundingDimensionsM(), serviceMassKg(hull, checkedFit, checkedConsumables));
        appendYardIssues(requirements, checkedYard, issues);
        Snapshot repaired = fullyRepaired(checkedDamage);
        return new WorkPlan(
                WorkKind.REPAIR, checkedAsset, checkedFit, checkedFit, requirements,
                new Feasibility(issues), List.of(), affected, repaired);
    }

    /**
     * Plans scheduled service for every fitted module whose authored interval is due.
     *
     * @param assetId existing physical ship ID
     * @param fit current installed fit
     * @param consumables current physical loads
     * @param maintenance current service-age state
     * @param yard candidate shipyard
     * @return deterministic maintenance plan
     */
    public WorkPlan planMaintenance(
            EntityId assetId,
            InstalledFit fit,
            ConsumableState consumables,
            MaintenanceState maintenance,
            ShipyardCapability yard) {
        EntityId checkedAsset = Objects.requireNonNull(assetId, "assetId");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedConsumables = Objects.requireNonNull(consumables, "consumables");
        MaintenanceState checkedMaintenance = Objects.requireNonNull(maintenance, "maintenance");
        ShipyardCapability checkedYard = Objects.requireNonNull(yard, "yard");
        HullDefinition hull = requireHull(checkedFit.hullId());
        List<FeasibilityIssue> issues = new ArrayList<>();
        validateFit(hull, checkedFit, checkedConsumables, DamageState.pristine(), issues);
        RequirementAccumulator accumulator = new RequirementAccumulator();
        List<String> affected = new ArrayList<>();
        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            if (module == null) {
                continue;
            }
            double age = checkedMaintenance.secondsSinceServiceByMount()
                    .getOrDefault(assignment.mountId(), 0d);
            if (age + EPSILON < module.maintenance().serviceIntervalSeconds()) {
                continue;
            }
            affected.add(assignment.mountId());
            ModuleIndustrialProfile profile = requireModuleProfile(module.id(), issues);
            if (profile != null) {
                accumulator.addModuleCapabilities(profile);
                accumulator.workSeconds += module.maintenance().maintenanceWorkSeconds();
            }
        }
        WorkRequirements requirements = accumulator.toRequirements(
                hull.boundingDimensionsM(), serviceMassKg(hull, checkedFit, checkedConsumables));
        appendYardIssues(requirements, checkedYard, issues);
        return new WorkPlan(
                WorkKind.MAINTENANCE, checkedAsset, checkedFit, checkedFit, requirements,
                new Feasibility(issues), List.of(), affected, null);
    }

    /**
     * Advances service age for modules that remain installed in the current fit.
     *
     * @param fit current installed fit
     * @param state current maintenance state
     * @param elapsedSeconds elapsed operating/service age seconds
     * @return updated deterministic state
     */
    public MaintenanceState advanceMaintenance(
            InstalledFit fit, MaintenanceState state, double elapsedSeconds) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        MaintenanceState checkedState = Objects.requireNonNull(state, "state");
        requireNonNegative(elapsedSeconds, "elapsedSeconds");
        TreeMap<String, Double> ages = new TreeMap<>();
        for (InstalledModuleDefinition assignment : checkedFit.installedModules()) {
            double age = checkedState.secondsSinceServiceByMount().getOrDefault(assignment.mountId(), 0d);
            ages.put(assignment.mountId(), age + elapsedSeconds);
        }
        return new MaintenanceState(ages);
    }

    /**
     * Completes one fully settled new-build plan.
     *
     * @param newAssetId newly allocated persistent entity ID
     * @param plan build plan
     * @param settlement physical input/work settlement
     * @return build completion payload for the ordinary world-entity creation boundary
     */
    public BuildCompletion completeBuild(
            EntityId newAssetId, WorkPlan plan, WorkSettlement settlement) {
        requireSettled(plan, settlement, WorkKind.BUILD);
        return new BuildCompletion(Objects.requireNonNull(newAssetId, "newAssetId"), plan.targetFit());
    }

    /**
     * Completes one fully settled refit while preserving asset identity.
     *
     * @param plan refit plan
     * @param settlement physical input/work settlement
     * @return identity-preserving refit result
     */
    public RefitCompletion completeRefit(WorkPlan plan, WorkSettlement settlement) {
        requireSettled(plan, settlement, WorkKind.REFIT);
        return new RefitCompletion(
                plan.assetId(), plan.targetFit(), Objects.requireNonNull(plan.completionDamage(), "completionDamage"),
                plan.removedModules());
    }

    /**
     * Completes one fully settled repair while preserving asset identity.
     *
     * @param plan repair plan
     * @param settlement physical input/work settlement
     * @return identity-preserving repaired damage state
     */
    public RepairCompletion completeRepair(WorkPlan plan, WorkSettlement settlement) {
        requireSettled(plan, settlement, WorkKind.REPAIR);
        return new RepairCompletion(
                plan.assetId(), Objects.requireNonNull(plan.completionDamage(), "completionDamage"));
    }

    /**
     * Completes scheduled service and resets age only for actually serviced mounts.
     *
     * @param plan maintenance plan
     * @param settlement completed work settlement
     * @param current current maintenance state
     * @return identity-preserving maintenance result
     */
    public MaintenanceCompletion completeMaintenance(
            WorkPlan plan, WorkSettlement settlement, MaintenanceState current) {
        requireSettled(plan, settlement, WorkKind.MAINTENANCE);
        MaintenanceState checked = Objects.requireNonNull(current, "current");
        TreeMap<String, Double> ages = new TreeMap<>(checked.secondsSinceServiceByMount());
        for (String mount : plan.affectedMounts()) {
            ages.put(mount, 0d);
        }
        return new MaintenanceCompletion(plan.assetId(), new MaintenanceState(ages));
    }

    private void validateFit(
            HullDefinition hull,
            InstalledFit fit,
            ConsumableState consumables,
            DamageState damage,
            List<FeasibilityIssue> issues) {
        ValidationResult result = fittingValidator.validate(hull, fit, consumables, damage);
        for (ValidationIssue validation : result.issues()) {
            if (validation.severity() == ValidationSeverity.ERROR) {
                issue(issues, FeasibilityCode.INVALID_TARGET_FIT, validation.subject(),
                        validation.code().name() + ":" + validation.detail());
            }
        }
    }

    private ModuleIndustrialProfile requireModuleProfile(
            String moduleId, List<FeasibilityIssue> issues) {
        ModuleIndustrialProfile profile = industrial.findModuleProfile(moduleId);
        if (profile == null) {
            issue(issues, FeasibilityCode.MISSING_MODULE_PROFILE, moduleId, "industrial requirements missing");
        }
        return profile;
    }

    private static void appendYardIssues(
            WorkRequirements requirements,
            ShipyardCapability yard,
            List<FeasibilityIssue> issues) {
        if (!fits(requirements.berthDimensionsM(), yard.berthDimensionsM())) {
            issue(issues, FeasibilityCode.BERTH_ENVELOPE_EXCEEDED, yard.yardId(),
                    "required=" + requirements.berthDimensionsM() + ",available=" + yard.berthDimensionsM());
        }
        if (requirements.serviceMassKg() > yard.maxServiceMassKg()) {
            issue(issues, FeasibilityCode.BERTH_MASS_EXCEEDED, yard.yardId(),
                    "requiredKg=" + requirements.serviceMassKg() + ",availableKg=" + yard.maxServiceMassKg());
        }
        for (String capability : requirements.fabricationCapabilities()) {
            if (!yard.fabricationCapabilities().contains(capability)) {
                issue(issues, FeasibilityCode.FABRICATION_CAPABILITY_MISSING, capability, yard.yardId());
            }
        }
        for (IndustrialInputRequirement input : requirements.inputs()) {
            if (!yard.handledInputContentIds().contains(input.contentId())) {
                issue(issues, FeasibilityCode.MATERIAL_HANDLING_MISSING, input.contentId(), yard.yardId());
            }
        }
        for (String tooling : requirements.toolingTags()) {
            if (!yard.toolingTags().contains(tooling)) {
                issue(issues, FeasibilityCode.TOOLING_MISSING, tooling, yard.yardId());
            }
        }
        if (requirements.precisionRequirement() > yard.precisionCapability()) {
            issue(issues, FeasibilityCode.PRECISION_CAPABILITY_INSUFFICIENT, yard.yardId(),
                    "required=" + requirements.precisionRequirement() + ",available=" + yard.precisionCapability());
        }
        if (requirements.industrialPowerW() > yard.industrialPowerW()) {
            issue(issues, FeasibilityCode.INDUSTRIAL_POWER_INSUFFICIENT, yard.yardId(),
                    "requiredW=" + requirements.industrialPowerW() + ",availableW=" + yard.industrialPowerW());
        }
        if (requirements.laborRequirement() > yard.laborCapacity()) {
            issue(issues, FeasibilityCode.LABOR_CAPACITY_INSUFFICIENT, yard.yardId(),
                    "required=" + requirements.laborRequirement() + ",available=" + yard.laborCapacity());
        }
        if (requirements.automationRequirement() > yard.automationCapacity()) {
            issue(issues, FeasibilityCode.AUTOMATION_CAPACITY_INSUFFICIENT, yard.yardId(),
                    "required=" + requirements.automationRequirement() + ",available=" + yard.automationCapacity());
        }
    }

    private static void requireSettled(
            WorkPlan plan, WorkSettlement settlement, WorkKind expectedKind) {
        WorkPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        WorkSettlement checkedSettlement = Objects.requireNonNull(settlement, "settlement");
        if (checkedPlan.kind() != expectedKind) {
            throw new IllegalArgumentException("Expected " + expectedKind + " plan, got " + checkedPlan.kind());
        }
        if (!checkedPlan.feasibility().feasible()) {
            throw new IllegalStateException("Cannot complete infeasible shipyard work");
        }
        if (checkedSettlement.completedWorkSeconds() + EPSILON < checkedPlan.requirements().totalWorkSeconds()) {
            throw new IllegalStateException("Shipyard work is not complete");
        }
        for (IndustrialInputRequirement required : checkedPlan.requirements().inputs()) {
            double delivered = checkedSettlement.deliveredInputs().getOrDefault(required.contentId(), 0d);
            if (delivered + EPSILON < required.amount()) {
                throw new IllegalStateException("Missing physical shipyard input: " + required.contentId());
            }
        }
    }

    private Snapshot reconcileRefitDamage(
            InstalledFit sourceFit, InstalledFit targetFit, Snapshot damage) {
        Map<String, InstalledModuleDefinition> oldByMount = installedByMount(sourceFit);
        Map<String, InstalledModuleDefinition> newByMount = installedByMount(targetFit);
        TreeMap<String, Double> retainedIntegrity = new TreeMap<>();
        for (Map.Entry<String, InstalledModuleDefinition> entry : newByMount.entrySet()) {
            InstalledModuleDefinition oldValue = oldByMount.get(entry.getKey());
            if (oldValue != null && oldValue.moduleId().equals(entry.getValue().moduleId())) {
                Double integrity = damage.moduleDamage().moduleIntegrityByMount().get(entry.getKey());
                if (integrity != null && integrity < 1d) {
                    retainedIntegrity.put(entry.getKey(), integrity);
                }
            }
        }
        return new Snapshot(damage.compartmentIntegrityById(), new DamageState(retainedIntegrity));
    }

    private static Snapshot fullyRepaired(Snapshot damage) {
        TreeMap<String, Double> compartments = new TreeMap<>();
        for (String compartmentId : damage.compartmentIntegrityById().keySet()) {
            compartments.put(compartmentId, 1d);
        }
        return new Snapshot(compartments, DamageState.pristine());
    }

    private double serviceMassKg(
            HullDefinition hull, InstalledFit fit, ConsumableState consumables) {
        double mass = hull.bareHullMassKg() + consumables.totalCarriedMassKg();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            if (module != null) {
                mass += module.massKg();
            }
        }
        return mass;
    }

    private HullDefinition requireHull(String hullId) {
        HullDefinition hull = engineering.findHull(hullId);
        if (hull == null) {
            throw new IllegalArgumentException("Unknown shipyard hull: " + hullId);
        }
        return hull;
    }

    private static Map<String, InstalledModuleDefinition> installedByMount(InstalledFit fit) {
        Map<String, InstalledModuleDefinition> result = new HashMap<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            result.put(assignment.mountId(), assignment);
        }
        return result;
    }

    private static List<String> mountIds(InstalledFit fit) {
        List<String> result = new ArrayList<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            result.add(assignment.mountId());
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private static boolean fits(Dimensions3d required, Dimensions3d available) {
        return required.lengthM() <= available.lengthM()
                && required.widthM() <= available.widthM()
                && required.heightM() <= available.heightM();
    }

    private static void issue(
            List<FeasibilityIssue> issues, FeasibilityCode code, String subject, String detail) {
        issues.add(new FeasibilityIssue(code, subject, detail));
    }

    private static Set<String> immutableSet(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        TreeSet<String> result = new TreeSet<>();
        for (String value : values) {
            requireNonBlank(value, field + " entry");
            result.add(value);
        }
        return Collections.unmodifiableSet(result);
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }

    private static void requireUnitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }

    private final class RequirementAccumulator {
        private final TreeMap<String, Double> inputs = new TreeMap<>();
        private final TreeSet<String> fabrication = new TreeSet<>();
        private final TreeSet<String> tooling = new TreeSet<>();
        private double precision;
        private double powerW;
        private int labor;
        private int automation;
        private double workSeconds;

        void addHull(HullIndustrialProfile profile, boolean includeConstruction) {
            addHullCapabilities(profile);
            if (includeConstruction) {
                addInputs(profile.constructionInputs(), 1d);
                workSeconds += profile.assemblyWorkSeconds();
            }
        }

        void addHullCapabilities(HullIndustrialProfile profile) {
            fabrication.addAll(profile.fabricationCapabilities());
            tooling.addAll(profile.toolingTags());
            precision = Math.max(precision, profile.precisionRequirement());
            powerW = Math.max(powerW, profile.industrialPowerW());
            labor = Math.max(labor, profile.laborRequirement());
            automation = Math.max(automation, profile.automationRequirement());
        }

        void addModule(
                ModuleIndustrialProfile profile,
                ModuleDefinition module,
                boolean includeManufacturing,
                boolean includeInstallation,
                boolean includeRemoval) {
            addModuleCapabilities(profile);
            if (includeManufacturing) {
                addInputs(module.constructionInputs(), 1d);
                workSeconds += profile.manufacturingWorkSeconds();
            }
            if (includeInstallation) {
                workSeconds += profile.installationWorkSeconds();
            }
            if (includeRemoval) {
                workSeconds += profile.removalWorkSeconds();
            }
        }

        void addModuleCapabilities(ModuleIndustrialProfile profile) {
            fabrication.addAll(profile.fabricationCapabilities());
            tooling.addAll(profile.toolingTags());
            precision = Math.max(precision, profile.precisionRequirement());
            powerW = Math.max(powerW, profile.industrialPowerW());
            labor = Math.max(labor, profile.laborRequirement());
            automation = Math.max(automation, profile.automationRequirement());
        }

        void addInputs(List<ConstructionInputDefinition> values, double scale) {
            requireNonNegative(scale, "input scale");
            if (scale <= EPSILON) {
                return;
            }
            for (ConstructionInputDefinition input : values) {
                inputs.merge(input.contentId(), input.amount() * scale, Double::sum);
            }
        }

        WorkRequirements toRequirements(Dimensions3d dimensions, double massKg) {
            List<IndustrialInputRequirement> inputRequirements = new ArrayList<>();
            for (Map.Entry<String, Double> entry : inputs.entrySet()) {
                if (entry.getValue() > EPSILON) {
                    inputRequirements.add(new IndustrialInputRequirement(entry.getKey(), entry.getValue()));
                }
            }
            return new WorkRequirements(
                    inputRequirements, fabrication, tooling, precision, powerW, labor, automation,
                    workSeconds, dimensions, massKg);
        }
    }
}

package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalog.CompartmentRepairProfile;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.Status;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.IndustrialInputRequirement;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkKind;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-18G physical shipyard integration over Stage-17.5G engineering plans.
 *
 * <p>The runtime gives the existing shipyard planner a capability only when an explicit installed
 * yard is operational at a real Stage-18F station and all authored Stage-18E support facilities are
 * installed and active. Settlement consumes kilograms and finished Stage-18D module products from
 * canonical station storage before generating the compatibility {@link WorkSettlement} accepted by
 * Stage-17.5G completion.</p>
 *
 * <p>Provisional Stage-17.5G component-unit amounts are never interpreted as kilograms. They survive
 * only as planner tokens inside the generated compatibility settlement after real Stage-18 physical
 * requirements have already been consumed atomically.</p>
 */
public final class Stage18ShipyardRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ShipyardCatalog catalog;
    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;

    /**
     * Creates the physical Stage-18G shipyard runtime.
     *
     * @param catalog authoritative Stage-18G shipyard catalog
     * @param ontology authoritative Stage-18 resource ontology
     * @param products authoritative Stage-18D manufactured-product registry
     */
    public Stage18ShipyardRuntime(
            Stage18ShipyardCatalog catalog,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
    }

    /** Stable installed-yard projection status. */
    public enum YardStatus {
        /** Yard and all required support infrastructure are physically operational. */ ACTIVE,
        /** Yard instance is disabled. */ DISABLED,
        /** Yard physical condition has reached zero. */ CONDITION_ZERO,
        /** Yard definition ID is unknown. */ DEFINITION_NOT_FOUND,
        /** Station location cannot host this yard design. */ LOCATION_INCOMPATIBLE,
        /** A required Stage-18E support facility is absent or inactive. */ SUPPORT_FACILITY_MISSING,
        /** Power or engineering work supply is zero after physical limits. */ RESOURCE_STARVED
    }

    /** Stable Stage-18G work settlement status. */
    public enum SettlementStatus {
        /** Physical inputs and finite yard work were consumed atomically. */ SETTLED,
        /** Arguments or requested work kind are invalid. */ INVALID_REQUEST,
        /** Stage-17.5G planner rejected the work before physical settlement. */ PLAN_INFEASIBLE,
        /** Installed yard is not currently operational. */ YARD_INACTIVE,
        /** Stage-18G has no physical profile for the required hull/module. */ PROFILE_NOT_FOUND,
        /** Finite yard work budget cannot complete the plan. */ INSUFFICIENT_WORK,
        /** Canonical station storage lacks required Stage-18 commodity mass. */ INSUFFICIENT_COMMODITY,
        /** Canonical station storage lacks a required finished Stage-18D module. */ INSUFFICIENT_PRODUCT,
        /** Yard cannot physically exchange with a required storage class. */ STORAGE_CLASS_INCOMPATIBLE,
        /** A finished module exceeds the yard's single-unit handling envelope. */ UNIT_HANDLING_LIMIT
    }

    /**
     * World-owned state for one installed shipyard instance.
     *
     * @param yardInstanceId stable installed-yard identity
     * @param yardDefinitionId Stage-18G yard definition ID
     * @param conditionFraction physical condition in {@code [0,1]}
     * @param allocatedIntegrationPowerW power allocated to ship construction/service integration
     * @param availableIntegrationWorkRate engineering work-seconds available per simulation second
     * @param availableLaborCapacity currently available staffed labor capacity
     * @param availableAutomationCapacity currently available automation capacity
     * @param enabled whether the yard is enabled
     */
    public record InstalledYardState(
            String yardInstanceId,
            String yardDefinitionId,
            double conditionFraction,
            double allocatedIntegrationPowerW,
            double availableIntegrationWorkRate,
            int availableLaborCapacity,
            int availableAutomationCapacity,
            boolean enabled) {
        /**
         * Validates one installed-yard state.
         *
         * @param yardInstanceId installed-yard identity
         * @param yardDefinitionId yard definition ID
         * @param conditionFraction physical condition
         * @param allocatedIntegrationPowerW allocated power
         * @param availableIntegrationWorkRate available engineering work rate
         * @param availableLaborCapacity staffed labor capacity
         * @param availableAutomationCapacity automation capacity
         * @param enabled enabled state
         */
        public InstalledYardState {
            yardInstanceId = requireText(yardInstanceId, "yardInstanceId");
            yardDefinitionId = requireText(yardDefinitionId, "yardDefinitionId");
            requireFraction(conditionFraction, "conditionFraction");
            requireNonNegative(allocatedIntegrationPowerW, "allocatedIntegrationPowerW");
            requireNonNegative(availableIntegrationWorkRate, "availableIntegrationWorkRate");
            if (availableLaborCapacity < 0 || availableAutomationCapacity < 0) {
                throw new IllegalArgumentException("yard labor/automation availability must be non-negative");
            }
        }
    }

    /**
     * Effective installed shipyard projection.
     *
     * @param yardInstanceId installed yard identity
     * @param yardDefinitionId yard definition ID
     * @param status operational status
     * @param plannerCapability Stage-17.5G-compatible capability; {@code null} while inactive
     * @param handledStorageClassIds physical Stage-18 storage interfaces exposed while active
     * @param maxHandledUnitMassKg maximum handled finished-module unit mass
     */
    public record YardCapabilitySnapshot(
            String yardInstanceId,
            String yardDefinitionId,
            YardStatus status,
            ShipyardCapability plannerCapability,
            Set<String> handledStorageClassIds,
            double maxHandledUnitMassKg) {
        /**
         * Validates and freezes one yard projection.
         *
         * @param yardInstanceId yard instance identity
         * @param yardDefinitionId yard definition ID
         * @param status yard operational status
         * @param plannerCapability planner capability or {@code null}
         * @param handledStorageClassIds handled storage classes
         * @param maxHandledUnitMassKg maximum handled unit mass
         */
        public YardCapabilitySnapshot {
            yardInstanceId = requireText(yardInstanceId, "yardInstanceId");
            yardDefinitionId = requireText(yardDefinitionId, "yardDefinitionId");
            Objects.requireNonNull(status, "status");
            handledStorageClassIds = immutableSet(handledStorageClassIds);
            requireNonNegative(maxHandledUnitMassKg, "maxHandledUnitMassKg");
            if (status == YardStatus.ACTIVE && plannerCapability == null) {
                throw new IllegalArgumentException("active yard projection requires plannerCapability");
            }
            if (status != YardStatus.ACTIVE && plannerCapability != null) {
                throw new IllegalArgumentException("inactive yard projection must not grant plannerCapability");
            }
        }

        /** @return whether the installed yard can execute physical shipyard work */
        public boolean active() {
            return status == YardStatus.ACTIVE;
        }

        /**
         * Opens one finite engineering-work interval.
         *
         * @param durationSeconds positive simulation duration
         * @return mutable shared yard work budget
         */
        public YardWorkBudget openInterval(double durationSeconds) {
            requirePositive(durationSeconds, "durationSeconds");
            if (!active()) {
                throw new IllegalStateException("Cannot open work interval for inactive yard");
            }
            double work = finiteProduct(
                    plannerCapability.workRate(), durationSeconds, "yard interval engineering work");
            return new YardWorkBudget(durationSeconds, work);
        }
    }

    /** Shared finite shipyard engineering work for one simulation interval. */
    public static final class YardWorkBudget {
        private final double durationSeconds;
        private double remainingWorkSeconds;

        private YardWorkBudget(double durationSeconds, double remainingWorkSeconds) {
            this.durationSeconds = durationSeconds;
            this.remainingWorkSeconds = remainingWorkSeconds;
        }

        /** @return represented simulation duration */
        public double durationSeconds() {
            return durationSeconds;
        }

        /** @return remaining Stage-17.5G engineering work-seconds */
        public double remainingWorkSeconds() {
            return remainingWorkSeconds;
        }

        private void consume(double workSeconds) {
            remainingWorkSeconds -= workSeconds;
            if (remainingWorkSeconds <= EPSILON) {
                remainingWorkSeconds = 0d;
            }
        }
    }

    /**
     * Immutable result of one physical Stage-18G settlement.
     *
     * @param status stable settlement status
     * @param subject failing or completed subject ID
     * @param compatibilitySettlement Stage-17.5G completion proof generated only after physical settlement
     * @param consumedCommodityMassKg actual Stage-18 commodity mass consumed
     * @param consumedProductCount actual finished Stage-18D module count consumed
     * @param releasedProductCount physical modules removed by refit; condition remains in refit continuity output
     */
    public record SettlementResult(
            SettlementStatus status,
            String subject,
            WorkSettlement compatibilitySettlement,
            Map<String, Double> consumedCommodityMassKg,
            Map<String, Integer> consumedProductCount,
            Map<String, Integer> releasedProductCount) {
        /**
         * Freezes one settlement result.
         *
         * @param status stable status
         * @param subject diagnostic subject
         * @param compatibilitySettlement Stage-17.5G proof or {@code null}
         * @param consumedCommodityMassKg consumed commodity mass
         * @param consumedProductCount consumed finished products
         * @param releasedProductCount released refit products
         */
        public SettlementResult {
            Objects.requireNonNull(status, "status");
            subject = subject == null ? "" : subject;
            consumedCommodityMassKg = immutableDoubleMap(consumedCommodityMassKg);
            consumedProductCount = immutableIntegerMap(consumedProductCount);
            releasedProductCount = immutableIntegerMap(releasedProductCount);
            if (status == SettlementStatus.SETTLED && compatibilitySettlement == null) {
                throw new IllegalArgumentException("settled shipyard work requires compatibility settlement");
            }
            if (status != SettlementStatus.SETTLED && compatibilitySettlement != null) {
                throw new IllegalArgumentException("rejected shipyard work must not contain compatibility settlement");
            }
        }

        /** @return whether canonical storage and finite yard work were committed */
        public boolean settled() {
            return status == SettlementStatus.SETTLED;
        }
    }

    /**
     * Projects an installed yard at one Stage-18F station.
     *
     * @param state current installed yard state
     * @param station owning Stage-18F station node
     * @param supportFacilitySnapshots current Stage-18E snapshots for station facilities
     * @return effective yard projection; no capability is granted when prerequisites are absent
     */
    public YardCapabilitySnapshot projectYard(
            InstalledYardState state,
            Stage18StationIndustrialNode station,
            List<FacilityCapabilitySnapshot> supportFacilitySnapshots) {
        Objects.requireNonNull(state, "state");
        Stage18StationIndustrialNode checkedStation = Objects.requireNonNull(station, "station");
        Objects.requireNonNull(supportFacilitySnapshots, "supportFacilitySnapshots");
        YardDefinition definition = catalog.findYard(state.yardDefinitionId());
        if (definition == null) {
            return inactive(state, YardStatus.DEFINITION_NOT_FOUND);
        }
        if (!state.enabled()) {
            return inactive(state, YardStatus.DISABLED);
        }
        if (state.conditionFraction() <= EPSILON) {
            return inactive(state, YardStatus.CONDITION_ZERO);
        }
        if (!definition.allowedLocationTags().contains(checkedStation.locationTag())) {
            return inactive(state, YardStatus.LOCATION_INCOMPATIBLE);
        }

        Map<String, FacilityCapabilitySnapshot> snapshotByInstance = new HashMap<>();
        for (FacilityCapabilitySnapshot snapshot : supportFacilitySnapshots) {
            FacilityCapabilitySnapshot checked = Objects.requireNonNull(snapshot, "support facility snapshot");
            snapshotByInstance.put(checked.facilityInstanceId(), checked);
        }
        double supportWorkRate = 0d;
        for (String requiredDefinition : definition.requiredSupportFacilityDefinitionIds()) {
            FacilityCapabilitySnapshot match = null;
            for (Stage18StationIndustrialNode.InstalledFacilityReference reference : checkedStation.installedFacilities()) {
                if (!reference.facilityDefinitionId().equals(requiredDefinition)) {
                    continue;
                }
                FacilityCapabilitySnapshot candidate = snapshotByInstance.get(reference.facilityInstanceId());
                if (candidate != null
                        && candidate.definitionId().equals(requiredDefinition)
                        && candidate.status() == Status.ACTIVE) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                return inactive(state, YardStatus.SUPPORT_FACILITY_MISSING);
            }
            supportWorkRate += match.effectiveEngineeringWorkRate();
        }

        double powerW = Math.min(
                definition.ratedIntegrationPowerW() * state.conditionFraction(),
                state.allocatedIntegrationPowerW());
        double workRate = Math.min(
                definition.ratedEngineeringWorkRate() * state.conditionFraction(),
                Math.min(state.availableIntegrationWorkRate(), supportWorkRate));
        if (powerW <= EPSILON || workRate <= EPSILON) {
            return inactive(state, YardStatus.RESOURCE_STARVED);
        }
        int labor = Math.min(definition.laborCapacity(), state.availableLaborCapacity());
        int automation = Math.min(definition.automationCapacity(), state.availableAutomationCapacity());
        ShipyardCapability planner = new ShipyardCapability(
                state.yardInstanceId(),
                definition.berthDimensionsM(),
                definition.maxServiceMassKg() * state.conditionFraction(),
                definition.stage175FabricationCapabilities(),
                definition.stage175HandledRequirementIds(),
                definition.toolingTags(),
                definition.precisionCapability() * state.conditionFraction(),
                workRate,
                labor,
                automation,
                powerW);
        return new YardCapabilitySnapshot(
                state.yardInstanceId(),
                definition.id(),
                YardStatus.ACTIVE,
                planner,
                definition.handledStorageClassIds(),
                definition.maxHandledUnitMassKg() * state.conditionFraction());
    }

    /**
     * Settles physical bare-hull materials plus one finished Stage-18D module per fitted mount.
     *
     * @param plan feasible BUILD plan created with this yard projection
     * @param storage canonical owning station storage
     * @param yard active installed yard projection
     * @param budget finite shared yard work budget
     * @return atomic Stage-18G build settlement
     */
    public SettlementResult settleBuild(
            WorkPlan plan,
            Stage18StationStorage storage,
            YardCapabilitySnapshot yard,
            YardWorkBudget budget) {
        if (!validKind(plan, WorkKind.BUILD)) {
            return rejected(SettlementStatus.INVALID_REQUEST, "BUILD");
        }
        RequirementSet requirements = new RequirementSet();
        HullPhysicalProfile hull = catalog.findHullProfile(plan.targetFit().hullId());
        if (hull == null) {
            return rejected(SettlementStatus.PROFILE_NOT_FOUND, plan.targetFit().hullId());
        }
        requirements.addInputs(hull.buildInputsKg(), 1d);
        for (var assignment : plan.targetFit().installedModules()) {
            if (catalog.findModuleProfile(assignment.moduleId()) == null) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, assignment.moduleId());
            }
            requirements.addProduct(assignment.moduleId(), 1);
        }
        return settle(plan, storage, yard, budget, requirements);
    }

    /**
     * Settles physical finished modules newly introduced by one identity-preserving refit.
     *
     * <p>Removed modules are reported but are not collapsed into undamaged station product counts;
     * {@code ShipyardRefitContinuity} retains their integrity/service age for the caller.</p>
     *
     * @param plan feasible REFIT plan
     * @param storage canonical owning station storage
     * @param yard active installed yard projection
     * @param budget finite shared yard work budget
     * @return atomic Stage-18G refit settlement
     */
    public SettlementResult settleRefit(
            WorkPlan plan,
            Stage18StationStorage storage,
            YardCapabilitySnapshot yard,
            YardWorkBudget budget) {
        if (!validKind(plan, WorkKind.REFIT) || plan.sourceFit() == null) {
            return rejected(SettlementStatus.INVALID_REQUEST, "REFIT");
        }
        RequirementSet requirements = new RequirementSet();
        Map<String, String> before = modulesByMount(plan.sourceFit());
        Map<String, String> after = modulesByMount(plan.targetFit());
        TreeSet<String> mounts = new TreeSet<>();
        mounts.addAll(before.keySet());
        mounts.addAll(after.keySet());
        for (String mount : mounts) {
            String oldModule = before.get(mount);
            String newModule = after.get(mount);
            if (Objects.equals(oldModule, newModule)) {
                continue;
            }
            if (newModule != null) {
                if (catalog.findModuleProfile(newModule) == null) {
                    return rejected(SettlementStatus.PROFILE_NOT_FOUND, newModule);
                }
                requirements.addProduct(newModule, 1);
            }
            if (oldModule != null) {
                requirements.releaseProduct(oldModule, 1);
            }
        }
        return settle(plan, storage, yard, budget, requirements);
    }

    /**
     * Settles damage-scaled Stage-18 materials/components for one repair plan.
     *
     * @param plan feasible REPAIR plan
     * @param sourceDamage physical damage snapshot used when the plan was authored
     * @param storage canonical owning station storage
     * @param yard active installed yard projection
     * @param budget finite shared yard work budget
     * @return atomic Stage-18G repair settlement
     */
    public SettlementResult settleRepair(
            WorkPlan plan,
            Snapshot sourceDamage,
            Stage18StationStorage storage,
            YardCapabilitySnapshot yard,
            YardWorkBudget budget) {
        if (!validKind(plan, WorkKind.REPAIR) || plan.sourceFit() == null || sourceDamage == null) {
            return rejected(SettlementStatus.INVALID_REQUEST, "REPAIR");
        }
        RequirementSet requirements = new RequirementSet();
        HullPhysicalProfile hull = catalog.findHullProfile(plan.sourceFit().hullId());
        if (hull == null) {
            return rejected(SettlementStatus.PROFILE_NOT_FOUND, plan.sourceFit().hullId());
        }
        for (Map.Entry<String, Double> entry : sourceDamage.compartmentIntegrityById().entrySet()) {
            double loss = clamp01(1d - entry.getValue());
            if (loss <= EPSILON) {
                continue;
            }
            CompartmentRepairProfile profile = hull.findCompartmentRepair(entry.getKey());
            if (profile == null) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, entry.getKey());
            }
            requirements.addInputs(profile.inputsAtFullLossKg(), loss);
        }
        Map<String, String> installed = modulesByMount(plan.sourceFit());
        for (Map.Entry<String, Double> entry : sourceDamage.moduleDamage().moduleIntegrityByMount().entrySet()) {
            double loss = clamp01(1d - entry.getValue());
            if (loss <= EPSILON) {
                continue;
            }
            String moduleId = installed.get(entry.getKey());
            if (moduleId == null) {
                continue;
            }
            ModuleServiceProfile profile = catalog.findModuleProfile(moduleId);
            if (profile == null) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, moduleId);
            }
            requirements.addInputs(profile.repairInputsAtFullLossKg(), loss);
        }
        return settle(plan, storage, yard, budget, requirements);
    }

    /**
     * Settles physical spares/consumables for every due mount in one maintenance plan.
     *
     * @param plan feasible MAINTENANCE plan
     * @param storage canonical owning station storage
     * @param yard active installed yard projection
     * @param budget finite shared yard work budget
     * @return atomic Stage-18G maintenance settlement
     */
    public SettlementResult settleMaintenance(
            WorkPlan plan,
            Stage18StationStorage storage,
            YardCapabilitySnapshot yard,
            YardWorkBudget budget) {
        if (!validKind(plan, WorkKind.MAINTENANCE) || plan.sourceFit() == null) {
            return rejected(SettlementStatus.INVALID_REQUEST, "MAINTENANCE");
        }
        RequirementSet requirements = new RequirementSet();
        Map<String, String> installed = modulesByMount(plan.sourceFit());
        for (String mount : plan.affectedMounts()) {
            String moduleId = installed.get(mount);
            if (moduleId == null) {
                continue;
            }
            ModuleServiceProfile profile = catalog.findModuleProfile(moduleId);
            if (profile == null) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, moduleId);
            }
            requirements.addInputs(profile.maintenanceInputsKg(), 1d);
        }
        return settle(plan, storage, yard, budget, requirements);
    }

    private SettlementResult settle(
            WorkPlan plan,
            Stage18StationStorage storage,
            YardCapabilitySnapshot yard,
            YardWorkBudget budget,
            RequirementSet requirements) {
        if (storage == null || yard == null || budget == null) {
            return rejected(SettlementStatus.INVALID_REQUEST, "shipyard settlement");
        }
        if (!plan.feasibility().feasible()) {
            return rejected(SettlementStatus.PLAN_INFEASIBLE, plan.kind().name());
        }
        if (!yard.active()) {
            return rejected(SettlementStatus.YARD_INACTIVE, yard.yardDefinitionId());
        }
        double work = plan.requirements().totalWorkSeconds();
        if (budget.remainingWorkSeconds() + EPSILON < work) {
            return rejected(SettlementStatus.INSUFFICIENT_WORK, plan.kind().name());
        }

        for (Map.Entry<String, Double> entry : requirements.commodityMassKg.entrySet()) {
            CommodityDefinition commodity = ontology.findCommodity(entry.getKey());
            if (commodity == null) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, entry.getKey());
            }
            if (!yard.handledStorageClassIds().contains(commodity.storageClassId())) {
                return rejected(SettlementStatus.STORAGE_CLASS_INCOMPATIBLE, entry.getKey());
            }
            if (storage.commodityMassKg(entry.getKey()) + EPSILON < entry.getValue()) {
                return rejected(SettlementStatus.INSUFFICIENT_COMMODITY, entry.getKey());
            }
        }
        for (Map.Entry<String, Integer> entry : requirements.productCount.entrySet()) {
            ProductDefinition product = products.findProduct(entry.getKey());
            if (product == null || product.kind() != ProductKind.MODULE) {
                return rejected(SettlementStatus.PROFILE_NOT_FOUND, entry.getKey());
            }
            if (!yard.handledStorageClassIds().contains(product.storageClassId())) {
                return rejected(SettlementStatus.STORAGE_CLASS_INCOMPATIBLE, entry.getKey());
            }
            if (product.unitMassKg() > yard.maxHandledUnitMassKg() + EPSILON) {
                return rejected(SettlementStatus.UNIT_HANDLING_LIMIT, entry.getKey());
            }
            if (storage.productCount(entry.getKey()) < entry.getValue()) {
                return rejected(SettlementStatus.INSUFFICIENT_PRODUCT, entry.getKey());
            }
        }

        for (Map.Entry<String, Double> entry : requirements.commodityMassKg.entrySet()) {
            storage.removeCommodity(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : requirements.productCount.entrySet()) {
            storage.removeProduct(entry.getKey(), entry.getValue());
        }
        budget.consume(work);
        WorkSettlement compatibility = compatibilitySettlement(plan);
        return new SettlementResult(
                SettlementStatus.SETTLED,
                plan.kind().name(),
                compatibility,
                requirements.commodityMassKg,
                requirements.productCount,
                requirements.releasedProductCount);
    }

    private static WorkSettlement compatibilitySettlement(WorkPlan plan) {
        TreeMap<String, Double> delivered = new TreeMap<>();
        for (IndustrialInputRequirement input : plan.requirements().inputs()) {
            delivered.put(input.contentId(), input.amount());
        }
        return new WorkSettlement(delivered, plan.requirements().totalWorkSeconds());
    }

    private static boolean validKind(WorkPlan plan, WorkKind expected) {
        return plan != null && plan.kind() == expected;
    }

    private static Map<String, String> modulesByMount(InstalledFit fit) {
        TreeMap<String, String> result = new TreeMap<>();
        fit.installedModules().forEach(value -> result.put(value.mountId(), value.moduleId()));
        return result;
    }

    private static YardCapabilitySnapshot inactive(InstalledYardState state, YardStatus status) {
        return new YardCapabilitySnapshot(
                state.yardInstanceId(),
                state.yardDefinitionId(),
                status,
                null,
                Set.of(),
                0d);
    }

    private static SettlementResult rejected(SettlementStatus status, String subject) {
        return new SettlementResult(status, subject, null, Map.of(), Map.of(), Map.of());
    }

    private static final class RequirementSet {
        private final TreeMap<String, Double> commodityMassKg = new TreeMap<>();
        private final TreeMap<String, Integer> productCount = new TreeMap<>();
        private final TreeMap<String, Integer> releasedProductCount = new TreeMap<>();

        private void addInputs(List<PhysicalInputDefinition> inputs, double scale) {
            requireNonNegative(scale, "input scale");
            if (scale <= EPSILON) {
                return;
            }
            for (PhysicalInputDefinition input : inputs) {
                double mass = finiteProduct(input.massKg(), scale, "scaled shipyard input");
                commodityMassKg.merge(input.commodityId(), mass, Double::sum);
            }
        }

        private void addProduct(String productId, int count) {
            productCount.merge(requireText(productId, "productId"), count, Math::addExact);
        }

        private void releaseProduct(String productId, int count) {
            releasedProductCount.merge(requireText(productId, "productId"), count, Math::addExact);
        }
    }

    private static Set<String> immutableSet(Set<String> source) {
        Objects.requireNonNull(source, "source");
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, "set entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, Double> copy = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            requireText(entry.getKey(), "map key");
            double value = Objects.requireNonNull(entry.getValue(), "map value");
            requireNonNegative(value, "map value");
            if (value > EPSILON) {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableIntegerMap(Map<String, Integer> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            requireText(entry.getKey(), "map key");
            int value = Objects.requireNonNull(entry.getValue(), "map value");
            if (value < 0) {
                throw new IllegalArgumentException("map value must be non-negative");
            }
            if (value > 0) {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }

    private static double finiteProduct(double left, double right, String name) {
        double result = left * right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return result;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}

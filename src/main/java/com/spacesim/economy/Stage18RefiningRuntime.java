package com.spacesim.economy;

import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RecipeInputDefinition;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-18C deterministic settlement boundary for feedstock refining and material production.
 *
 * <p>One accepted operation atomically consumes physical input mass, process energy, engineering
 * work and maintenance work, then stores the retained output mass. Tailings/byproducts are
 * explicitly reported as discarded mass so the baseline never creates matter. Stage 18F owns the
 * later shared station/storage/logistics layer; this slice therefore uses a small physical material
 * store that can be initialized from a Stage-18B extraction cargo snapshot without changing legacy
 * cargo/save semantics.</p>
 */
public final class Stage18RefiningRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18RefiningCatalog refiningCatalog;

    /**
     * Creates the physical refining settlement boundary.
     *
     * @param ontology authoritative Stage-18 resource ontology
     * @param refiningCatalog authoritative Stage-18C refining recipe catalog
     */
    public Stage18RefiningRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18RefiningCatalog refiningCatalog) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.refiningCatalog = Objects.requireNonNull(refiningCatalog, "refiningCatalog");
    }

    /** Stable outcome of one requested refining settlement. */
    public enum Status {
        /** Input mass and process budgets were atomically converted into retained output. */
        REFINED,
        /** Request amount or runtime state is invalid. */
        INVALID_REQUEST,
        /** Stable recipe ID is unknown. */
        RECIPE_NOT_FOUND,
        /** Refining unit lacks one or more required physical process capabilities. */
        MISSING_CAPABILITY,
        /** One or more required feedstocks are not physically available. */
        INSUFFICIENT_INPUT,
        /** Interval has insufficient electrical/process energy. */
        INSUFFICIENT_POWER,
        /** Interval has insufficient engineering work-seconds. */
        INSUFFICIENT_WORK,
        /** Interval has insufficient maintenance service-work. */
        INSUFFICIENT_MAINTENANCE,
        /** Compatible physical storage cannot accept the retained output. */
        STORAGE_FULL
    }

    /**
     * Physical process capability projected into one refining interval.
     *
     * @param capabilityId stable refining-unit/facility capability ID
     * @param capabilityTags installed process capabilities
     * @param availablePowerW power available to refining during an interval
     * @param workRate engineering work-seconds completed per simulation second
     * @param maintenanceWorkRate maintenance work-seconds available per simulation second
     */
    public record RefiningCapability(
            String capabilityId,
            Set<String> capabilityTags,
            double availablePowerW,
            double workRate,
            double maintenanceWorkRate) {
        /**
         * Validates and freezes one refining capability projection.
         *
         * @param capabilityId stable refining-unit/facility capability ID
         * @param capabilityTags installed process capabilities
         * @param availablePowerW available process power
         * @param workRate engineering work rate
         * @param maintenanceWorkRate maintenance service-work rate
         */
        public RefiningCapability {
            capabilityId = requireText(capabilityId, "capabilityId");
            capabilityTags = immutableTags(capabilityTags, "capabilityTags");
            requireNonNegative(availablePowerW, "availablePowerW");
            requireNonNegative(workRate, "workRate");
            requireNonNegative(maintenanceWorkRate, "maintenanceWorkRate");
        }

        /**
         * Opens a shared finite process budget for one simulation interval.
         *
         * @param durationSeconds finite positive simulation interval
         * @return mutable interval budget owned by the caller
         */
        public IntervalBudget openInterval(double durationSeconds) {
            requirePositive(durationSeconds, "durationSeconds");
            return new IntervalBudget(
                    capabilityTags,
                    durationSeconds,
                    finiteProduct(availablePowerW, durationSeconds, "interval energy"),
                    finiteProduct(workRate, durationSeconds, "interval work"),
                    finiteProduct(maintenanceWorkRate, durationSeconds, "interval maintenance work"));
        }
    }

    /** Shared finite same-interval process budget. */
    public static final class IntervalBudget {
        private final Set<String> capabilityTags;
        private final double durationSeconds;
        private double remainingEnergyJ;
        private double remainingWorkSeconds;
        private double remainingMaintenanceWorkSeconds;

        private IntervalBudget(
                Set<String> capabilityTags,
                double durationSeconds,
                double remainingEnergyJ,
                double remainingWorkSeconds,
                double remainingMaintenanceWorkSeconds) {
            this.capabilityTags = capabilityTags;
            this.durationSeconds = durationSeconds;
            this.remainingEnergyJ = remainingEnergyJ;
            this.remainingWorkSeconds = remainingWorkSeconds;
            this.remainingMaintenanceWorkSeconds = remainingMaintenanceWorkSeconds;
        }

        /** @return immutable process capability tags available to this interval */
        public Set<String> capabilityTags() {
            return capabilityTags;
        }

        /** @return simulation duration represented by this shared budget */
        public double durationSeconds() {
            return durationSeconds;
        }

        /** @return uncommitted electrical/process energy in joules */
        public double remainingEnergyJ() {
            return remainingEnergyJ;
        }

        /** @return uncommitted engineering work-seconds */
        public double remainingWorkSeconds() {
            return remainingWorkSeconds;
        }

        /** @return uncommitted maintenance work-seconds */
        public double remainingMaintenanceWorkSeconds() {
            return remainingMaintenanceWorkSeconds;
        }

        private void consume(double energyJ, double workSeconds, double maintenanceWorkSeconds) {
            remainingEnergyJ = clampZero(remainingEnergyJ - energyJ);
            remainingWorkSeconds = clampZero(remainingWorkSeconds - workSeconds);
            remainingMaintenanceWorkSeconds = clampZero(
                    remainingMaintenanceWorkSeconds - maintenanceWorkSeconds);
        }
    }

    /** Physical Stage-18C feedstock/material store measured in kilograms by ontology storage class. */
    public static final class PhysicalMaterialStore {
        private final Stage18ResourceOntologyCatalog ontology;
        private final Map<String, Double> capacityByStorageClassKg;
        private final Map<String, Double> massByCommodityKg;

        /**
         * Creates a physical material store or restores one from explicit mass values.
         *
         * @param ontology authoritative Stage-18 resource ontology
         * @param capacityByStorageClassKg non-negative capacity by storage class
         * @param initialMassByCommodityKg non-negative initial commodity masses
         */
        public PhysicalMaterialStore(
                Stage18ResourceOntologyCatalog ontology,
                Map<String, Double> capacityByStorageClassKg,
                Map<String, Double> initialMassByCommodityKg) {
            this.ontology = Objects.requireNonNull(ontology, "ontology");
            Objects.requireNonNull(capacityByStorageClassKg, "capacityByStorageClassKg");
            Objects.requireNonNull(initialMassByCommodityKg, "initialMassByCommodityKg");

            TreeMap<String, Double> capacities = new TreeMap<>();
            for (Map.Entry<String, Double> entry : capacityByStorageClassKg.entrySet()) {
                String storageClassId = requireText(entry.getKey(), "storage class ID");
                if (ontology.findStorageClass(storageClassId) == null) {
                    throw new IllegalArgumentException("Unknown storage class: " + storageClassId);
                }
                double capacity = Objects.requireNonNull(entry.getValue(), "storage capacity");
                requireNonNegative(capacity, "storage capacity");
                capacities.put(storageClassId, capacity);
            }
            this.capacityByStorageClassKg = Collections.unmodifiableMap(capacities);

            TreeMap<String, Double> masses = new TreeMap<>();
            for (Map.Entry<String, Double> entry : initialMassByCommodityKg.entrySet()) {
                String commodityId = requireText(entry.getKey(), "commodity ID");
                CommodityDefinition commodity = requireMassCommodity(ontology, commodityId);
                double massKg = Objects.requireNonNull(entry.getValue(), "commodity mass");
                requireNonNegative(massKg, "commodity mass");
                if (massKg > 0d) {
                    if (!capacities.containsKey(commodity.storageClassId())) {
                        throw new IllegalArgumentException("Initial cargo has no compatible storage: " + commodityId);
                    }
                    masses.put(commodityId, massKg);
                }
            }
            this.massByCommodityKg = masses;
            for (String storageClassId : capacities.keySet()) {
                if (usedCapacityKg(storageClassId) > capacities.get(storageClassId) + EPSILON) {
                    throw new IllegalArgumentException("Initial cargo exceeds storage capacity: " + storageClassId);
                }
            }
        }

        /**
         * Copies the current physical extraction cargo masses into a Stage-18C material store.
         * Storage capacity remains explicit because Stage 18F, not 18B/18C, owns persistent storage.
         *
         * @param ontology authoritative Stage-18 resource ontology
         * @param capacityByStorageClassKg explicit destination capacity by storage class
         * @param extractionCargo Stage-18B physical extraction cargo to snapshot
         * @return new independent Stage-18C physical material store
         */
        public static PhysicalMaterialStore fromExtractionCargo(
                Stage18ResourceOntologyCatalog ontology,
                Map<String, Double> capacityByStorageClassKg,
                Stage18ExtractionRuntime.PhysicalCargoStore extractionCargo) {
            Objects.requireNonNull(extractionCargo, "extractionCargo");
            return new PhysicalMaterialStore(
                    ontology,
                    capacityByStorageClassKg,
                    extractionCargo.snapshotMassByCommodityKg());
        }

        /**
         * Returns stored mass of one Stage-18 commodity.
         *
         * @param commodityId stable Stage-18 commodity ID
         * @return non-negative stored mass in kilograms
         */
        public double massKg(String commodityId) {
            requireText(commodityId, "commodityId");
            return massByCommodityKg.getOrDefault(commodityId, 0d);
        }

        /**
         * Returns remaining compatible capacity for one Stage-18 commodity.
         *
         * @param commodityId stable Stage-18 commodity ID
         * @return non-negative remaining capacity in kilograms
         */
        public double remainingCapacityKg(String commodityId) {
            CommodityDefinition commodity = requireMassCommodity(ontology, commodityId);
            Double capacity = capacityByStorageClassKg.get(commodity.storageClassId());
            if (capacity == null) {
                return 0d;
            }
            return Math.max(0d, capacity - usedCapacityKg(commodity.storageClassId()));
        }

        /** @return immutable snapshot of stored mass by commodity ID */
        public Map<String, Double> snapshotMassByCommodityKg() {
            return Collections.unmodifiableMap(new TreeMap<>(massByCommodityKg));
        }

        private double usedCapacityKg(String storageClassId) {
            double used = 0d;
            for (Map.Entry<String, Double> entry : massByCommodityKg.entrySet()) {
                CommodityDefinition commodity = ontology.findCommodity(entry.getKey());
                if (commodity != null && commodity.storageClassId().equals(storageClassId)) {
                    used += entry.getValue();
                }
            }
            return used;
        }

        private void remove(String commodityId, double massKg) {
            double remaining = massKg(commodityId) - massKg;
            if (remaining <= EPSILON) {
                massByCommodityKg.remove(commodityId);
            } else {
                massByCommodityKg.put(commodityId, remaining);
            }
        }

        private void add(String commodityId, double massKg) {
            if (massKg > EPSILON) {
                massByCommodityKg.merge(commodityId, massKg, Double::sum);
            }
        }
    }

    /**
     * Immutable result of one refining request.
     *
     * @param status stable settlement outcome
     * @param consumedInputMassByCommodityKg committed input mass by commodity ID
     * @param outputCommodityId retained output commodity ID, or empty on rejection
     * @param outputMassStoredKg retained output mass stored in kilograms
     * @param discardedMassKg input mass explicitly discarded as tailings/byproducts
     * @param energyConsumedJ committed electrical/process energy in joules
     * @param workConsumedSeconds committed engineering work-seconds
     * @param maintenanceWorkConsumedSeconds committed maintenance work-seconds
     */
    public record RefiningResult(
            Status status,
            Map<String, Double> consumedInputMassByCommodityKg,
            String outputCommodityId,
            double outputMassStoredKg,
            double discardedMassKg,
            double energyConsumedJ,
            double workConsumedSeconds,
            double maintenanceWorkConsumedSeconds) {
        /**
         * Freezes one refining result snapshot.
         *
         * @param status stable settlement outcome
         * @param consumedInputMassByCommodityKg committed input mass by commodity ID
         * @param outputCommodityId retained output commodity ID, or empty on rejection
         * @param outputMassStoredKg retained output mass stored in kilograms
         * @param discardedMassKg input mass explicitly discarded as tailings/byproducts
         * @param energyConsumedJ committed electrical/process energy in joules
         * @param workConsumedSeconds committed engineering work-seconds
         * @param maintenanceWorkConsumedSeconds committed maintenance work-seconds
         */
        public RefiningResult {
            consumedInputMassByCommodityKg = Collections.unmodifiableMap(
                    new TreeMap<>(Objects.requireNonNull(consumedInputMassByCommodityKg, "consumedInputMassByCommodityKg")));
        }

        /** @return {@code true} only when the requested batch was atomically refined */
        public boolean accepted() {
            return status == Status.REFINED;
        }
    }

    /**
     * Atomically settles one gross-input refining batch.
     *
     * @param recipeId stable recipe ID
     * @param requestedInputMassKg gross feedstock batch mass
     * @param store physical input/output store
     * @param intervalBudget shared finite process budget
     * @return immutable accepted or rejected settlement result
     */
    public RefiningResult refine(
            String recipeId,
            double requestedInputMassKg,
            PhysicalMaterialStore store,
            IntervalBudget intervalBudget) {
        if (recipeId == null || recipeId.isBlank() || !Double.isFinite(requestedInputMassKg)
                || requestedInputMassKg <= 0d || store == null || intervalBudget == null) {
            return rejected(Status.INVALID_REQUEST);
        }
        RefiningRecipeDefinition recipe = refiningCatalog.findRecipe(recipeId);
        if (recipe == null) {
            return rejected(Status.RECIPE_NOT_FOUND);
        }
        if (!intervalBudget.capabilityTags.containsAll(recipe.requiredCapabilityTags())) {
            return rejected(Status.MISSING_CAPABILITY);
        }

        TreeMap<String, Double> requiredInputs = new TreeMap<>();
        for (RecipeInputDefinition input : recipe.inputs()) {
            double requiredMassKg = requestedInputMassKg * input.fractionOfInputMass();
            requiredInputs.put(input.commodityId(), requiredMassKg);
            if (store.massKg(input.commodityId()) + EPSILON < requiredMassKg) {
                return rejected(Status.INSUFFICIENT_INPUT);
            }
        }

        double energyJ = finiteProduct(requestedInputMassKg, recipe.energyJPerInputKg(), "refining energy");
        double workSeconds = finiteProduct(requestedInputMassKg, recipe.workSecondsPerInputKg(), "refining work");
        double maintenanceWorkSeconds = finiteProduct(
                requestedInputMassKg,
                recipe.maintenanceWorkSecondsPerInputKg(),
                "refining maintenance work");
        if (intervalBudget.remainingEnergyJ + EPSILON < energyJ) {
            return rejected(Status.INSUFFICIENT_POWER);
        }
        if (intervalBudget.remainingWorkSeconds + EPSILON < workSeconds) {
            return rejected(Status.INSUFFICIENT_WORK);
        }
        if (intervalBudget.remainingMaintenanceWorkSeconds + EPSILON < maintenanceWorkSeconds) {
            return rejected(Status.INSUFFICIENT_MAINTENANCE);
        }

        double outputMassKg = requestedInputMassKg * recipe.outputMassFraction();
        double discardedMassKg = requestedInputMassKg * recipe.discardedMassFraction();
        CommodityDefinition output = requireMassCommodity(ontology, recipe.outputCommodityId());
        double capacityAfterInputRemoval = store.remainingCapacityKg(output.id());
        for (Map.Entry<String, Double> input : requiredInputs.entrySet()) {
            CommodityDefinition inputCommodity = requireMassCommodity(ontology, input.getKey());
            if (inputCommodity.storageClassId().equals(output.storageClassId())) {
                capacityAfterInputRemoval += input.getValue();
            }
        }
        if (capacityAfterInputRemoval + EPSILON < outputMassKg) {
            return rejected(Status.STORAGE_FULL);
        }

        for (Map.Entry<String, Double> input : requiredInputs.entrySet()) {
            store.remove(input.getKey(), input.getValue());
        }
        intervalBudget.consume(energyJ, workSeconds, maintenanceWorkSeconds);
        store.add(output.id(), outputMassKg);
        return new RefiningResult(
                Status.REFINED,
                requiredInputs,
                output.id(),
                outputMassKg,
                discardedMassKg,
                energyJ,
                workSeconds,
                maintenanceWorkSeconds);
    }

    private static RefiningResult rejected(Status status) {
        return new RefiningResult(status, Map.of(), "", 0d, 0d, 0d, 0d, 0d);
    }

    private static CommodityDefinition requireMassCommodity(
            Stage18ResourceOntologyCatalog ontology, String commodityId) {
        CommodityDefinition commodity = ontology.findCommodity(commodityId);
        if (commodity == null) {
            throw new IllegalArgumentException("Unknown Stage-18 commodity: " + commodityId);
        }
        if (commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
            throw new IllegalArgumentException("Commodity is not mass-based: " + commodityId);
        }
        return commodity;
    }

    private static Set<String> immutableTags(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, name + " entry"));
        }
        return Collections.unmodifiableSet(copy);
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

    private static double finiteProduct(double left, double right, String name) {
        double product = left * right;
        if (!Double.isFinite(product)) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return product;
    }

    private static double clampZero(double value) {
        return value <= EPSILON ? 0d : value;
    }
}

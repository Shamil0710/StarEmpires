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

    public Stage18RefiningRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18RefiningCatalog refiningCatalog) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.refiningCatalog = Objects.requireNonNull(refiningCatalog, "refiningCatalog");
    }

    /** Stable outcome of one requested refining settlement. */
    public enum Status {
        REFINED,
        INVALID_REQUEST,
        RECIPE_NOT_FOUND,
        MISSING_CAPABILITY,
        INSUFFICIENT_INPUT,
        INSUFFICIENT_POWER,
        INSUFFICIENT_WORK,
        INSUFFICIENT_MAINTENANCE,
        STORAGE_FULL
    }

    /** Physical process capability projected into one refining interval. */
    public record RefiningCapability(
            String capabilityId,
            Set<String> capabilityTags,
            double availablePowerW,
            double workRate,
            double maintenanceWorkRate) {
        public RefiningCapability {
            capabilityId = requireText(capabilityId, "capabilityId");
            capabilityTags = immutableTags(capabilityTags, "capabilityTags");
            requireNonNegative(availablePowerW, "availablePowerW");
            requireNonNegative(workRate, "workRate");
            requireNonNegative(maintenanceWorkRate, "maintenanceWorkRate");
        }

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

        public Set<String> capabilityTags() {
            return capabilityTags;
        }

        public double durationSeconds() {
            return durationSeconds;
        }

        public double remainingEnergyJ() {
            return remainingEnergyJ;
        }

        public double remainingWorkSeconds() {
            return remainingWorkSeconds;
        }

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

    /**
     * Physical Stage-18C feedstock/material store measured in kilograms by ontology storage class.
     */
    public static final class PhysicalMaterialStore {
        private final Stage18ResourceOntologyCatalog ontology;
        private final Map<String, Double> capacityByStorageClassKg;
        private final Map<String, Double> massByCommodityKg;

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

        public double massKg(String commodityId) {
            requireText(commodityId, "commodityId");
            return massByCommodityKg.getOrDefault(commodityId, 0d);
        }

        public double remainingCapacityKg(String commodityId) {
            CommodityDefinition commodity = requireMassCommodity(ontology, commodityId);
            Double capacity = capacityByStorageClassKg.get(commodity.storageClassId());
            if (capacity == null) {
                return 0d;
            }
            return Math.max(0d, capacity - usedCapacityKg(commodity.storageClassId()));
        }

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

    /** Immutable result of one refining request. */
    public record RefiningResult(
            Status status,
            Map<String, Double> consumedInputMassByCommodityKg,
            String outputCommodityId,
            double outputMassStoredKg,
            double discardedMassKg,
            double energyConsumedJ,
            double workConsumedSeconds,
            double maintenanceWorkConsumedSeconds) {
        public RefiningResult {
            consumedInputMassByCommodityKg = Collections.unmodifiableMap(
                    new TreeMap<>(Objects.requireNonNull(consumedInputMassByCommodityKg, "consumedInputMassByCommodityKg")));
        }

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

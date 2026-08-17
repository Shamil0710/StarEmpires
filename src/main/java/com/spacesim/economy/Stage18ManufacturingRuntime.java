package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
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
 * Stage-18D deterministic physical settlement for component and finished-product manufacturing.
 *
 * <p>Every accepted operation consumes exactly the output product's physical mass in Stage-18
 * materials/components plus finite process energy, engineering work and maintenance work. The
 * runtime has no station-class bonus and no ownership/player branch. Stage 18E will project real
 * installed facility capabilities into the interval budget consumed here.</p>
 */
public final class Stage18ManufacturingRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingCatalog catalog;
    private final Stage18ManufacturingProductRegistry products;

    /**
     * Creates the Stage-18D manufacturing settlement boundary.
     *
     * @param ontology authoritative Stage-18 ontology
     * @param catalog authoritative Stage-18D manufacturing recipes
     * @param products authoritative existing Stage-17.5 product registry
     */
    public Stage18ManufacturingRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingCatalog catalog,
            Stage18ManufacturingProductRegistry products) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.products = Objects.requireNonNull(products, "products");
    }

    /** Stable result status for one requested manufacturing settlement. */
    public enum Status {
        /** Requested component or finished product was manufactured and committed. */
        MANUFACTURED,
        /** Request amount/count or supplied runtime state is invalid. */
        INVALID_REQUEST,
        /** Requested component recipe does not exist. */
        RECIPE_NOT_FOUND,
        /** Requested finished product is not registered or has no manufacturing binding/profile. */
        PRODUCT_NOT_FOUND,
        /** The interval lacks one or more process/fabrication capabilities. */
        MISSING_CAPABILITY,
        /** One or more physical material/component inputs are insufficient. */
        INSUFFICIENT_INPUT,
        /** Available interval process energy is insufficient. */
        INSUFFICIENT_POWER,
        /** Available interval engineering work is insufficient. */
        INSUFFICIENT_WORK,
        /** Available interval maintenance/service work is insufficient. */
        INSUFFICIENT_MAINTENANCE,
        /** Compatible output storage cannot accept the manufactured mass. */
        STORAGE_FULL
    }

    /**
     * Physical manufacturing capability projected into one finite interval.
     *
     * @param capabilityId stable facility/line capability identity
     * @param capabilityTags installed fabrication/process tags
     * @param availablePowerW electrical/process power available to manufacturing
     * @param workRate engineering work-seconds completed per simulation second
     * @param maintenanceWorkRate maintenance work-seconds available per simulation second
     */
    public record ManufacturingCapability(
            String capabilityId,
            Set<String> capabilityTags,
            double availablePowerW,
            double workRate,
            double maintenanceWorkRate) {
        /**
         * Validates one manufacturing capability projection.
         *
         * @param capabilityId stable facility/line capability identity
         * @param capabilityTags installed fabrication/process tags
         * @param availablePowerW available process power
         * @param workRate engineering work rate
         * @param maintenanceWorkRate maintenance work rate
         */
        public ManufacturingCapability {
            requireText(capabilityId, "capabilityId");
            capabilityTags = immutableTags(capabilityTags, "capabilityTags");
            requireNonNegative(availablePowerW, "availablePowerW");
            requireNonNegative(workRate, "workRate");
            requireNonNegative(maintenanceWorkRate, "maintenanceWorkRate");
        }

        /**
         * Opens a shared finite budget for manufacturing operations during one simulation interval.
         *
         * @param durationSeconds positive interval duration
         * @return mutable interval budget that cannot reuse already committed resources
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

    /** Shared finite same-interval manufacturing budget. */
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

        /** @return installed capability tags projected into this interval */
        public Set<String> capabilityTags() {
            return capabilityTags;
        }

        /** @return represented simulation interval in seconds */
        public double durationSeconds() {
            return durationSeconds;
        }

        /** @return remaining process energy in joules */
        public double remainingEnergyJ() {
            return remainingEnergyJ;
        }

        /** @return remaining engineering work-seconds */
        public double remainingWorkSeconds() {
            return remainingWorkSeconds;
        }

        /** @return remaining maintenance work-seconds */
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
     * Physical Stage-18D inventory containing bulk commodities plus countable finished products.
     *
     * <p>Capacity is shared by Stage-18 storage class, so raw materials, components, modules and
     * ammunition compete for the same compatible storage budget. Persistent station ownership and
     * hauling remain Stage-18F responsibilities.</p>
     */
    public static final class ManufacturingInventory {
        private final Stage18ResourceOntologyCatalog ontology;
        private final Stage18ManufacturingProductRegistry products;
        private final Map<String, Double> capacityByStorageClassKg;
        private final Map<String, Double> commodityMassByIdKg;
        private final Map<String, Integer> productCountById;

        /**
         * Creates or restores one physical manufacturing inventory.
         *
         * @param ontology authoritative ontology
         * @param products authoritative finished-product registry
         * @param capacityByStorageClassKg non-negative capacity by storage class
         * @param initialCommodityMassByIdKg initial material/component masses
         * @param initialProductCountById initial finished-product counts
         */
        public ManufacturingInventory(
                Stage18ResourceOntologyCatalog ontology,
                Stage18ManufacturingProductRegistry products,
                Map<String, Double> capacityByStorageClassKg,
                Map<String, Double> initialCommodityMassByIdKg,
                Map<String, Integer> initialProductCountById) {
            this.ontology = Objects.requireNonNull(ontology, "ontology");
            this.products = Objects.requireNonNull(products, "products");
            Objects.requireNonNull(capacityByStorageClassKg, "capacityByStorageClassKg");
            Objects.requireNonNull(initialCommodityMassByIdKg, "initialCommodityMassByIdKg");
            Objects.requireNonNull(initialProductCountById, "initialProductCountById");

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
            for (Map.Entry<String, Double> entry : initialCommodityMassByIdKg.entrySet()) {
                CommodityDefinition commodity = requireMassCommodity(ontology, entry.getKey());
                double massKg = Objects.requireNonNull(entry.getValue(), "commodity mass");
                requireNonNegative(massKg, "commodity mass");
                if (massKg > EPSILON) {
                    requireStorage(capacities, commodity.storageClassId(), commodity.id());
                    masses.put(commodity.id(), massKg);
                }
            }
            this.commodityMassByIdKg = masses;

            TreeMap<String, Integer> counts = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : initialProductCountById.entrySet()) {
                ProductDefinition product = products.findProduct(entry.getKey());
                if (product == null) {
                    throw new IllegalArgumentException("Unknown manufactured product: " + entry.getKey());
                }
                int count = Objects.requireNonNull(entry.getValue(), "product count");
                if (count < 0) {
                    throw new IllegalArgumentException("product count must be non-negative");
                }
                if (count > 0) {
                    requireStorage(capacities, product.storageClassId(), product.contentId());
                    counts.put(product.contentId(), count);
                }
            }
            this.productCountById = counts;

            for (Map.Entry<String, Double> capacity : capacities.entrySet()) {
                if (usedCapacityKg(capacity.getKey()) > capacity.getValue() + EPSILON) {
                    throw new IllegalArgumentException("Initial inventory exceeds storage capacity: " + capacity.getKey());
                }
            }
        }

        /**
         * Seeds manufacturing inventory from the current Stage-18C material-store snapshot.
         *
         * @param ontology authoritative ontology
         * @param products authoritative product registry
         * @param capacityByStorageClassKg explicit Stage-18D storage capacities
         * @param refinedStore source Stage-18C material store
         * @return manufacturing inventory with copied commodity masses and no finished products
         */
        public static ManufacturingInventory fromRefinedMaterialStore(
                Stage18ResourceOntologyCatalog ontology,
                Stage18ManufacturingProductRegistry products,
                Map<String, Double> capacityByStorageClassKg,
                Stage18RefiningRuntime.PhysicalMaterialStore refinedStore) {
            Objects.requireNonNull(refinedStore, "refinedStore");
            return new ManufacturingInventory(
                    ontology, products, capacityByStorageClassKg, refinedStore.snapshotMassByCommodityKg(), Map.of());
        }

        /**
         * Returns stored mass for one Stage-18 commodity.
         *
         * @param commodityId commodity ID
         * @return non-negative mass in kilograms
         */
        public double commodityMassKg(String commodityId) {
            requireText(commodityId, "commodityId");
            return commodityMassByIdKg.getOrDefault(commodityId, 0d);
        }

        /**
         * Returns stored count for one finished product identity.
         *
         * @param productContentId existing product content ID
         * @return non-negative unit count
         */
        public int productCount(String productContentId) {
            requireText(productContentId, "productContentId");
            return productCountById.getOrDefault(productContentId, 0);
        }

        /** @return immutable deterministic commodity-mass snapshot */
        public Map<String, Double> snapshotCommodityMassByIdKg() {
            return Collections.unmodifiableMap(new TreeMap<>(commodityMassByIdKg));
        }

        /** @return immutable deterministic finished-product count snapshot */
        public Map<String, Integer> snapshotProductCountById() {
            return Collections.unmodifiableMap(new TreeMap<>(productCountById));
        }

        private double remainingCapacityKg(String storageClassId) {
            Double capacity = capacityByStorageClassKg.get(storageClassId);
            if (capacity == null) {
                return 0d;
            }
            return Math.max(0d, capacity - usedCapacityKg(storageClassId));
        }

        private double usedCapacityKg(String storageClassId) {
            double used = 0d;
            for (Map.Entry<String, Double> entry : commodityMassByIdKg.entrySet()) {
                CommodityDefinition commodity = ontology.findCommodity(entry.getKey());
                if (commodity != null && commodity.storageClassId().equals(storageClassId)) {
                    used += entry.getValue();
                }
            }
            for (Map.Entry<String, Integer> entry : productCountById.entrySet()) {
                ProductDefinition product = products.findProduct(entry.getKey());
                if (product != null && product.storageClassId().equals(storageClassId)) {
                    used += product.unitMassKg() * entry.getValue();
                }
            }
            return used;
        }

        private void removeCommodity(String commodityId, double massKg) {
            double remaining = commodityMassKg(commodityId) - massKg;
            if (remaining <= EPSILON) {
                commodityMassByIdKg.remove(commodityId);
            } else {
                commodityMassByIdKg.put(commodityId, remaining);
            }
        }

        private void addCommodity(String commodityId, double massKg) {
            if (massKg > EPSILON) {
                commodityMassByIdKg.merge(commodityId, massKg, Double::sum);
            }
        }

        private void addProduct(String productContentId, int count) {
            productCountById.merge(productContentId, count, Math::addExact);
        }
    }

    /**
     * Immutable outcome of one manufacturing request.
     *
     * @param status stable settlement outcome
     * @param consumedInputMassByCommodityKg physical mass consumed by Stage-18 commodity
     * @param outputId component commodity ID or finished-product content ID
     * @param outputMassKg physical output mass
     * @param outputUnitCount finished-product unit count, or zero for bulk component output
     * @param energyConsumedJ committed process energy
     * @param workConsumedSeconds committed engineering work-seconds
     * @param maintenanceWorkConsumedSeconds committed maintenance work-seconds
     */
    public record ManufacturingResult(
            Status status,
            Map<String, Double> consumedInputMassByCommodityKg,
            String outputId,
            double outputMassKg,
            int outputUnitCount,
            double energyConsumedJ,
            double workConsumedSeconds,
            double maintenanceWorkConsumedSeconds) {
        /**
         * Freezes one immutable result snapshot.
         *
         * @param status stable settlement outcome
         * @param consumedInputMassByCommodityKg consumed physical mass
         * @param outputId output identity
         * @param outputMassKg output physical mass
         * @param outputUnitCount finished unit count or zero for bulk components
         * @param energyConsumedJ committed energy
         * @param workConsumedSeconds committed engineering work
         * @param maintenanceWorkConsumedSeconds committed maintenance work
         */
        public ManufacturingResult {
            Objects.requireNonNull(status, "status");
            consumedInputMassByCommodityKg = Collections.unmodifiableMap(
                    new TreeMap<>(Objects.requireNonNull(consumedInputMassByCommodityKg, "consumedInputMassByCommodityKg")));
            outputId = outputId == null ? "" : outputId;
        }

        /** @return whether the operation committed physical state */
        public boolean accepted() {
            return status == Status.MANUFACTURED;
        }
    }

    /**
     * Manufactures bulk mass of one industrial component family.
     *
     * @param recipeId component recipe ID
     * @param requestedOutputMassKg requested finished component mass
     * @param inventory physical input/output inventory
     * @param budget shared interval manufacturing budget
     * @return atomic settlement result
     */
    public ManufacturingResult manufactureComponent(
            String recipeId,
            double requestedOutputMassKg,
            ManufacturingInventory inventory,
            IntervalBudget budget) {
        if (recipeId == null || recipeId.isBlank() || !Double.isFinite(requestedOutputMassKg)
                || requestedOutputMassKg <= 0d || inventory == null || budget == null) {
            return rejected(Status.INVALID_REQUEST);
        }
        ComponentRecipeDefinition recipe = catalog.findComponentRecipe(recipeId);
        if (recipe == null) {
            return rejected(Status.RECIPE_NOT_FOUND);
        }
        CommodityDefinition output = requireMassCommodity(ontology, recipe.outputCommodityId());
        return settle(
                requestedOutputMassKg,
                0,
                output.id(),
                output.storageClassId(),
                recipe.inputs(),
                recipe.requiredCapabilityTags(),
                recipe.energyJPerOutputKg(),
                recipe.workSecondsPerOutputKg(),
                recipe.maintenanceWorkSecondsPerOutputKg(),
                inventory,
                budget,
                true);
    }

    /**
     * Manufactures countable units of one existing Stage-17.5 module or ammunition identity.
     *
     * @param productContentId existing product content ID
     * @param requestedUnitCount positive unit count
     * @param inventory physical input/output inventory
     * @param budget shared interval manufacturing budget
     * @return atomic settlement result
     */
    public ManufacturingResult manufactureProduct(
            String productContentId,
            int requestedUnitCount,
            ManufacturingInventory inventory,
            IntervalBudget budget) {
        if (productContentId == null || productContentId.isBlank() || requestedUnitCount <= 0
                || inventory == null || budget == null) {
            return rejected(Status.INVALID_REQUEST);
        }
        ProductDefinition product = products.findProduct(productContentId);
        ProductBindingDefinition binding = catalog.findProductBinding(productContentId);
        if (product == null || binding == null) {
            return rejected(Status.PRODUCT_NOT_FOUND);
        }
        ProductProfileDefinition profile = catalog.findProductProfile(binding.profileId());
        if (profile == null) {
            return rejected(Status.PRODUCT_NOT_FOUND);
        }
        double outputMassKg = finiteProduct(product.unitMassKg(), requestedUnitCount, "finished product mass");
        return settle(
                outputMassKg,
                requestedUnitCount,
                product.contentId(),
                product.storageClassId(),
                profile.inputs(),
                profile.requiredCapabilityTags(),
                profile.energyJPerOutputKg(),
                profile.workSecondsPerOutputKg(),
                profile.maintenanceWorkSecondsPerOutputKg(),
                inventory,
                budget,
                false);
    }

    private ManufacturingResult settle(
            double outputMassKg,
            int outputUnitCount,
            String outputId,
            String outputStorageClassId,
            java.util.List<ManufacturingInputDefinition> inputs,
            Set<String> requiredCapabilities,
            double energyPerKg,
            double workPerKg,
            double maintenancePerKg,
            ManufacturingInventory inventory,
            IntervalBudget budget,
            boolean componentOutput) {
        if (!budget.capabilityTags.containsAll(requiredCapabilities)) {
            return rejected(Status.MISSING_CAPABILITY);
        }

        TreeMap<String, Double> requiredInputs = new TreeMap<>();
        double releasedOutputStorageKg = 0d;
        for (ManufacturingInputDefinition input : inputs) {
            double requiredMassKg = finiteProduct(outputMassKg, input.fractionOfOutputMass(), "manufacturing input mass");
            requiredInputs.put(input.commodityId(), requiredMassKg);
            if (inventory.commodityMassKg(input.commodityId()) + EPSILON < requiredMassKg) {
                return rejected(Status.INSUFFICIENT_INPUT);
            }
            CommodityDefinition inputCommodity = requireMassCommodity(ontology, input.commodityId());
            if (inputCommodity.storageClassId().equals(outputStorageClassId)) {
                releasedOutputStorageKg += requiredMassKg;
            }
        }

        double energyJ = finiteProduct(outputMassKg, energyPerKg, "manufacturing energy");
        double workSeconds = finiteProduct(outputMassKg, workPerKg, "manufacturing work");
        double maintenanceSeconds = finiteProduct(outputMassKg, maintenancePerKg, "manufacturing maintenance work");
        if (budget.remainingEnergyJ + EPSILON < energyJ) {
            return rejected(Status.INSUFFICIENT_POWER);
        }
        if (budget.remainingWorkSeconds + EPSILON < workSeconds) {
            return rejected(Status.INSUFFICIENT_WORK);
        }
        if (budget.remainingMaintenanceWorkSeconds + EPSILON < maintenanceSeconds) {
            return rejected(Status.INSUFFICIENT_MAINTENANCE);
        }
        if (inventory.remainingCapacityKg(outputStorageClassId) + releasedOutputStorageKg + EPSILON < outputMassKg) {
            return rejected(Status.STORAGE_FULL);
        }

        for (Map.Entry<String, Double> input : requiredInputs.entrySet()) {
            inventory.removeCommodity(input.getKey(), input.getValue());
        }
        budget.consume(energyJ, workSeconds, maintenanceSeconds);
        if (componentOutput) {
            inventory.addCommodity(outputId, outputMassKg);
        } else {
            inventory.addProduct(outputId, outputUnitCount);
        }
        return new ManufacturingResult(
                Status.MANUFACTURED,
                requiredInputs,
                outputId,
                outputMassKg,
                outputUnitCount,
                energyJ,
                workSeconds,
                maintenanceSeconds);
    }

    private static ManufacturingResult rejected(Status status) {
        return new ManufacturingResult(status, Map.of(), "", 0d, 0, 0d, 0d, 0d);
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

    private static void requireStorage(Map<String, Double> capacities, String storageClassId, String subject) {
        if (!capacities.containsKey(storageClassId)) {
            throw new IllegalArgumentException("Initial inventory has no compatible storage for " + subject);
        }
    }

    private static Set<String> immutableTags(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            copy.add(value);
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

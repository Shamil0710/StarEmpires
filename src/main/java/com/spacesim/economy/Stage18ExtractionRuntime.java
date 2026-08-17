package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityKind;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;
import com.spacesim.content.Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-18B deterministic settlement boundary for finite physical extraction.
 *
 * <p>The runtime consumes source mass, electrical/process energy, engineering work-seconds,
 * maintenance work-seconds and compatible storage capacity atomically. It never reads ownership,
 * doctrine or player state. Legacy {@code ItemType} cargo is deliberately outside this boundary:
 * Stage-18 commodities use their ontology quantity unit, currently kilograms for the extraction
 * baseline.</p>
 *
 * <p>Engineering work uses the same unit established by Stage 17.5G shipyard work: one
 * {@code workRate} completes that many engineering work-seconds per simulation second.</p>
 */
public final class Stage18ExtractionRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ExtractionCatalog extractionCatalog;

    /**
     * Creates the physical extraction boundary.
     *
     * @param ontology authoritative Stage-18 resource ontology
     * @param extractionCatalog authoritative Stage-18 extraction methods
     */
    public Stage18ExtractionRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ExtractionCatalog extractionCatalog) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.extractionCatalog = Objects.requireNonNull(extractionCatalog, "extractionCatalog");
    }

    /** Stable outcome of one requested extraction settlement. */
    public enum Status {
        /** Source mass was transferred into a physical output plus explicitly accounted waste. */
        EXTRACTED,
        /** The final accessible source mass was consumed by this successful operation. */
        EXTRACTED_DEPLETED,
        /** Source was already depleted before the request. */
        DEPLETED,
        /** Request amount or runtime state is invalid. */
        INVALID_REQUEST,
        /** Stable method ID is unknown. */
        METHOD_NOT_FOUND,
        /** Method cannot operate on this source kind. */
        SOURCE_KIND_INCOMPATIBLE,
        /** Method cannot operate in this source environment. */
        ENVIRONMENT_INCOMPATIBLE,
        /** Natural occurrence type is unknown or incompatible with the method. */
        OCCURRENCE_INCOMPATIBLE,
        /** Source output is not a physically valid ontology commodity for the source. */
        OUTPUT_INCOMPATIBLE,
        /** Extraction unit lacks one or more required physical capabilities. */
        MISSING_CAPABILITY,
        /** Requested source mass exceeds method throughput for this interval. */
        THROUGHPUT_LIMIT,
        /** Interval has insufficient electrical/process energy. */
        INSUFFICIENT_POWER,
        /** Interval has insufficient engineering work-seconds. */
        INSUFFICIENT_WORK,
        /** Interval has insufficient maintenance service-work. */
        INSUFFICIENT_MAINTENANCE,
        /** Compatible physical storage cannot accept the recovered output. */
        STORAGE_FULL
    }

    /**
     * Mutable finite source state suitable for later persistence mapping.
     *
     * <p>For {@link SourceKind#NATURAL_OCCURRENCE}, {@code sourceTypeId} is a Stage-18 occurrence
     * type ID. For {@link SourceKind#SALVAGE_STREAM}, it is a stable pre-accounted salvage stream
     * type owned by the later salvage layer. A salvage source may only contain mass that the caller
     * has already proven came from a manufactured physical asset; this runtime cannot create that
     * mass.</p>
     */
    public static final class PhysicalSourceState {
        private final String sourceId;
        private final SourceKind sourceKind;
        private final String sourceTypeId;
        private final ExtractionEnvironment environment;
        private final String outputCommodityId;
        private final double initialAccessibleMassKg;
        private final double gradeFraction;
        private final double sourceRecoveryFraction;
        private final Set<String> requiredCapabilityTags;
        private double remainingAccessibleMassKg;

        /**
         * Creates or restores one finite physical source.
         *
         * @param sourceId stable source ID
         * @param sourceKind natural occurrence or bounded salvage stream
         * @param sourceTypeId occurrence type or salvage stream type
         * @param environment physical extraction environment
         * @param outputCommodityId commodity recovered from this source stream
         * @param initialAccessibleMassKg initial gross accessible source mass
         * @param remainingAccessibleMassKg current gross accessible source mass
         * @param gradeFraction useful target fraction in gross removed mass
         * @param sourceRecoveryFraction source-side recoverability/yield fraction
         * @param requiredCapabilityTags source-specific capabilities beyond the method baseline
         */
        public PhysicalSourceState(
                String sourceId,
                SourceKind sourceKind,
                String sourceTypeId,
                ExtractionEnvironment environment,
                String outputCommodityId,
                double initialAccessibleMassKg,
                double remainingAccessibleMassKg,
                double gradeFraction,
                double sourceRecoveryFraction,
                Set<String> requiredCapabilityTags) {
            this.sourceId = requireText(sourceId, "sourceId");
            this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
            this.sourceTypeId = requireText(sourceTypeId, "sourceTypeId");
            this.environment = Objects.requireNonNull(environment, "environment");
            this.outputCommodityId = requireText(outputCommodityId, "outputCommodityId");
            requirePositive(initialAccessibleMassKg, "initialAccessibleMassKg");
            requireNonNegative(remainingAccessibleMassKg, "remainingAccessibleMassKg");
            if (remainingAccessibleMassKg > initialAccessibleMassKg + EPSILON) {
                throw new IllegalArgumentException("remainingAccessibleMassKg exceeds initial reserve");
            }
            requireFraction(gradeFraction, "gradeFraction");
            requireFraction(sourceRecoveryFraction, "sourceRecoveryFraction");
            this.initialAccessibleMassKg = initialAccessibleMassKg;
            this.remainingAccessibleMassKg = Math.min(remainingAccessibleMassKg, initialAccessibleMassKg);
            this.gradeFraction = gradeFraction;
            this.sourceRecoveryFraction = sourceRecoveryFraction;
            this.requiredCapabilityTags = immutableTags(requiredCapabilityTags, "requiredCapabilityTags");
        }

        /** @return stable source ID */
        public String sourceId() {
            return sourceId;
        }

        /** @return physical source kind */
        public SourceKind sourceKind() {
            return sourceKind;
        }

        /** @return occurrence or salvage stream type ID */
        public String sourceTypeId() {
            return sourceTypeId;
        }

        /** @return physical extraction environment */
        public ExtractionEnvironment environment() {
            return environment;
        }

        /** @return recovered Stage-18 commodity ID */
        public String outputCommodityId() {
            return outputCommodityId;
        }

        /** @return initial gross accessible source mass in kilograms */
        public double initialAccessibleMassKg() {
            return initialAccessibleMassKg;
        }

        /** @return remaining gross accessible source mass in kilograms */
        public double remainingAccessibleMassKg() {
            return remainingAccessibleMassKg;
        }

        /** @return target resource grade in {@code (0,1]} */
        public double gradeFraction() {
            return gradeFraction;
        }

        /** @return source-side recovery fraction in {@code (0,1]} */
        public double sourceRecoveryFraction() {
            return sourceRecoveryFraction;
        }

        /** @return immutable source-specific required capabilities */
        public Set<String> requiredCapabilityTags() {
            return requiredCapabilityTags;
        }

        /** @return whether no accessible source mass remains */
        public boolean isDepleted() {
            return remainingAccessibleMassKg <= EPSILON;
        }

        private void removeMass(double massKg) {
            remainingAccessibleMassKg -= massKg;
            if (remainingAccessibleMassKg <= EPSILON) {
                remainingAccessibleMassKg = 0d;
            }
        }
    }

    /**
     * Physical extraction-unit capability projection.
     *
     * @param capabilityId stable unit/facility capability ID
     * @param capabilityTags installed process/extraction capabilities
     * @param availablePowerW power available to extraction during an interval
     * @param workRate engineering work-seconds completed per simulation second
     * @param maintenanceWorkRate maintenance work-seconds available per simulation second
     */
    public record ExtractionCapability(
            String capabilityId,
            Set<String> capabilityTags,
            double availablePowerW,
            double workRate,
            double maintenanceWorkRate) {
        /**
         * Validates and freezes one extraction capability projection.
         *
         * @param capabilityId stable capability ID
         * @param capabilityTags installed capability tags
         * @param availablePowerW available power
         * @param workRate engineering work rate
         * @param maintenanceWorkRate maintenance service-work rate
         */
        public ExtractionCapability {
            requireText(capabilityId, "capabilityId");
            capabilityTags = immutableTags(capabilityTags, "capabilityTags");
            requireNonNegative(availablePowerW, "availablePowerW");
            requireNonNegative(workRate, "workRate");
            requireNonNegative(maintenanceWorkRate, "maintenanceWorkRate");
        }

        /**
         * Opens a shared finite interval budget. Multiple extraction operations may consume it, but
         * cannot reuse power/work/maintenance already committed by a previous operation.
         *
         * @param durationSeconds finite positive simulation interval
         * @return mutable interval budget owned by the caller
         */
        public IntervalBudget openInterval(double durationSeconds) {
            requirePositive(durationSeconds, "durationSeconds");
            return new IntervalBudget(
                    durationSeconds,
                    finiteProduct(availablePowerW, durationSeconds, "interval energy"),
                    finiteProduct(workRate, durationSeconds, "interval work"),
                    finiteProduct(maintenanceWorkRate, durationSeconds, "interval maintenance work"));
        }
    }

    /** Shared same-interval engineering budget for extraction operations. */
    public static final class IntervalBudget {
        private final double durationSeconds;
        private double remainingEnergyJ;
        private double remainingWorkSeconds;
        private double remainingMaintenanceWorkSeconds;

        private IntervalBudget(
                double durationSeconds,
                double remainingEnergyJ,
                double remainingWorkSeconds,
                double remainingMaintenanceWorkSeconds) {
            this.durationSeconds = durationSeconds;
            this.remainingEnergyJ = remainingEnergyJ;
            this.remainingWorkSeconds = remainingWorkSeconds;
            this.remainingMaintenanceWorkSeconds = remainingMaintenanceWorkSeconds;
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

    /**
     * Physical Stage-18 cargo store measured in ontology-native mass units.
     *
     * <p>Capacity is authored by storage class. A commodity can be loaded only into the storage
     * class declared by the ontology; unrelated storage capacity is not interchangeable.</p>
     */
    public static final class PhysicalCargoStore {
        private final Stage18ResourceOntologyCatalog ontology;
        private final Map<String, Double> capacityByStorageClassKg;
        private final Map<String, Double> massByCommodityKg;

        /**
         * Creates a physical cargo store or restores one from persistent mass values.
         *
         * @param ontology authoritative ontology
         * @param capacityByStorageClassKg non-negative capacity by storage class
         * @param initialMassByCommodityKg non-negative initial commodity masses
         */
        public PhysicalCargoStore(
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
                double mass = Objects.requireNonNull(entry.getValue(), "commodity mass");
                requireNonNegative(mass, "commodity mass");
                if (mass > 0d) {
                    masses.put(commodity.id(), mass);
                }
            }
            this.massByCommodityKg = masses;
            for (String storageClassId : capacities.keySet()) {
                if (usedCapacityKg(storageClassId) > capacities.get(storageClassId) + EPSILON) {
                    throw new IllegalArgumentException("Initial cargo exceeds storage capacity: " + storageClassId);
                }
            }
            for (String commodityId : masses.keySet()) {
                CommodityDefinition commodity = requireMassCommodity(ontology, commodityId);
                if (!capacities.containsKey(commodity.storageClassId())) {
                    throw new IllegalArgumentException(
                            "Initial cargo has no compatible storage: " + commodityId);
                }
            }
        }

        /**
         * Returns stored mass of one commodity.
         *
         * @param commodityId Stage-18 commodity ID
         * @return non-negative stored mass in kilograms
         */
        public double massKg(String commodityId) {
            requireText(commodityId, "commodityId");
            return massByCommodityKg.getOrDefault(commodityId, 0d);
        }

        /**
         * Returns remaining compatible capacity for one commodity.
         *
         * @param commodityId Stage-18 commodity ID
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

        private void add(String commodityId, double massKg) {
            massByCommodityKg.merge(commodityId, massKg, Double::sum);
        }
    }

    /**
     * Immutable result of one extraction request.
     *
     * @param status stable outcome
     * @param sourceMassRemovedKg gross mass removed from the finite source
     * @param outputMassStoredKg recovered commodity mass added to compatible storage
     * @param discardedMassKg removed source mass not recovered as the target commodity
     * @param energyConsumedJ committed electrical/process energy
     * @param workConsumedSeconds committed engineering work-seconds
     * @param maintenanceWorkConsumedSeconds committed maintenance work-seconds
     */
    public record ExtractionResult(
            Status status,
            double sourceMassRemovedKg,
            double outputMassStoredKg,
            double discardedMassKg,
            double energyConsumedJ,
            double workConsumedSeconds,
            double maintenanceWorkConsumedSeconds) {
        /**
         * Validates one deterministic result.
         *
         * @param status stable outcome
         * @param sourceMassRemovedKg removed source mass
         * @param outputMassStoredKg stored output mass
         * @param discardedMassKg discarded/waste mass
         * @param energyConsumedJ consumed energy
         * @param workConsumedSeconds consumed engineering work
         * @param maintenanceWorkConsumedSeconds consumed maintenance work
         */
        public ExtractionResult {
            Objects.requireNonNull(status, "status");
            requireNonNegative(sourceMassRemovedKg, "sourceMassRemovedKg");
            requireNonNegative(outputMassStoredKg, "outputMassStoredKg");
            requireNonNegative(discardedMassKg, "discardedMassKg");
            requireNonNegative(energyConsumedJ, "energyConsumedJ");
            requireNonNegative(workConsumedSeconds, "workConsumedSeconds");
            requireNonNegative(maintenanceWorkConsumedSeconds, "maintenanceWorkConsumedSeconds");
        }

        /** @return whether this result committed physical state changes */
        public boolean committed() {
            return status == Status.EXTRACTED || status == Status.EXTRACTED_DEPLETED;
        }
    }

    /**
     * Attempts one atomic physical extraction settlement.
     *
     * <p>Insufficient capability, throughput, power, work, maintenance or storage rejects the whole
     * request without mutating source, cargo or the shared interval budget. If the request exceeds
     * only the remaining finite reserve, the final reserve is extracted and the result is
     * {@link Status#EXTRACTED_DEPLETED}.</p>
     *
     * @param source finite physical source
     * @param methodId stable extraction method ID
     * @param requestedSourceMassKg gross source mass requested for this operation
     * @param capability physical extraction capability
     * @param budget shared same-interval engineering budget
     * @param destination compatible physical cargo store
     * @return deterministic settlement result
     */
    public ExtractionResult extract(
            PhysicalSourceState source,
            String methodId,
            double requestedSourceMassKg,
            ExtractionCapability capability,
            IntervalBudget budget,
            PhysicalCargoStore destination) {
        if (source == null || capability == null || budget == null || destination == null
                || methodId == null || methodId.isBlank()
                || !Double.isFinite(requestedSourceMassKg) || requestedSourceMassKg <= 0d) {
            return rejected(Status.INVALID_REQUEST);
        }
        ExtractionMethodDefinition method = extractionCatalog.findMethod(methodId);
        if (method == null) {
            return rejected(Status.METHOD_NOT_FOUND);
        }
        if (source.isDepleted()) {
            return rejected(Status.DEPLETED);
        }
        if (method.sourceKind() != source.sourceKind()) {
            return rejected(Status.SOURCE_KIND_INCOMPATIBLE);
        }
        if (method.environment() != source.environment()) {
            return rejected(Status.ENVIRONMENT_INCOMPATIBLE);
        }

        CommodityDefinition output = ontology.findCommodity(source.outputCommodityId());
        if (output == null || output.quantityUnit() != QuantityUnit.KILOGRAM) {
            return rejected(Status.OUTPUT_INCOMPATIBLE);
        }
        if (source.sourceKind() == SourceKind.NATURAL_OCCURRENCE) {
            ResourceOccurrenceTypeDefinition occurrence = ontology.findOccurrenceType(source.sourceTypeId());
            if (occurrence == null || !method.compatibleOccurrenceTypeIds().contains(occurrence.id())) {
                return rejected(Status.OCCURRENCE_INCOMPATIBLE);
            }
            if (output.kind() != CommodityKind.EXTRACTED_FEEDSTOCK
                    || !occurrence.feedstockCommodityIds().contains(output.id())) {
                return rejected(Status.OUTPUT_INCOMPATIBLE);
            }
        }

        if (!capability.capabilityTags().containsAll(method.requiredCapabilityTags())
                || !capability.capabilityTags().containsAll(source.requiredCapabilityTags())) {
            return rejected(Status.MISSING_CAPABILITY);
        }

        double sourceMassKg = Math.min(requestedSourceMassKg, source.remainingAccessibleMassKg());
        double intervalThroughputKg = finiteProduct(
                method.maxSourceKgPerSecond(), budget.durationSeconds(), "method interval throughput");
        if (sourceMassKg > intervalThroughputKg + EPSILON) {
            return rejected(Status.THROUGHPUT_LIMIT);
        }

        double energyJ = finiteProduct(sourceMassKg, method.energyJPerSourceKg(), "extraction energy");
        double workSeconds = finiteProduct(
                sourceMassKg, method.workSecondsPerSourceKg(), "extraction work");
        double maintenanceWorkSeconds = finiteProduct(
                sourceMassKg,
                method.maintenanceWorkSecondsPerSourceKg(),
                "extraction maintenance work");
        double outputMassKg = sourceMassKg
                * source.gradeFraction()
                * source.sourceRecoveryFraction()
                * method.recoveryFraction();
        if (!Double.isFinite(outputMassKg) || outputMassKg < 0d || outputMassKg > sourceMassKg + EPSILON) {
            return rejected(Status.INVALID_REQUEST);
        }
        double discardedMassKg = Math.max(0d, sourceMassKg - outputMassKg);

        if (energyJ > budget.remainingEnergyJ() + EPSILON) {
            return rejected(Status.INSUFFICIENT_POWER);
        }
        if (workSeconds > budget.remainingWorkSeconds() + EPSILON) {
            return rejected(Status.INSUFFICIENT_WORK);
        }
        if (maintenanceWorkSeconds > budget.remainingMaintenanceWorkSeconds() + EPSILON) {
            return rejected(Status.INSUFFICIENT_MAINTENANCE);
        }
        if (outputMassKg > destination.remainingCapacityKg(output.id()) + EPSILON) {
            return rejected(Status.STORAGE_FULL);
        }

        source.removeMass(sourceMassKg);
        budget.consume(energyJ, workSeconds, maintenanceWorkSeconds);
        destination.add(output.id(), outputMassKg);
        Status status = source.isDepleted() ? Status.EXTRACTED_DEPLETED : Status.EXTRACTED;
        return new ExtractionResult(
                status,
                sourceMassKg,
                outputMassKg,
                discardedMassKg,
                energyJ,
                workSeconds,
                maintenanceWorkSeconds);
    }

    private static ExtractionResult rejected(Status status) {
        return new ExtractionResult(status, 0d, 0d, 0d, 0d, 0d, 0d);
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

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    private static double finiteProduct(double left, double right, String name) {
        double result = left * right;
        if (!Double.isFinite(result) || result < 0d) {
            throw new IllegalArgumentException(name + " is not finite");
        }
        return result;
    }

    private static double clampZero(double value) {
        return value <= EPSILON ? 0d : value;
    }
}

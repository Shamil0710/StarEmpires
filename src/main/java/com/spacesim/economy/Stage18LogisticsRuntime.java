package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage-18F deterministic cargo-transfer boundary between physical storage nodes.
 *
 * <p>The runtime models handling compatibility and finite transfer mass. It deliberately does not
 * teleport cargo across travel distance: route selection, hauler movement and delivery scheduling
 * may consume this boundary at loading/unloading endpoints without creating a second inventory
 * model.</p>
 */
public final class Stage18LogisticsRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;

    /**
     * Creates a Stage-18F physical logistics boundary.
     *
     * @param ontology authoritative Stage-18 resource ontology
     * @param products authoritative finished-product registry
     */
    public Stage18LogisticsRuntime(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
    }

    /** Stable transfer outcome. */
    public enum Status {
        /** Physical cargo was moved atomically. */
        TRANSFERRED,
        /** Request amount/count or supplied state is invalid. */
        INVALID_REQUEST,
        /** Commodity or finished-product identity is unknown. */
        CARGO_NOT_FOUND,
        /** Handling equipment cannot exchange the cargo's storage class. */
        STORAGE_CLASS_INCOMPATIBLE,
        /** Source does not contain the requested physical cargo. */
        SOURCE_INSUFFICIENT,
        /** Destination lacks compatible remaining mass capacity. */
        DESTINATION_FULL,
        /** Remaining interval handling mass is insufficient. */
        THROUGHPUT_LIMIT,
        /** A countable finished unit exceeds the handling equipment's single-unit mass envelope. */
        UNIT_HANDLING_LIMIT
    }

    /**
     * Physical cargo-handling capability shared by two transfer endpoints.
     *
     * @param handlingId stable transfer-interface identity
     * @param supportedStorageClassIds storage classes accepted at both endpoints
     * @param massRateKgPerSecond maximum transferred cargo mass per simulation second
     * @param maxUnitMassKg maximum single finished-product unit mass
     */
    public record HandlingCapability(
            String handlingId,
            Set<String> supportedStorageClassIds,
            double massRateKgPerSecond,
            double maxUnitMassKg) {
        /**
         * Validates one physical handling capability.
         *
         * @param handlingId stable handling identity
         * @param supportedStorageClassIds supported Stage-18 storage classes
         * @param massRateKgPerSecond cargo-handling mass rate
         * @param maxUnitMassKg maximum single handled unit mass
         */
        public HandlingCapability {
            requireText(handlingId, "handlingId");
            Objects.requireNonNull(supportedStorageClassIds, "supportedStorageClassIds");
            TreeSet<String> copy = new TreeSet<>();
            for (String value : supportedStorageClassIds) {
                copy.add(requireText(value, "supported storage class"));
            }
            supportedStorageClassIds = Collections.unmodifiableSet(copy);
            requirePositive(massRateKgPerSecond, "massRateKgPerSecond");
            requirePositive(maxUnitMassKg, "maxUnitMassKg");
        }

        /**
         * Opens a finite handling interval.
         *
         * @param durationSeconds positive simulation duration
         * @return mutable interval mass budget
         */
        public TransferBudget openInterval(double durationSeconds) {
            requirePositive(durationSeconds, "durationSeconds");
            return new TransferBudget(
                    durationSeconds,
                    finiteProduct(massRateKgPerSecond, durationSeconds, "transfer interval mass"));
        }

        /**
         * Derives the physical handling intersection of two station archetypes.
         *
         * @param handlingId stable transfer-interface identity
         * @param source source station archetype
         * @param destination destination station archetype
         * @return handling capability limited by the weaker endpoint
         */
        public static HandlingCapability between(
                String handlingId,
                StationArchetypeDefinition source,
                StationArchetypeDefinition destination) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
            Set<String> classes = new HashSet<>(source.transferStorageClassIds());
            classes.retainAll(destination.transferStorageClassIds());
            return new HandlingCapability(
                    handlingId,
                    classes,
                    Math.min(source.transferMassRateKgPerSecond(), destination.transferMassRateKgPerSecond()),
                    Math.min(source.maxTransferUnitMassKg(), destination.maxTransferUnitMassKg()));
        }
    }

    /** Shared finite cargo-handling mass budget for one interval. */
    public static final class TransferBudget {
        private final double durationSeconds;
        private double remainingMassKg;

        private TransferBudget(double durationSeconds, double remainingMassKg) {
            this.durationSeconds = durationSeconds;
            this.remainingMassKg = remainingMassKg;
        }

        /** @return represented simulation duration in seconds */
        public double durationSeconds() {
            return durationSeconds;
        }

        /** @return remaining transferable cargo mass in kilograms */
        public double remainingMassKg() {
            return remainingMassKg;
        }

        private void consume(double massKg) {
            remainingMassKg -= massKg;
            if (remainingMassKg <= EPSILON) {
                remainingMassKg = 0d;
            }
        }
    }

    /**
     * Immutable outcome of one cargo transfer request.
     *
     * @param status stable transfer outcome
     * @param cargoId commodity or finished-product ID
     * @param transferredMassKg physical mass moved
     * @param transferredUnitCount count moved for finished products, otherwise zero
     */
    public record TransferResult(
            Status status,
            String cargoId,
            double transferredMassKg,
            int transferredUnitCount) {
        /**
         * Validates one transfer result snapshot.
         *
         * @param status stable transfer outcome
         * @param cargoId cargo identity, or empty string for invalid request
         * @param transferredMassKg physical mass moved
         * @param transferredUnitCount finished-product count moved
         */
        public TransferResult {
            Objects.requireNonNull(status, "status");
            cargoId = cargoId == null ? "" : cargoId;
        }

        /** @return whether the transfer committed both source and destination storage */
        public boolean transferred() {
            return status == Status.TRANSFERRED;
        }
    }

    /**
     * Transfers physical Stage-18 commodity mass between two storage nodes.
     *
     * @param source source station storage
     * @param destination destination station storage
     * @param commodityId Stage-18 commodity ID
     * @param massKg requested physical mass
     * @param handling common endpoint handling capability
     * @param budget shared finite interval transfer budget
     * @return atomic transfer result
     */
    public TransferResult transferCommodity(
            Stage18StationStorage source,
            Stage18StationStorage destination,
            String commodityId,
            double massKg,
            HandlingCapability handling,
            TransferBudget budget) {
        if (source == null || destination == null || source == destination || commodityId == null
                || commodityId.isBlank() || !Double.isFinite(massKg) || massKg <= 0d
                || handling == null || budget == null) {
            return rejected(Status.INVALID_REQUEST);
        }
        CommodityDefinition commodity = ontology.findCommodity(commodityId);
        if (commodity == null) {
            return rejected(Status.CARGO_NOT_FOUND);
        }
        if (!handling.supportedStorageClassIds().contains(commodity.storageClassId())) {
            return rejected(Status.STORAGE_CLASS_INCOMPATIBLE);
        }
        if (source.commodityMassKg(commodityId) + EPSILON < massKg) {
            return rejected(Status.SOURCE_INSUFFICIENT);
        }
        if (!destination.canAddCommodity(commodityId, massKg)) {
            return rejected(Status.DESTINATION_FULL);
        }
        if (budget.remainingMassKg() + EPSILON < massKg) {
            return rejected(Status.THROUGHPUT_LIMIT);
        }
        source.removeCommodity(commodityId, massKg);
        destination.addCommodity(commodityId, massKg);
        budget.consume(massKg);
        return new TransferResult(Status.TRANSFERRED, commodityId, massKg, 0);
    }

    /**
     * Transfers countable finished modules/ammunition between two storage nodes.
     *
     * @param source source station storage
     * @param destination destination station storage
     * @param productContentId existing finished-product content ID
     * @param count positive requested unit count
     * @param handling common endpoint handling capability
     * @param budget shared finite interval transfer budget
     * @return atomic transfer result
     */
    public TransferResult transferProduct(
            Stage18StationStorage source,
            Stage18StationStorage destination,
            String productContentId,
            int count,
            HandlingCapability handling,
            TransferBudget budget) {
        if (source == null || destination == null || source == destination || productContentId == null
                || productContentId.isBlank() || count <= 0 || handling == null || budget == null) {
            return rejected(Status.INVALID_REQUEST);
        }
        ProductDefinition product = products.findProduct(productContentId);
        if (product == null) {
            return rejected(Status.CARGO_NOT_FOUND);
        }
        if (!handling.supportedStorageClassIds().contains(product.storageClassId())) {
            return rejected(Status.STORAGE_CLASS_INCOMPATIBLE);
        }
        if (product.unitMassKg() > handling.maxUnitMassKg() + EPSILON) {
            return rejected(Status.UNIT_HANDLING_LIMIT);
        }
        if (source.productCount(productContentId) < count) {
            return rejected(Status.SOURCE_INSUFFICIENT);
        }
        double massKg = finiteProduct(product.unitMassKg(), count, "product transfer mass");
        if (!destination.canAddProduct(productContentId, count)) {
            return rejected(Status.DESTINATION_FULL);
        }
        if (budget.remainingMassKg() + EPSILON < massKg) {
            return rejected(Status.THROUGHPUT_LIMIT);
        }
        source.removeProduct(productContentId, count);
        destination.addProduct(productContentId, count);
        budget.consume(massKg);
        return new TransferResult(Status.TRANSFERRED, productContentId, massKg, count);
    }

    private static TransferResult rejected(Status status) {
        return new TransferResult(status, "", 0d, 0);
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

    private static double finiteProduct(double left, double right, String name) {
        double value = left * right;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return value;
    }
}

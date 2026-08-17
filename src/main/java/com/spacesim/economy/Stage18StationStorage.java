package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Canonical Stage-18F physical storage state for one station, depot or outpost.
 *
 * <p>Stage-18 commodity mass and countable finished modules/ammunition share the same capacity by
 * storage class. This class intentionally does not reinterpret the legacy integer
 * {@code InventoryComponent}; migration between those models requires an explicit policy.</p>
 */
public final class Stage18StationStorage {
    private static final double EPSILON = 1e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;
    private final String stationId;
    private final Map<String, Double> capacityByStorageClassKg;
    private final Map<String, Double> commodityMassByIdKg;
    private final Map<String, Integer> productCountById;

    /**
     * Creates or restores one physical station storage state.
     *
     * @param ontology authoritative Stage-18 resource ontology
     * @param products authoritative finished-product registry
     * @param stationId stable station/depot identity
     * @param capacityByStorageClassKg physical mass capacity by storage class
     * @param initialCommodityMassByIdKg initial Stage-18 commodity masses
     * @param initialProductCountById initial finished-product counts
     */
    public Stage18StationStorage(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            String stationId,
            Map<String, Double> capacityByStorageClassKg,
            Map<String, Double> initialCommodityMassByIdKg,
            Map<String, Integer> initialProductCountById) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
        this.stationId = requireText(stationId, "stationId");
        Objects.requireNonNull(capacityByStorageClassKg, "capacityByStorageClassKg");
        Objects.requireNonNull(initialCommodityMassByIdKg, "initialCommodityMassByIdKg");
        Objects.requireNonNull(initialProductCountById, "initialProductCountById");

        TreeMap<String, Double> capacities = new TreeMap<>();
        for (Map.Entry<String, Double> entry : capacityByStorageClassKg.entrySet()) {
            String storageClassId = requireText(entry.getKey(), "storage class ID");
            if (ontology.findStorageClass(storageClassId) == null) {
                throw new IllegalArgumentException("Unknown storage class: " + storageClassId);
            }
            double capacityKg = Objects.requireNonNull(entry.getValue(), "storage capacity");
            requireNonNegative(capacityKg, "storage capacity");
            if (capacityKg > EPSILON) {
                capacities.put(storageClassId, capacityKg);
            }
        }
        if (capacities.isEmpty()) {
            throw new IllegalArgumentException("Station storage requires positive physical capacity");
        }
        this.capacityByStorageClassKg = Collections.unmodifiableMap(capacities);
        this.commodityMassByIdKg = new TreeMap<>();
        this.productCountById = new TreeMap<>();
        replaceContents(initialCommodityMassByIdKg, initialProductCountById);
    }

    /** @return stable station/depot identity */
    public String stationId() {
        return stationId;
    }

    /** @return immutable deterministic capacity by Stage-18 storage class */
    public Map<String, Double> snapshotCapacityByStorageClassKg() {
        return capacityByStorageClassKg;
    }

    /**
     * Returns stored Stage-18 commodity mass.
     *
     * @param commodityId Stage-18 commodity ID
     * @return non-negative mass in kilograms
     */
    public double commodityMassKg(String commodityId) {
        requireText(commodityId, "commodityId");
        return commodityMassByIdKg.getOrDefault(commodityId, 0d);
    }

    /**
     * Returns stored finished-product count.
     *
     * @param productContentId existing module/ammunition content ID
     * @return non-negative unit count
     */
    public int productCount(String productContentId) {
        requireText(productContentId, "productContentId");
        return productCountById.getOrDefault(productContentId, 0);
    }

    /**
     * Returns total occupied mass in one physical storage class.
     *
     * @param storageClassId Stage-18 storage class ID
     * @return occupied mass in kilograms, including commodities and finished products
     */
    public double usedCapacityKg(String storageClassId) {
        requireKnownStorageClass(storageClassId);
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

    /**
     * Returns unoccupied mass capacity in one storage class.
     *
     * @param storageClassId Stage-18 storage class ID
     * @return non-negative remaining mass capacity in kilograms
     */
    public double remainingCapacityKg(String storageClassId) {
        requireKnownStorageClass(storageClassId);
        Double capacity = capacityByStorageClassKg.get(storageClassId);
        if (capacity == null) {
            return 0d;
        }
        return Math.max(0d, capacity - usedCapacityKg(storageClassId));
    }

    /** @return immutable deterministic Stage-18 commodity-mass snapshot */
    public Map<String, Double> snapshotCommodityMassByIdKg() {
        return Collections.unmodifiableMap(new TreeMap<>(commodityMassByIdKg));
    }

    /** @return immutable deterministic finished-product count snapshot */
    public Map<String, Integer> snapshotProductCountById() {
        return Collections.unmodifiableMap(new TreeMap<>(productCountById));
    }

    /**
     * Captures a deterministic persistence-friendly storage snapshot.
     *
     * @return immutable snapshot containing capacities and all physical inventory
     */
    public StationStorageSnapshot snapshot() {
        return new StationStorageSnapshot(
                stationId,
                capacityByStorageClassKg,
                commodityMassByIdKg,
                productCountById);
    }

    /**
     * Restores canonical station storage from an immutable snapshot.
     *
     * @param ontology authoritative Stage-18 ontology
     * @param products authoritative product registry
     * @param snapshot saved storage snapshot
     * @return validated restored station storage
     */
    public static Stage18StationStorage restore(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            StationStorageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new Stage18StationStorage(
                ontology,
                products,
                snapshot.stationId(),
                snapshot.capacityByStorageClassKg(),
                snapshot.commodityMassByIdKg(),
                snapshot.productCountById());
    }

    void restore(StationStorageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!stationId.equals(snapshot.stationId())) {
            throw new IllegalArgumentException("Cannot restore storage snapshot for a different station");
        }
        if (!capacityByStorageClassKg.equals(snapshot.capacityByStorageClassKg())) {
            throw new IllegalArgumentException("Cannot change station storage capacity during in-place restore");
        }
        replaceContents(snapshot.commodityMassByIdKg(), snapshot.productCountById());
    }

    boolean canAddCommodity(String commodityId, double massKg) {
        CommodityDefinition commodity = requireMassCommodity(commodityId);
        requirePositive(massKg, "massKg");
        return remainingCapacityKg(commodity.storageClassId()) + EPSILON >= massKg;
    }

    void addCommodity(String commodityId, double massKg) {
        if (!canAddCommodity(commodityId, massKg)) {
            throw new IllegalStateException("Insufficient compatible station storage for " + commodityId);
        }
        commodityMassByIdKg.merge(commodityId, massKg, Double::sum);
    }

    void removeCommodity(String commodityId, double massKg) {
        requireMassCommodity(commodityId);
        requirePositive(massKg, "massKg");
        double stored = commodityMassKg(commodityId);
        if (stored + EPSILON < massKg) {
            throw new IllegalStateException("Insufficient station commodity mass: " + commodityId);
        }
        double remaining = stored - massKg;
        if (remaining <= EPSILON) {
            commodityMassByIdKg.remove(commodityId);
        } else {
            commodityMassByIdKg.put(commodityId, remaining);
        }
    }

    boolean canAddProduct(String productContentId, int count) {
        ProductDefinition product = requireProduct(productContentId);
        requirePositiveCount(count);
        double massKg = finiteProduct(product.unitMassKg(), count, "finished product mass");
        return remainingCapacityKg(product.storageClassId()) + EPSILON >= massKg;
    }

    void addProduct(String productContentId, int count) {
        if (!canAddProduct(productContentId, count)) {
            throw new IllegalStateException("Insufficient compatible station storage for " + productContentId);
        }
        productCountById.merge(productContentId, count, Math::addExact);
    }

    void removeProduct(String productContentId, int count) {
        requireProduct(productContentId);
        requirePositiveCount(count);
        int stored = productCount(productContentId);
        if (stored < count) {
            throw new IllegalStateException("Insufficient station product count: " + productContentId);
        }
        int remaining = stored - count;
        if (remaining == 0) {
            productCountById.remove(productContentId);
        } else {
            productCountById.put(productContentId, remaining);
        }
    }

    Map<String, Double> commodityLayerCapacityByStorageClassKg() {
        TreeMap<String, Double> result = new TreeMap<>();
        for (Map.Entry<String, Double> capacity : capacityByStorageClassKg.entrySet()) {
            double productMass = productMassInStorageClassKg(capacity.getKey());
            result.put(capacity.getKey(), Math.max(0d, capacity.getValue() - productMass));
        }
        return Collections.unmodifiableMap(result);
    }

    void replaceContents(Map<String, Double> commodityMasses, Map<String, Integer> productCounts) {
        Objects.requireNonNull(commodityMasses, "commodityMasses");
        Objects.requireNonNull(productCounts, "productCounts");
        TreeMap<String, Double> checkedMasses = new TreeMap<>();
        for (Map.Entry<String, Double> entry : commodityMasses.entrySet()) {
            CommodityDefinition commodity = requireMassCommodity(entry.getKey());
            double massKg = Objects.requireNonNull(entry.getValue(), "commodity mass");
            requireNonNegative(massKg, "commodity mass");
            if (massKg > EPSILON) {
                requireCapacityEntry(commodity.storageClassId(), commodity.id());
                checkedMasses.put(commodity.id(), massKg);
            }
        }
        TreeMap<String, Integer> checkedProducts = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : productCounts.entrySet()) {
            ProductDefinition product = requireProduct(entry.getKey());
            int count = Objects.requireNonNull(entry.getValue(), "product count");
            if (count < 0) {
                throw new IllegalArgumentException("product count must be non-negative");
            }
            if (count > 0) {
                requireCapacityEntry(product.storageClassId(), product.contentId());
                checkedProducts.put(product.contentId(), count);
            }
        }

        Map<String, Double> oldMasses = new TreeMap<>(commodityMassByIdKg);
        Map<String, Integer> oldProducts = new TreeMap<>(productCountById);
        commodityMassByIdKg.clear();
        commodityMassByIdKg.putAll(checkedMasses);
        productCountById.clear();
        productCountById.putAll(checkedProducts);
        try {
            validateCapacities();
        } catch (RuntimeException exception) {
            commodityMassByIdKg.clear();
            commodityMassByIdKg.putAll(oldMasses);
            productCountById.clear();
            productCountById.putAll(oldProducts);
            throw exception;
        }
    }

    private double productMassInStorageClassKg(String storageClassId) {
        double massKg = 0d;
        for (Map.Entry<String, Integer> entry : productCountById.entrySet()) {
            ProductDefinition product = products.findProduct(entry.getKey());
            if (product != null && product.storageClassId().equals(storageClassId)) {
                massKg += product.unitMassKg() * entry.getValue();
            }
        }
        return massKg;
    }

    private void validateCapacities() {
        for (Map.Entry<String, Double> capacity : capacityByStorageClassKg.entrySet()) {
            if (usedCapacityKg(capacity.getKey()) > capacity.getValue() + EPSILON) {
                throw new IllegalArgumentException("Station inventory exceeds storage capacity: " + capacity.getKey());
            }
        }
    }

    private CommodityDefinition requireMassCommodity(String commodityId) {
        CommodityDefinition commodity = ontology.findCommodity(requireText(commodityId, "commodityId"));
        if (commodity == null || commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
            throw new IllegalArgumentException("Unknown or non-mass Stage-18 commodity: " + commodityId);
        }
        return commodity;
    }

    private ProductDefinition requireProduct(String productContentId) {
        ProductDefinition product = products.findProduct(requireText(productContentId, "productContentId"));
        if (product == null) {
            throw new IllegalArgumentException("Unknown manufactured product: " + productContentId);
        }
        return product;
    }

    private void requireKnownStorageClass(String storageClassId) {
        String checked = requireText(storageClassId, "storageClassId");
        if (ontology.findStorageClass(checked) == null) {
            throw new IllegalArgumentException("Unknown storage class: " + checked);
        }
    }

    private void requireCapacityEntry(String storageClassId, String subject) {
        if (!capacityByStorageClassKg.containsKey(storageClassId)) {
            throw new IllegalArgumentException("No compatible station storage for " + subject);
        }
    }

    /**
     * Immutable persistence-friendly physical station-storage snapshot.
     *
     * @param stationId stable station/depot identity
     * @param capacityByStorageClassKg physical capacity by storage class
     * @param commodityMassByIdKg Stage-18 commodity mass by ID
     * @param productCountById finished-product count by content ID
     */
    public record StationStorageSnapshot(
            String stationId,
            Map<String, Double> capacityByStorageClassKg,
            Map<String, Double> commodityMassByIdKg,
            Map<String, Integer> productCountById) {
        /**
         * Freezes one storage snapshot.
         *
         * @param stationId stable station/depot identity
         * @param capacityByStorageClassKg capacity by storage class
         * @param commodityMassByIdKg commodity mass map
         * @param productCountById finished-product count map
         */
        public StationStorageSnapshot {
            stationId = requireText(stationId, "stationId");
            capacityByStorageClassKg = immutableDoubleMap(capacityByStorageClassKg, "capacityByStorageClassKg");
            commodityMassByIdKg = immutableDoubleMap(commodityMassByIdKg, "commodityMassByIdKg");
            productCountById = immutableIntegerMap(productCountById);
        }
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source, String name) {
        Objects.requireNonNull(source, name);
        TreeMap<String, Double> copy = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            requireText(entry.getKey(), name + " key");
            double value = Objects.requireNonNull(entry.getValue(), name + " value");
            requireNonNegative(value, name + " value");
            if (value > EPSILON || name.equals("capacityByStorageClassKg")) {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableIntegerMap(Map<String, Integer> source) {
        Objects.requireNonNull(source, "productCountById");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            requireText(entry.getKey(), "productCountById key");
            int value = Objects.requireNonNull(entry.getValue(), "productCountById value");
            if (value < 0) {
                throw new IllegalArgumentException("productCountById values must be non-negative");
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

    private static void requirePositiveCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    private static double finiteProduct(double left, double right, String name) {
        double product = left * right;
        if (!Double.isFinite(product)) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return product;
    }
}

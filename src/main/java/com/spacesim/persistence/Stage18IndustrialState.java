package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Immutable persistent Stage-18 industrial-world extension state.
 *
 * <p>The core {@link GameState} already persists the ordinary world and Stage-17.5 ship engineering
 * state. This extension persists only the kg-native industrial state introduced by Stage 18:
 * finite resource/salvage sources, station physical inventory, installed facilities/yards and
 * persistent long-running construction/process orders. Legacy integer inventory is intentionally
 * absent and is never reinterpreted here.</p>
 *
 * @param schemaVersion Stage-18 industrial-state schema version
 * @param contentFingerprint semantic fingerprint of the Stage-17.5/18 industrial catalogs
 * @param simulationTick fixed-tick checkpoint associated with this industrial snapshot
 * @param sources finite natural/salvage physical sources
 * @param stationStorages canonical Stage-18F station/depot storage snapshots
 * @param facilities installed Stage-18E facility states bound to owning stations
 * @param yards installed Stage-18G yard states bound to owning stations
 * @param constructionOrders persistent Stage-18H facility-construction orders
 * @param processOrders persistent queued/in-progress Stage-18B-D industrial process orders
 */
public record Stage18IndustrialState(
        int schemaVersion,
        String contentFingerprint,
        long simulationTick,
        List<PhysicalSourceSnapshot> sources,
        List<StationStorageSnapshot> stationStorages,
        List<FacilityInstallationSnapshot> facilities,
        List<YardInstallationSnapshot> yards,
        List<ConstructionOrderSnapshot> constructionOrders,
        List<ProcessOrderSnapshot> processOrders) {
    /** Current Stage-18 industrial persistence schema. */
    public static final int CURRENT_VERSION = 1;
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final double EPSILON = 1e-9d;

    /**
     * Validates, sorts and freezes one deterministic industrial snapshot.
     *
     * @param schemaVersion industrial schema version
     * @param contentFingerprint content semantic fingerprint
     * @param simulationTick non-negative fixed simulation tick
     * @param sources physical sources
     * @param stationStorages station storage snapshots
     * @param facilities installed facility states
     * @param yards installed yard states
     * @param constructionOrders construction orders
     * @param processOrders process orders
     */
    public Stage18IndustrialState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 industrial schema: " + schemaVersion);
        }
        contentFingerprint = requireFingerprint(contentFingerprint);
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("simulationTick must be non-negative");
        }
        sources = sortedUnique(
                sources, Comparator.comparing(PhysicalSourceSnapshot::sourceId),
                PhysicalSourceSnapshot::sourceId, "physical source");
        stationStorages = sortedUnique(
                stationStorages, Comparator.comparing(StationStorageSnapshot::stationId),
                StationStorageSnapshot::stationId, "station storage");
        facilities = sortedUnique(
                facilities,
                Comparator.comparing((FacilityInstallationSnapshot value) -> value.state().facilityInstanceId()),
                value -> value.state().facilityInstanceId(),
                "facility instance");
        yards = sortedUnique(
                yards,
                Comparator.comparing((YardInstallationSnapshot value) -> value.state().yardInstanceId()),
                value -> value.state().yardInstanceId(),
                "yard instance");
        constructionOrders = sortedUnique(
                constructionOrders, Comparator.comparing(ConstructionOrderSnapshot::orderId),
                ConstructionOrderSnapshot::orderId, "construction order");
        processOrders = sortedUnique(
                processOrders, Comparator.comparing(ProcessOrderSnapshot::orderId),
                ProcessOrderSnapshot::orderId, "process order");
        validateReferences(stationStorages, facilities, yards, constructionOrders, processOrders, sources);
    }

    /**
     * Returns an empty neutral industrial snapshot for a world that has no Stage-18 state yet.
     *
     * @param simulationTick fixed simulation tick
     * @return current-schema empty snapshot bound to current industrial content
     */
    public static Stage18IndustrialState empty(long simulationTick) {
        return new Stage18IndustrialState(
                CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(),
                simulationTick,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Persistence form of one mutable Stage-18B finite source.
     *
     * @param sourceId stable source identity
     * @param sourceKind natural occurrence or bounded salvage stream
     * @param sourceTypeId occurrence/salvage type ID
     * @param environment physical extraction environment
     * @param outputCommodityId recovered commodity ID
     * @param initialAccessibleMassKg initial gross source mass
     * @param remainingAccessibleMassKg current remaining gross source mass
     * @param gradeFraction resource grade in {@code (0,1]}
     * @param sourceRecoveryFraction source-side recovery fraction in {@code (0,1]}
     * @param requiredCapabilityTags source-specific capability requirements
     */
    public record PhysicalSourceSnapshot(
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
        /**
         * Validates one source snapshot.
         *
         * @param sourceId source identity
         * @param sourceKind source kind
         * @param sourceTypeId occurrence/salvage type
         * @param environment extraction environment
         * @param outputCommodityId output commodity
         * @param initialAccessibleMassKg initial source mass
         * @param remainingAccessibleMassKg remaining source mass
         * @param gradeFraction grade
         * @param sourceRecoveryFraction recovery fraction
         * @param requiredCapabilityTags source capabilities
         */
        public PhysicalSourceSnapshot {
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(sourceKind, "sourceKind");
            sourceTypeId = requireText(sourceTypeId, "sourceTypeId");
            Objects.requireNonNull(environment, "environment");
            outputCommodityId = requireText(outputCommodityId, "outputCommodityId");
            requirePositive(initialAccessibleMassKg, "initialAccessibleMassKg");
            requireNonNegative(remainingAccessibleMassKg, "remainingAccessibleMassKg");
            if (remainingAccessibleMassKg > initialAccessibleMassKg + EPSILON) {
                throw new IllegalArgumentException("remaining source mass exceeds initial mass");
            }
            requireFraction(gradeFraction, "gradeFraction");
            requireFraction(sourceRecoveryFraction, "sourceRecoveryFraction");
            requiredCapabilityTags = immutableSet(requiredCapabilityTags, "requiredCapabilityTags");
        }

        /**
         * Captures one mutable runtime source.
         *
         * @param source finite Stage-18B source
         * @return immutable persistence snapshot
         */
        public static PhysicalSourceSnapshot capture(PhysicalSourceState source) {
            PhysicalSourceState checked = Objects.requireNonNull(source, "source");
            return new PhysicalSourceSnapshot(
                    checked.sourceId(), checked.sourceKind(), checked.sourceTypeId(), checked.environment(),
                    checked.outputCommodityId(), checked.initialAccessibleMassKg(),
                    checked.remainingAccessibleMassKg(), checked.gradeFraction(),
                    checked.sourceRecoveryFraction(), checked.requiredCapabilityTags());
        }

        /** @return restored mutable Stage-18B physical source */
        public PhysicalSourceState restore() {
            return new PhysicalSourceState(
                    sourceId, sourceKind, sourceTypeId, environment, outputCommodityId,
                    initialAccessibleMassKg, remainingAccessibleMassKg, gradeFraction,
                    sourceRecoveryFraction, requiredCapabilityTags);
        }
    }

    /**
     * Station ownership wrapper for one installed Stage-18E facility state.
     *
     * @param stationId owning station/site identity
     * @param state installed facility state
     */
    public record FacilityInstallationSnapshot(String stationId, InstalledFacilityState state) {
        /**
         * Validates one facility installation.
         *
         * @param stationId owning station ID
         * @param state installed facility state
         */
        public FacilityInstallationSnapshot {
            stationId = requireText(stationId, "stationId");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * Station ownership wrapper for one installed Stage-18G yard state.
     *
     * @param stationId owning station/site identity
     * @param state installed yard state
     */
    public record YardInstallationSnapshot(String stationId, InstalledYardState state) {
        /**
         * Validates one yard installation.
         *
         * @param stationId owning station ID
         * @param state installed yard state
         */
        public YardInstallationSnapshot {
            stationId = requireText(stationId, "stationId");
            Objects.requireNonNull(state, "state");
        }
    }

    /** Type of persistent Stage-18B-D process order. */
    public enum ProcessKind {
        /** Finite source extraction order. */ EXTRACTION,
        /** Refining/material-production order. */ REFINING,
        /** Bulk component manufacturing order. */ COMPONENT_MANUFACTURING,
        /** Countable module/ammunition manufacturing order. */ PRODUCT_MANUFACTURING
    }

    /**
     * Persistent queue/progress seam for an industrial process that may span save/load checkpoints.
     *
     * @param orderId stable process-order identity
     * @param kind process family
     * @param operationId extraction method, recipe or product ID
     * @param stationId executing station ID
     * @param sourceId source ID for extraction, otherwise empty text
     * @param requestedAmount requested kg amount or product-equivalent batch amount
     * @param requestedUnits count for product manufacturing, zero otherwise
     * @param completedFraction scheduling progress in {@code [0,1]}
     * @param reservedCommodityMassByIdKg physically reserved commodity mass
     * @param reservedProductCountById physically reserved finished-product counts
     */
    public record ProcessOrderSnapshot(
            String orderId,
            ProcessKind kind,
            String operationId,
            String stationId,
            String sourceId,
            double requestedAmount,
            int requestedUnits,
            double completedFraction,
            Map<String, Double> reservedCommodityMassByIdKg,
            Map<String, Integer> reservedProductCountById) {
        /**
         * Validates and freezes one process order.
         *
         * @param orderId order identity
         * @param kind process family
         * @param operationId method/recipe/product ID
         * @param stationId executing station
         * @param sourceId extraction source or empty string
         * @param requestedAmount requested batch amount
         * @param requestedUnits requested unit count
         * @param completedFraction scheduling progress
         * @param reservedCommodityMassByIdKg reserved commodity mass
         * @param reservedProductCountById reserved products
         */
        public ProcessOrderSnapshot {
            orderId = requireText(orderId, "orderId");
            Objects.requireNonNull(kind, "kind");
            operationId = requireText(operationId, "operationId");
            stationId = requireText(stationId, "stationId");
            sourceId = sourceId == null ? "" : sourceId;
            requireNonNegative(requestedAmount, "requestedAmount");
            if (requestedUnits < 0) {
                throw new IllegalArgumentException("requestedUnits must be non-negative");
            }
            requireFractionInclusive(completedFraction, "completedFraction");
            reservedCommodityMassByIdKg = immutableDoubleMap(
                    reservedCommodityMassByIdKg, "reservedCommodityMassByIdKg");
            reservedProductCountById = immutableIntegerMap(reservedProductCountById);
            if (kind == ProcessKind.EXTRACTION && sourceId.isBlank()) {
                throw new IllegalArgumentException("extraction process order requires sourceId");
            }
            if (kind != ProcessKind.PRODUCT_MANUFACTURING && requestedUnits != 0) {
                throw new IllegalArgumentException("requestedUnits is only valid for product manufacturing");
            }
        }
    }

    private static void validateReferences(
            List<StationStorageSnapshot> stations,
            List<FacilityInstallationSnapshot> facilities,
            List<YardInstallationSnapshot> yards,
            List<ConstructionOrderSnapshot> constructionOrders,
            List<ProcessOrderSnapshot> processOrders,
            List<PhysicalSourceSnapshot> sources) {
        Set<String> stationIds = new HashSet<>();
        stations.forEach(value -> stationIds.add(value.stationId()));
        for (FacilityInstallationSnapshot facility : facilities) {
            requireStation(stationIds, facility.stationId(), "facility");
        }
        for (YardInstallationSnapshot yard : yards) {
            requireStation(stationIds, yard.stationId(), "yard");
        }
        for (ConstructionOrderSnapshot order : constructionOrders) {
            requireStation(stationIds, order.stationId(), "construction order");
        }
        Set<String> sourceIds = new HashSet<>();
        sources.forEach(value -> sourceIds.add(value.sourceId()));
        for (ProcessOrderSnapshot order : processOrders) {
            requireStation(stationIds, order.stationId(), "process order");
            if (order.kind() == ProcessKind.EXTRACTION && !sourceIds.contains(order.sourceId())) {
                throw new IllegalArgumentException("Process order references unknown source: " + order.sourceId());
            }
        }
    }

    private static void requireStation(Set<String> stationIds, String stationId, String subject) {
        if (!stationIds.contains(stationId)) {
            throw new IllegalArgumentException(subject + " references unknown station: " + stationId);
        }
    }

    private static <T> List<T> sortedUnique(
            List<T> source,
            Comparator<T> comparator,
            java.util.function.Function<T, String> id,
            String label) {
        Objects.requireNonNull(source, label + " list");
        List<T> copy = new ArrayList<>(source);
        copy.forEach(value -> Objects.requireNonNull(value, label));
        copy.sort(comparator);
        TreeSet<String> ids = new TreeSet<>();
        for (T value : copy) {
            String key = requireText(id.apply(value), label + " id");
            if (!ids.add(key)) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + key);
            }
        }
        return List.copyOf(copy);
    }

    private static String requireFingerprint(String value) {
        if (value == null || !FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException("Stage-18 content fingerprint must be lowercase SHA-256 hex");
        }
        return value;
    }

    private static Set<String> immutableSet(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, name + " entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> source, String name) {
        Objects.requireNonNull(source, name);
        TreeMap<String, Double> copy = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), name + " key");
            double value = Objects.requireNonNull(entry.getValue(), name + " value");
            requireNonNegative(value, name + " value");
            if (value > EPSILON) {
                copy.put(key, value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableIntegerMap(Map<String, Integer> source) {
        Objects.requireNonNull(source, "reservedProductCountById");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), "reservedProductCountById key");
            int value = Objects.requireNonNull(entry.getValue(), "reservedProductCountById value");
            if (value < 0) {
                throw new IllegalArgumentException("reserved product count must be non-negative");
            }
            if (value > 0) {
                copy.put(key, value);
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
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0,1]");
        }
    }

    private static void requireFractionInclusive(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }
}

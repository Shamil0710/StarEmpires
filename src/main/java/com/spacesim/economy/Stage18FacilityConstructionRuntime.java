package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityConstructionCatalog;
import com.spacesim.content.Stage18FacilityConstructionCatalog.ConstructionProfileDefinition;
import com.spacesim.content.Stage18FacilityConstructionCatalog.FacilityConstructionDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.Status;
import com.spacesim.economy.Stage18StationIndustrialNode.InstalledFacilityReference;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-18H kg-native physical facility-construction settlement.
 *
 * <p>The runtime follows the Stage-16 construction invariant without reinterpreting the legacy
 * integer construction inventory. Material is physically delivered from canonical Stage-18F
 * station storage into a persistent-ready order; only after all required kilograms are delivered
 * may finite engineering work advance. Completion yields an ordinary Stage-18E installed-facility
 * reference for later world/persistence integration.</p>
 */
public final class Stage18FacilityConstructionRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18FacilityConstructionCatalog construction;
    private final Stage18FacilityCatalog facilities;
    private final Stage18ResourceOntologyCatalog ontology;

    /**
     * Creates the Stage-18H physical construction runtime.
     *
     * @param construction authoritative Stage-18H construction catalog
     * @param facilities authoritative Stage-18E facility catalog
     * @param ontology authoritative Stage-18 resource ontology
     */
    public Stage18FacilityConstructionRuntime(
            Stage18FacilityConstructionCatalog construction,
            Stage18FacilityCatalog facilities,
            Stage18ResourceOntologyCatalog ontology) {
        this.construction = Objects.requireNonNull(construction, "construction");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
    }

    /** Stable lifecycle state for one physical facility-construction order. */
    public enum OrderStatus {
        /** More physical materials/components must be delivered. */ AWAITING_MATERIALS,
        /** All materials are delivered and engineering work may begin. */ READY_FOR_WORK,
        /** At least some finite engineering work has been completed. */ BUILDING,
        /** Materials and engineering work are fully settled. */ COMPLETE,
        /** Explicitly cancelled before engineering work began, with delivered material returned. */ CANCELLED
    }

    /** Stable result of one material delivery attempt. */
    public enum DeliveryStatus {
        /** Physical mass moved from station storage into the construction order. */ DELIVERED,
        /** Request is invalid, remote, or the order is terminal. */ INVALID_REQUEST,
        /** Commodity is not part of this construction bill. */ NOT_REQUIRED,
        /** Requirement is already fully delivered. */ ALREADY_FULFILLED,
        /** Source station lacks the requested accepted physical mass. */ INSUFFICIENT_STOCK
    }

    /** Stable result of one finite engineering-work advancement. */
    public enum WorkStatus {
        /** Finite construction work advanced but the order remains incomplete. */ ADVANCED,
        /** Final required work was completed. */ COMPLETED,
        /** Physical material delivery is incomplete. */ MATERIALS_INCOMPLETE,
        /** Construction capability lacks an authored capability tag. */ MISSING_CAPABILITY,
        /** Shared interval has no remaining engineering work. */ INSUFFICIENT_WORK,
        /** Request is invalid or the order is terminal. */ INVALID_REQUEST
    }

    /**
     * Persistent-ready mutable physical facility construction order.
     *
     * @param orderId stable construction-order identity
     * @param facilityInstanceId stable identity of the facility that will be installed
     * @param facilityDefinitionId Stage-18E facility definition to install
     * @param stationId target physical station/site identity
     * @param locationTag target physical installation location
     * @param requiredMassByCommodityKg immutable physical bill
     * @param deliveredMassByCommodityKg current delivered/committed physical mass
     * @param requiredWorkSeconds total engineering work
     * @param completedWorkSeconds completed engineering work
     * @param status current lifecycle state
     */
    public record ConstructionOrderSnapshot(
            String orderId,
            String facilityInstanceId,
            String facilityDefinitionId,
            String stationId,
            String locationTag,
            Map<String, Double> requiredMassByCommodityKg,
            Map<String, Double> deliveredMassByCommodityKg,
            double requiredWorkSeconds,
            double completedWorkSeconds,
            OrderStatus status) {
        /**
         * Validates and freezes one construction order snapshot.
         *
         * @param orderId order identity
         * @param facilityInstanceId future installed facility identity
         * @param facilityDefinitionId Stage-18E facility definition
         * @param stationId target station/site identity
         * @param locationTag target location
         * @param requiredMassByCommodityKg physical required mass
         * @param deliveredMassByCommodityKg current delivered mass
         * @param requiredWorkSeconds total required engineering work
         * @param completedWorkSeconds completed engineering work
         * @param status lifecycle state
         */
        public ConstructionOrderSnapshot {
            orderId = requireText(orderId, "orderId");
            facilityInstanceId = requireText(facilityInstanceId, "facilityInstanceId");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            stationId = requireText(stationId, "stationId");
            locationTag = requireText(locationTag, "locationTag");
            requiredMassByCommodityKg = immutableMassMap(requiredMassByCommodityKg, "requiredMassByCommodityKg");
            deliveredMassByCommodityKg = immutableMassMapAllowEmpty(
                    deliveredMassByCommodityKg, "deliveredMassByCommodityKg");
            requirePositive(requiredWorkSeconds, "requiredWorkSeconds");
            requireNonNegative(completedWorkSeconds, "completedWorkSeconds");
            if (completedWorkSeconds > requiredWorkSeconds + EPSILON) {
                throw new IllegalArgumentException("completed construction work exceeds required work");
            }
            Objects.requireNonNull(status, "status");
            for (Map.Entry<String, Double> entry : deliveredMassByCommodityKg.entrySet()) {
                double required = requiredMassByCommodityKg.getOrDefault(entry.getKey(), -1d);
                if (required < 0d || entry.getValue() > required + EPSILON) {
                    throw new IllegalArgumentException("delivered construction mass exceeds requirement: " + entry.getKey());
                }
            }
        }

        /** @return total installed mass represented by this physical construction bill */
        public double installedMassKg() {
            return requiredMassByCommodityKg.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        /** @return whether every physical input has been fully delivered */
        public boolean materialsFulfilled() {
            for (Map.Entry<String, Double> required : requiredMassByCommodityKg.entrySet()) {
                if (deliveredMassByCommodityKg.getOrDefault(required.getKey(), 0d) + EPSILON < required.getValue()) {
                    return false;
                }
            }
            return true;
        }

        /** @return remaining engineering work-seconds */
        public double remainingWorkSeconds() {
            return Math.max(0d, requiredWorkSeconds - completedWorkSeconds);
        }
    }

    /**
     * Finite construction capability assembled from active Stage-18E facilities.
     *
     * @param capabilityId diagnostic capability identity
     * @param capabilityTags union of active Stage-18E capability tags
     * @param engineeringWorkRate summed active engineering work-seconds per simulation second
     */
    public record ConstructionCapability(
            String capabilityId,
            Set<String> capabilityTags,
            double engineeringWorkRate) {
        /**
         * Validates and freezes a construction capability.
         *
         * @param capabilityId diagnostic identity
         * @param capabilityTags exposed fabrication capabilities
         * @param engineeringWorkRate finite work rate
         */
        public ConstructionCapability {
            capabilityId = requireText(capabilityId, "capabilityId");
            capabilityTags = immutableSet(capabilityTags);
            requireNonNegative(engineeringWorkRate, "engineeringWorkRate");
        }

        /**
         * Opens one shared finite construction-work interval.
         *
         * @param durationSeconds positive simulation duration
         * @return mutable finite interval work budget
         */
        public WorkBudget openInterval(double durationSeconds) {
            requirePositive(durationSeconds, "durationSeconds");
            double work = engineeringWorkRate * durationSeconds;
            if (!Double.isFinite(work) || work < 0d) {
                throw new IllegalArgumentException("construction interval work overflowed finite range");
            }
            return new WorkBudget(capabilityTags, work);
        }
    }

    /** Shared finite work budget that cannot be reused by multiple orders. */
    public static final class WorkBudget {
        private final Set<String> capabilityTags;
        private double remainingWorkSeconds;

        private WorkBudget(Set<String> capabilityTags, double remainingWorkSeconds) {
            this.capabilityTags = immutableSet(capabilityTags);
            if (!Double.isFinite(remainingWorkSeconds) || remainingWorkSeconds < 0d) {
                throw new IllegalArgumentException("construction interval work must be finite and non-negative");
            }
            this.remainingWorkSeconds = remainingWorkSeconds;
        }

        /** @return immutable fabrication capabilities available in this interval */
        public Set<String> capabilityTags() {
            return capabilityTags;
        }

        /** @return uncommitted engineering work-seconds */
        public double remainingWorkSeconds() {
            return remainingWorkSeconds;
        }

        private void consume(double workSeconds) {
            remainingWorkSeconds = Math.max(0d, remainingWorkSeconds - workSeconds);
        }
    }

    /**
     * One material-delivery result.
     *
     * @param status stable outcome
     * @param acceptedMassKg mass physically committed to the construction site
     * @param order resulting order state
     */
    public record DeliveryResult(
            DeliveryStatus status,
            double acceptedMassKg,
            ConstructionOrderSnapshot order) {
        /**
         * Validates a delivery result.
         *
         * @param status stable status
         * @param acceptedMassKg committed mass
         * @param order resulting order
         */
        public DeliveryResult {
            Objects.requireNonNull(status, "status");
            requireNonNegative(acceptedMassKg, "acceptedMassKg");
            Objects.requireNonNull(order, "order");
        }
    }

    /**
     * One finite engineering advancement result.
     *
     * @param status stable outcome
     * @param appliedWorkSeconds committed engineering work
     * @param order resulting order state
     * @param installedFacility completed ordinary Stage-18E reference, otherwise {@code null}
     */
    public record WorkResult(
            WorkStatus status,
            double appliedWorkSeconds,
            ConstructionOrderSnapshot order,
            InstalledFacilityReference installedFacility) {
        /**
         * Validates a work result.
         *
         * @param status stable status
         * @param appliedWorkSeconds applied finite work
         * @param order resulting order state
         * @param installedFacility installed facility on completion
         */
        public WorkResult {
            Objects.requireNonNull(status, "status");
            requireNonNegative(appliedWorkSeconds, "appliedWorkSeconds");
            Objects.requireNonNull(order, "order");
            if (status == WorkStatus.COMPLETED && installedFacility == null) {
                throw new IllegalArgumentException("completed construction requires installed facility reference");
            }
            if (status != WorkStatus.COMPLETED && installedFacility != null) {
                throw new IllegalArgumentException("incomplete construction must not install facility");
            }
        }
    }

    /**
     * Creates a new physical construction order with zero delivered material/work.
     *
     * @param orderId stable order identity
     * @param facilityInstanceId future installed facility identity
     * @param facilityDefinitionId Stage-18E facility definition
     * @param stationId target station/site identity
     * @param locationTag physical installation location
     * @return persistent-ready construction order
     */
    public ConstructionOrderSnapshot createOrder(
            String orderId,
            String facilityInstanceId,
            String facilityDefinitionId,
            String stationId,
            String locationTag) {
        Stage18FacilityCatalog.FacilityDefinition facility = facilities.findFacility(
                requireText(facilityDefinitionId, "facilityDefinitionId"));
        if (facility == null) {
            throw new IllegalArgumentException("Unknown Stage-18E facility: " + facilityDefinitionId);
        }
        if (!facility.allowedLocationTags().contains(requireText(locationTag, "locationTag"))) {
            throw new IllegalArgumentException("Facility cannot be installed at location: " + locationTag);
        }
        FacilityConstructionDefinition definition = construction.findFacility(facilityDefinitionId);
        if (definition == null) {
            throw new IllegalArgumentException("No Stage-18H physical construction definition: " + facilityDefinitionId);
        }
        return new ConstructionOrderSnapshot(
                orderId,
                facilityInstanceId,
                facilityDefinitionId,
                stationId,
                locationTag,
                construction.requiredMassByCommodityKg(facilityDefinitionId),
                Map.of(),
                construction.totalWorkSeconds(facilityDefinitionId),
                0d,
                OrderStatus.AWAITING_MATERIALS);
    }

    /**
     * Physically delivers one required commodity from canonical station storage to the order.
     *
     * @param order current order snapshot
     * @param sourceStorage canonical source station storage at the construction site
     * @param commodityId required Stage-18 commodity
     * @param requestedMassKg requested delivery mass
     * @return delivery result with updated persistent-ready order state
     */
    public DeliveryResult deliver(
            ConstructionOrderSnapshot order,
            Stage18StationStorage sourceStorage,
            String commodityId,
            double requestedMassKg) {
        ConstructionOrderSnapshot checked = Objects.requireNonNull(order, "order");
        Stage18StationStorage storage = Objects.requireNonNull(sourceStorage, "sourceStorage");
        if (checked.status() == OrderStatus.COMPLETE || checked.status() == OrderStatus.CANCELLED
                || !checked.stationId().equals(storage.stationId())
                || !Double.isFinite(requestedMassKg) || requestedMassKg <= 0d) {
            return new DeliveryResult(DeliveryStatus.INVALID_REQUEST, 0d, checked);
        }
        String id = requireText(commodityId, "commodityId");
        Double required = checked.requiredMassByCommodityKg().get(id);
        if (required == null) {
            return new DeliveryResult(DeliveryStatus.NOT_REQUIRED, 0d, checked);
        }
        double delivered = checked.deliveredMassByCommodityKg().getOrDefault(id, 0d);
        double remaining = Math.max(0d, required - delivered);
        if (remaining <= EPSILON) {
            return new DeliveryResult(DeliveryStatus.ALREADY_FULFILLED, 0d, checked);
        }
        double accepted = Math.min(requestedMassKg, remaining);
        if (storage.commodityMassKg(id) + EPSILON < accepted) {
            return new DeliveryResult(DeliveryStatus.INSUFFICIENT_STOCK, 0d, checked);
        }
        storage.removeCommodity(id, accepted);
        TreeMap<String, Double> deliveredMap = new TreeMap<>(checked.deliveredMassByCommodityKg());
        deliveredMap.put(id, delivered + accepted);
        ConstructionOrderSnapshot updated = replace(
                checked,
                deliveredMap,
                checked.completedWorkSeconds(),
                allMaterialsFulfilled(checked.requiredMassByCommodityKg(), deliveredMap)
                        ? OrderStatus.READY_FOR_WORK
                        : OrderStatus.AWAITING_MATERIALS);
        return new DeliveryResult(DeliveryStatus.DELIVERED, accepted, updated);
    }

    /**
     * Combines active Stage-18E facility snapshots into one physical construction capability.
     *
     * @param capabilityId diagnostic capability identity
     * @param snapshots current installed-facility projections
     * @return construction capability using only active facilities
     */
    public ConstructionCapability projectCapability(
            String capabilityId,
            java.util.List<FacilityCapabilitySnapshot> snapshots) {
        TreeSet<String> tags = new TreeSet<>();
        double workRate = 0d;
        for (FacilityCapabilitySnapshot snapshot : Objects.requireNonNull(snapshots, "snapshots")) {
            FacilityCapabilitySnapshot checked = Objects.requireNonNull(snapshot, "facility snapshot");
            if (checked.status() == Status.ACTIVE) {
                tags.addAll(checked.capabilityTags());
                workRate += checked.effectiveEngineeringWorkRate();
            }
        }
        if (!Double.isFinite(workRate)) {
            throw new IllegalArgumentException("combined construction work rate overflowed finite range");
        }
        return new ConstructionCapability(capabilityId, tags, workRate);
    }

    /**
     * Applies finite work to a fully supplied construction order.
     *
     * @param order current order state
     * @param budget shared finite interval work budget
     * @return advancement result and installed facility reference on completion
     */
    public WorkResult advanceWork(ConstructionOrderSnapshot order, WorkBudget budget) {
        ConstructionOrderSnapshot checked = Objects.requireNonNull(order, "order");
        WorkBudget checkedBudget = Objects.requireNonNull(budget, "budget");
        if (checked.status() == OrderStatus.COMPLETE || checked.status() == OrderStatus.CANCELLED) {
            return new WorkResult(WorkStatus.INVALID_REQUEST, 0d, checked, null);
        }
        if (!checked.materialsFulfilled()) {
            return new WorkResult(WorkStatus.MATERIALS_INCOMPLETE, 0d, checked, null);
        }
        FacilityConstructionDefinition definition = construction.findFacility(checked.facilityDefinitionId());
        ConstructionProfileDefinition profile = construction.findProfile(definition.profileId());
        if (!checkedBudget.capabilityTags().containsAll(profile.requiredCapabilityTags())) {
            return new WorkResult(WorkStatus.MISSING_CAPABILITY, 0d, checked, null);
        }
        if (checkedBudget.remainingWorkSeconds() <= EPSILON) {
            return new WorkResult(WorkStatus.INSUFFICIENT_WORK, 0d, checked, null);
        }
        double applied = Math.min(checked.remainingWorkSeconds(), checkedBudget.remainingWorkSeconds());
        checkedBudget.consume(applied);
        double completedWork = checked.completedWorkSeconds() + applied;
        boolean complete = completedWork + EPSILON >= checked.requiredWorkSeconds();
        ConstructionOrderSnapshot updated = replace(
                checked,
                checked.deliveredMassByCommodityKg(),
                complete ? checked.requiredWorkSeconds() : completedWork,
                complete ? OrderStatus.COMPLETE : OrderStatus.BUILDING);
        if (complete) {
            return new WorkResult(
                    WorkStatus.COMPLETED,
                    applied,
                    updated,
                    new InstalledFacilityReference(updated.facilityInstanceId(), updated.facilityDefinitionId()));
        }
        return new WorkResult(WorkStatus.ADVANCED, applied, updated, null);
    }

    /**
     * Cancels an order before engineering work begins and atomically returns delivered kilograms.
     *
     * <p>Once any construction work has been applied, cancellation must use an explicit physical
     * destruction/salvage fate instead of recovering all delivered material as pristine stock.</p>
     *
     * @param order order to cancel
     * @param destination same-station canonical storage receiving returned material
     * @return cancelled order state
     */
    public ConstructionOrderSnapshot cancelAndReturn(
            ConstructionOrderSnapshot order,
            Stage18StationStorage destination) {
        ConstructionOrderSnapshot checked = Objects.requireNonNull(order, "order");
        Stage18StationStorage storage = Objects.requireNonNull(destination, "destination");
        if (checked.status() == OrderStatus.COMPLETE || checked.status() == OrderStatus.CANCELLED
                || checked.completedWorkSeconds() > EPSILON) {
            throw new IllegalStateException("Construction order cannot return pristine materials after work begins");
        }
        if (!checked.stationId().equals(storage.stationId())) {
            throw new IllegalArgumentException("Construction cancellation destination must be the construction station");
        }
        TreeMap<String, Double> returnMassByStorageClass = new TreeMap<>();
        for (Map.Entry<String, Double> entry : checked.deliveredMassByCommodityKg().entrySet()) {
            Stage18ResourceOntologyCatalog.CommodityDefinition commodity = ontology.findCommodity(entry.getKey());
            if (commodity == null) {
                throw new IllegalStateException("Unknown delivered construction commodity: " + entry.getKey());
            }
            returnMassByStorageClass.merge(commodity.storageClassId(), entry.getValue(), Double::sum);
        }
        for (Map.Entry<String, Double> entry : returnMassByStorageClass.entrySet()) {
            if (storage.remainingCapacityKg(entry.getKey()) + EPSILON < entry.getValue()) {
                throw new IllegalStateException("Cannot atomically return construction storage class: " + entry.getKey());
            }
        }
        for (Map.Entry<String, Double> entry : checked.deliveredMassByCommodityKg().entrySet()) {
            storage.addCommodity(entry.getKey(), entry.getValue());
        }
        return replace(checked, Map.of(), 0d, OrderStatus.CANCELLED);
    }

    private static ConstructionOrderSnapshot replace(
            ConstructionOrderSnapshot source,
            Map<String, Double> delivered,
            double completedWork,
            OrderStatus status) {
        return new ConstructionOrderSnapshot(
                source.orderId(),
                source.facilityInstanceId(),
                source.facilityDefinitionId(),
                source.stationId(),
                source.locationTag(),
                source.requiredMassByCommodityKg(),
                delivered,
                source.requiredWorkSeconds(),
                completedWork,
                status);
    }

    private static boolean allMaterialsFulfilled(
            Map<String, Double> required,
            Map<String, Double> delivered) {
        for (Map.Entry<String, Double> entry : required.entrySet()) {
            if (delivered.getOrDefault(entry.getKey(), 0d) + EPSILON < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Double> immutableMassMap(Map<String, Double> source, String name) {
        Map<String, Double> result = immutableMassMapAllowEmpty(source, name);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return result;
    }

    private static Map<String, Double> immutableMassMapAllowEmpty(Map<String, Double> source, String name) {
        Objects.requireNonNull(source, name);
        TreeMap<String, Double> copy = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String id = requireText(entry.getKey(), name + " key");
            double mass = Objects.requireNonNull(entry.getValue(), name + " value");
            requireNonNegative(mass, name + " value");
            if (mass > EPSILON) {
                copy.put(id, mass);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableSet(Set<String> source) {
        Objects.requireNonNull(source, "source");
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, "capability tag"));
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
}

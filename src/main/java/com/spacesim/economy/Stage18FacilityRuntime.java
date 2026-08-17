package com.spacesim.economy;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningCapability;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage-18E projector from one installed physical industrial facility to finite process capability.
 *
 * <p>The projection is deliberately resource-based: station role names do not grant production.
 * Effective power is limited by installed allocation and heat rejection, work rate is further
 * limited by staffing/automation and condition, while maintenance capacity is finite. The resulting
 * snapshot can be adapted directly into the Stage-18B extraction, Stage-18C refining and Stage-18D
 * manufacturing settlement boundaries.</p>
 */
public final class Stage18FacilityRuntime {
    private static final double EPSILON = 1e-9d;

    private final Stage18FacilityCatalog catalog;

    /**
     * Creates a facility capability projector.
     *
     * @param catalog authoritative Stage-18E facility catalog
     */
    public Stage18FacilityRuntime(Stage18FacilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** Stable operational classification for an installed facility projection. */
    public enum Status {
        /** Facility is installed in a compatible location and can expose its process capabilities. */
        ACTIVE,
        /** Facility instance is administratively or physically disabled. */
        DISABLED,
        /** Facility condition is zero and therefore no physical capability remains operational. */
        CONDITION_ZERO,
        /** Installed location is physically incompatible with the facility definition. */
        LOCATION_INCOMPATIBLE,
        /** Installed state references no known facility definition. */
        DEFINITION_NOT_FOUND
    }

    /**
     * Mutable-world projection inputs for one installed facility instance.
     *
     * @param facilityInstanceId stable installed facility identity
     * @param definitionId Stage-18E facility definition ID
     * @param conditionFraction current physical condition in {@code [0,1]}
     * @param allocatedProcessPowerW electrical/process power allocated to the facility
     * @param availableHeatRejectionW heat-rejection capacity available to the facility
     * @param availableLaborUnits staffed labor-equivalent units currently available
     * @param availableMaintenanceWorkRate maintenance work-seconds available per simulation second
     * @param locationTag physical installation-location tag
     * @param enabled whether the facility is currently enabled
     */
    public record InstalledFacilityState(
            String facilityInstanceId,
            String definitionId,
            double conditionFraction,
            double allocatedProcessPowerW,
            double availableHeatRejectionW,
            double availableLaborUnits,
            double availableMaintenanceWorkRate,
            String locationTag,
            boolean enabled) {
        /**
         * Validates one installed-facility state projection.
         *
         * @param facilityInstanceId stable facility instance ID
         * @param definitionId Stage-18E definition ID
         * @param conditionFraction physical condition in {@code [0,1]}
         * @param allocatedProcessPowerW allocated process power
         * @param availableHeatRejectionW available heat-rejection capacity
         * @param availableLaborUnits available staffed labor units
         * @param availableMaintenanceWorkRate available maintenance work rate
         * @param locationTag physical location tag
         * @param enabled enabled state
         */
        public InstalledFacilityState {
            requireText(facilityInstanceId, "facilityInstanceId");
            requireText(definitionId, "definitionId");
            requireFractionInclusive(conditionFraction, "conditionFraction");
            requireNonNegative(allocatedProcessPowerW, "allocatedProcessPowerW");
            requireNonNegative(availableHeatRejectionW, "availableHeatRejectionW");
            requireNonNegative(availableLaborUnits, "availableLaborUnits");
            requireNonNegative(availableMaintenanceWorkRate, "availableMaintenanceWorkRate");
            requireText(locationTag, "locationTag");
        }
    }

    /**
     * Immutable effective capability of one installed facility at the current physical state.
     *
     * @param facilityInstanceId installed facility identity
     * @param definitionId referenced facility definition ID
     * @param status stable operational status
     * @param capabilityTags currently exposed Stage-18 capability tags
     * @param storageClassInterfaces compatible physical storage interfaces
     * @param effectiveProcessPowerW usable process power after allocation/heat/condition limits
     * @param effectiveEngineeringWorkRate usable engineering work-seconds per simulation second
     * @param effectiveMaintenanceWorkRate usable maintenance work-seconds per simulation second
     * @param effectiveThroughputFraction fraction of pristine engineering throughput available
     * @param requiredHeatRejectionW heat rejection actually required at effective process power
     * @param maxHandledUnitMassKg maximum single handled unit mass while operational
     */
    public record FacilityCapabilitySnapshot(
            String facilityInstanceId,
            String definitionId,
            Status status,
            Set<String> capabilityTags,
            Set<String> storageClassInterfaces,
            double effectiveProcessPowerW,
            double effectiveEngineeringWorkRate,
            double effectiveMaintenanceWorkRate,
            double effectiveThroughputFraction,
            double requiredHeatRejectionW,
            double maxHandledUnitMassKg) {
        /**
         * Freezes one facility capability snapshot.
         *
         * @param facilityInstanceId installed facility identity
         * @param definitionId facility definition ID
         * @param status operational status
         * @param capabilityTags exposed capability tags
         * @param storageClassInterfaces compatible storage interfaces
         * @param effectiveProcessPowerW effective process power
         * @param effectiveEngineeringWorkRate effective engineering work rate
         * @param effectiveMaintenanceWorkRate effective maintenance work rate
         * @param effectiveThroughputFraction fraction of pristine engineering throughput
         * @param requiredHeatRejectionW actual heat-rejection requirement
         * @param maxHandledUnitMassKg maximum handled unit mass
         */
        public FacilityCapabilitySnapshot {
            requireText(facilityInstanceId, "facilityInstanceId");
            requireText(definitionId, "definitionId");
            Objects.requireNonNull(status, "status");
            capabilityTags = immutableSet(capabilityTags, "capabilityTags");
            storageClassInterfaces = immutableSet(storageClassInterfaces, "storageClassInterfaces");
            requireNonNegative(effectiveProcessPowerW, "effectiveProcessPowerW");
            requireNonNegative(effectiveEngineeringWorkRate, "effectiveEngineeringWorkRate");
            requireNonNegative(effectiveMaintenanceWorkRate, "effectiveMaintenanceWorkRate");
            requireFractionInclusive(effectiveThroughputFraction, "effectiveThroughputFraction");
            requireNonNegative(requiredHeatRejectionW, "requiredHeatRejectionW");
            requireNonNegative(maxHandledUnitMassKg, "maxHandledUnitMassKg");
        }

        /**
         * Checks whether the operational facility can exchange with a storage class.
         *
         * @param storageClassId Stage-18 storage class ID
         * @return {@code true} only when the facility is active and exposes the interface
         */
        public boolean supportsStorageClass(String storageClassId) {
            requireText(storageClassId, "storageClassId");
            return status == Status.ACTIVE && storageClassInterfaces.contains(storageClassId);
        }

        /**
         * Checks the facility's single-unit handling envelope.
         *
         * @param massKg positive finished/source unit mass
         * @return whether an active facility can physically handle that unit mass
         */
        public boolean canHandleUnitMass(double massKg) {
            requirePositive(massKg, "massKg");
            return status == Status.ACTIVE && massKg <= maxHandledUnitMassKg + EPSILON;
        }
    }

    /**
     * Projects one installed facility state into effective finite capabilities.
     *
     * @param state current installed facility state
     * @return immutable physical capability snapshot
     */
    public FacilityCapabilitySnapshot project(InstalledFacilityState state) {
        Objects.requireNonNull(state, "state");
        FacilityDefinition definition = catalog.findFacility(state.definitionId());
        if (definition == null) {
            return inactive(state, Status.DEFINITION_NOT_FOUND);
        }
        if (!state.enabled()) {
            return inactive(state, Status.DISABLED);
        }
        if (state.conditionFraction() <= EPSILON) {
            return inactive(state, Status.CONDITION_ZERO);
        }
        if (!definition.allowedLocationTags().contains(state.locationTag())) {
            return inactive(state, Status.LOCATION_INCOMPATIBLE);
        }

        double conditionPowerW = finiteProduct(
                definition.ratedProcessPowerW(), state.conditionFraction(), "condition process power");
        double powerByHeatW = state.availableHeatRejectionW() / definition.heatRejectionWPerProcessW();
        double effectivePowerW = Math.max(0d, Math.min(
                conditionPowerW,
                Math.min(state.allocatedProcessPowerW(), powerByHeatW)));
        double powerFraction = conditionPowerW <= EPSILON ? 0d : clamp01(effectivePowerW / conditionPowerW);

        double laborFraction;
        if (definition.requiredLaborUnitsAtFullRate() <= EPSILON) {
            laborFraction = 1d;
        } else {
            double staffedFraction = clamp01(
                    state.availableLaborUnits() / definition.requiredLaborUnitsAtFullRate());
            laborFraction = definition.automationFloorFraction()
                    + (1d - definition.automationFloorFraction()) * staffedFraction;
        }
        double throughputLimiter = Math.min(powerFraction, laborFraction);
        double effectiveWorkRate = definition.engineeringWorkRate()
                * state.conditionFraction() * throughputLimiter;
        double effectiveMaintenanceRate = Math.min(
                definition.maintenanceWorkRate() * state.conditionFraction(),
                state.availableMaintenanceWorkRate());
        double throughputFraction = clamp01(state.conditionFraction() * throughputLimiter);
        double requiredHeatW = effectivePowerW * definition.heatRejectionWPerProcessW();

        return new FacilityCapabilitySnapshot(
                state.facilityInstanceId(),
                definition.id(),
                Status.ACTIVE,
                definition.capabilityTags(),
                definition.storageClassInterfaces(),
                effectivePowerW,
                effectiveWorkRate,
                effectiveMaintenanceRate,
                throughputFraction,
                requiredHeatW,
                definition.maxHandledUnitMassKg());
    }

    /**
     * Adapts a facility snapshot to the Stage-18B extraction settlement boundary.
     *
     * @param snapshot projected facility capability
     * @return extraction-compatible finite capability
     */
    public ExtractionCapability toExtractionCapability(FacilityCapabilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ExtractionCapability(
                snapshot.facilityInstanceId(),
                snapshot.capabilityTags(),
                snapshot.effectiveProcessPowerW(),
                snapshot.effectiveEngineeringWorkRate(),
                snapshot.effectiveMaintenanceWorkRate());
    }

    /**
     * Adapts a facility snapshot to the Stage-18C refining settlement boundary.
     *
     * @param snapshot projected facility capability
     * @return refining-compatible finite capability
     */
    public RefiningCapability toRefiningCapability(FacilityCapabilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new RefiningCapability(
                snapshot.facilityInstanceId(),
                snapshot.capabilityTags(),
                snapshot.effectiveProcessPowerW(),
                snapshot.effectiveEngineeringWorkRate(),
                snapshot.effectiveMaintenanceWorkRate());
    }

    /**
     * Adapts a facility snapshot to the Stage-18D manufacturing settlement boundary.
     *
     * @param snapshot projected facility capability
     * @return manufacturing-compatible finite capability
     */
    public ManufacturingCapability toManufacturingCapability(FacilityCapabilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ManufacturingCapability(
                snapshot.facilityInstanceId(),
                snapshot.capabilityTags(),
                snapshot.effectiveProcessPowerW(),
                snapshot.effectiveEngineeringWorkRate(),
                snapshot.effectiveMaintenanceWorkRate());
    }

    private static FacilityCapabilitySnapshot inactive(InstalledFacilityState state, Status status) {
        return new FacilityCapabilitySnapshot(
                state.facilityInstanceId(),
                state.definitionId(),
                status,
                Set.of(),
                Set.of(),
                0d,
                0d,
                0d,
                0d,
                0d,
                0d);
    }

    private static Set<String> immutableSet(Set<String> source, String name) {
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

    private static void requireFractionInclusive(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static double finiteProduct(double left, double right, String name) {
        double product = left * right;
        if (!Double.isFinite(product)) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return product;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}

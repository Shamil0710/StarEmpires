package com.spacesim.content.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable Stage-17.5F protection-response and subsystem-location content.
 *
 * <p>The Stage-17.5A engineering catalog already owns materials, protection stacks, hull
 * compartments and fitted mounts. This catalog adds only the data that becomes necessary when a
 * physical impact must be converted into local material/compartment/subsystem consequences. It does
 * not create a second mass, power, heat or fitting model.</p>
 */
public final class ShipProtectionCatalog {
    private final int schemaVersion;
    private final List<HeavyImpactModel> heavyImpactModels;
    private final List<HullDamageLayout> hullDamageLayouts;
    private final Map<String, HeavyImpactModel> impactByResponseSurfaceId;
    private final Map<String, HullDamageLayout> layoutByHullId;

    ShipProtectionCatalog(
            int schemaVersion,
            List<HeavyImpactModel> heavyImpactModels,
            List<HullDamageLayout> hullDamageLayouts) {
        this.schemaVersion = schemaVersion;
        Objects.requireNonNull(heavyImpactModels, "heavyImpactModels");
        Objects.requireNonNull(hullDamageLayouts, "hullDamageLayouts");
        List<HeavyImpactModel> models = new ArrayList<>(heavyImpactModels);
        models.sort(Comparator.comparing(HeavyImpactModel::responseSurfaceId));
        this.heavyImpactModels = List.copyOf(models);
        List<HullDamageLayout> layouts = new ArrayList<>(hullDamageLayouts);
        layouts.sort(Comparator.comparing(HullDamageLayout::hullId));
        this.hullDamageLayouts = List.copyOf(layouts);
        this.impactByResponseSurfaceId = index(this.heavyImpactModels, HeavyImpactModel::responseSurfaceId);
        this.layoutByHullId = index(this.hullDamageLayouts, HullDamageLayout::hullId);
    }

    /** @return protection-runtime schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic heavy-impact response models */
    public List<HeavyImpactModel> getHeavyImpactModels() {
        return heavyImpactModels;
    }

    /** @return deterministic hull damage layouts */
    public List<HullDamageLayout> getHullDamageLayouts() {
        return hullDamageLayouts;
    }

    /**
     * Finds response behavior for an existing Stage-17.5A response-surface ID.
     *
     * @param responseSurfaceId stable response-surface ID
     * @return response model or {@code null}
     */
    public HeavyImpactModel findHeavyImpactModel(String responseSurfaceId) {
        return impactByResponseSurfaceId.get(responseSurfaceId);
    }

    /**
     * Finds the explicit compartment/subsystem location layout for a hull.
     *
     * @param hullId stable hull ID
     * @return damage layout or {@code null}
     */
    public HullDamageLayout findHullDamageLayout(String hullId) {
        return layoutByHullId.get(hullId);
    }

    private static <T> Map<String, T> index(List<T> values, java.util.function.Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate protection-runtime ID: " + id);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Bounded deterministic material-response coefficients attached to a Stage-17.5A response surface.
     *
     * <p>The current production values are explicitly synthetic demonstrator coefficients. Stage 22
     * may replace them with calibrated datasets without changing the runtime contract.</p>
     *
     * @param responseSurfaceId referenced Stage-17.5A surface ID
     * @param specificAbsorptionJPerKg energy absorbed per kilogram of directly encountered layer material
     * @param spallMassFraction fraction of encountered material emitted as an aggregate fragment cloud
     * @param spallEnergyFraction fraction of absorbed energy carried by the aggregate fragment cloud
     * @param ricochetCriticalAngleRad minimum absolute incidence angle from normal for authored ricochet
     * @param ricochetRetainedEnergyFraction fraction of projectile energy retained after authored ricochet
     */
    public record HeavyImpactModel(
            String responseSurfaceId,
            double specificAbsorptionJPerKg,
            double spallMassFraction,
            double spallEnergyFraction,
            double ricochetCriticalAngleRad,
            double ricochetRetainedEnergyFraction) {
        /**
         * Validates a bounded response model.
         *
         * @param responseSurfaceId referenced Stage-17.5A surface ID
         * @param specificAbsorptionJPerKg energy absorbed per kilogram of directly encountered layer material
         * @param spallMassFraction fraction of encountered material emitted as an aggregate fragment cloud
         * @param spallEnergyFraction fraction of absorbed energy carried by the aggregate fragment cloud
         * @param ricochetCriticalAngleRad minimum absolute incidence angle from normal for authored ricochet
         * @param ricochetRetainedEnergyFraction fraction of projectile energy retained after authored ricochet
         */
        public HeavyImpactModel {
            requireNonBlank(responseSurfaceId, "responseSurfaceId");
            requirePositiveFinite(specificAbsorptionJPerKg, "specificAbsorptionJPerKg");
            requireUnitInterval(spallMassFraction, "spallMassFraction");
            requireUnitInterval(spallEnergyFraction, "spallEnergyFraction");
            if (!Double.isFinite(ricochetCriticalAngleRad)
                    || ricochetCriticalAngleRad <= 0d || ricochetCriticalAngleRad > Math.PI / 2d) {
                throw new IllegalArgumentException("ricochetCriticalAngleRad must be in (0,pi/2]");
            }
            requireUnitInterval(ricochetRetainedEnergyFraction, "ricochetRetainedEnergyFraction");
        }
    }

    /**
     * Explicit local damage topology for one hull.
     *
     * @param hullId referenced engineering hull ID
     * @param compartments authored compartment structural damage capacities/coupling
     * @param mounts explicit installed-mount locations and subsystem damage capacities
     */
    public record HullDamageLayout(
            String hullId,
            List<CompartmentDamageDefinition> compartments,
            List<MountDamageDefinition> mounts) {
        /**
         * Validates and freezes deterministic damage-layout data.
         *
         * @param hullId referenced engineering hull ID
         * @param compartments authored compartment structural damage capacities/coupling
         * @param mounts explicit installed-mount locations and subsystem damage capacities
         */
        public HullDamageLayout {
            requireNonBlank(hullId, "hullId");
            Objects.requireNonNull(compartments, "compartments");
            Objects.requireNonNull(mounts, "mounts");
            List<CompartmentDamageDefinition> compartmentCopy = new ArrayList<>(compartments);
            compartmentCopy.sort(Comparator.comparing(CompartmentDamageDefinition::compartmentId));
            compartments = List.copyOf(compartmentCopy);
            List<MountDamageDefinition> mountCopy = new ArrayList<>(mounts);
            mountCopy.sort(Comparator.comparing(MountDamageDefinition::mountId));
            mounts = List.copyOf(mountCopy);
        }

        /** @return compartment definitions keyed by stable hull-local ID */
        public Map<String, CompartmentDamageDefinition> compartmentsById() {
            TreeMap<String, CompartmentDamageDefinition> result = new TreeMap<>();
            for (CompartmentDamageDefinition definition : compartments) {
                result.put(definition.compartmentId(), definition);
            }
            return Collections.unmodifiableMap(result);
        }

        /** @return mount definitions keyed by stable hull-local mount ID */
        public Map<String, MountDamageDefinition> mountsById() {
            TreeMap<String, MountDamageDefinition> result = new TreeMap<>();
            for (MountDamageDefinition definition : mounts) {
                result.put(definition.mountId(), definition);
            }
            return Collections.unmodifiableMap(result);
        }
    }

    /**
     * Local structural-damage authoring for one compartment.
     *
     * @param compartmentId hull-local compartment ID
     * @param structuralDamageCapacityJ energy scale for degrading compartment structural integrity
     * @param subsystemCouplingFraction fraction of penetrating internal energy coupled to located subsystems
     */
    public record CompartmentDamageDefinition(
            String compartmentId,
            double structuralDamageCapacityJ,
            double subsystemCouplingFraction) {
        /**
         * Validates compartment damage parameters.
         *
         * @param compartmentId hull-local compartment ID
         * @param structuralDamageCapacityJ energy scale for degrading compartment structural integrity
         * @param subsystemCouplingFraction fraction of penetrating internal energy coupled to located subsystems
         */
        public CompartmentDamageDefinition {
            requireNonBlank(compartmentId, "compartmentId");
            requirePositiveFinite(structuralDamageCapacityJ, "structuralDamageCapacityJ");
            requireUnitInterval(subsystemCouplingFraction, "subsystemCouplingFraction");
        }
    }

    /**
     * Explicit placement and damage capacity of one fitted mount.
     *
     * @param mountId hull-local slot/hardpoint ID
     * @param compartmentId containing compartment ID
     * @param subsystemDamageCapacityJ energy scale for reducing this mount's integrity from 1 to 0
     */
    public record MountDamageDefinition(
            String mountId,
            String compartmentId,
            double subsystemDamageCapacityJ) {
        /**
         * Validates mount location/capacity.
         *
         * @param mountId hull-local slot/hardpoint ID
         * @param compartmentId containing compartment ID
         * @param subsystemDamageCapacityJ energy scale for reducing this mount's integrity from 1 to 0
         */
        public MountDamageDefinition {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(compartmentId, "compartmentId");
            requirePositiveFinite(subsystemDamageCapacityJ, "subsystemDamageCapacityJ");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static void requireUnitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}

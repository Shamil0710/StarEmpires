package com.spacesim.economy;

import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.Status;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningCapability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage-18I composition seam for multiple physically installed industrial lines working as one campus.
 *
 * <p>Earlier Stage-18E adapters intentionally projected one installed facility at a time. Recipes
 * may legitimately require capabilities installed on different active lines. This network combines
 * only ACTIVE snapshots; capability tags are unioned while finite process power, engineering work
 * and maintenance-work rates are summed.</p>
 */
public final class Stage18FacilityCapabilityNetwork {
    private Stage18FacilityCapabilityNetwork() {
        throw new AssertionError("No instances");
    }

    /**
     * Combines active installed lines into one extraction capability network.
     *
     * @param networkId stable diagnostic network identity
     * @param facilities installed facility snapshots
     * @return composed finite extraction capability
     */
    public static ExtractionCapability extraction(
            String networkId, List<FacilityCapabilitySnapshot> facilities) {
        Aggregate aggregate = aggregate(networkId, facilities);
        return new ExtractionCapability(
                aggregate.id(), aggregate.tags(), aggregate.powerW(),
                aggregate.workRate(), aggregate.maintenanceRate());
    }

    /**
     * Combines active installed lines into one refining capability network.
     *
     * @param networkId stable diagnostic network identity
     * @param facilities installed facility snapshots
     * @return composed finite refining capability
     */
    public static RefiningCapability refining(
            String networkId, List<FacilityCapabilitySnapshot> facilities) {
        Aggregate aggregate = aggregate(networkId, facilities);
        return new RefiningCapability(
                aggregate.id(), aggregate.tags(), aggregate.powerW(),
                aggregate.workRate(), aggregate.maintenanceRate());
    }

    /**
     * Combines active installed lines into one manufacturing capability network.
     *
     * @param networkId stable diagnostic network identity
     * @param facilities installed facility snapshots
     * @return composed finite manufacturing capability
     */
    public static ManufacturingCapability manufacturing(
            String networkId, List<FacilityCapabilitySnapshot> facilities) {
        Aggregate aggregate = aggregate(networkId, facilities);
        return new ManufacturingCapability(
                aggregate.id(), aggregate.tags(), aggregate.powerW(),
                aggregate.workRate(), aggregate.maintenanceRate());
    }

    private static Aggregate aggregate(
            String networkId, List<FacilityCapabilitySnapshot> facilities) {
        String id = requireText(networkId, "networkId");
        Objects.requireNonNull(facilities, "facilities");
        List<FacilityCapabilitySnapshot> active = new ArrayList<>();
        for (FacilityCapabilitySnapshot snapshot : facilities) {
            FacilityCapabilitySnapshot checked = Objects.requireNonNull(snapshot, "facility snapshot");
            if (checked.status() == Status.ACTIVE) {
                active.add(checked);
            }
        }
        active.sort(Comparator.comparing(FacilityCapabilitySnapshot::facilityInstanceId));
        if (active.isEmpty()) {
            throw new IllegalArgumentException("Capability network requires at least one active facility");
        }
        TreeSet<String> tags = new TreeSet<>();
        double power = 0d;
        double work = 0d;
        double maintenance = 0d;
        for (FacilityCapabilitySnapshot snapshot : active) {
            tags.addAll(snapshot.capabilityTags());
            power = finiteAdd(power, snapshot.effectiveProcessPowerW(), "network process power");
            work = finiteAdd(work, snapshot.effectiveEngineeringWorkRate(), "network engineering work rate");
            maintenance = finiteAdd(
                    maintenance, snapshot.effectiveMaintenanceWorkRate(), "network maintenance work rate");
        }
        return new Aggregate(id, Set.copyOf(tags), power, work, maintenance);
    }

    private static double finiteAdd(double left, double right, String name) {
        double value = left + right;
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " overflowed finite range");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private record Aggregate(
            String id,
            Set<String> tags,
            double powerW,
            double workRate,
            double maintenanceRate) { }
}

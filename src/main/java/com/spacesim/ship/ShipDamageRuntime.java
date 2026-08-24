package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog.CompartmentDamageDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.content.ship.ShipProtectionCatalog.MountDamageDefinition;
import com.spacesim.ship.HeavyImpactResolver.ImpactResult;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic Stage-17.5F local compartment/subsystem damage router.
 *
 * <p>Damage enters through a physical hull-local hit point, selects the nearest authored compartment,
 * degrades that compartment's structural state, and couples an authored fraction of internal impact
 * energy into only the fitted subsystem mounts explicitly located in that compartment. No class-name
 * modifier or global hull-HP shortcut is used.</p>
 */
public final class ShipDamageRuntime {
    /**
     * Persistent damage snapshot for the Stage-17.5F runtime seam.
     *
     * @param compartmentIntegrityById local structural integrity values in [0,1]
     * @param moduleDamage central module-integrity state consumed by the derived ship calculator
     */
    public record Snapshot(
            Map<String, Double> compartmentIntegrityById,
            DamageState moduleDamage) {
        /**
         * Validates and freezes one damage snapshot.
         *
         * @param compartmentIntegrityById local structural integrity values in [0,1]
         * @param moduleDamage central module-integrity state consumed by the derived ship calculator
         */
        public Snapshot {
            Objects.requireNonNull(compartmentIntegrityById, "compartmentIntegrityById");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : compartmentIntegrityById.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("compartment ID must be non-blank");
                }
                Double value = Objects.requireNonNull(entry.getValue(), "compartment integrity");
                requireIntegrity(value, "compartment integrity");
                copy.put(entry.getKey(), value);
            }
            compartmentIntegrityById = Collections.unmodifiableMap(copy);
            Objects.requireNonNull(moduleDamage, "moduleDamage");
        }

        /**
         * Creates a pristine snapshot for a hull/layout pair.
         *
         * @param hull authoritative hull
         * @param layout explicit Stage-17.5F damage layout
         * @return pristine local damage state
         */
        public static Snapshot pristine(HullDefinition hull, HullDamageLayout layout) {
            HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
            HullDamageLayout checkedLayout = Objects.requireNonNull(layout, "layout");
            if (!checkedHull.id().equals(checkedLayout.hullId())) {
                throw new IllegalArgumentException("Hull/layout ID mismatch");
            }
            TreeMap<String, Double> compartments = new TreeMap<>();
            for (CompartmentDefinition compartment : checkedHull.compartments()) {
                compartments.put(compartment.id(), 1d);
            }
            return new Snapshot(compartments, DamageState.pristine());
        }
    }

    /**
     * Result of one local damage event.
     *
     * @param snapshot updated persistent damage snapshot
     * @param compartmentId selected compartment
     * @param compartmentDamageEnergyJ energy applied to local structure
     * @param subsystemDamageEnergyJ energy coupled into located installed subsystems
     * @param damagedMounts stable IDs of mounts whose integrity decreased
     */
    public record DamageEvent(
            Snapshot snapshot,
            String compartmentId,
            double compartmentDamageEnergyJ,
            double subsystemDamageEnergyJ,
            List<String> damagedMounts) {
        /**
         * Validates and freezes one local damage event.
         *
         * @param snapshot updated persistent damage snapshot
         * @param compartmentId selected compartment
         * @param compartmentDamageEnergyJ energy applied to local structure
         * @param subsystemDamageEnergyJ energy coupled into located installed subsystems
         * @param damagedMounts stable IDs of mounts whose integrity decreased
         */
        public DamageEvent {
            Objects.requireNonNull(snapshot, "snapshot");
            if (compartmentId == null || compartmentId.isBlank()) {
                throw new IllegalArgumentException("compartmentId must be non-blank");
            }
            Objects.requireNonNull(damagedMounts, "damagedMounts");
            damagedMounts = List.copyOf(damagedMounts);
        }
    }

    /**
     * Tests catastrophic physical destruction from local structure plus installed subsystem state.
     *
     * <p>A ship is destroyed only when every authored compartment has zero structural integrity and
     * every module actually installed in each compartment has zero integrity. Uninstalled layout
     * mounts are deliberately ignored so fitting choices cannot create immortal phantom subsystems.</p>
     *
     * @param hull authoritative fitted hull definition
     * @param fit exact installed fit being evaluated
     * @param layout explicit hull damage-routing layout
     * @param snapshot current physical damage snapshot
     * @return true only when all fitted physical structure and installed local subsystems are destroyed
     */
    public static boolean isFullyDestroyed(
            HullDefinition hull,
            InstalledFit fit,
            HullDamageLayout layout,
            Snapshot snapshot) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        HullDamageLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        Snapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!checkedHull.id().equals(checkedFit.hullId()) || !checkedHull.id().equals(checkedLayout.hullId())) {
            throw new IllegalArgumentException("Hull/fit/damage-layout ID mismatch");
        }
        Set<String> installedMounts = new TreeSet<>();
        for (InstalledModuleDefinition installed : checkedFit.installedModules()) {
            installedMounts.add(installed.mountId());
        }
        for (CompartmentDefinition compartment : checkedHull.compartments()) {
            if (checkedSnapshot.compartmentIntegrityById().getOrDefault(compartment.id(), 1d) > 0d) {
                return false;
            }
            for (MountDamageDefinition mount : checkedLayout.mounts()) {
                if (mount.compartmentId().equals(compartment.id())
                        && installedMounts.contains(mount.mountId())
                        && checkedSnapshot.moduleDamage().moduleIntegrityByMount()
                                .getOrDefault(mount.mountId(), 1d) > 0d) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Applies one penetration/spall result at one hull-local position.
     *
     * @param hull authoritative hull definition
     * @param fit current installed fit
     * @param layout explicit hull damage layout
     * @param snapshot current damage snapshot
     * @param impact physical material-response result
     * @param hitPointM hull-local impact point
     * @return deterministic local damage event
     */
    public DamageEvent applyImpact(
            HullDefinition hull,
            InstalledFit fit,
            HullDamageLayout layout,
            Snapshot snapshot,
            ImpactResult impact,
            Vector3d hitPointM) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        HullDamageLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        Snapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        ImpactResult checkedImpact = Objects.requireNonNull(impact, "impact");
        Vector3d checkedHit = Objects.requireNonNull(hitPointM, "hitPointM");
        if (!checkedHull.id().equals(checkedFit.hullId()) || !checkedHull.id().equals(checkedLayout.hullId())) {
            throw new IllegalArgumentException("Hull/fit/damage-layout ID mismatch");
        }

        CompartmentDefinition target = nearestCompartment(checkedHull, checkedHit);
        Map<String, CompartmentDamageDefinition> compartmentDefinitions = checkedLayout.compartmentsById();
        CompartmentDamageDefinition compartmentDamage = compartmentDefinitions.get(target.id());
        if (compartmentDamage == null) {
            throw new IllegalStateException("Damage layout lost compartment: " + target.id());
        }

        double internalEnergyJ = checkedImpact.internalDamageEnergyJ();
        TreeMap<String, Double> compartmentIntegrity = new TreeMap<>(checkedSnapshot.compartmentIntegrityById());
        double oldCompartmentIntegrity = compartmentIntegrity.getOrDefault(target.id(), 1d);
        double compartmentLoss = internalEnergyJ / compartmentDamage.structuralDamageCapacityJ();
        double newCompartmentIntegrity = clampIntegrity(oldCompartmentIntegrity - compartmentLoss);
        compartmentIntegrity.put(target.id(), newCompartmentIntegrity);

        double subsystemEnergyJ = internalEnergyJ * compartmentDamage.subsystemCouplingFraction();
        Set<String> installedMounts = new TreeSet<>();
        for (InstalledModuleDefinition installed : checkedFit.installedModules()) {
            installedMounts.add(installed.mountId());
        }
        List<MountDamageDefinition> localMounts = checkedLayout.mounts().stream()
                .filter(value -> value.compartmentId().equals(target.id()))
                .filter(value -> installedMounts.contains(value.mountId()))
                .toList();

        TreeMap<String, Double> moduleIntegrity = new TreeMap<>(checkedSnapshot.moduleDamage().moduleIntegrityByMount());
        List<String> damagedMounts = new ArrayList<>();
        if (!localMounts.isEmpty() && subsystemEnergyJ > 0d) {
            double shareJ = subsystemEnergyJ / localMounts.size();
            for (MountDamageDefinition mount : localMounts) {
                double oldIntegrity = moduleIntegrity.getOrDefault(mount.mountId(), 1d);
                double newIntegrity = clampIntegrity(oldIntegrity - shareJ / mount.subsystemDamageCapacityJ());
                moduleIntegrity.put(mount.mountId(), newIntegrity);
                if (newIntegrity < oldIntegrity) {
                    damagedMounts.add(mount.mountId());
                }
            }
        }

        Snapshot updated = new Snapshot(compartmentIntegrity, new DamageState(moduleIntegrity));
        return new DamageEvent(
                updated,
                target.id(),
                internalEnergyJ,
                subsystemEnergyJ,
                damagedMounts);
    }

    private static CompartmentDefinition nearestCompartment(HullDefinition hull, Vector3d hit) {
        CompartmentDefinition best = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (CompartmentDefinition compartment : hull.compartments()) {
            Vector3d center = compartment.centerM();
            double dx = center.xM() - hit.xM();
            double dy = center.yM() - hit.yM();
            double dz = center.zM() - hit.zM();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < bestDistanceSquared
                    || (distanceSquared == bestDistanceSquared
                    && best != null && compartment.id().compareTo(best.id()) < 0)) {
                best = compartment;
                bestDistanceSquared = distanceSquared;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Hull has no compartments: " + hull.id());
        }
        return best;
    }

    private static double clampIntegrity(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static void requireIntegrity(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}

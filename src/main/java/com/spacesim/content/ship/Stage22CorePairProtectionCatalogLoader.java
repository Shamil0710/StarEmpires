package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.SlotDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.CompartmentDamageDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.HeavyImpactModel;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.content.ship.ShipProtectionCatalog.MountDamageDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Projects Stage-22 core-faction engineering into the existing Stage-17.5F protection authority.
 *
 * <p>This bridge introduces no faction multiplier and no second damage solver. Both core packages use
 * the same response coefficients and damage-capacity calibration inherited from the accepted
 * Stage-17.5I physical test envelope. Faction differences therefore remain authored in ordinary
 * Stage-22 hull mass, compartment geometry, material/protection stacks and fitted modules.</p>
 *
 * <p>The legacy Stage-17.5I reference destroyer owns 238 GJ of compartment structural capacity at a
 * 12.5 Mt bare-hull mass and 197 GJ of mount capacity across 15.3 Mt of mount envelopes. M22.6 keeps
 * those common J/kg calibration ratios and distributes structural capacity by authored compartment
 * volume. This is a deterministic compatibility projection until later calibrated material datasets
 * replace the provisional Stage-17.5F coefficients through the same {@link ShipProtectionCatalog}
 * contract.</p>
 */
public final class Stage22CorePairProtectionCatalogLoader {
    /** Protection projection semantic version included in the M22.6 freeze surface. */
    public static final String PROJECTION_VERSION = "stage22.core_pair_protection_projection.v1";

    private static final double REFERENCE_BARE_HULL_MASS_KG = 12_500_000d;
    private static final double REFERENCE_STRUCTURAL_CAPACITY_J = 238_000_000_000d;
    private static final double REFERENCE_MOUNT_ENVELOPE_MASS_KG = 15_300_000d;
    private static final double REFERENCE_MOUNT_CAPACITY_J = 197_000_000_000d;
    private static final double STRUCTURAL_CAPACITY_J_PER_KG =
            REFERENCE_STRUCTURAL_CAPACITY_J / REFERENCE_BARE_HULL_MASS_KG;
    private static final double MOUNT_CAPACITY_J_PER_KG =
            REFERENCE_MOUNT_CAPACITY_J / REFERENCE_MOUNT_ENVELOPE_MASS_KG;

    private static final double SPECIFIC_ABSORPTION_J_PER_KG = 2_200_000d;
    private static final double SPALL_MASS_FRACTION = 0.10d;
    private static final double SPALL_ENERGY_FRACTION = 0.14d;
    private static final double RICOCHET_CRITICAL_ANGLE_RAD = 1.2217304763960306d;
    private static final double RICOCHET_RETAINED_ENERGY_FRACTION = 0.62d;

    private Stage22CorePairProtectionCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Builds a validated protection projection for one accepted Stage-22 engineering catalog.
     *
     * @param engineering accepted physical engineering catalog
     * @return immutable protection catalog consumed by the ordinary Stage-19 tactical runtime
     */
    public static ShipProtectionCatalog project(ShipEngineeringCatalog engineering) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(engineering, "engineering");
        List<HeavyImpactModel> models = checked.getResponseSurfaces().stream()
                .map(surface -> new HeavyImpactModel(
                        surface.id(),
                        SPECIFIC_ABSORPTION_J_PER_KG,
                        SPALL_MASS_FRACTION,
                        SPALL_ENERGY_FRACTION,
                        RICOCHET_CRITICAL_ANGLE_RAD,
                        RICOCHET_RETAINED_ENERGY_FRACTION))
                .toList();
        if (models.isEmpty()) {
            throw new IllegalArgumentException("Stage-22 protection projection requires a response surface");
        }

        List<HullDamageLayout> layouts = checked.getHulls().stream()
                .map(Stage22CorePairProtectionCatalogLoader::layoutFor)
                .toList();
        ShipProtectionCatalog result = new ShipProtectionCatalog(
                ShipProtectionCatalogLoader.CURRENT_SCHEMA_VERSION,
                models,
                layouts);
        validateClosure(checked, result);
        return result;
    }

    /**
     * Computes a deterministic semantic fingerprint for one projected protection catalog.
     *
     * @param engineering accepted physical engineering catalog
     * @return lowercase SHA-256 projection fingerprint
     */
    public static String fingerprint(ShipEngineeringCatalog engineering) {
        ShipProtectionCatalog catalog = project(engineering);
        StringBuilder canonical = new StringBuilder(8192);
        canonical.append(PROJECTION_VERSION).append('|')
                .append(Objects.requireNonNull(engineering, "engineering").getFingerprint()).append('\n');
        for (HeavyImpactModel model : catalog.getHeavyImpactModels()) {
            canonical.append("response|").append(model).append('\n');
        }
        for (HullDamageLayout layout : catalog.getHullDamageLayouts()) {
            canonical.append("hull|").append(layout.hullId()).append('\n');
            layout.compartments().forEach(value -> canonical.append("compartment|").append(value).append('\n'));
            layout.mounts().forEach(value -> canonical.append("mount|").append(value).append('\n'));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static HullDamageLayout layoutFor(HullDefinition hull) {
        if (hull.compartments().isEmpty()) {
            throw new IllegalArgumentException("Stage-22 tactical hull has no compartments: " + hull.id());
        }
        double totalVolume = hull.compartments().stream().mapToDouble(CompartmentDefinition::volumeM3).sum();
        if (!Double.isFinite(totalVolume) || totalVolume <= 0d) {
            throw new IllegalArgumentException("Stage-22 tactical hull has invalid compartment volume: " + hull.id());
        }
        double totalStructuralCapacity = hull.bareHullMassKg() * STRUCTURAL_CAPACITY_J_PER_KG;
        List<CompartmentDamageDefinition> compartments = hull.compartments().stream()
                .map(compartment -> new CompartmentDamageDefinition(
                        compartment.id(),
                        totalStructuralCapacity * compartment.volumeM3() / totalVolume,
                        subsystemCoupling(compartment)))
                .toList();

        ArrayList<MountDamageDefinition> mounts = new ArrayList<>();
        for (SlotDefinition slot : hull.slots()) {
            mounts.add(new MountDamageDefinition(
                    slot.id(),
                    compartmentForMount(hull, slot.id(), false),
                    slot.maxMassKg() * MOUNT_CAPACITY_J_PER_KG));
        }
        for (HardpointDefinition hardpoint : hull.hardpoints()) {
            mounts.add(new MountDamageDefinition(
                    hardpoint.id(),
                    compartmentForMount(hull, hardpoint.id(), true),
                    hardpoint.maxModuleMassKg() * MOUNT_CAPACITY_J_PER_KG));
        }
        mounts.sort(Comparator.comparing(MountDamageDefinition::mountId));
        return new HullDamageLayout(hull.id(), compartments, mounts);
    }

    private static double subsystemCoupling(CompartmentDefinition compartment) {
        if (hasAnyTag(compartment, "weapon", "magazine", "military")) {
            return 0.70d;
        }
        if (hasAnyTag(compartment, "reactor", "drive", "engineering", "replaceable_bank")) {
            return 0.60d;
        }
        return 0.50d;
    }

    private static String compartmentForMount(HullDefinition hull, String mountId, boolean hardpoint) {
        if (hardpoint || mountId.startsWith("weapon")) {
            CompartmentDefinition weapons = findByTag(hull, "weapon", "magazine", "military");
            if (weapons != null) {
                return weapons.id();
            }
        }
        if (mountId.contains("reactor") || mountId.contains("drive") || mountId.contains("thermal")) {
            CompartmentDefinition engineering = findByTag(hull, "reactor", "drive", "engineering", "replaceable_bank");
            if (engineering != null) {
                return engineering.id();
            }
        }
        if (mountId.contains("mission") || mountId.contains("cargo") || mountId.contains("hangar")) {
            CompartmentDefinition mission = findByTag(hull, "mission", "support", "stores", "military");
            if (mission != null) {
                return mission.id();
            }
        }
        if (mountId.contains("sensor") || mountId.contains("defense") || mountId.contains("shield")) {
            CompartmentDefinition operations = findByTag(hull, "sensor", "crew", "command", "standard_control");
            if (operations != null) {
                return operations.id();
            }
        }
        return hull.compartments().stream()
                .min(Comparator.comparingDouble(value -> Math.abs(value.centerM().yM())))
                .orElseThrow()
                .id();
    }

    private static CompartmentDefinition findByTag(HullDefinition hull, String... tags) {
        return hull.compartments().stream()
                .filter(compartment -> hasAnyTag(compartment, tags))
                .min(Comparator.comparingDouble(value -> Math.abs(value.centerM().yM())))
                .orElse(null);
    }

    private static boolean hasAnyTag(CompartmentDefinition compartment, String... tags) {
        for (String tag : tags) {
            if (compartment.tags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void validateClosure(ShipEngineeringCatalog engineering, ShipProtectionCatalog protection) {
        for (ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition surface : engineering.getResponseSurfaces()) {
            if (protection.findHeavyImpactModel(surface.id()) == null) {
                throw new IllegalStateException("Missing projected heavy-impact model: " + surface.id());
            }
        }
        for (HullDefinition hull : engineering.getHulls()) {
            HullDamageLayout layout = protection.findHullDamageLayout(hull.id());
            if (layout == null) {
                throw new IllegalStateException("Missing projected hull damage layout: " + hull.id());
            }
            if (layout.compartments().size() != hull.compartments().size()
                    || layout.mounts().size() != hull.slots().size() + hull.hardpoints().size()) {
                throw new IllegalStateException("Projected protection topology is incomplete: " + hull.id());
            }
        }
    }
}

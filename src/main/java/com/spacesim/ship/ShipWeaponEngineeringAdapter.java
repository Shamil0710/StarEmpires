package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponDefinition.KineticRound;
import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects fitted Stage-17.5 engineering capabilities into physical Stage-17.5E weapon mounts.
 *
 * <p>The adapter does not infer performance from ship class or role. Muzzle velocity and recoil are
 * taken from the installed engineering module, capability-specific timing/geometry comes from a
 * profile linked to that exact module ID, ammunition body properties come from the ammunition
 * catalog, and feed quantity/mass remains in central consumable state.</p>
 */
public final class ShipWeaponEngineeringAdapter {
    private static final double RELATIVE_TOLERANCE = 1e-9d;

    /**
     * One fitted kinetic mount ready for common fire-control/ammunition runtime.
     *
     * @param mountId fitted hull-local mount ID
     * @param moduleId engineering module content ID
     * @param round loaded physical kinetic round combined with fitted muzzle velocity
     * @param launcher physical launcher/feed cycle definition
     * @param pointingJitterRad one-sigma fitted launcher pointing uncertainty
     * @param recoilImpulseNs physical shot recoil impulse derived from loaded round and muzzle velocity
     */
    public record FittedKineticMount(
            String mountId,
            String moduleId,
            KineticRound round,
            Launcher launcher,
            double pointingJitterRad,
            double recoilImpulseNs) {
        /**
         * Validates one immutable fitted kinetic mount.
         *
         * @param mountId fitted hull-local mount ID
         * @param moduleId engineering module content ID
         * @param round loaded physical kinetic round
         * @param launcher physical launcher/feed definition
         * @param pointingJitterRad one-sigma fitted launcher pointing uncertainty
         * @param recoilImpulseNs physical recoil impulse
         */
        public FittedKineticMount {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(round, "round");
            Objects.requireNonNull(launcher, "launcher");
            requireNonNegativeFinite(pointingJitterRad, "pointingJitterRad");
            requirePositiveFinite(recoilImpulseNs, "recoilImpulseNs");
        }
    }

    /**
     * Resolves all loaded kinetic weapon mounts from one accepted fitted ship.
     *
     * @param derived central derived fitted ship state
     * @param ammunition physical ammunition content catalog
     * @param launcherCatalog launcher profiles linked to engineering module IDs
     * @param loadout physical feed-to-ammunition identity bindings
     * @return deterministic fitted kinetic mount list
     */
    public List<FittedKineticMount> deriveKineticMounts(
            DerivedShipState derived,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launcherCatalog,
            WeaponLoadoutState loadout) {
        DerivedShipState checkedDerived = Objects.requireNonNull(derived, "derived");
        WeaponAmmunitionCatalog checkedAmmunition = Objects.requireNonNull(ammunition, "ammunition");
        WeaponLauncherCatalog checkedLaunchers = Objects.requireNonNull(launcherCatalog, "launcherCatalog");
        WeaponLoadoutState checkedLoadout = Objects.requireNonNull(loadout, "loadout");

        List<FittedKineticMount> result = new ArrayList<>();
        for (InstalledCapability capability : checkedDerived.installedCapabilities()) {
            if (capability.family() != ModuleFamily.WEAPON_AMMUNITION) {
                continue;
            }
            LauncherProfile profile = checkedLaunchers.findByModuleId(capability.moduleId());
            if (profile == null) {
                throw new IllegalArgumentException(
                        "Installed weapon module lacks Stage-17.5E launcher profile: " + capability.moduleId());
            }
            if (profile.family() != Family.KINETIC) {
                continue;
            }
            String ammunitionId = checkedLoadout
                    .ammunitionContentId(capability.mountId(), profile.ammunitionInterfaceId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Kinetic mount has no loaded ammunition identity: " + capability.mountId()));
            KineticAmmunitionDefinition ammo = checkedAmmunition.findKinetic(ammunitionId);
            if (ammo == null) {
                throw new IllegalArgumentException(
                        "Kinetic launcher feed references non-kinetic/unknown ammunition: " + ammunitionId);
            }
            validateEnvelope(profile, ammo);
            Map<String, Double> parameters = capability.parameters();
            double muzzleVelocity = requirePositiveParameter(parameters, "muzzle_velocity_mps", capability.moduleId());
            double authoredRecoil = requirePositiveParameter(parameters, "recoil_impulse_ns", capability.moduleId());
            Double authoredMass = parameters.get("projectile_mass_kg");
            if (authoredMass != null && !nearlyEqual(authoredMass, ammo.massKg())) {
                throw new IllegalArgumentException(
                        "Loaded ammunition mass disagrees with engineering module projectile mass: "
                                + capability.moduleId() + " -> " + ammunitionId);
            }
            KineticRound round = ammo.toRuntimeRound(muzzleVelocity);
            double physicalRecoil = round.momentumNs();
            if (!nearlyEqual(authoredRecoil, physicalRecoil)) {
                throw new IllegalArgumentException(
                        "Engineering recoil impulse disagrees with loaded ammunition mass*muzzle velocity: "
                                + capability.moduleId());
            }
            Launcher launcher = new Launcher(
                    "launcher." + capability.moduleId(),
                    profile.ammunitionInterfaceId(),
                    profile.ammunitionAmountPerShot(),
                    profile.cycleTimeSeconds(),
                    profile.supportChannelCount());
            result.add(new FittedKineticMount(
                    capability.mountId(),
                    capability.moduleId(),
                    round,
                    launcher,
                    profile.pointingJitterRad(),
                    physicalRecoil));
        }
        result.sort(Comparator.comparing(FittedKineticMount::mountId).thenComparing(FittedKineticMount::moduleId));
        return List.copyOf(result);
    }

    private static void validateEnvelope(LauncherProfile profile, KineticAmmunitionDefinition ammunition) {
        if (ammunition.massKg() > profile.maxProjectileMassKg()
                || ammunition.lengthM() > profile.maxProjectileLengthM()
                || ammunition.diameterM() > profile.maxProjectileDiameterM()) {
            throw new IllegalArgumentException(
                    "Loaded ammunition exceeds launcher physical envelope: " + ammunition.id());
        }
    }

    private static double requirePositiveParameter(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("Weapon module lacks positive " + key + ": " + moduleId);
        }
        return value;
    }

    private static boolean nearlyEqual(double left, double right) {
        double scale = Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= RELATIVE_TOLERANCE * scale;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

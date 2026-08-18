package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponDefinition.KineticRound;
import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects fitted Stage-17.5 engineering capabilities into physical Stage-17.5E/F weapon mounts.
 *
 * <p>Stage 17.5F consumes local mount integrity from the central derived state. A destroyed mount is
 * absent; a damaged kinetic mount retains the authored projectile body/muzzle energy but pays a
 * longer physical cycle and worse pointing uncertainty. Beam mounts likewise retain authored
 * emitter geometry and output while physical engineering admission decides whether the requested
 * dwell can actually receive incremental power and thermal capacity. This avoids the dangerous
 * generic pattern of scaling every parameter, which could accidentally make lower-is-better values
 * improve.</p>
 */
public final class ShipWeaponEngineeringAdapter {
    private static final double RELATIVE_TOLERANCE = 1e-9d;
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;

    /**
     * One fitted kinetic mount ready for common fire-control/ammunition runtime.
     *
     * @param mountId physical fitted weapon mount
     * @param moduleId weapon module content ID
     * @param round physical loaded kinetic round
     * @param launcher damage-aware launcher timing/support definition
     * @param pointingJitterRad current pointing uncertainty
     * @param recoilImpulseNs physical recoil impulse
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
         * @param mountId physical fitted weapon mount
         * @param moduleId weapon module content ID
         * @param round physical loaded kinetic round
         * @param launcher damage-aware launcher timing/support definition
         * @param pointingJitterRad current pointing uncertainty
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
     * One fitted directed-energy emitter ready for physical beam/engineering execution.
     *
     * @param mountId physical fitted weapon mount
     * @param moduleId weapon module content ID
     * @param weapon authored physical beam definition
     */
    public record FittedBeamMount(String mountId, String moduleId, BeamWeapon weapon) {
        /**
         * Validates one immutable fitted beam emitter.
         *
         * @param mountId physical fitted weapon mount
         * @param moduleId weapon module content ID
         * @param weapon authored physical beam definition
         */
        public FittedBeamMount {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(weapon, "weapon");
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
            double integrity = runtimeIntegrity(capability.parameters());
            if (integrity <= MIN_OPERATIONAL_INTEGRITY) {
                continue;
            }
            LauncherProfile profile = checkedLaunchers.findByModuleId(capability.moduleId());
            if (profile == null) {
                // A fitted weapon without an ammunition interface can be a beam emitter rather than
                // a launcher. Beam derivation is handled by deriveBeamMounts().
                continue;
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
                    profile.cycleTimeSeconds() / integrity,
                    profile.supportChannelCount());
            result.add(new FittedKineticMount(
                    capability.mountId(),
                    capability.moduleId(),
                    round,
                    launcher,
                    profile.pointingJitterRad() / integrity,
                    physicalRecoil));
        }
        result.sort(Comparator.comparing(FittedKineticMount::mountId).thenComparing(FittedKineticMount::moduleId));
        return List.copyOf(result);
    }

    /**
     * Resolves all operational fitted beam emitters from the central derived state.
     *
     * <p>The beam content stores optical/output parameters on the installed capability while the
     * module definition owns the actual electrical and thermal integration budgets. Because the
     * module's continuous demand is already included in the ship's standing engineering load, beam
     * execution requests only the incremental rise from continuous to peak demand.</p>
     *
     * @param derived central derived fitted ship state
     * @param engineeringCatalog production engineering definitions
     * @return deterministic fitted beam emitter list
     */
    public List<FittedBeamMount> deriveBeamMounts(
            DerivedShipState derived,
            ShipEngineeringCatalog engineeringCatalog) {
        DerivedShipState checkedDerived = Objects.requireNonNull(derived, "derived");
        ShipEngineeringCatalog checkedCatalog = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        List<FittedBeamMount> result = new ArrayList<>();
        for (InstalledCapability capability : checkedDerived.installedCapabilities()) {
            if (capability.family() != ModuleFamily.WEAPON_AMMUNITION
                    || runtimeIntegrity(capability.parameters()) <= MIN_OPERATIONAL_INTEGRITY) {
                continue;
            }
            Map<String, Double> parameters = capability.parameters();
            if (!parameters.containsKey("beam_power_w")) {
                continue;
            }
            ModuleDefinition module = checkedCatalog.findModule(capability.moduleId());
            if (module == null) {
                throw new IllegalArgumentException("Unknown fitted beam module: " + capability.moduleId());
            }
            double incrementalPowerW = Math.max(0d, module.peakPowerDemandW() - module.continuousPowerDemandW());
            BeamWeapon weapon = new BeamWeapon(
                    "beam." + capability.moduleId(),
                    requirePositiveParameter(parameters, "wavelength_m", capability.moduleId()),
                    requirePositiveParameter(parameters, "aperture_diameter_m", capability.moduleId()),
                    requireNonNegativeParameter(parameters, "pointing_jitter_rad", capability.moduleId()),
                    requirePositiveParameter(parameters, "beam_power_w", capability.moduleId()),
                    incrementalPowerW,
                    module.wasteHeatW(),
                    requirePositiveParameter(parameters, "max_continuous_dwell_s", capability.moduleId()));
            result.add(new FittedBeamMount(capability.mountId(), capability.moduleId(), weapon));
        }
        result.sort(Comparator.comparing(FittedBeamMount::mountId).thenComparing(FittedBeamMount::moduleId));
        return List.copyOf(result);
    }

    private static double runtimeIntegrity(Map<String, Double> parameters) {
        double value = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid runtime weapon integrity");
        }
        return value;
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

    private static double requireNonNegativeParameter(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("Weapon module lacks non-negative " + key + ": " + moduleId);
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
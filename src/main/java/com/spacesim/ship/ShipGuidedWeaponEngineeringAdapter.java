package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects ordinary fitted Stage-17.5 engineering capability into physical guided-weapon launchers.
 *
 * <p>The adapter is intentionally parallel only to the existing kinetic projection seam, not to the
 * combat runtime. It resolves one production fit, its current module damage, launcher profile and
 * weapon-feed identity into guided ammunition that can be consumed by the existing
 * {@link AmmunitionRuntime}, cycled by {@link WeaponMountRuntime} and materialized as
 * {@link GuidedWeaponBody}. No ammunition quantity, guidance result or combat outcome is stored
 * here.</p>
 */
public final class ShipGuidedWeaponEngineeringAdapter {
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;

    /**
     * One operational fitted guided launcher and its loaded physical ammunition identity.
     *
     * @param mountId physical fitted weapon mount
     * @param moduleId guided launcher engineering module ID
     * @param ammunition loaded physical guided ammunition definition
     * @param launcher damage-aware launcher timing/support definition
     * @param pointingJitterRad current launcher pointing uncertainty
     */
    public record FittedGuidedMount(
            String mountId,
            String moduleId,
            GuidedAmmunitionDefinition ammunition,
            Launcher launcher,
            double pointingJitterRad) {
        /**
         * Validates one immutable fitted guided launcher projection.
         *
         * @param mountId physical fitted weapon mount
         * @param moduleId guided launcher engineering module ID
         * @param ammunition loaded physical guided ammunition definition
         * @param launcher damage-aware launcher timing/support definition
         * @param pointingJitterRad current launcher pointing uncertainty
         */
        public FittedGuidedMount {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(ammunition, "ammunition");
            Objects.requireNonNull(launcher, "launcher");
            requireNonNegativeFinite(pointingJitterRad, "pointingJitterRad");
        }
    }

    /**
     * Resolves all loaded guided weapon mounts from one current damage-aware fitted ship.
     *
     * @param derived central derived fitted ship state
     * @param ammunition physical ammunition content catalog
     * @param launcherCatalog launcher profiles linked to engineering module IDs
     * @param loadout physical feed-to-ammunition identity bindings
     * @return deterministic mount-sorted guided launcher projections
     */
    public List<FittedGuidedMount> deriveGuidedMounts(
            DerivedShipState derived,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launcherCatalog,
            WeaponLoadoutState loadout) {
        DerivedShipState checkedDerived = Objects.requireNonNull(derived, "derived");
        WeaponAmmunitionCatalog checkedAmmunition = Objects.requireNonNull(ammunition, "ammunition");
        WeaponLauncherCatalog checkedLaunchers = Objects.requireNonNull(launcherCatalog, "launcherCatalog");
        WeaponLoadoutState checkedLoadout = Objects.requireNonNull(loadout, "loadout");

        List<FittedGuidedMount> result = new ArrayList<>();
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
                throw new IllegalArgumentException(
                        "Installed weapon module lacks Stage-17.5E launcher profile: " + capability.moduleId());
            }
            if (profile.family() != Family.GUIDED) {
                continue;
            }
            String ammunitionId = checkedLoadout
                    .ammunitionContentId(capability.mountId(), profile.ammunitionInterfaceId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Guided mount has no loaded ammunition identity: " + capability.mountId()));
            GuidedAmmunitionDefinition guided = checkedAmmunition.findGuided(ammunitionId);
            if (guided == null) {
                throw new IllegalArgumentException(
                        "Guided launcher feed references non-guided/unknown ammunition: " + ammunitionId);
            }
            validateEnvelope(profile, guided);
            Launcher launcher = new Launcher(
                    "launcher." + capability.moduleId(),
                    profile.ammunitionInterfaceId(),
                    profile.ammunitionAmountPerShot(),
                    profile.cycleTimeSeconds() / integrity,
                    profile.supportChannelCount());
            result.add(new FittedGuidedMount(
                    capability.mountId(),
                    capability.moduleId(),
                    guided,
                    launcher,
                    profile.pointingJitterRad() / integrity));
        }
        result.sort(Comparator.comparing(FittedGuidedMount::mountId)
                .thenComparing(FittedGuidedMount::moduleId));
        return List.copyOf(result);
    }

    private static double runtimeIntegrity(Map<String, Double> parameters) {
        double value = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid runtime guided-weapon integrity");
        }
        return value;
    }

    private static void validateEnvelope(
            LauncherProfile profile,
            GuidedAmmunitionDefinition ammunition) {
        if (ammunition.wetMassKg() > profile.maxProjectileMassKg()
                || ammunition.lengthM() > profile.maxProjectileLengthM()
                || ammunition.diameterM() > profile.maxProjectileDiameterM()) {
            throw new IllegalArgumentException(
                    "Loaded guided ammunition exceeds launcher physical envelope: " + ammunition.id());
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}

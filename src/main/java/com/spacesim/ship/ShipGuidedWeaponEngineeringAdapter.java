package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
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
 * <p>The adapter resolves one production fit, current module damage, launcher profile and feed
 * identity into guided ammunition that can be consumed by common runtime. Explicit fitted beam
 * emitters are routed by {@link ShipWeaponEngineeringAdapter} instead of being misclassified as a
 * missing guided launcher. Authored {@link GuidedEngagementRole} is an explicit routing semantic only;
 * it grants no accuracy, propulsion, damage, range or launcher-performance modifier.</p>
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
     * Resolves offensive ship-target guided mounts only.
     *
     * <p>This compatibility/default path is intentionally STRIKE-only so an interceptor loaded into a
     * compatible guided launcher can never be silently consumed by ordinary ship-target fire logic.
     * Defensive code must request {@link GuidedEngagementRole#INTERCEPTOR} explicitly through the
     * overload below.</p>
     *
     * @param derived central derived fitted ship state
     * @param ammunition physical ammunition content catalog
     * @param launcherCatalog launcher profiles linked to engineering module IDs
     * @param loadout physical feed-to-ammunition identity bindings
     * @return deterministic mount-sorted STRIKE launcher projections
     */
    public List<FittedGuidedMount> deriveGuidedMounts(
            DerivedShipState derived,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launcherCatalog,
            WeaponLoadoutState loadout) {
        return deriveGuidedMounts(
                derived,
                ammunition,
                launcherCatalog,
                loadout,
                GuidedEngagementRole.STRIKE);
    }

    /**
     * Resolves guided mounts loaded for one explicit authored engagement role.
     *
     * @param derived central derived fitted ship state
     * @param ammunition physical ammunition content catalog
     * @param launcherCatalog launcher profiles linked to engineering module IDs
     * @param loadout physical feed-to-ammunition identity bindings
     * @param engagementRole required authored guided-ammunition role
     * @return deterministic mount-sorted launcher projections with matching role
     */
    public List<FittedGuidedMount> deriveGuidedMounts(
            DerivedShipState derived,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launcherCatalog,
            WeaponLoadoutState loadout,
            GuidedEngagementRole engagementRole) {
        DerivedShipState checkedDerived = Objects.requireNonNull(derived, "derived");
        WeaponAmmunitionCatalog checkedAmmunition = Objects.requireNonNull(ammunition, "ammunition");
        WeaponLauncherCatalog checkedLaunchers = Objects.requireNonNull(launcherCatalog, "launcherCatalog");
        WeaponLoadoutState checkedLoadout = Objects.requireNonNull(loadout, "loadout");
        GuidedEngagementRole checkedRole = Objects.requireNonNull(engagementRole, "engagementRole");

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
                if (capability.parameters().containsKey("beam_power_w")) {
                    continue;
                }
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
            if (guided.engagementRole() != checkedRole) {
                continue;
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
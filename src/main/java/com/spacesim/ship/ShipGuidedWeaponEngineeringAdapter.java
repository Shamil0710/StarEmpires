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
 * Projects fitted engineering state into physical guided missile/interceptor launcher mounts.
 *
 * <p>The adapter mirrors the kinetic fitting boundary: launcher/feed identity comes from the ordinary
 * engineering/launcher catalogs, ammunition identity comes from the persistent physical loadout, and
 * local mount integrity degrades cycle time rather than creating a class or doctrine bonus.</p>
 */
public final class ShipGuidedWeaponEngineeringAdapter {
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;

    /**
     * One fitted guided launcher ready for ammunition/guidance/defense runtime.
     *
     * @param mountId physical fitted launcher mount
     * @param moduleId engineering module content ID
     * @param ammunition physical loaded guided body definition
     * @param launcher damage-aware launcher/feed definition
     */
    public record FittedGuidedMount(
            String mountId,
            String moduleId,
            GuidedAmmunitionDefinition ammunition,
            Launcher launcher) {
        /**
         * Validates one immutable guided mount.
         *
         * @param mountId physical fitted launcher mount
         * @param moduleId engineering module content ID
         * @param ammunition physical loaded guided body definition
         * @param launcher damage-aware launcher/feed definition
         */
        public FittedGuidedMount {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(ammunition, "ammunition");
            Objects.requireNonNull(launcher, "launcher");
        }
    }

    /**
     * Resolves all loaded guided mounts from one current fitted ship.
     *
     * @param derived central damage-aware derived ship state
     * @param ammunition physical ammunition catalog
     * @param launcherCatalog launcher profiles linked to fitted module IDs
     * @param loadout physical feed-to-ammunition bindings
     * @return deterministic immutable guided mount list
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
            if (profile == null || profile.family() != Family.GUIDED) {
                continue;
            }
            String ammunitionId = checkedLoadout
                    .ammunitionContentId(capability.mountId(), profile.ammunitionInterfaceId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Guided mount has no loaded ammunition identity: " + capability.mountId()));
            GuidedAmmunitionDefinition loaded = checkedAmmunition.findGuided(ammunitionId);
            if (loaded == null) {
                throw new IllegalArgumentException(
                        "Guided launcher references non-guided/unknown ammunition: " + ammunitionId);
            }
            double wetMassKg = loaded.toRuntimeWeapon().wetMassKg();
            if (wetMassKg > profile.maxProjectileMassKg()
                    || loaded.lengthM() > profile.maxProjectileLengthM()
                    || loaded.diameterM() > profile.maxProjectileDiameterM()) {
                throw new IllegalArgumentException(
                        "Loaded guided body exceeds launcher physical envelope: " + ammunitionId);
            }
            Launcher launcher = new Launcher(
                    "launcher." + capability.moduleId(),
                    profile.ammunitionInterfaceId(),
                    profile.ammunitionAmountPerShot(),
                    profile.cycleTimeSeconds() / integrity,
                    profile.supportChannelCount());
            result.add(new FittedGuidedMount(
                    capability.mountId(), capability.moduleId(), loaded, launcher));
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

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}

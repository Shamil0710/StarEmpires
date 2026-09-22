package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.util.ArrayList;
import java.util.Objects;

/**
 * M22.8J ordinary Stage-17.5E weapon-content projection for production strike craft.
 *
 * <p>The compact kinetic mount still uses the common launcher/ammunition adapters, finite
 * {@code ConsumableState} feed and exact Stage-19 fire-control. The two ammunition definitions differ
 * only in physical material identity; no faction or role multiplier is introduced.</p>
 */
public final class Stage228SmallCraftWeaponRuntimeProjection {
    /** Empire compact kinetic round. */
    public static final String EMPIRE_AMMO_ID = "ammo.empire_small_craft_dart_12kg_v1";
    /** Industrial Union compact kinetic round. */
    public static final String UNION_AMMO_ID = "ammo.industrial_union_small_craft_dart_12kg_v1";

    private Stage228SmallCraftWeaponRuntimeProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Appends the compact kinetic launcher profile to the ordinary launcher catalog.
     *
     * @param source accepted core launcher content
     * @param engineering M22.8J-completed engineering universe
     * @return immutable launcher catalog including the small-craft kinetic mount
     */
    public static WeaponLauncherCatalog applyLaunchers(
            WeaponLauncherCatalog source,
            ShipEngineeringCatalog engineering) {
        WeaponLauncherCatalog checked = Objects.requireNonNull(source, "source");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (checkedEngineering.findModule(Stage228SmallCraftProductionProjection.KINETIC_ID) == null) {
            throw new IllegalArgumentException("M22.8J compact kinetic module is absent");
        }
        if (checked.findByModuleId(Stage228SmallCraftProductionProjection.KINETIC_ID) != null) {
            throw new IllegalArgumentException("M22.8J compact launcher profile already exists");
        }
        ArrayList<LauncherProfile> profiles = new ArrayList<>(checked.getProfiles());
        profiles.add(new LauncherProfile(
                Stage228SmallCraftProductionProjection.KINETIC_ID,
                Family.KINETIC,
                "kinetic_feed",
                1d,
                1.5d,
                1,
                0.00015d,
                20d,
                0.8d,
                0.05d));
        return new WeaponLauncherCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                profiles);
    }

    /**
     * Appends faction-material compact kinetic ammunition to the ordinary ammunition catalog.
     *
     * @param source accepted core ammunition content
     * @param engineering M22.8J-completed engineering universe
     * @return immutable ammunition catalog including both compact rounds
     */
    public static WeaponAmmunitionCatalog applyAmmunition(
            WeaponAmmunitionCatalog source,
            ShipEngineeringCatalog engineering) {
        WeaponAmmunitionCatalog checked = Objects.requireNonNull(source, "source");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (checked.findKinetic(EMPIRE_AMMO_ID) != null || checked.findKinetic(UNION_AMMO_ID) != null) {
            throw new IllegalArgumentException("M22.8J compact ammunition already exists");
        }
        if (checkedEngineering.findMaterial("material.empire_service_alloy_v1") == null
                || checkedEngineering.findMaterial("material.industrial_union_mill_steel_v1") == null) {
            throw new IllegalArgumentException("M22.8J compact ammunition material is absent");
        }
        ArrayList<KineticAmmunitionDefinition> kinetic =
                new ArrayList<>(checked.getKineticAmmunition());
        kinetic.add(new KineticAmmunitionDefinition(
                EMPIRE_AMMO_ID,
                "material.empire_service_alloy_v1",
                ProjectileShape.DART,
                0.62d,
                0.04d,
                12d));
        kinetic.add(new KineticAmmunitionDefinition(
                UNION_AMMO_ID,
                "material.industrial_union_mill_steel_v1",
                ProjectileShape.DART,
                0.60d,
                0.042d,
                12d));
        return new WeaponAmmunitionCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                kinetic,
                checked.getGuidedAmmunition());
    }
}

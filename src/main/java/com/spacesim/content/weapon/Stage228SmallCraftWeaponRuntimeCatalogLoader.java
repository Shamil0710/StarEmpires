package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;

/**
 * M22.8J weapon-runtime universe for production small craft.
 *
 * <p>The accepted M22.6 core weapon bridge remains frozen. J appends the compact kinetic launcher
 * profile and finite faction-material ammunition while preserving the same Stage-17.5E/Stage-19
 * weapon authorities.</p>
 */
public final class Stage228SmallCraftWeaponRuntimeCatalogLoader {
    private Stage228SmallCraftWeaponRuntimeCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /** @return core runtime weapon content plus M22.8J compact small-craft weapon content */
    public static Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent loadCombined() {
        var base = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        WeaponLauncherCatalog launchers =
                Stage228SmallCraftWeaponRuntimeProjection.applyLaunchers(
                        base.launchers(), engineering);
        WeaponAmmunitionCatalog ammunition =
                Stage228SmallCraftWeaponRuntimeProjection.applyAmmunition(
                        base.ammunition(), engineering);
        return new Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent(
                engineering, launchers, ammunition);
    }
}

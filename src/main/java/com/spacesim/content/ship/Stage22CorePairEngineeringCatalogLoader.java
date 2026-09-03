package com.spacesim.content.ship;

import java.util.ArrayList;

/**
 * Composes the two accepted Stage-22 core engineering packages into one immutable tactical universe.
 *
 * <p>The original M22.3/M22.4 package loaders remain unchanged so their accepted visual/content
 * fingerprints stay stable. M22.6 then applies its explicit versioned radar/shield runtime projections
 * only to this combined tactical universe. No normalization or faction-name capability is added: every
 * package-specific material, hull, module burden and fit remains the authored source value.</p>
 */
public final class Stage22CorePairEngineeringCatalogLoader {
    private Stage22CorePairEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads, runtime-completes and combines Empire and Industrial Union engineering content.
     *
     * @return one immutable common Stage-17.5 schema catalog containing both core packages
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog empire = runtimeComplete(Stage22EmpireEngineeringCatalogLoader.loadDefault());
        ShipEngineeringCatalog union = runtimeComplete(Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault());
        if (empire.getSchemaVersion() != union.getSchemaVersion()
                || empire.getMigrationVersion() != union.getMigrationVersion()) {
            throw new IllegalStateException("Core engineering packages disagree on schema/migration versions");
        }
        return new ShipEngineeringCatalog(
                empire.getSchemaVersion(),
                empire.getMigrationVersion(),
                concat(empire.getMaterials(), union.getMaterials()),
                concat(empire.getResponseSurfaces(), union.getResponseSurfaces()),
                concat(empire.getProtectionStacks(), union.getProtectionStacks()),
                concat(empire.getHulls(), union.getHulls()),
                concat(empire.getModules(), union.getModules()),
                concat(empire.getDemonstratorFits(), union.getDemonstratorFits()));
    }

    private static ShipEngineeringCatalog runtimeComplete(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog sensors = Stage22CorePairSensorModeProjection.apply(source);
        return Stage22CorePairShieldModeProjection.apply(sensors);
    }

    private static <T> java.util.List<T> concat(java.util.List<T> first, java.util.List<T> second) {
        ArrayList<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}

package com.spacesim.content.ship;

import java.util.ArrayList;

/**
 * Composes the two accepted Stage-22 core engineering packages into one immutable runtime universe.
 *
 * <p>The original M22.3/M22.4 package loaders remain unchanged so their accepted visual/content
 * fingerprints stay stable. M22.6 then applies explicit versioned radar/shield/thermal runtime
 * completion, a common paid command-network projection and the common strategic-mobility projection
 * to the combined universe. No normalization or faction-name capability is added: every
 * package-specific material, hull and base-fit burden remains authored, while command-network and
 * strategic variants pay for their capability by displacing an existing defensive utility module.</p>
 */
public final class Stage22CorePairEngineeringCatalogLoader {
    private Stage22CorePairEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads, runtime-completes and combines Empire and Industrial Union engineering content.
     *
     * @return one immutable common Stage-17.5 schema catalog containing both core packages and the
     *         versioned common command-network and strategic-mobility variants
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog empire = runtimeComplete(Stage22EmpireEngineeringCatalogLoader.loadDefault());
        ShipEngineeringCatalog union = runtimeComplete(Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault());
        if (empire.getSchemaVersion() != union.getSchemaVersion()
                || empire.getMigrationVersion() != union.getMigrationVersion()) {
            throw new IllegalStateException("Core engineering packages disagree on schema/migration versions");
        }
        ShipEngineeringCatalog combined = new ShipEngineeringCatalog(
                empire.getSchemaVersion(),
                empire.getMigrationVersion(),
                concat(empire.getMaterials(), union.getMaterials()),
                concat(empire.getResponseSurfaces(), union.getResponseSurfaces()),
                concat(empire.getProtectionStacks(), union.getProtectionStacks()),
                concat(empire.getHulls(), union.getHulls()),
                concat(empire.getModules(), union.getModules()),
                concat(empire.getDemonstratorFits(), union.getDemonstratorFits()));
        ShipEngineeringCatalog network = Stage22CorePairCommandNetworkProjection.apply(combined);
        return Stage22CorePairStrategicMobilityProjection.apply(network);
    }

    private static ShipEngineeringCatalog runtimeComplete(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog sensors = Stage22CorePairSensorModeProjection.apply(source);
        ShipEngineeringCatalog shields = Stage22CorePairShieldModeProjection.apply(sensors);
        return Stage22CorePairThermalRuntimeProjection.apply(shields);
    }

    private static <T> java.util.List<T> concat(java.util.List<T> first, java.util.List<T> second) {
        ArrayList<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}
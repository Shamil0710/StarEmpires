package com.spacesim.content.ship;

import java.util.ArrayList;

/**
 * Composes the two accepted Stage-22 core engineering packages into one immutable tactical universe.
 *
 * <p>The composition performs no normalization and adds no capability. Every material, response
 * surface, protection stack, hull, module and fit is copied unchanged from the package loaders. The
 * ordinary {@link ShipEngineeringCatalog} constructor remains responsible for deterministic ordering
 * and duplicate-ID rejection.</p>
 */
public final class Stage22CorePairEngineeringCatalogLoader {
    private Stage22CorePairEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and combines Empire and Industrial Union engineering content.
     *
     * @return one immutable common Stage-17.5 schema catalog containing both core packages
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog empire = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog union = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
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

    private static <T> java.util.List<T> concat(java.util.List<T> first, java.util.List<T> second) {
        ArrayList<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}

package com.spacesim.content.ship;

/**
 * M22.8J production engineering universe for persistent carrier small craft.
 *
 * <p>The accepted M22.6 core-pair loader remains frozen. J starts from that immutable baseline,
 * appends small-craft hull/module/fit definitions, then reuses the same idempotent sensor, shield and
 * thermal runtime projections so the new compact systems enter ordinary Stage-17.5/19 authorities.</p>
 */
public final class Stage228SmallCraftEngineeringCatalogLoader {
    private Stage228SmallCraftEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /** @return M22.6 core engineering plus validated M22.8J production small-craft content */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog projected =
                Stage228SmallCraftProductionProjection.apply(
                        Stage22CorePairEngineeringCatalogLoader.loadDefault());
        projected = Stage22CorePairSensorModeProjection.apply(projected);
        projected = Stage22CorePairShieldModeProjection.apply(projected);
        return Stage22CorePairThermalRuntimeProjection.apply(projected);
    }
}

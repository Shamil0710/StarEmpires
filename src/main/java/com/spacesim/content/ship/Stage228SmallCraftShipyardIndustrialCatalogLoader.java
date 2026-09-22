package com.spacesim.content.ship;

/**
 * M22.8J shipyard-industrial universe for production small craft.
 *
 * <p>The accepted M22.6 core-pair industrial loaders remain frozen. J appends only the compact
 * small-craft hull/module profiles required by the ordinary Stage-17.5G planner.</p>
 */
public final class Stage228SmallCraftShipyardIndustrialCatalogLoader {
    private Stage228SmallCraftShipyardIndustrialCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /** @return Empire M22.6 industrial catalog plus M22.8J small-craft profiles */
    public static ShipyardIndustrialCatalog loadEmpireDefault() {
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        return Stage228SmallCraftIndustrialProjection.applyEmpire(
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault(),
                engineering);
    }

    /** @return Union M22.6 industrial catalog plus M22.8J small-craft profiles */
    public static ShipyardIndustrialCatalog loadIndustrialUnionDefault() {
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        return Stage228SmallCraftIndustrialProjection.applyIndustrialUnion(
                Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault(),
                engineering);
    }
}

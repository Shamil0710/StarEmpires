package com.spacesim.content.ship;

/**
 * M22.6 composition loader for the core-pair shipyard industrial surface.
 *
 * <p>The accepted M22.3/M22.4 authored resources remain immutable. Each faction first loads its
 * already-validated base shipyard requirements and only then receives the common strategic FTL
 * module through the versioned M22.6 industrial projection. This preserves earlier fingerprints and
 * keeps manufacturing/integration work inside the ordinary Stage-17.5G/Stage-18 authority.</p>
 */
public final class Stage22CorePairShipyardIndustrialCatalogLoader {
    private Stage22CorePairShipyardIndustrialCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the Empire's accepted industrial requirements plus the paid core strategic FTL profile.
     *
     * @return immutable M22.6 Empire industrial catalog
     */
    public static ShipyardIndustrialCatalog loadEmpireDefault() {
        ShipyardIndustrialCatalog base = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        return Stage22CorePairStrategicMobilityIndustrialProjection.applyEmpire(base, engineering);
    }

    /**
     * Loads the Industrial Union's accepted industrial requirements plus the paid core strategic FTL profile.
     *
     * @return immutable M22.6 Industrial Union industrial catalog
     */
    public static ShipyardIndustrialCatalog loadIndustrialUnionDefault() {
        ShipyardIndustrialCatalog base = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        return Stage22CorePairStrategicMobilityIndustrialProjection.applyIndustrialUnion(base, engineering);
    }
}

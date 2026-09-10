package com.spacesim.content.ship;

/**
 * M22.6 composition loader for the core-pair shipyard industrial surface.
 *
 * <p>The accepted M22.3/M22.4 authored resources remain immutable. Each faction first loads its
 * already-validated base shipyard requirements and only then receives the common strategic FTL and
 * command-network datalink through versioned M22.6 industrial projections. This preserves earlier
 * fingerprints and keeps manufacturing/integration work inside the ordinary Stage-17.5G/Stage-18
 * authority.</p>
 */
public final class Stage22CorePairShipyardIndustrialCatalogLoader {
    private Stage22CorePairShipyardIndustrialCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the Empire's accepted industrial requirements plus paid common M22.6 module profiles.
     *
     * @return completed Empire shipyard industrial catalog for M22.6
     */
    public static ShipyardIndustrialCatalog loadEmpireDefault() {
        ShipyardIndustrialCatalog base = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog strategic =
                Stage22CorePairStrategicMobilityIndustrialProjection.applyEmpire(base, engineering);
        return Stage22CorePairCommandNetworkIndustrialProjection.applyEmpire(strategic, engineering);
    }

    /**
     * Loads the Industrial Union's accepted industrial requirements plus paid common M22.6 module profiles.
     *
     * @return completed Industrial Union shipyard industrial catalog for M22.6
     */
    public static ShipyardIndustrialCatalog loadIndustrialUnionDefault() {
        ShipyardIndustrialCatalog base = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog strategic =
                Stage22CorePairStrategicMobilityIndustrialProjection.applyIndustrialUnion(base, engineering);
        return Stage22CorePairCommandNetworkIndustrialProjection.applyIndustrialUnion(strategic, engineering);
    }
}

package com.spacesim.content.ship;

import com.spacesim.content.Stage22AuthoredResourceFragments;

import java.util.List;

/** Loads M22.4 Industrial Union requirements through the accepted Stage-17.5G schema. */
public final class Stage22IndustrialUnionShipyardIndustrialCatalogLoader {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "data/content/stage22-industrial-union-shipyard-industrial-v1.part00",
            "data/content/stage22-industrial-union-shipyard-industrial-v1.part01",
            "data/content/stage22-industrial-union-shipyard-industrial-v1.part02");

    private Stage22IndustrialUnionShipyardIndustrialCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads and validates Union industrial requirements against the Union engineering catalog.
     *
     * @return immutable Stage-17.5G shipyard-industrial catalog
     */
    public static ShipyardIndustrialCatalog loadDefault(){
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        return ShipyardIndustrialCatalogLoader.parse(Stage22AuthoredResourceFragments.read(
                Stage22IndustrialUnionShipyardIndustrialCatalogLoader.class,
                DEFAULT_RESOURCES,
                "Industrial Union shipyard-industrial"), engineering);
    }
}

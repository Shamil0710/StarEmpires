package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionShipyardIndustrialCatalogLoader;

import java.util.List;

/** Loads Union physical shipyard/build/repair content through the accepted Stage-18G authority. */
public final class Stage22IndustrialUnionShipyardCatalogLoader {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "data/content/stage22-industrial-union-stage18-shipyards-v1.part00",
            "data/content/stage22-industrial-union-stage18-shipyards-v1.part01",
            "data/content/stage22-industrial-union-stage18-shipyards-v1.part02",
            "data/content/stage22-industrial-union-stage18-shipyards-v1.part03");

    private Stage22IndustrialUnionShipyardCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads and validates the Union physical shipyard package against accepted ontology,
     * facilities, engineering and Stage-17.5G industrial requirements.
     *
     * @return immutable Stage-18G shipyard catalog
     */
    public static Stage18ShipyardCatalog loadDefault(){
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial=Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        return Stage18ShipyardCatalogLoader.parse(
                Stage22AuthoredResourceFragments.read(
                        Stage22IndustrialUnionShipyardCatalogLoader.class,
                        DEFAULT_RESOURCES,
                        "Industrial Union Stage-18G shipyard"),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                engineering,
                industrial);
    }
}

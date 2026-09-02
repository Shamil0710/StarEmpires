package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionShipyardIndustrialCatalogLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads Union physical shipyard/build/repair content through the accepted Stage-18G authority. */
public final class Stage22IndustrialUnionShipyardCatalogLoader {
    public static final String DEFAULT_RESOURCE="data/content/stage22-industrial-union-stage18-shipyards-v1.json";
    private Stage22IndustrialUnionShipyardCatalogLoader(){throw new AssertionError("utility class");}
    public static Stage18ShipyardCatalog loadDefault(){
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial=Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        try(InputStream stream=Stage22IndustrialUnionShipyardCatalogLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing Industrial Union Stage-18G shipyard resource: "+DEFAULT_RESOURCE);
            return Stage18ShipyardCatalogLoader.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8),
                    Stage18ResourceOntologyLoader.loadDefault(),Stage18FacilityCatalogLoader.loadDefault(),engineering,industrial);
        }catch(IOException exception){throw new IllegalStateException("Cannot read Industrial Union Stage-18G shipyard resource",exception);}
    }
}

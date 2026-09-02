package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads M22.4 Industrial Union requirements through the accepted Stage-17.5G schema. */
public final class Stage22IndustrialUnionShipyardIndustrialCatalogLoader {
    /** Built-in Industrial Union shipyard-industrial requirement resource. */
    public static final String DEFAULT_RESOURCE="data/content/stage22-industrial-union-shipyard-industrial-v1.json";
    private Stage22IndustrialUnionShipyardIndustrialCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads and validates Union industrial requirements against the Union engineering catalog.
     *
     * @return immutable Stage-17.5G shipyard-industrial catalog
     */
    public static ShipyardIndustrialCatalog loadDefault(){
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        try(InputStream stream=Stage22IndustrialUnionShipyardIndustrialCatalogLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing Industrial Union shipyard-industrial resource: "+DEFAULT_RESOURCE);
            return ShipyardIndustrialCatalogLoader.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8),engineering);
        }catch(IOException exception){throw new IllegalStateException("Cannot read Industrial Union shipyard-industrial resource",exception);}
    }
}

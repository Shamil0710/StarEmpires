package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the M22.4 Industrial Union engineering catalog through the common Stage-17.5 schema. */
public final class Stage22IndustrialUnionEngineeringCatalogLoader {
    /** Built-in Industrial Union engineering resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-industrial-union-engineering-v1.json";
    private Stage22IndustrialUnionEngineeringCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads and strictly validates the built-in Union engineering package.
     *
     * @return common immutable Stage-17.5 engineering catalog
     */
    public static ShipEngineeringCatalog loadDefault(){
        try(InputStream stream=Stage22IndustrialUnionEngineeringCatalogLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing Industrial Union engineering resource: "+DEFAULT_RESOURCE);
            return ShipEngineeringCatalogLoader.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }catch(IOException exception){throw new IllegalStateException("Cannot read Industrial Union engineering resource",exception);}
    }
}

package com.spacesim.content.ship;

import com.spacesim.content.Stage22AuthoredResourceFragments;

import java.util.List;

/** Loads the M22.4 Industrial Union engineering catalog through the common Stage-17.5 schema. */
public final class Stage22IndustrialUnionEngineeringCatalogLoader {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "data/content/stage22-industrial-union-engineering-v1.part00",
            "data/content/stage22-industrial-union-engineering-v1.part01",
            "data/content/stage22-industrial-union-engineering-v1.part02",
            "data/content/stage22-industrial-union-engineering-v1.part03",
            "data/content/stage22-industrial-union-engineering-v1.part04",
            "data/content/stage22-industrial-union-engineering-v1.part05",
            "data/content/stage22-industrial-union-engineering-v1.part06");

    private Stage22IndustrialUnionEngineeringCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads and strictly validates the built-in Union engineering package.
     *
     * @return common immutable Stage-17.5 engineering catalog
     */
    public static ShipEngineeringCatalog loadDefault(){
        ShipEngineeringCatalog authored = ShipEngineeringCatalogLoader.parse(Stage22AuthoredResourceFragments.read(
                Stage22IndustrialUnionEngineeringCatalogLoader.class,
                DEFAULT_RESOURCES,
                "Industrial Union engineering"));
        ShipEngineeringCatalog sensors = Stage22CorePairSensorModeProjection.apply(authored);
        return Stage22CorePairShieldModeProjection.apply(sensors);
    }
}

package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22EmpireShipyardIndustrialCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads Empire physical shipyard/build/repair content through the accepted Stage-18G authority. */
public final class Stage22EmpireShipyardCatalogLoader {
    /** Empire Stage-18G physical shipyard resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-empire-stage18-shipyards-v1.json";

    private Stage22EmpireShipyardCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and validates the Empire physical shipyard package against existing Stage-18 ontology and
     * facilities plus the re-authored Empire engineering/industrial requirements.
     *
     * @return immutable Stage-18G shipyard catalog
     */
    public static Stage18ShipyardCatalog loadDefault() {
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        try (InputStream stream = Stage22EmpireShipyardCatalogLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire Stage-18G shipyard resource: " + DEFAULT_RESOURCE);
            }
            return Stage18ShipyardCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    Stage18ResourceOntologyLoader.loadDefault(),
                    Stage18FacilityCatalogLoader.loadDefault(),
                    engineering,
                    industrial);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Empire Stage-18G shipyard resource", exception);
        }
    }
}

package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads M22.3 Empire shipyard requirements through the accepted Stage-17.5G schema. */
public final class Stage22EmpireShipyardIndustrialCatalogLoader {
    /** Empire shipyard-industrial requirement resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-empire-shipyard-industrial-v1.json";

    private Stage22EmpireShipyardIndustrialCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and validates the Empire requirement catalog against the Empire engineering definitions.
     *
     * @return immutable Stage-17.5G requirement catalog
     */
    public static ShipyardIndustrialCatalog loadDefault() {
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        try (InputStream stream = Stage22EmpireShipyardIndustrialCatalogLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire shipyard-industrial resource: " + DEFAULT_RESOURCE);
            }
            return ShipyardIndustrialCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Empire shipyard-industrial resource", exception);
        }
    }
}

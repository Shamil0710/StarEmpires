package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the re-authored M22.3 Empire engineering catalog through the common Stage-17.5 schema. */
public final class Stage22EmpireEngineeringCatalogLoader {
    /** Empire production engineering resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-empire-engineering-v1.json";

    private Stage22EmpireEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads and strictly validates the built-in Empire engineering package.
     *
     * @return common immutable engineering catalog
     */
    public static ShipEngineeringCatalog loadDefault() {
        try (InputStream stream = Stage22EmpireEngineeringCatalogLoader.class.getClassLoader()
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire engineering resource: " + DEFAULT_RESOURCE);
            }
            return ShipEngineeringCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Empire engineering resource", exception);
        }
    }
}

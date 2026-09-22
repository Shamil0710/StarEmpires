package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * M22.8J immutable production engineering universe for carriers and persistent small craft.
 *
 * <p>The accepted core-pair catalogue remains unchanged. This loader appends a separate versioned
 * small-craft production document and applies the same common sensor-mode projection to the newly
 * authored sensor modules. Existing M22.3-M22.6 IDs and fingerprints therefore remain stable in
 * their original loaders while M22.8 obtains one superset authority for individual craft.</p>
 */
public final class Stage22CarrierEngineeringCatalogLoader {
    /** Versioned small-craft engineering resource. */
    public static final String SMALL_CRAFT_RESOURCE =
            "data/content/stage22-small-craft-engineering-v1.json";

    private Stage22CarrierEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the accepted core pair plus M22.8J small-craft hulls, modules and fits.
     *
     * @return immutable superset production engineering catalogue
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog core = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipEngineeringCatalog small = Stage22CorePairSensorModeProjection.apply(
                ShipEngineeringCatalogLoader.parse(readResource(SMALL_CRAFT_RESOURCE)));
        if (core.getSchemaVersion() != small.getSchemaVersion()
                || core.getMigrationVersion() != small.getMigrationVersion()) {
            throw new IllegalStateException("Carrier small-craft engineering schema mismatch");
        }
        return new ShipEngineeringCatalog(
                core.getSchemaVersion(),
                core.getMigrationVersion(),
                concat(core.getMaterials(), small.getMaterials()),
                concat(core.getResponseSurfaces(), small.getResponseSurfaces()),
                concat(core.getProtectionStacks(), small.getProtectionStacks()),
                concat(core.getHulls(), small.getHulls()),
                concat(core.getModules(), small.getModules()),
                concat(core.getDemonstratorFits(), small.getDemonstratorFits()));
    }

    private static String readResource(String path) {
        try (InputStream stream = Stage22CarrierEngineeringCatalogLoader.class
                .getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing M22.8J engineering resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read M22.8J engineering resource: " + path, exception);
        }
    }

    private static <T> java.util.List<T> concat(
            java.util.List<T> first,
            java.util.List<T> second) {
        ArrayList<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }
}

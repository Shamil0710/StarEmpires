package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the production-valid but content-provisional Stage 17.5I engineering packs.
 *
 * <p>Both representative hull vocabulary and doctrine fits deliberately use
 * {@link ShipEngineeringCatalogLoader} rather than a parallel test schema. Stage 22 may replace or
 * explicitly promote individual definitions; their presence here proves production
 * loader/validator compatibility only.</p>
 */
public final class Stage175ICombatTestContentPack {
    /** Classpath resource for the Stage 17.5I representative hull engineering pack. */
    public static final String RESOURCE = "data/content/stage17_5i-combat-test-engineering-v1.json";
    /** Classpath resource for the Stage 17.5I five-doctrine engineering pack. */
    public static final String DOCTRINE_RESOURCE = "data/content/stage17_5i-doctrine-engineering-v1.json";

    private Stage175ICombatTestContentPack() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the representative hull pack through the ordinary production engineering parser.
     *
     * @return immutable validated representative engineering catalog
     */
    public static ShipEngineeringCatalog load() {
        return loadResource(RESOURCE);
    }

    /**
     * Loads the five-doctrine combat pack through the ordinary production engineering parser.
     *
     * @return immutable validated doctrine engineering catalog
     */
    public static ShipEngineeringCatalog loadDoctrines() {
        return loadResource(DOCTRINE_RESOURCE);
    }

    private static ShipEngineeringCatalog loadResource(String resource) {
        ClassLoader classLoader = Stage175ICombatTestContentPack.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage 17.5I combat-test content: " + resource);
            }
            return ShipEngineeringCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage 17.5I combat-test content: " + resource, exception);
        }
    }
}

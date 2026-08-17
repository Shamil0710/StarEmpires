package com.spacesim.content.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the content-provisional Stage-17.5I protection/damage sidecar through production validation. */
public final class Stage175ICombatTestProtectionPack {
    /** Classpath resource containing the acceptance protection and compartment layout. */
    public static final String RESOURCE = "data/content/stage17_5i-protection-runtime-v1.json";

    private Stage175ICombatTestProtectionPack() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the protection sidecar against the ordinary Stage-17.5I doctrine engineering catalog.
     *
     * @return immutable validated protection catalog
     */
    public static ShipProtectionCatalog load() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        ClassLoader classLoader = Stage175ICombatTestProtectionPack.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage 17.5I protection content: " + RESOURCE);
            }
            return ShipProtectionCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage 17.5I protection content: " + RESOURCE, exception);
        }
    }
}

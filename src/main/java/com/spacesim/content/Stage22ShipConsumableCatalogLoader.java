package com.spacesim.content;

import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Stage-22 production overlay for physical ship-consumable servicing.
 *
 * <p>The Stage-18 default catalog is intentionally left unchanged because it participates in the
 * persisted Stage-18 industrial content fingerprint. This loader composes the later production
 * propulsion vocabulary at the Stage-22 boundary instead, so new core and long-haul drives can be
 * serviced without invalidating historical industrial saves.</p>
 */
public final class Stage22ShipConsumableCatalogLoader {
    /** Stage-22 production servicing-binding resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-ship-consumables-v1.json";

    private Stage22ShipConsumableCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads Stage-22 production servicing bindings against the current resource ontology and
     * production engineering catalog.
     *
     * @return immutable validated servicing catalog including legacy and Stage-22 drive bindings
     */
    public static Stage18ShipConsumableCatalog loadDefault() {
        ClassLoader loader = Stage22ShipConsumableCatalogLoader.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing Stage-22 ship-consumable catalog: " + DEFAULT_RESOURCE);
            }
            return Stage18ShipConsumableCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    Stage18ResourceOntologyLoader.loadDefault(),
                    Stage22FreightStrategicEngineeringCatalogLoader.loadDefault());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22 ship-consumable catalog", exception);
        }
    }
}

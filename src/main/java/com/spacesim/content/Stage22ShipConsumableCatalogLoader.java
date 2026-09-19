package com.spacesim.content;

import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;

import java.util.ArrayList;

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
    /** Stage-22 core/freight servicing-binding resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-ship-consumables-v1.json";
    /** Stage-22 overlay bindings for the current generated-military Stage-21 drive vocabulary. */
    public static final String GENERATED_MILITARY_RESOURCE =
            "data/content/stage22-generated-military-consumables-v1.json";

    private Stage22ShipConsumableCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads Stage-22 production servicing bindings against the current resource ontology and
     * production engineering catalog.
     *
     * @return immutable validated servicing catalog for Stage-22 production drive bindings
     */
    public static Stage18ShipConsumableCatalog loadDefault() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ShipConsumableCatalog core = parseResource(
                DEFAULT_RESOURCE,
                ontology,
                Stage22FreightStrategicEngineeringCatalogLoader.loadDefault());
        Stage18ShipConsumableCatalog generatedMilitary = parseResource(
                GENERATED_MILITARY_RESOURCE,
                ontology,
                Stage21GeneratedMilitaryEngineeringCatalog.load());
        ArrayList<Stage18ShipConsumableCatalog.ShipConsumableBinding> bindings =
                new ArrayList<>(core.getBindings());
        bindings.addAll(generatedMilitary.getBindings());
        return new Stage18ShipConsumableCatalog(core.getSchemaVersion(), bindings);
    }

    private static Stage18ShipConsumableCatalog parseResource(
            String resource,
            Stage18ResourceOntologyCatalog ontology,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering) {
        ClassLoader loader = Stage22ShipConsumableCatalogLoader.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22 ship-consumable catalog: " + resource);
            }
            return Stage18ShipConsumableCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    ontology,
                    engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22 ship-consumable catalog: " + resource, exception);
        }
    }
}

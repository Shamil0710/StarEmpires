package com.spacesim.content.ship;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipProtectionCatalogLoaderTest {
    @Test
    void defaultCatalogResolvesExistingEngineeringReferences() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipProtectionCatalog catalog = ShipProtectionCatalogLoader.loadDefault(engineering);

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(1, catalog.getHeavyImpactModels().size());
        assertEquals(1, catalog.getHullDamageLayouts().size());
        assertNotNull(catalog.findHeavyImpactModel("response.synthetic_heavy_v1"));
        ShipProtectionCatalog.HullDamageLayout layout =
                catalog.findHullDamageLayout("hull.escort_destroyer_v1");
        assertNotNull(layout);
        assertEquals(3, layout.compartments().size());
        assertEquals(5, layout.mounts().size());
        assertEquals("engineering", layout.mountsById().get("core_drive").compartmentId());
    }

    @Test
    void rejectsMalformedUnknownAndIncompleteLayouts() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        String json = defaultJson();

        assertThrows(NullPointerException.class,
                () -> ShipProtectionCatalogLoader.parse(null, engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse("[]", engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("response.synthetic_heavy_v1", "response.missing"), engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("\"hull.escort_destroyer_v1\"", "\"hull.missing\""), engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("\"mountId\": \"core_drive\"", "\"mountId\": \"missing_mount\""), engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("\"compartmentId\": \"weapons\",\n          \"structuralDamageCapacityJ\"", "\"compartmentId\": \"missing\",\n          \"structuralDamageCapacityJ\""), engineering));
        assertThrows(IllegalArgumentException.class,
                () -> ShipProtectionCatalogLoader.parse(json.replace("\"spallMassFraction\": 0.08", "\"spallMassFraction\": 1.5"), engineering));
    }

    private static String defaultJson() {
        try (InputStream stream = ShipProtectionCatalogLoaderTest.class.getClassLoader()
                .getResourceAsStream(ShipProtectionCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing protection test resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package com.spacesim.content.ship;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage175ICombatTestProtectionContentTest {
    private static final String ENGINEERING = "data/content/stage17_5i-combat-test-engineering-v1.json";
    private static final String PROTECTION = "data/content/stage17_5i-combat-test-protection-v1.json";

    @Test
    void everyRepresentativeHullHasACompleteProductionDamageLayout() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.parse(read(ENGINEERING));
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.parse(read(PROTECTION), engineering);

        assertEquals(1, protection.getHeavyImpactModels().size());
        assertNotNull(protection.findHeavyImpactModel("response.ct_heavy_v1"));
        assertEquals(Set.of(
                "hull.ct_corvette_v1",
                "hull.ct_frigate_v1",
                "hull.ct_destroyer_v1",
                "hull.ct_cruiser_v1",
                "hull.ct_bulk_freighter_v1",
                "hull.ct_tanker_v1"), protection.getHullDamageLayouts().stream()
                .map(ShipProtectionCatalog.HullDamageLayout::hullId)
                .collect(Collectors.toSet()));
    }

    private static String read(String resource) {
        ClassLoader classLoader = Stage175ICombatTestProtectionContentTest.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + resource, exception);
        }
    }
}

package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22FitFingerprintTest {
    @Test
    void exactRegisteredFitFingerprintIsDeterministicAndBounded() {
        var engineering = ShipEngineeringCatalogLoader.loadDefault();

        String first = Stage22FitFingerprint.compute(engineering, "fit.escort_destroyer_schema_v1");
        String second = Stage22FitFingerprint.compute(engineering, "fit.escort_destroyer_schema_v1");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(engineering.getFingerprint(), first,
                "Fit binding must include exact fit/hull/module semantics, not reuse the catalog fingerprint verbatim");
    }

    @Test
    void unknownFitCannotAcquireAVisualFingerprint() {
        var engineering = ShipEngineeringCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class,
                () -> Stage22FitFingerprint.compute(engineering, "fit.missing.stage22_v1"));
    }
}

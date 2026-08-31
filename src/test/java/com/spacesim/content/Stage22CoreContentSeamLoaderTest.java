package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CoreContentSeamLoaderTest {
    @Test
    void repeatedLoadsAreDeterministic() {
        var first = Stage22CoreContentSeamLoader.loadDefault();
        var second = Stage22CoreContentSeamLoader.loadDefault();
        var productionFirst = Stage22CoreProductionManifestLoader.loadDefault();
        var productionSecond = Stage22CoreProductionManifestLoader.loadDefault();

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.roles(), second.roles());
        assertEquals(productionFirst.fingerprint(), productionSecond.fingerprint());
        assertEquals(productionFirst.productionManifests(), productionSecond.productionManifests());
    }

    @Test
    void commonSeamRejectsFactionSpecificPackageLeak() {
        String json = read(Stage22CoreContentSeamLoader.DEFAULT_RESOURCE);
        String biased = json.replace(
                "Low-burden local escort",
                "core.empire Low-burden local escort");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> Stage22CoreContentSeamLoader.parse(biased));
        assertTrue(failure.getMessage().contains("faction-specific package token"));
    }

    @Test
    void unknownFitAndNonDiagnosticTelemetryFailClosed() {
        String json = read(Stage22CoreContentSeamLoader.DEFAULT_RESOURCE);
        String unknownFit = json.replace(
                "fit.escort_destroyer_schema_v1",
                "fit.stage22_missing_v1");
        assertThrows(IllegalArgumentException.class, () -> Stage22CoreContentSeamLoader.parse(unknownFit));

        String gameplayTelemetry = json.replaceFirst("\\\"diagnosticOnly\\\":true", "\\\"diagnosticOnly\\\":false");
        assertThrows(IllegalArgumentException.class, () -> Stage22CoreContentSeamLoader.parse(gameplayTelemetry));
    }

    @Test
    void productionManifestRejectsDuplicateComponentsAndInvalidSchema() {
        String json = read(Stage22CoreProductionManifestLoader.DEFAULT_RESOURCE);
        String duplicate = json.replace(
                "\"module.railgun_large_v1\"",
                "\"module.railgun_large_v1\",\"module.railgun_large_v1\"");
        assertThrows(IllegalArgumentException.class, () -> Stage22CoreProductionManifestLoader.parse(duplicate));

        String badSchema = json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99");
        assertThrows(IllegalArgumentException.class, () -> Stage22CoreProductionManifestLoader.parse(badSchema));
    }

    @Test
    void productionManifestRejectsFactionBiasAndUnknownMaturity() {
        String json = read(Stage22CoreProductionManifestLoader.DEFAULT_RESOURCE);
        String biased = json.replace(
                "Faction-neutral physical authoring exemplar",
                "core.industrial_union physical authoring exemplar");
        IllegalArgumentException biasFailure = assertThrows(
                IllegalArgumentException.class,
                () -> Stage22CoreProductionManifestLoader.parse(biased));
        assertTrue(biasFailure.getMessage().contains("faction-specific package token"));

        String unknownMaturity = json.replace(
                "\"contentMaturity\":\"CANDIDATE\"",
                "\"contentMaturity\":\"UNREVIEWED\"");
        IllegalArgumentException maturityFailure = assertThrows(
                IllegalArgumentException.class,
                () -> Stage22CoreProductionManifestLoader.parse(unknownMaturity));
        assertTrue(maturityFailure.getMessage().contains("Unknown contentMaturity"));
    }

    @Test
    void productionMaturityIsPartOfSemanticFingerprint() {
        String json = read(Stage22CoreProductionManifestLoader.DEFAULT_RESOURCE);
        var candidate = Stage22CoreProductionManifestLoader.parse(json);
        var seed = Stage22CoreProductionManifestLoader.parse(json.replace(
                "\"contentMaturity\":\"CANDIDATE\"",
                "\"contentMaturity\":\"SEED\""));

        assertNotEquals(candidate.fingerprint(), seed.fingerprint());
        assertNotEquals(candidate.productionManifests(), seed.productionManifests());
    }

    private static String read(String resource) {
        ClassLoader classLoader = Stage22CoreContentSeamLoaderTest.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Cannot read test resource: " + resource, exception);
        }
    }
}

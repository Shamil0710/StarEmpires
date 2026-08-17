package com.spacesim.content;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ExtractionCatalogLoaderTest {
    @Test
    void defaultCatalogDefinesRequiredPhysicalExtractionPaths() {
        Stage18ExtractionCatalog catalog = Stage18ExtractionCatalogLoader.loadDefault();

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(5, catalog.getMethods().size());
        Set<String> ids = catalog.getMethods().stream()
                .map(Stage18ExtractionCatalog.ExtractionMethodDefinition::id)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "extraction.asteroid_excavation",
                "extraction.surface_mining",
                "extraction.deep_mining",
                "extraction.thermal_volatiles",
                "extraction.salvage_recovery"), ids);

        var asteroid = catalog.findMethod("extraction.asteroid_excavation");
        assertEquals(SourceKind.NATURAL_OCCURRENCE, asteroid.sourceKind());
        assertEquals(ExtractionEnvironment.FREE_BODY, asteroid.environment());
        assertTrue(asteroid.requiredCapabilityTags().contains("capability.extraction.asteroid_excavation"));
        assertTrue(asteroid.compatibleOccurrenceTypeIds().contains("occurrence.metallic"));

        var salvage = catalog.findMethod("extraction.salvage_recovery");
        assertEquals(SourceKind.SALVAGE_STREAM, salvage.sourceKind());
        assertEquals(ExtractionEnvironment.SALVAGE_SITE, salvage.environment());
        assertTrue(salvage.compatibleOccurrenceTypeIds().isEmpty());
        assertTrue(salvage.recoveryFraction() < 1d);
    }

    @Test
    void repeatedLoadsHaveStableSemanticFingerprint() {
        Stage18ExtractionCatalog first = Stage18ExtractionCatalogLoader.loadDefault();
        Stage18ExtractionCatalog second = Stage18ExtractionCatalogLoader.loadDefault();

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(64, first.getFingerprint().length());
        assertNotEquals(Stage18ResourceOntologyLoader.loadDefault().getFingerprint(), first.getFingerprint());
    }

    @Test
    void parserRejectsUnknownOccurrenceAndCapabilityReferences() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        String unknownOccurrence = document(method(
                "NATURAL_OCCURRENCE", "FREE_BODY", "[\"occurrence.missing\"]",
                "[\"capability.extraction.asteroid_excavation\"]"));
        String unknownCapability = document(method(
                "NATURAL_OCCURRENCE", "FREE_BODY", "[\"occurrence.metallic\"]",
                "[\"capability.missing\"]"));

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage18ExtractionCatalogLoader.parse(unknownOccurrence, ontology));
        assertThrows(
                IllegalArgumentException.class,
                () -> Stage18ExtractionCatalogLoader.parse(unknownCapability, ontology));
    }

    @Test
    void parserRejectsSalvageMethodPretendingToBeGeologicalOccurrence() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        String json = document(method(
                "SALVAGE_STREAM", "SALVAGE_SITE", "[\"occurrence.metallic\"]",
                "[\"capability.process.recycling\"]"));

        assertThrows(IllegalArgumentException.class, () -> Stage18ExtractionCatalogLoader.parse(json, ontology));
    }

    @Test
    void parserRejectsDuplicateMethodAndInvalidEngineeringInputs() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        String validMethod = method(
                "NATURAL_OCCURRENCE", "FREE_BODY", "[\"occurrence.metallic\"]",
                "[\"capability.extraction.asteroid_excavation\"]");
        String duplicate = "{\"schemaVersion\":1,\"methods\":[" + validMethod + "," + validMethod + "]}";
        String nonPositiveWork = document(validMethod.replace("\"workSecondsPerSourceKg\":0.1", "\"workSecondsPerSourceKg\":0.0"));

        assertThrows(IllegalArgumentException.class, () -> Stage18ExtractionCatalogLoader.parse(duplicate, ontology));
        assertThrows(IllegalArgumentException.class, () -> Stage18ExtractionCatalogLoader.parse(nonPositiveWork, ontology));
        assertThrows(IllegalArgumentException.class, () -> Stage18ExtractionCatalogLoader.parse("[]", ontology));
        assertThrows(IllegalArgumentException.class, () -> Stage18ExtractionCatalogLoader.parse(" ", ontology));
    }

    private static String document(String method) {
        return "{\"schemaVersion\":1,\"methods\":[" + method + "]}";
    }

    private static String method(
            String sourceKind,
            String environment,
            String occurrences,
            String capabilities) {
        return """
                {
                  "id":"extraction.test",
                  "displayName":"Test extraction",
                  "sourceKind":"%s",
                  "environment":"%s",
                  "compatibleOccurrenceTypeIds":%s,
                  "requiredCapabilityTags":%s,
                  "workSecondsPerSourceKg":0.1,
                  "energyJPerSourceKg":1000.0,
                  "maintenanceWorkSecondsPerSourceKg":0.01,
                  "maxSourceKgPerSecond":10.0,
                  "recoveryFraction":0.8
                }
                """.formatted(sourceKind, environment, occurrences, capabilities);
    }
}

package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18FacilityCatalogTest {
    @Test
    void productionCatalogProvidesFourteenPhysicalFacilityDefinitionsAndCompleteCapabilityCoverage() {
        Stage18FacilityCatalog catalog = Stage18FacilityCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();

        assertEquals(14, catalog.getFacilities().size());
        assertEquals(64, catalog.getFingerprint().length());
        assertNotNull(catalog.findFacility("facility.processing.bulk_refinery"));
        assertNotNull(catalog.findFacility("facility.fabrication.assembly"));
        assertNotNull(catalog.findFacility("facility.fabrication.ordnance"));

        Set<String> covered = new HashSet<>();
        catalog.getFacilities().forEach(facility -> {
            covered.addAll(facility.capabilityTags());
            facility.capabilityTags().forEach(tag -> assertNotNull(ontology.findCapabilityTag(tag)));
            facility.storageClassInterfaces().forEach(storage -> assertNotNull(ontology.findStorageClass(storage)));
            assertTrue(facility.maxHandledUnitMassKg() > 0d);
        });

        Set<String> expected = Set.of(
                "capability.extraction.asteroid_excavation",
                "capability.extraction.surface_mining",
                "capability.extraction.deep_mining",
                "capability.extraction.thermal_volatiles",
                "capability.extraction.atmospheric_harvesting",
                "capability.process.beneficiation",
                "capability.process.volatile_processing",
                "capability.process.bulk_refining",
                "capability.process.advanced_materials",
                "capability.process.chemical_processing",
                "capability.process.recycling",
                "capability.fabrication.heavy",
                "capability.fabrication.electrical",
                "capability.fabrication.precision",
                "capability.fabrication.assembly",
                "capability.fabrication.ordnance");
        assertTrue(covered.containsAll(expected));
    }

    @Test
    void parserRejectsUnknownCapabilityAndStorageReferences() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        String unknownCapability = singleFacilityJson(
                "[\"capability.fabrication.nonexistent\"]",
                "[\"storage.dry_bulk\"]");
        String unknownStorage = singleFacilityJson(
                "[\"capability.fabrication.heavy\"]",
                "[\"storage.nonexistent\"]");

        assertThrows(IllegalArgumentException.class,
                () -> Stage18FacilityCatalogLoader.parse(unknownCapability, ontology));
        assertThrows(IllegalArgumentException.class,
                () -> Stage18FacilityCatalogLoader.parse(unknownStorage, ontology));
    }

    private static String singleFacilityJson(String capabilities, String storage) {
        return """
                {
                  "schemaVersion":1,
                  "facilities":[{
                    "id":"facility.test.invalid",
                    "displayName":"Invalid",
                    "family":"FABRICATION",
                    "capabilityTags":%s,
                    "ratedProcessPowerW":1.0,
                    "engineeringWorkRate":1.0,
                    "maintenanceWorkRate":1.0,
                    "heatRejectionWPerProcessW":1.0,
                    "requiredLaborUnitsAtFullRate":1.0,
                    "automationFloorFraction":0.0,
                    "storageClassInterfaces":%s,
                    "maxHandledUnitMassKg":1.0,
                    "allowedLocationTags":["location.test"]
                  }]
                }
                """.formatted(capabilities, storage);
    }
}

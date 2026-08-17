package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage18ResourceOntologySerializerTest {
    @Test
    void canonicalSerializationRoundTripsWithoutChangingFingerprint() {
        Stage18ResourceOntologyCatalog source = Stage18ResourceOntologyLoader.loadDefault();

        String firstJson = Stage18ResourceOntologySerializer.serialize(source);
        Stage18ResourceOntologyCatalog restored = Stage18ResourceOntologyLoader.parse(firstJson);
        String secondJson = Stage18ResourceOntologySerializer.serialize(restored);

        assertEquals(source.getFingerprint(), restored.getFingerprint());
        assertEquals(firstJson, secondJson);
        assertNotNull(restored.findStorageClass("storage.dry_bulk"));
        assertNotNull(restored.findCapabilityTag("capability.process.bulk_refining"));
        assertNotNull(restored.findCommodity("commodity.material.structural_alloy"));
        assertNotNull(restored.findOccurrenceType("occurrence.metallic"));
        assertNotNull(restored.findLegacyMapping("item.steel"));
    }
}

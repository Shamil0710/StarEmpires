package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativePropulsionCatalogLoaderTest {
    @Test
    void packagedReferenceCatalogCoversAllRolesWithExplicitProvisionalProvenance() {
        Stage20RepresentativePropulsionCatalog catalog =
                Stage20RepresentativePropulsionCatalogLoader.loadDefault();

        assertEquals(2, catalog.schemaVersion());
        assertEquals("stage20a.representative-propulsion.v2", catalog.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, catalog.status());
        assertEquals("ship_mathematics_v1_0_design_baseline", catalog.sourceBaselineId());
        assertTrue(catalog.stage22ReviewRequired());
        assertEquals(9, catalog.references().size());
        assertTrue(catalog.references().stream().allMatch(value -> !value.sourceEvidenceId().isBlank()));

        ReferenceDefinition freighter = catalog.findByRepresentativeClass("BULK_FREIGHTER_LOADED");
        assertNotNull(freighter);
        assertEquals(143_000_000d, freighter.departureMassKg(), 0d);
        assertEquals(25_000_000d, freighter.reactionMassKg(), 0d);
        assertEquals(12_000_000d, freighter.thrustN(), 0d);
        assertEquals(80_000d, freighter.exhaustVelocityMps(), 0d);
        assertEquals(0.08391608391608392d, freighter.expectedAccelerationMps2(), 1e-15d);
        assertEquals(15_372.800463539408d, freighter.expectedDeltaVMps(), 1e-9d);

        ReferenceDefinition early = catalog.findByRepresentativeClass("EARLY_CIVILIAN_FREIGHTER");
        assertNotNull(early);
        assertEquals(28_000_000d, early.departureMassKg(), 0d);
        assertEquals(8_000_000d, early.reactionMassKg(), 0d);
        assertEquals(5_600_000d, early.thrustN(), 0d);
        assertEquals(0.2d, early.expectedAccelerationMps2(), 0d);
        assertTrue(early.sourceEvidenceId().contains("stage20a_representative_propulsion_v2.md"));

        ReferenceDefinition miner = catalog.findByRepresentativeClass("MINING_SHIP");
        assertNotNull(miner);
        assertEquals(56_000_000d, miner.departureMassKg(), 0d);
        assertEquals(14_000_000d, miner.reactionMassKg(), 0d);
        assertEquals(7_000_000d, miner.thrustN(), 0d);
        assertEquals(0.125d, miner.expectedAccelerationMps2(), 0d);
        assertTrue(miner.sourceEvidenceId().contains("stage20a_representative_propulsion_v2.md"));

        ReferenceDefinition cruiser = catalog.findByRepresentativeClass("CRUISER");
        ReferenceDefinition carrier = catalog.findByRepresentativeClass("CARRIER_AVIATION_GROUP");
        assertTrue(cruiser.sourceEvidenceId().contains("ship_reference_designs_v0_2.json"));
        assertTrue(carrier.sourceEvidenceId().contains("ship_reference_designs_v0_2.json"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.references().add(freighter));
    }

    @Test
    void referenceEnvelopeKeepsPerReferenceProvenanceAndNeverPretendsToBeProduction() {
        Stage20RepresentativePropulsionCatalog catalog =
                Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        ReferenceDefinition corvette = catalog.findByRepresentativeClass("TORPEDO_CORVETTE");

        var envelope = Stage20ScaleCalibrationCalculator.deriveReference(catalog, corvette);

        assertEquals("TORPEDO_CORVETTE", envelope.representativeId());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, envelope.authority());
        assertEquals(corvette.sourceEvidenceId() + ":" + corvette.id(), envelope.provenanceId());
        assertEquals(corvette.departureMassKg(), envelope.wetMassKg(), 0d);
        assertEquals(corvette.expectedAccelerationMps2(), envelope.initialAccelerationMps2(), 0d);
        assertEquals(corvette.expectedDeltaVMps(), envelope.deltaVMps(), 0d);

        Stage20RepresentativePropulsionCatalog wrongAuthority = new Stage20RepresentativePropulsionCatalog(
                2,
                "test",
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                "source.test",
                "evidence.test",
                false,
                List.of(corvette));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20ScaleCalibrationCalculator.deriveReference(wrongAuthority, corvette));
    }

    @Test
    void parserRejectsBrokenPhysicalClosureMissingProvenanceAndSchemaDrift() {
        String valid = """
                {
                  "schemaVersion": 2,
                  "version": "test.v2",
                  "status": "PROVISIONAL_ACCEPTED_REFERENCE",
                  "sourceBaselineId": "baseline.test",
                  "sourceEvidence": "evidence.test",
                  "stage22ReviewRequired": true,
                  "references": [{
                    "id": "reference.test.v2",
                    "representativeClass": "TEST_SHIP",
                    "sourceEvidenceId": "evidence.reference.test",
                    "designDryMassKg": 800.0,
                    "ammunitionMassKg": 0.0,
                    "missionCargoStoresMassKg": 0.0,
                    "reactionMassKg": 200.0,
                    "departureMassKg": 1000.0,
                    "thrustN": 100.0,
                    "exhaustVelocityMps": 1000.0,
                    "expectedAccelerationMps2": 0.1,
                    "expectedDeltaVMps": 223.14355131420976
                  }]
                }
                """;
        Stage20RepresentativePropulsionCatalog parsed =
                Stage20RepresentativePropulsionCatalogLoader.parse(valid);
        assertEquals(1, parsed.references().size());

        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(valid.replace(
                        "\"departureMassKg\": 1000.0", "\"departureMassKg\": 1001.0")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(valid.replace(
                        "\"sourceEvidenceId\": \"evidence.reference.test\",", "")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(valid.replace(
                        "\"schemaVersion\": 2", "\"schemaVersion\": 3")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(valid.replace(
                        "PROVISIONAL_ACCEPTED_REFERENCE", "NOT_AN_AUTHORITY")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(" "));
        assertThrows(NullPointerException.class,
                () -> Stage20RepresentativePropulsionCatalogLoader.parse(null));
    }
}

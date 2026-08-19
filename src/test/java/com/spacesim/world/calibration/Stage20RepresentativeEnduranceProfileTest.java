package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeEnduranceProfileTest {
    @Test
    void packagedPolicyIsProvisionalCompleteAndExplicitlySourced() {
        Stage20RepresentativeEnduranceReferenceCatalog catalog =
                Stage20RepresentativeEnduranceReferenceCatalogLoader.loadDefault();

        assertEquals(1, catalog.schemaVersion());
        assertEquals(Stage20RepresentativeEnduranceProfile.CURRENT_VERSION, catalog.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, catalog.status());
        assertTrue(catalog.stage22ReviewRequired());
        assertEquals(9, catalog.references().size());
        assertTrue(catalog.references().stream().allMatch(value ->
                !value.sustainedThrustSourceEvidenceId().isBlank()
                        && !value.missionStoresSourceEvidenceId().isBlank()));
        assertEquals(600_000d,
                catalog.findByRepresentativeClass("TORPEDO_CORVETTE").sustainedThrustN(), 0d);
        assertEquals(1_800_000d,
                catalog.findByRepresentativeClass("EARLY_CIVILIAN_FREIGHTER").sustainedThrustN(), 0d);
        assertEquals(2_100_000d,
                catalog.findByRepresentativeClass("MINING_SHIP").sustainedThrustN(), 0d);
        assertEquals(120d * 86_400d,
                catalog.findByRepresentativeClass("CARRIER_AVIATION_GROUP").missionStoresEnduranceS(), 0d);
    }

    @Test
    void derivedProfileMatchesAllNineCurrentPropulsionRepresentatives() {
        Stage20RepresentativeEnduranceProfile profile = Stage20RepresentativeEnduranceProfile.deriveCurrent();
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();

        assertEquals(Stage20RepresentativeEnduranceProfile.CURRENT_VERSION, profile.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, profile.authority());
        assertTrue(profile.stage22ReviewRequired());
        assertEquals(9, profile.samples().size());
        assertEquals(
                scale.representativeShips().stream()
                        .map(Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope::representativeId)
                        .collect(Collectors.toSet()),
                profile.samples().stream()
                        .map(Stage20RepresentativeEnduranceProfile.EnduranceSample::representativeId)
                        .collect(Collectors.toSet()));

        Map<String, Stage20RepresentativeEnduranceProfile.EnduranceSample> byId = profile.samples().stream()
                .collect(Collectors.toMap(
                        Stage20RepresentativeEnduranceProfile.EnduranceSample::representativeId,
                        Function.identity()));
        var escort = byId.get("ESCORT_DESTROYER");
        assertNotNull(escort);
        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, escort.propulsionAuthority());
        assertEquals("fit.escort_destroyer_schema_v1", escort.propulsionProvenanceId());
        assertEquals(3_300_000d, escort.sustainedThrustN(), 0d);
        assertEquals(0.25d, escort.sustainedToMaxThrustRatio(), 1e-15d);
        assertEquals(escort.sustainedThrustN() / escort.wetMassKg(),
                escort.sustainedAccelerationMps2(), 1e-15d);
        assertEquals(escort.maxThrustN() / escort.effectiveExhaustVelocityMps(),
                escort.maxMassFlowKgPerS(), 1e-15d);
        assertEquals(escort.sustainedThrustN() / escort.effectiveExhaustVelocityMps(),
                escort.sustainedMassFlowKgPerS(), 1e-15d);
        assertTrue(escort.fullReactionMassBurnAtSustainedS() > escort.fullReactionMassBurnAtMaxS());

        var early = byId.get("EARLY_CIVILIAN_FREIGHTER");
        var miner = byId.get("MINING_SHIP");
        assertEquals(14d * 86_400d, early.missionStoresEnduranceS(), 0d);
        assertEquals(45d * 86_400d, miner.missionStoresEnduranceS(), 0d);
        assertTrue(early.sustainedThrustSourceEvidenceId().contains("bounded_authoring"));
        assertTrue(miner.sustainedThrustSourceEvidenceId().contains("bounded_authoring"));
        assertEquals(1_800_000d / 5_600_000d, early.sustainedToMaxThrustRatio(), 1e-15d);
        assertEquals(0.3d, miner.sustainedToMaxThrustRatio(), 1e-15d);
    }

    @Test
    void everySampleKeepsSustainedConsequencesInsideCurrentPhysicalEnvelope() {
        Stage20RepresentativeEnduranceProfile profile = Stage20RepresentativeEnduranceProfile.deriveCurrent();

        assertTrue(profile.samples().stream().allMatch(value -> value.sustainedThrustN() <= value.maxThrustN()));
        assertTrue(profile.samples().stream().allMatch(value ->
                value.sustainedAccelerationMps2() <= value.maxAccelerationMps2()));
        assertTrue(profile.samples().stream().allMatch(value ->
                value.sustainedMassFlowKgPerS() <= value.maxMassFlowKgPerS()));
        assertTrue(profile.samples().stream().allMatch(value ->
                value.fullReactionMassBurnAtSustainedS() >= value.fullReactionMassBurnAtMaxS()));
        assertTrue(profile.samples().stream().allMatch(value -> value.missionStoresEnduranceS() > 0d));
    }

    @Test
    void loaderRejectsDuplicateMissingAndInvalidRows() {
        String valid = """
                {
                  "schemaVersion": 1,
                  "version": "stage20a.representative-endurance.v1",
                  "status": "PROVISIONAL_ACCEPTED_REFERENCE",
                  "stage22ReviewRequired": true,
                  "policyEvidence": "test policy evidence",
                  "references": [{
                    "representativeClass": "TEST_SHIP",
                    "sustainedThrustN": 10.0,
                    "sustainedThrustSourceEvidenceId": "source.thrust",
                    "missionStoresEnduranceS": 100.0,
                    "missionStoresSourceEvidenceId": "source.stores"
                  }]
                }
                """;
        assertEquals(1, Stage20RepresentativeEnduranceReferenceCatalogLoader.parse(valid).references().size());
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativeEnduranceReferenceCatalogLoader.parse(
                        valid.replace("\"sustainedThrustN\": 10.0", "\"sustainedThrustN\": 0.0")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativeEnduranceReferenceCatalogLoader.parse(
                        valid.replace("\"missionStoresEnduranceS\": 100.0", "\"missionStoresEnduranceS\": -1.0")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativeEnduranceReferenceCatalogLoader.parse(
                        valid.replace("\"sustainedThrustSourceEvidenceId\": \"source.thrust\",", "")));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20RepresentativeEnduranceReferenceCatalogLoader.parse(
                        valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
    }
}

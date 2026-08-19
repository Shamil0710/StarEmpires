package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.BandEndpoint;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.RepresentativeGroup;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.SemanticRouteSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.ThrustPolicy;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20LocalRouteSemanticCalibrationProfileTest {
    @Test
    void packagedCatalogDefinesExactlyFourRequiredProvisionalSemanticBands() {
        Stage20LocalRouteSemanticBandCatalog catalog = Stage20LocalRouteSemanticBandCatalogLoader.loadDefault();

        assertEquals(1, catalog.schemaVersion());
        assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION, catalog.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, catalog.status());
        assertTrue(catalog.stage22ReviewRequired());
        assertEquals(Set.of(BandId.values()), catalog.bands().stream().map(value -> value.id()).collect(Collectors.toSet()));
        assertEquals(4, catalog.bands().size());
        assertTrue(catalog.bands().stream().allMatch(value ->
                value.minDistanceM() > 0d
                        && value.maxDistanceM() >= value.minDistanceM()
                        && !value.sourceEvidenceId().isBlank()));

        Map<BandId, Stage20LocalRouteSemanticBandCatalog.BandDefinition> byId = catalog.bands().stream()
                .collect(Collectors.toMap(value -> value.id(), Function.identity()));
        assertEquals(10_000_000d, byId.get(BandId.STATION_TO_STATION).minDistanceM(), 0d);
        assertEquals(100_000_000d, byId.get(BandId.STATION_TO_STATION).maxDistanceM(), 0d);
        assertEquals(50_000_000d, byId.get(BandId.STATION_TO_RESOURCE_FIELD).minDistanceM(), 0d);
        assertEquals(500_000_000d, byId.get(BandId.STATION_TO_RESOURCE_FIELD).maxDistanceM(), 0d);
        assertEquals(100_000_000d, byId.get(BandId.JUMP_ARRIVAL_TO_MAJOR_HUB).minDistanceM(), 0d);
        assertEquals(1_000_000_000d, byId.get(BandId.JUMP_ARRIVAL_TO_MAJOR_HUB).maxDistanceM(), 0d);
        assertEquals(1_000_000_000d, byId.get(BandId.INNER_TO_OUTER_SYSTEM).minDistanceM(), 0d);
        assertEquals(10_000_000_000d, byId.get(BandId.INNER_TO_OUTER_SYSTEM).maxDistanceM(), 0d);
    }

    @Test
    void currentProfileProducesNineRoleTwoPolicyEndpointMatrix() {
        Stage20LocalRouteSemanticCalibrationProfile first =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20LocalRouteSemanticCalibrationProfile second =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.distanceAuthority());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(4, first.bands().size());
        assertEquals(144, first.samples().size());
        assertEquals(Set.of(BandId.values()), first.samples().stream().map(SemanticRouteSample::bandId).collect(Collectors.toSet()));
        assertEquals(Set.of(ThrustPolicy.values()), first.samples().stream().map(SemanticRouteSample::thrustPolicy).collect(Collectors.toSet()));
        assertEquals(Set.of(BandEndpoint.values()), first.samples().stream().map(SemanticRouteSample::endpoint).collect(Collectors.toSet()));
        assertEquals(Set.of(RepresentativeGroup.values()), first.samples().stream().map(SemanticRouteSample::representativeGroup).collect(Collectors.toSet()));
        assertEquals(9, first.samples().stream().map(SemanticRouteSample::representativeId).distinct().count());
    }

    @Test
    void routineSustainedIsNeverFasterThanMaxResponseAtTheSameEndpoint() {
        Stage20LocalRouteSemanticCalibrationProfile profile =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Map<String, SemanticRouteSample> byKey = profile.samples().stream()
                .collect(Collectors.toMap(Stage20LocalRouteSemanticCalibrationProfileTest::key, Function.identity()));

        profile.samples().stream()
                .filter(value -> value.thrustPolicy() == ThrustPolicy.ROUTINE_SUSTAINED)
                .forEach(routine -> {
                    SemanticRouteSample response = byKey.get(key(
                            routine.bandId(), routine.endpoint(), routine.representativeId(),
                            ThrustPolicy.MAX_THRUST_RESPONSE));
                    assertTrue(routine.appliedThrustN() <= response.appliedThrustN());
                    assertTrue(routine.totalTravelTimeS() + 1e-9d >= response.totalTravelTimeS());
                    assertEquals(routine.distanceM(), response.distanceM(), 0d);
                });
    }

    @Test
    void largerBandEndpointRaisesTravelTimeAndKeepsPhysicalConsequencesVisible() {
        Stage20LocalRouteSemanticCalibrationProfile profile =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Map<String, SemanticRouteSample> byKey = profile.samples().stream()
                .collect(Collectors.toMap(Stage20LocalRouteSemanticCalibrationProfileTest::key, Function.identity()));

        for (String representative : profile.samples().stream().map(SemanticRouteSample::representativeId).distinct().toList()) {
            for (BandId band : BandId.values()) {
                for (ThrustPolicy policy : ThrustPolicy.values()) {
                    SemanticRouteSample min = byKey.get(key(band, BandEndpoint.MIN, representative, policy));
                    SemanticRouteSample max = byKey.get(key(band, BandEndpoint.MAX, representative, policy));
                    assertTrue(max.distanceM() >= min.distanceM());
                    assertTrue(max.totalTravelTimeS() >= min.totalTravelTimeS());
                    assertTrue(max.requiredDeltaVMps() + 1e-9d >= min.requiredDeltaVMps());
                    assertTrue(max.reactionMassConsumedKg() + 1e-6d >= min.reactionMassConsumedKg());
                }
            }
        }
    }

    @Test
    void civilianAndMilitaryGroupingAndEscortProvenanceRemainExplicit() {
        Stage20LocalRouteSemanticCalibrationProfile profile =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();

        Set<String> civilian = profile.samples().stream()
                .filter(value -> value.representativeGroup() == RepresentativeGroup.CIVILIAN_LOGISTICS)
                .map(SemanticRouteSample::representativeId)
                .collect(Collectors.toSet());
        Set<String> military = profile.samples().stream()
                .filter(value -> value.representativeGroup() == RepresentativeGroup.MILITARY)
                .map(SemanticRouteSample::representativeId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("EARLY_CIVILIAN_FREIGHTER", "BULK_FREIGHTER_LOADED", "MINING_SHIP", "FLEET_TANKER_LOADED"), civilian);
        assertEquals(Set.of("TORPEDO_CORVETTE", "ESCORT_DESTROYER", "CRUISER", "BATTLESHIP", "CARRIER_AVIATION_GROUP"), military);

        SemanticRouteSample escortRoutine = profile.samples().stream()
                .filter(value -> value.representativeId().equals("ESCORT_DESTROYER"))
                .filter(value -> value.bandId() == BandId.STATION_TO_STATION)
                .filter(value -> value.endpoint() == BandEndpoint.MAX)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.ROUTINE_SUSTAINED)
                .findFirst().orElseThrow();
        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, escortRoutine.propulsionAuthority());
        assertEquals("fit.escort_destroyer_schema_v1", escortRoutine.propulsionProvenanceId());
        assertTrue(escortRoutine.thrustPolicyProvenanceId().contains("ship_reference_designs_v0_2.json"));
        assertTrue(escortRoutine.distanceSourceEvidenceId().contains("stage20"));
    }

    @Test
    void parserRejectsIncompleteDuplicateOrReversedBands() {
        String valid = """
                {
                  "schemaVersion": 1,
                  "version": "test",
                  "status": "PROVISIONAL_ACCEPTED_REFERENCE",
                  "stage22ReviewRequired": true,
                  "policyEvidence": "test",
                  "bands": [
                    {"id":"STATION_TO_STATION","minDistanceM":1,"maxDistanceM":2,"sourceEvidenceId":"a"},
                    {"id":"STATION_TO_RESOURCE_FIELD","minDistanceM":2,"maxDistanceM":3,"sourceEvidenceId":"b"},
                    {"id":"JUMP_ARRIVAL_TO_MAJOR_HUB","minDistanceM":3,"maxDistanceM":4,"sourceEvidenceId":"c"},
                    {"id":"INNER_TO_OUTER_SYSTEM","minDistanceM":4,"maxDistanceM":5,"sourceEvidenceId":"d"}
                  ]
                }
                """;
        assertEquals(4, Stage20LocalRouteSemanticBandCatalogLoader.parse(valid).bands().size());
        assertThrows(IllegalArgumentException.class, () -> Stage20LocalRouteSemanticBandCatalogLoader.parse(
                valid.replace("\"id\":\"INNER_TO_OUTER_SYSTEM\"", "\"id\":\"STATION_TO_STATION\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage20LocalRouteSemanticBandCatalogLoader.parse(
                valid.replace("\"minDistanceM\":1,\"maxDistanceM\":2", "\"minDistanceM\":3,\"maxDistanceM\":2")));
    }

    private static String key(SemanticRouteSample value) {
        return key(value.bandId(), value.endpoint(), value.representativeId(), value.thrustPolicy());
    }

    private static String key(BandId band, BandEndpoint endpoint, String representative, ThrustPolicy policy) {
        return band.name() + ":" + endpoint.name() + ":" + representative + ":" + policy.name();
    }
}

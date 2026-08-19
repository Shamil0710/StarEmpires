package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.GateStatus;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementId;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementResult;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ACalibrationReadinessProfileTest {
    @Test
    void currentGateIsDeterministicAndAuditsTheCompleteStage20ADod() {
        Stage20ACalibrationReadinessProfile first = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Stage20ACalibrationReadinessProfile second = Stage20ACalibrationReadinessCalculator.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20ACalibrationReadinessProfile.CURRENT_VERSION, first.version());
        assertEquals(GateStatus.BLOCKED_FOR_STAGE20B, first.overallStatus());
        assertEquals(RequirementId.values().length, first.requirements().size());
        assertEquals(
                Set.of(
                        RequirementId.PD_SAFE_INTERCEPT_GEOMETRY,
                        RequirementId.TOPOLOGY_QUALITY_CALIBRATION_BANDS,
                        RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS,
                        RequirementId.MATERIALIZATION_LOD_CLOSURE),
                first.blockingRequirements().stream()
                        .map(RequirementResult::id)
                        .collect(Collectors.toSet()));
        assertEquals(4, first.blockingRequirements().size());
    }

    @Test
    void representativeCoverageNowContainsAllNineRequiredRolesWithExplicitAuthority() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();

        assertEquals(9, profile.representativeCoverage().size());
        assertEquals(9, profile.representativeCoverage().stream().filter(value -> value.present()).count());
        assertTrue(profile.missingRepresentativeRoles().isEmpty());
        assertTrue(profile.representativeCoverage().stream()
                .allMatch(value -> value.authority().isPresent() && value.provenance().isPresent()));
        assertEquals(1, profile.representativeCoverage().stream()
                .filter(value -> value.authority().orElseThrow().equals("PRODUCTION_ENGINEERING"))
                .count());
    }

    @Test
    void gateSeparatesSatisfiedDeferredAndLaterStage20OwnershipFromTrueBlockers() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));

        assertStatus(byId, RequirementStatus.SATISFIED,
                RequirementId.REPRESENTATIVE_PROPULSION_COVERAGE,
                RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE,
                RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE,
                RequirementId.FTL_TOPOLOGY_SEMANTICS,
                RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS,
                RequirementId.SENSOR_TARGET_CLASS_COVERAGE,
                RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE,
                RequirementId.WEAPON_PD_SPATIAL_EVIDENCE,
                RequirementId.WEAPON_REPRESENTATIVE_TARGET_COVERAGE,
                RequirementId.FORMATION_SPATIAL_EVIDENCE,
                RequirementId.FORMATION_SPACING_BAND_CLOSURE,
                RequirementId.STATION_PHYSICAL_GEOMETRY,
                RequirementId.STATION_DEFENSIVE_SENSOR_GEOMETRY,
                RequirementId.STATION_JUMP_ARRIVAL_STANDOFF,
                RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS,
                RequirementId.FAR_COORDINATE_PRECISION);
        assertStatus(byId, RequirementStatus.DEFERRED_STAGE22_CONTENT,
                RequirementId.PRODUCTION_FTL_MODULE_PROMOTION,
                RequirementId.FTL_HEAT_COEFFICIENT);
        assertStatus(byId, RequirementStatus.OWNED_BY_LATER_STAGE20,
                RequirementId.FTL_EDGE_TRANSIT_DISTRIBUTION);
    }

    @Test
    void sensorTargetClassCoverageIsClosedByVersionedPhysicalEvidence() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult sensorCoverage = byId.get(RequirementId.SENSOR_TARGET_CLASS_COVERAGE);

        assertEquals(RequirementStatus.SATISFIED, sensorCoverage.status());
        assertTrue(sensorCoverage.evidence().contains(Stage20SensorTargetClassCoverageProfile.CURRENT_VERSION));
        assertTrue(sensorCoverage.evidence().contains("PASSIVE_THERMAL"));
        assertTrue(sensorCoverage.evidence().contains("ACTIVE_RADAR"));
        assertTrue(sensorCoverage.evidence().contains("targets=7"));
        assertTrue(sensorCoverage.evidence().contains("provisional_stage22=6"));
    }

    @Test
    void weaponTargetClassCoverageIsClosedWithoutInventingDestroyerP50() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult weaponCoverage = byId.get(RequirementId.WEAPON_REPRESENTATIVE_TARGET_COVERAGE);

        assertEquals(RequirementStatus.SATISFIED, weaponCoverage.status());
        assertTrue(weaponCoverage.evidence().contains(Stage20WeaponTargetClassCoverageProfile.CURRENT_VERSION));
        assertTrue(weaponCoverage.evidence().contains("p50_target_classes=5"));
        assertTrue(weaponCoverage.evidence().contains("unsupported_p50=DESTROYER"));
        assertTrue(weaponCoverage.evidence().contains("KINETIC_DIRECT_FIRE"));
        assertTrue(weaponCoverage.evidence().contains("BEAM_DIRECT_FIRE"));
        assertTrue(weaponCoverage.evidence().contains("GUIDED_STRIKE"));
        assertTrue(weaponCoverage.evidence().contains("LAYERED_DEFENSE"));
        assertTrue(weaponCoverage.evidence().contains("stage22_review_required=true"));
    }

    @Test
    void formationSpacingIsClosedByVersionedStage19AcceptanceBands() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult formation = byId.get(RequirementId.FORMATION_SPACING_BAND_CLOSURE);

        assertEquals(RequirementStatus.SATISFIED, formation.status());
        assertTrue(formation.evidence().contains(Stage20FormationSpacingCalibrationProfile.CURRENT_VERSION));
        assertTrue(formation.evidence().contains("COMPACT_ACCEPTANCE"));
        assertTrue(formation.evidence().contains("DISPERSED_ACCEPTANCE"));
        assertTrue(formation.evidence().contains("source_samples=3"));
        assertTrue(formation.evidence().contains("stage22_review_required=true"));
    }

    @Test
    void stationDefensiveSensorGeometryIsClosedByAcceptedReferenceTiers() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult stationDefense = byId.get(RequirementId.STATION_DEFENSIVE_SENSOR_GEOMETRY);

        assertEquals(RequirementStatus.SATISFIED, stationDefense.status());
        assertTrue(stationDefense.evidence().contains(Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION));
        assertTrue(stationDefense.evidence().contains("station_rows=8"));
        assertTrue(stationDefense.evidence().contains("BASIC_SECURITY"));
        assertTrue(stationDefense.evidence().contains("HARDENED_SECURITY"));
        assertTrue(stationDefense.evidence().contains("NAVAL_FORTIFIED"));
        assertTrue(stationDefense.evidence().contains("stage22_review_required=true"));
    }

    @Test
    void stationJumpArrivalStandOffIsClosedByEightDerivedPhysicalSamples() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult standOff = byId.get(RequirementId.STATION_JUMP_ARRIVAL_STANDOFF);

        assertEquals(RequirementStatus.SATISFIED, standOff.status());
        assertEquals("closed_station_stand_offs=8/8", standOff.evidence());
    }

    @Test
    void localRouteSemanticBandsAreClosedByVersionedPhysicalEndpointMatrix() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
        RequirementResult routes = byId.get(RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS);

        assertEquals(RequirementStatus.SATISFIED, routes.status());
        assertTrue(routes.evidence().contains(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION));
        assertTrue(routes.evidence().contains("STATION_TO_STATION"));
        assertTrue(routes.evidence().contains("STATION_TO_RESOURCE_FIELD"));
        assertTrue(routes.evidence().contains("JUMP_ARRIVAL_TO_MAJOR_HUB"));
        assertTrue(routes.evidence().contains("INNER_TO_OUTER_SYSTEM"));
        assertTrue(routes.evidence().contains("samples=144"));
        assertTrue(routes.evidence().contains("max_station_standoff_m="));
        assertTrue(routes.evidence().contains("stage22_review_required=true"));
    }

    @Test
    void currentPhysicalAndCalibrationGapsCannotBeHiddenByFallbackConstants() {
        Stage20ACalibrationReadinessProfile profile = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Map<RequirementId, RequirementResult> byId = profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));

        assertEquals(
                "compatible_civilian_representatives=EARLY_CIVILIAN_FREIGHTER,MINING_SHIP",
                byId.get(RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE).evidence());
        assertTrue(byId.get(RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE).evidence()
                .contains("endurance_samples=9/9"));
        assertTrue(byId.get(RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE).evidence()
                .contains(Stage20RepresentativeEnduranceProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE).evidence()
                .contains("stage22_review_required=true"));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains("FLEET_REINFORCEMENT_3_HOP"));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains("CARRIER_AVIATION_GROUP"));
        assertTrue(byId.get(RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE).evidence()
                .contains("historical_stage20a4_pending=true"));
        assertTrue(byId.get(RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE).evidence()
                .contains(Stage20FireControlPolicyClosureProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.PD_SAFE_INTERCEPT_GEOMETRY).evidence()
                .contains("scheduler_probe_input"));
        assertEquals(
                "placement_ready_stations=8/8",
                byId.get(RequirementId.STATION_PHYSICAL_GEOMETRY).evidence());
        assertEquals(
                "closed_station_stand_offs=8/8",
                byId.get(RequirementId.STATION_JUMP_ARRIVAL_STANDOFF).evidence());
        assertTrue(byId.get(RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS).evidence()
                .contains("samples=144"));
        assertTrue(byId.get(RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS).evidence()
                .contains("station_geometry_is_closed"));
        assertTrue(byId.get(RequirementId.TOPOLOGY_QUALITY_CALIBRATION_BANDS).evidence()
                .contains("maxLinearCorridorLength"));
        assertTrue(byId.get(RequirementId.MATERIALIZATION_LOD_CLOSURE).evidence()
                .contains("numeric_activation_bands_closed=false"));
        assertTrue(byId.get(RequirementId.MATERIALIZATION_LOD_CLOSURE).evidence()
                .contains("lossless_materialization_lifecycle_closed=true"));
    }

    private static void assertStatus(
            Map<RequirementId, RequirementResult> byId,
            RequirementStatus expected,
            RequirementId... ids) {
        List<RequirementId> mismatched = java.util.Arrays.stream(ids)
                .filter(id -> byId.get(id).status() != expected)
                .toList();
        assertEquals(List.of(), mismatched);
    }
}

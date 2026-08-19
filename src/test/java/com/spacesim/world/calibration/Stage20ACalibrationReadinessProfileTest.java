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
                        RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE,
                        RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS,
                        RequirementId.SENSOR_TARGET_CLASS_COVERAGE,
                        RequirementId.WEAPON_REPRESENTATIVE_TARGET_COVERAGE,
                        RequirementId.PD_SAFE_INTERCEPT_GEOMETRY,
                        RequirementId.FORMATION_SPACING_BAND_CLOSURE,
                        RequirementId.STATION_PHYSICAL_GEOMETRY,
                        RequirementId.STATION_DEFENSIVE_SENSOR_GEOMETRY,
                        RequirementId.STATION_JUMP_ARRIVAL_STANDOFF,
                        RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS,
                        RequirementId.TOPOLOGY_QUALITY_CALIBRATION_BANDS,
                        RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS,
                        RequirementId.MATERIALIZATION_LOD_CLOSURE),
                first.blockingRequirements().stream()
                        .map(RequirementResult::id)
                        .collect(Collectors.toSet()));
        assertEquals(13, first.blockingRequirements().size());
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
                RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE,
                RequirementId.FTL_TOPOLOGY_SEMANTICS,
                RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE,
                RequirementId.WEAPON_PD_SPATIAL_EVIDENCE,
                RequirementId.FORMATION_SPATIAL_EVIDENCE,
                RequirementId.FAR_COORDINATE_PRECISION);
        assertStatus(byId, RequirementStatus.DEFERRED_STAGE22_CONTENT,
                RequirementId.PRODUCTION_FTL_MODULE_PROMOTION,
                RequirementId.FTL_HEAT_COEFFICIENT);
        assertStatus(byId, RequirementStatus.OWNED_BY_LATER_STAGE20,
                RequirementId.FTL_EDGE_TRANSIT_DISTRIBUTION);
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
                .contains("no_machine_readable_stores_endurance"));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains("regional_3_5_hop"));
        assertTrue(byId.get(RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE).evidence()
                .contains("historical_stage20a4_pending=true"));
        assertTrue(byId.get(RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE).evidence()
                .contains(Stage20FireControlPolicyClosureProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.PD_SAFE_INTERCEPT_GEOMETRY).evidence()
                .contains("scheduler_probe_input"));
        assertTrue(byId.get(RequirementId.FORMATION_SPACING_BAND_CLOSURE).evidence()
                .contains("provisional_stage19_tactical_probes"));
        assertEquals(
                "placement_ready_stations=0/8",
                byId.get(RequirementId.STATION_PHYSICAL_GEOMETRY).evidence());
        assertEquals(
                "closed_station_stand_offs=0/8",
                byId.get(RequirementId.STATION_JUMP_ARRIVAL_STANDOFF).evidence());
        assertTrue(byId.get(RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS).evidence()
                .contains("station_to_station"));
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

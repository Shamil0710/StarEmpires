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
    void currentGateIsDeterministicCompleteAndReadyForStage20B() {
        Stage20ACalibrationReadinessProfile first = Stage20ACalibrationReadinessCalculator.deriveCurrent();
        Stage20ACalibrationReadinessProfile second = Stage20ACalibrationReadinessCalculator.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20ACalibrationReadinessProfile.CURRENT_VERSION, first.version());
        assertEquals(GateStatus.READY_FOR_STAGE20B, first.overallStatus());
        assertEquals(RequirementId.values().length, first.requirements().size());
        assertEquals(List.of(), first.blockingRequirements());
    }

    @Test
    void representativeCoverageContainsAllNineRequiredRolesWithExplicitAuthority() {
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
    void gateSeparatesSatisfiedDeferredAndLaterStage20Ownership() {
        Map<RequirementId, RequirementResult> byId = byId(Stage20ACalibrationReadinessCalculator.deriveCurrent());
        Set<RequirementId> deferred = Set.of(
                RequirementId.PRODUCTION_FTL_MODULE_PROMOTION,
                RequirementId.FTL_HEAT_COEFFICIENT);
        Set<RequirementId> laterStage20 = Set.of(RequirementId.FTL_EDGE_TRANSIT_DISTRIBUTION);

        for (RequirementId id : RequirementId.values()) {
            RequirementStatus expected = deferred.contains(id)
                    ? RequirementStatus.DEFERRED_STAGE22_CONTENT
                    : laterStage20.contains(id)
                            ? RequirementStatus.OWNED_BY_LATER_STAGE20
                            : RequirementStatus.SATISFIED;
            assertEquals(expected, byId.get(id).status(), id.name());
        }
    }

    @Test
    void sensorWeaponAndPdClosuresRemainMachineVisible() {
        Map<RequirementId, RequirementResult> byId = byId(Stage20ACalibrationReadinessCalculator.deriveCurrent());

        RequirementResult sensor = byId.get(RequirementId.SENSOR_TARGET_CLASS_COVERAGE);
        assertTrue(sensor.evidence().contains(Stage20SensorTargetClassCoverageProfile.CURRENT_VERSION));
        assertTrue(sensor.evidence().contains("targets=7"));
        assertTrue(sensor.evidence().contains("PASSIVE_THERMAL"));
        assertTrue(sensor.evidence().contains("ACTIVE_RADAR"));

        RequirementResult weapons = byId.get(RequirementId.WEAPON_REPRESENTATIVE_TARGET_COVERAGE);
        assertTrue(weapons.evidence().contains(Stage20WeaponTargetClassCoverageProfile.CURRENT_VERSION));
        assertTrue(weapons.evidence().contains("p50_target_classes=5"));
        assertTrue(weapons.evidence().contains("unsupported_p50=DESTROYER"));
        assertTrue(weapons.evidence().contains("KINETIC_DIRECT_FIRE"));
        assertTrue(weapons.evidence().contains("BEAM_DIRECT_FIRE"));
        assertTrue(weapons.evidence().contains("GUIDED_STRIKE"));
        assertTrue(weapons.evidence().contains("LAYERED_DEFENSE"));

        RequirementResult pd = byId.get(RequirementId.PD_SAFE_INTERCEPT_GEOMETRY);
        assertEquals(RequirementStatus.SATISFIED, pd.status());
        assertEquals("pd_safe_intercept_geometry_physically_closed", pd.evidence());
        Stage20PdSafeInterceptCalibrationProfile pdProfile = Stage20PdSafeInterceptCalibrationProfile.deriveCurrent();
        assertTrue(pdProfile.closesStage20BEntryCoverage());
        assertEquals(100_000d, pdProfile.selectedMinimumInterceptDistanceM(), 0d);
        assertTrue(pdProfile.selectedIntersectingEnergyJ() > 0d);
        assertTrue(pdProfile.stage22ReviewRequired());
    }

    @Test
    void formationStationRouteAndTopologyClosuresRemainMachineVisible() {
        Map<RequirementId, RequirementResult> byId = byId(Stage20ACalibrationReadinessCalculator.deriveCurrent());

        RequirementResult formation = byId.get(RequirementId.FORMATION_SPACING_BAND_CLOSURE);
        assertTrue(formation.evidence().contains(Stage20FormationSpacingCalibrationProfile.CURRENT_VERSION));
        assertTrue(formation.evidence().contains("COMPACT_ACCEPTANCE"));
        assertTrue(formation.evidence().contains("DISPERSED_ACCEPTANCE"));

        assertEquals("placement_ready_stations=8/8", byId.get(RequirementId.STATION_PHYSICAL_GEOMETRY).evidence());
        assertEquals("closed_station_stand_offs=8/8", byId.get(RequirementId.STATION_JUMP_ARRIVAL_STANDOFF).evidence());

        RequirementResult stationDefense = byId.get(RequirementId.STATION_DEFENSIVE_SENSOR_GEOMETRY);
        assertTrue(stationDefense.evidence().contains(Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION));
        assertTrue(stationDefense.evidence().contains("station_rows=8"));
        assertTrue(stationDefense.evidence().contains("NAVAL_FORTIFIED"));

        RequirementResult routes = byId.get(RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS);
        assertTrue(routes.evidence().contains(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION));
        assertTrue(routes.evidence().contains("STATION_TO_STATION"));
        assertTrue(routes.evidence().contains("STATION_TO_RESOURCE_FIELD"));
        assertTrue(routes.evidence().contains("JUMP_ARRIVAL_TO_MAJOR_HUB"));
        assertTrue(routes.evidence().contains("INNER_TO_OUTER_SYSTEM"));
        assertTrue(routes.evidence().contains("samples=144"));

        RequirementResult topology = byId.get(RequirementId.TOPOLOGY_QUALITY_CALIBRATION_BANDS);
        assertTrue(topology.evidence().contains(Stage20TopologyQualityCalibrationProfile.CURRENT_VERSION));
        assertTrue(topology.evidence().contains("maxLinearCorridorLength=3"));
        assertTrue(topology.evidence().contains("maxDegreeOneFraction=0.2"));
        assertTrue(topology.evidence().contains("regionalHopDistanceBand=3-5"));
    }

    @Test
    void infrastructurePrecisionAndLodRemainNonBoundaryPhysicalClosures() {
        Map<RequirementId, RequirementResult> byId = byId(Stage20ACalibrationReadinessCalculator.deriveCurrent());

        RequirementResult infrastructure = byId.get(RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS);
        assertTrue(infrastructure.evidence().contains(Stage20MajorInfrastructureExtentCalibrationProfile.CURRENT_VERSION));
        assertTrue(infrastructure.evidence().contains("max_extent_m=1.0E9"));
        assertTrue(infrastructure.evidence().contains("hard_boundary=false"));
        assertTrue(infrastructure.evidence().contains("clamp_allowed=false"));

        RequirementResult precision = byId.get(RequirementId.FAR_COORDINATE_PRECISION);
        assertTrue(precision.evidence().contains("error_budget_m="));
        assertTrue(precision.evidence().contains("hierarchical_half_ulp_m="));

        RequirementResult lod = byId.get(RequirementId.MATERIALIZATION_LOD_CLOSURE);
        assertTrue(lod.evidence().contains("closure_profile=" + Stage20MaterializationLodClosureProfile.CURRENT_VERSION));
        assertTrue(lod.evidence().contains("base_profile=" + Stage20MaterializationLodCalibrationProfile.CURRENT_VERSION));
        assertTrue(lod.evidence().contains("historical_numeric_bands_unresolved=true"));
        assertTrue(lod.evidence().contains("active_local_activation_m=1.0E9"));
        assertTrue(lod.evidence().contains("wake_latency_s=0.0"));
        assertTrue(lod.evidence().contains("authoritative_state_retained=true"));
        assertTrue(lod.evidence().contains("distance_can_suppress_direct_relevance=false"));
        assertTrue(lod.evidence().contains("render_boundary=false"));
        assertTrue(lod.evidence().contains("world_boundary=false"));
        assertTrue(lod.evidence().contains("lossless_materialization_lifecycle_closed=true"));
    }

    @Test
    void ftlAndEnduranceEvidenceRemainsExplicitAtReadyGate() {
        Map<RequirementId, RequirementResult> byId = byId(Stage20ACalibrationReadinessCalculator.deriveCurrent());

        assertEquals(
                "compatible_civilian_representatives=EARLY_CIVILIAN_FREIGHTER,MINING_SHIP",
                byId.get(RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE).evidence());
        assertTrue(byId.get(RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE).evidence()
                .contains("endurance_samples=9/9"));
        assertTrue(byId.get(RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE).evidence()
                .contains(Stage20RepresentativeEnduranceProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION));
        assertTrue(byId.get(RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS).evidence()
                .contains("FLEET_REINFORCEMENT_3_HOP"));
        assertTrue(byId.get(RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE).evidence()
                .contains(Stage20FireControlPolicyClosureProfile.CURRENT_VERSION));
    }

    private static Map<RequirementId, RequirementResult> byId(Stage20ACalibrationReadinessProfile profile) {
        return profile.requirements().stream()
                .collect(Collectors.toMap(RequirementResult::id, Function.identity()));
    }
}

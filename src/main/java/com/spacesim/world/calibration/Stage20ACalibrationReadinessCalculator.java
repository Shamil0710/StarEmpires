package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.GateStatus;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RepresentativeRoleCoverage;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequiredRepresentativeRole;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementId;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementResult;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementStatus;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.ReferenceDriveCompatibility;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.CalibrationGap;
import com.spacesim.world.calibration.Stage20FtlCalibrationReference.JumpTopologyMode;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.ArrivalSpatialAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Derives the complete Stage-20A closure/readiness gate directly from accepted calibration outputs. */
public final class Stage20ACalibrationReadinessCalculator {
    private static final Set<String> CIVILIAN_LOGISTICS_REPRESENTATIVES = Set.of(
            "EARLY_CIVILIAN_FREIGHTER",
            "BULK_FREIGHTER_LOADED",
            "MINING_SHIP",
            "FLEET_TANKER_LOADED");

    private Stage20ACalibrationReadinessCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the deterministic current Stage-20A readiness gate.
     *
     * <p>The calculation consumes the current production/provisional calibration profiles rather
     * than duplicating physical numbers. Provisional references explicitly allowed by the Stage-20A
     * roadmap are not blockers merely because Stage-22 production promotion remains pending. Missing
     * physical coverage or required Stage-20A acceptance bands are blocking when Stage 20B would
     * otherwise have to invent them.</p>
     *
     * @return current immutable Stage-20A readiness profile
     */
    public static Stage20ACalibrationReadinessProfile deriveCurrent() {
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20RepresentativeEnduranceProfile endurance = Stage20RepresentativeEnduranceProfile.deriveCurrent();
        Stage20FtlCalibrationProfile ftl = Stage20FtlCalibrationProfile.deriveCurrent();
        Stage20IntersystemCadenceCalibrationProfile intersystemCadence =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Stage20SensorCalibrationProfile sensor = Stage20SensorCalibrationProfile.deriveCurrent();
        Stage20SensorTargetClassCoverageProfile sensorTargetCoverage =
                Stage20SensorTargetClassCoverageProfile.deriveCurrent();
        Stage20FireControlPolicyClosureProfile fireControl = Stage20FireControlPolicyClosureProfile.deriveCurrent();
        Stage20WeaponSpatialCalibrationProfile weapons = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        Stage20WeaponTargetClassCoverageProfile weaponTargetCoverage =
                Stage20WeaponTargetClassCoverageProfile.deriveCurrent();
        Stage20FormationStationSpatialCalibrationProfile formationStation =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        Stage20FormationSpacingCalibrationProfile formationSpacing =
                Stage20FormationSpacingCalibrationProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        Stage20JumpArrivalSpatialCalibrationProfile jumpArrival =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Stage20LocalRouteSemanticCalibrationProfile localRoutes =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20FarCoordinatePrecisionCalibrationProfile precision =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();
        Stage20MaterializationLodCalibrationProfile materialization =
                Stage20MaterializationLodCalibrationCalculator.calibrate();

        List<RepresentativeRoleCoverage> representativeCoverage = deriveRepresentativeCoverage(scale);
        List<RequirementResult> requirements = new ArrayList<>();

        List<RepresentativeRoleCoverage> missingRoles = representativeCoverage.stream()
                .filter(value -> !value.present())
                .toList();
        requirements.add(result(
                RequirementId.REPRESENTATIVE_PROPULSION_COVERAGE,
                missingRoles.isEmpty(),
                missingRoles.isEmpty()
                        ? "all_9_required_stage20_roles_covered"
                        : "missing_roles=" + missingRoles.stream()
                                .map(value -> value.role().name())
                                .collect(Collectors.joining(","))));

        Set<String> propulsionIds = scale.representativeShips().stream()
                .map(RepresentativeShipPropulsionEnvelope::representativeId)
                .collect(Collectors.toSet());
        Set<String> enduranceIds = endurance.samples().stream()
                .map(Stage20RepresentativeEnduranceProfile.EnduranceSample::representativeId)
                .collect(Collectors.toSet());
        boolean enduranceClosed = propulsionIds.size() == 9
                && enduranceIds.equals(propulsionIds)
                && endurance.samples().stream().allMatch(value ->
                        value.sustainedThrustN() > 0d
                                && value.sustainedThrustN() <= value.maxThrustN()
                                && value.missionStoresEnduranceS() > 0d
                                && !value.sustainedThrustSourceEvidenceId().isBlank()
                                && !value.missionStoresSourceEvidenceId().isBlank());
        requirements.add(result(
                RequirementId.REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE,
                enduranceClosed,
                "endurance_samples=" + endurance.samples().size() + "/" + scale.representativeShips().size()
                        + ";profile=" + endurance.version()
                        + ";stage22_review_required=" + endurance.stage22ReviewRequired()));

        List<String> compatibleCivilianFtl = ftl.samples().stream()
                .filter(value -> CIVILIAN_LOGISTICS_REPRESENTATIVES.contains(value.representativeId()))
                .filter(value -> value.compatibility() == ReferenceDriveCompatibility.COMPATIBLE)
                .map(Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample::representativeId)
                .sorted()
                .toList();
        requirements.add(result(
                RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE,
                !compatibleCivilianFtl.isEmpty(),
                compatibleCivilianFtl.isEmpty()
                        ? "no_current_civilian_logistics_representative_is_compatible_with_accepted_reference_drive"
                        : "compatible_civilian_representatives=" + String.join(",", compatibleCivilianFtl)));

        boolean neighborOnly = ftl.reference().topologyMode() == JumpTopologyMode.NEIGHBOR_EDGE_ONLY;
        requirements.add(result(
                RequirementId.FTL_TOPOLOGY_SEMANTICS,
                neighborOnly,
                "topology_mode=" + ftl.reference().topologyMode().name()));

        Set<Stage20IntersystemCadenceCalibrationProfile.BandId> cadenceBandIds =
                intersystemCadence.bands().stream()
                        .map(Stage20IntersystemCadenceCalibrationProfile.CadenceBand::id)
                        .collect(Collectors.toSet());
        boolean cadenceClosed = cadenceBandIds.equals(Set.of(
                Stage20IntersystemCadenceCalibrationProfile.BandId.NEIGHBOR_EDGE,
                Stage20IntersystemCadenceCalibrationProfile.BandId.REGIONAL_3_HOP,
                Stage20IntersystemCadenceCalibrationProfile.BandId.REGIONAL_5_HOP,
                Stage20IntersystemCadenceCalibrationProfile.BandId.FLEET_REINFORCEMENT_3_HOP))
                && !intersystemCadence.samples().isEmpty();
        requirements.add(result(
                RequirementId.INTERSYSTEM_CADENCE_CALIBRATION_BANDS,
                cadenceClosed,
                "profile=" + intersystemCadence.version()
                        + ";bands=" + cadenceBandIds.stream().map(Enum::name).sorted().collect(Collectors.joining(","))
                        + ";excluded_overmass=" + String.join(",", intersystemCadence.excludedRepresentatives())));

        requirements.add(gapRequirement(
                RequirementId.PRODUCTION_FTL_MODULE_PROMOTION,
                ftl.reference().unresolvedGaps().contains(CalibrationGap.PRODUCTION_FTL_MODULE_NOT_AUTHORED),
                RequirementStatus.DEFERRED_STAGE22_CONTENT,
                "accepted_reference_drive_is_calibration_authority_stage22_review_required="
                        + ftl.reference().stage22ReviewRequired()));
        requirements.add(gapRequirement(
                RequirementId.FTL_EDGE_TRANSIT_DISTRIBUTION,
                ftl.reference().unresolvedGaps().contains(CalibrationGap.EDGE_TRANSIT_DISTRIBUTION_NOT_YET_WORLD_AUTHORED),
                RequirementStatus.OWNED_BY_LATER_STAGE20,
                "generated_edge_transit_distribution_is_stage20_world_data_not_stage20a_acceptance_band"));
        requirements.add(gapRequirement(
                RequirementId.FTL_HEAT_COEFFICIENT,
                ftl.reference().unresolvedGaps().contains(CalibrationGap.DRIVE_HEAT_COEFFICIENT_NOT_NUMERIC_IN_V1_BASELINE),
                RequirementStatus.DEFERRED_STAGE22_CONTENT,
                "numeric_ftl_heat_law_not_required_to_invent_stage20b_geometry_from_provisional_reference"));

        long provisionalSensorTargets = sensorTargetCoverage.targets().stream()
                .filter(value -> value.stage22ReviewRequired())
                .count();
        boolean sensorCoverageComplete = sensorTargetCoverage.closesStage20BEntryCoverage();
        requirements.add(result(
                RequirementId.SENSOR_TARGET_CLASS_COVERAGE,
                sensorCoverageComplete,
                "profile=" + sensorTargetCoverage.version()
                        + ";observer=" + sensorTargetCoverage.observerRepresentativeId()
                        + ";modes=" + sensorTargetCoverage.observerModes().stream()
                                .map(Enum::name).sorted().collect(Collectors.joining(","))
                        + ";targets=" + sensorTargetCoverage.targets().size()
                        + ";provisional_stage22=" + provisionalSensorTargets));

        boolean historicalFusedTrackPolicyGap = sensor.unresolvedGaps().stream()
                .anyMatch(value -> value.contains("final_fused_track_quality_policy_pending_weapon_geometry"));
        boolean fusedTrackPolicyClosed = fireControl.closesStage20FireControlPolicy();
        requirements.add(result(
                RequirementId.FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE,
                fusedTrackPolicyClosed,
                fusedTrackPolicyClosed
                        ? "historical_stage20a4_pending=" + historicalFusedTrackPolicyGap
                                + ";superseded_by=" + fireControl.version()
                                + ";minimum_shared_weapon_track_state="
                                + fireControl.minimumSharedWeaponTrackState().name()
                        : "stage20a4_track_policy_remains_provisional_after_stage20a5_weapon_geometry"));

        boolean weaponEvidence = !weapons.kineticSamples().isEmpty()
                && !weapons.beamSamples().isEmpty()
                && !weapons.guidedSamples().isEmpty()
                && !weapons.defenseSamples().isEmpty();
        requirements.add(result(
                RequirementId.WEAPON_PD_SPATIAL_EVIDENCE,
                weaponEvidence,
                "kinetic=" + weapons.kineticSamples().size()
                        + ";beam=" + weapons.beamSamples().size()
                        + ";guided=" + weapons.guidedSamples().size()
                        + ";defense=" + weapons.defenseSamples().size()));

        boolean weaponTargetCoverageComplete = weaponTargetCoverage.closesStage20BEntryCoverage();
        requirements.add(result(
                RequirementId.WEAPON_REPRESENTATIVE_TARGET_COVERAGE,
                weaponTargetCoverageComplete,
                "profile=" + weaponTargetCoverage.version()
                        + ";p50_target_classes=" + weaponTargetCoverage.kineticP50Samples().size()
                        + ";unsupported_p50=" + weaponTargetCoverage.unsupportedP50Targets().stream()
                                .map(Enum::name).collect(Collectors.joining(","))
                        + ";production_spatial_families=" + weaponTargetCoverage.productionSpatialFamilies().stream()
                                .map(Enum::name).sorted().collect(Collectors.joining(","))
                        + ";stage22_review_required=" + weaponTargetCoverage.stage22ReviewRequired()));

        boolean safeInterceptStillInput = weapons.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("safe_intercept_distance_is_scheduler_input"));
        requirements.add(result(
                RequirementId.PD_SAFE_INTERCEPT_GEOMETRY,
                !safeInterceptStillInput,
                safeInterceptStillInput
                        ? "safe_intercept_distance_remains_scheduler_probe_input_pending_fragmentation_blast_debris_closure"
                        : "pd_safe_intercept_geometry_physically_closed"));

        boolean formationEvidence = !formationStation.formationSamples().isEmpty();
        requirements.add(result(
                RequirementId.FORMATION_SPATIAL_EVIDENCE,
                formationEvidence,
                "formation_probe_count=" + formationStation.formationSamples().size()
                        + ";authority_remains_probe_not_world_constant"));

        boolean acceptedFormationBand = formationSpacing.closesStage20BEntryCoverage();
        requirements.add(result(
                RequirementId.FORMATION_SPACING_BAND_CLOSURE,
                acceptedFormationBand,
                "profile=" + formationSpacing.version()
                        + ";bands=" + formationSpacing.bands().stream()
                                .map(value -> value.id().name()).sorted().collect(Collectors.joining(","))
                        + ";source_samples=" + formationSpacing.sourceSamples().size()
                        + ";stage22_review_required=" + formationSpacing.stage22ReviewRequired()));

        long readyStations = formationStation.stationGeometrySamples().stream()
                .filter(Stage20FormationStationSpatialCalibrationProfile.StationGeometrySample::placementReady)
                .count();
        int stationCount = formationStation.stationGeometrySamples().size();
        boolean stationGeometryClosed = stationCount > 0 && readyStations == stationCount;
        requirements.add(result(
                RequirementId.STATION_PHYSICAL_GEOMETRY,
                stationGeometryClosed,
                "placement_ready_stations=" + readyStations + "/" + stationCount));

        boolean stationDefensiveClosed = stationDefensive.closesStage20BEntryCoverage();
        requirements.add(result(
                RequirementId.STATION_DEFENSIVE_SENSOR_GEOMETRY,
                stationDefensiveClosed,
                "profile=" + stationDefensive.version()
                        + ";station_rows=" + stationDefensive.stations().size()
                        + ";tiers=" + stationDefensive.stations().stream()
                                .map(value -> value.securityTier().name())
                                .distinct().sorted().collect(Collectors.joining(","))
                        + ";sensor_reference=" + stationDefensive.sensorReferenceProfileVersion()
                        + ";weapon_reference=" + stationDefensive.weaponReferenceProfileVersion()
                        + ";stage22_review_required=" + stationDefensive.stage22ReviewRequired()));

        long closedStandOffs = jumpArrival.stationStandOffSamples().stream()
                .filter(value -> value.authority() != ArrivalSpatialAuthority.UNRESOLVED)
                .filter(value -> value.centerStandOffM().isPresent())
                .count();
        int standOffCount = jumpArrival.stationStandOffSamples().size();
        boolean standOffClosed = standOffCount > 0 && closedStandOffs == standOffCount;
        requirements.add(result(
                RequirementId.STATION_JUMP_ARRIVAL_STANDOFF,
                standOffClosed,
                "closed_station_stand_offs=" + closedStandOffs + "/" + standOffCount));

        boolean localRouteClosed = localRoutes.closesStage20BEntryCoverage();
        requirements.add(result(
                RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS,
                localRouteClosed,
                "profile=" + localRoutes.version()
                        + ";bands=" + localRoutes.bands().stream()
                                .map(value -> value.id().name()).sorted().collect(Collectors.joining(","))
                        + ";samples=" + localRoutes.samples().size()
                        + ";max_station_standoff_m=" + localRoutes.maxClosedStationStandOffM()
                        + ";stage22_review_required=" + localRoutes.stage22ReviewRequired()));

        requirements.add(new RequirementResult(
                RequirementId.TOPOLOGY_QUALITY_CALIBRATION_BANDS,
                RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "required_maxLinearCorridorLength_maxDegreeOneFraction_cycle_redundancy_gateway_sector_exit_hub_and_hop_bands_are_not_yet_machine_readable"));

        requirements.add(new RequirementResult(
                RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS,
                RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                stationGeometryClosed
                        ? "station_geometry_is_closed_but_no_separate_machine_readable_major_infrastructure_extent_band_profile_exists"
                        : "major_infrastructure_extent_bands_cannot_close_while_station_footprints_are_unresolved_and_no_extent_band_profile_exists"));

        boolean precisionClosed = precision.policy().hierarchicalPhysicalCoordinatesRequired()
                && !precision.policy().legacyGlobalFloatPhysicalAuthorityAllowed()
                && precision.policy().hierarchicalRepresentationWithinBudget();
        requirements.add(result(
                RequirementId.FAR_COORDINATE_PRECISION,
                precisionClosed,
                "error_budget_m=" + precision.policy().absoluteErrorBudgetM()
                        + ";hierarchical_half_ulp_m=" + precision.policy().maximumHierarchicalHalfUlpErrorM()));

        boolean numericLodBandsClosed = !materialization.currentDistanceBandClosures().isEmpty()
                && materialization.currentDistanceBandClosures().stream()
                        .allMatch(value -> value.authority() == DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT
                                && value.activationDistanceM().isPresent());
        boolean lifecycleOpen = materialization.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("materialization_scheduler")
                        || value.contains("dematerialization_service"));
        boolean materializationClosed = numericLodBandsClosed && !lifecycleOpen;
        requirements.add(result(
                RequirementId.MATERIALIZATION_LOD_CLOSURE,
                materializationClosed,
                "numeric_activation_bands_closed=" + numericLodBandsClosed
                        + ";lossless_materialization_lifecycle_closed=" + !lifecycleOpen));

        boolean hasBlocker = requirements.stream()
                .anyMatch(value -> value.status() == RequirementStatus.BLOCKING_STAGE20B_ENTRY);
        return new Stage20ACalibrationReadinessProfile(
                Stage20ACalibrationReadinessProfile.CURRENT_VERSION,
                hasBlocker ? GateStatus.BLOCKED_FOR_STAGE20B : GateStatus.READY_FOR_STAGE20B,
                representativeCoverage,
                requirements);
    }

    private static List<RepresentativeRoleCoverage> deriveRepresentativeCoverage(Stage20ScaleCalibrationProfile scale) {
        Map<RequiredRepresentativeRole, String> expectedIds = new EnumMap<>(RequiredRepresentativeRole.class);
        expectedIds.put(RequiredRepresentativeRole.EARLY_CIVILIAN_FREIGHTER, "EARLY_CIVILIAN_FREIGHTER");
        expectedIds.put(RequiredRepresentativeRole.LOADED_BULK_FREIGHTER, "BULK_FREIGHTER_LOADED");
        expectedIds.put(RequiredRepresentativeRole.MINING_SHIP, "MINING_SHIP");
        expectedIds.put(RequiredRepresentativeRole.PATROL_CORVETTE, "TORPEDO_CORVETTE");
        expectedIds.put(RequiredRepresentativeRole.ESCORT_DESTROYER, "ESCORT_DESTROYER");
        expectedIds.put(RequiredRepresentativeRole.CRUISER, "CRUISER");
        expectedIds.put(RequiredRepresentativeRole.CAPITAL_COMBATANT, "BATTLESHIP");
        expectedIds.put(RequiredRepresentativeRole.FLEET_TANKER, "FLEET_TANKER_LOADED");
        expectedIds.put(RequiredRepresentativeRole.CARRIER_AVIATION, "CARRIER_AVIATION_GROUP");

        Map<String, RepresentativeShipPropulsionEnvelope> byId = scale.representativeShips().stream()
                .collect(Collectors.toMap(RepresentativeShipPropulsionEnvelope::representativeId, value -> value));
        List<RepresentativeRoleCoverage> result = new ArrayList<>();
        for (RequiredRepresentativeRole role : RequiredRepresentativeRole.values()) {
            String expectedId = expectedIds.get(role);
            RepresentativeShipPropulsionEnvelope envelope = byId.get(expectedId);
            if (envelope == null) {
                result.add(new RepresentativeRoleCoverage(
                        role, expectedId, false, Optional.empty(), Optional.empty()));
            } else {
                result.add(new RepresentativeRoleCoverage(
                        role,
                        expectedId,
                        true,
                        Optional.of(envelope.authority().name()),
                        Optional.of(envelope.provenanceId() + ":" + envelope.loadCaseId())));
            }
        }
        return List.copyOf(result);
    }

    private static RequirementResult result(RequirementId id, boolean satisfied, String evidence) {
        return new RequirementResult(
                id,
                satisfied ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                evidence);
    }

    private static RequirementResult gapRequirement(
            RequirementId id,
            boolean gapPresent,
            RequirementStatus openGapStatus,
            String evidence) {
        return new RequirementResult(
                id,
                gapPresent ? openGapStatus : RequirementStatus.SATISFIED,
                evidence + ";gap_present=" + gapPresent);
    }
}

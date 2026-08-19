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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Derives the Stage-20A closure/readiness gate directly from accepted calibration outputs. */
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
     * physical coverage required to place/activate Stage-20B objects is blocking.</p>
     *
     * @return current immutable Stage-20A readiness profile
     */
    public static Stage20ACalibrationReadinessProfile deriveCurrent() {
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20FtlCalibrationProfile ftl = Stage20FtlCalibrationProfile.deriveCurrent();
        Stage20SensorCalibrationProfile sensor = Stage20SensorCalibrationProfile.deriveCurrent();
        Stage20WeaponSpatialCalibrationProfile weapons = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        Stage20FormationStationSpatialCalibrationProfile formationStation =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        Stage20JumpArrivalSpatialCalibrationProfile jumpArrival =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Stage20FarCoordinatePrecisionCalibrationProfile precision =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();
        Stage20MaterializationLodCalibrationProfile materialization =
                Stage20MaterializationLodCalibrationCalculator.calibrate();

        List<RepresentativeRoleCoverage> representativeCoverage = deriveRepresentativeCoverage(scale);
        List<RequirementResult> requirements = new ArrayList<>();

        List<RepresentativeRoleCoverage> missingRoles = representativeCoverage.stream()
                .filter(value -> !value.present())
                .toList();
        requirements.add(new RequirementResult(
                RequirementId.REPRESENTATIVE_PROPULSION_COVERAGE,
                missingRoles.isEmpty() ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                missingRoles.isEmpty()
                        ? "all_9_required_stage20_roles_covered"
                        : "missing_roles=" + missingRoles.stream()
                                .map(value -> value.role().name())
                                .collect(Collectors.joining(","))));

        List<String> compatibleCivilianFtl = ftl.samples().stream()
                .filter(value -> CIVILIAN_LOGISTICS_REPRESENTATIVES.contains(value.representativeId()))
                .filter(value -> value.compatibility() == ReferenceDriveCompatibility.COMPATIBLE)
                .map(Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample::representativeId)
                .sorted()
                .toList();
        requirements.add(new RequirementResult(
                RequirementId.CIVILIAN_ORDINARY_FTL_COVERAGE,
                compatibleCivilianFtl.isEmpty()
                        ? RequirementStatus.BLOCKING_STAGE20B_ENTRY
                        : RequirementStatus.SATISFIED,
                compatibleCivilianFtl.isEmpty()
                        ? "no_current_civilian_logistics_representative_is_compatible_with_accepted_reference_drive"
                        : "compatible_civilian_representatives=" + String.join(",", compatibleCivilianFtl)));

        boolean neighborOnly = ftl.reference().topologyMode() == JumpTopologyMode.NEIGHBOR_EDGE_ONLY;
        requirements.add(new RequirementResult(
                RequirementId.FTL_TOPOLOGY_SEMANTICS,
                neighborOnly ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "topology_mode=" + ftl.reference().topologyMode().name()));

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
                "edge_transit_distribution_is_stage20_world_data_not_stage20a_input"));
        requirements.add(gapRequirement(
                RequirementId.FTL_HEAT_COEFFICIENT,
                ftl.reference().unresolvedGaps().contains(CalibrationGap.DRIVE_HEAT_COEFFICIENT_NOT_NUMERIC_IN_V1_BASELINE),
                RequirementStatus.DEFERRED_STAGE22_CONTENT,
                "numeric_ftl_heat_law_not_required_to_invent_stage20b_geometry_from_provisional_reference"));

        boolean sensorCoverageIncomplete = sensor.unresolvedGaps().stream()
                .anyMatch(value -> value.contains("representative_sensor_and_target_class_coverage_incomplete"));
        requirements.add(new RequirementResult(
                RequirementId.SENSOR_TARGET_CLASS_COVERAGE,
                sensorCoverageIncomplete
                        ? RequirementStatus.BLOCKING_STAGE20B_ENTRY
                        : RequirementStatus.SATISFIED,
                sensorCoverageIncomplete
                        ? "current_sensor_matrix=" + sensor.observerRepresentativeId() + "->"
                                + sensor.targetRepresentativeId() + ";representative_class_coverage_incomplete"
                        : "representative_sensor_target_coverage_closed"));

        boolean weaponEvidence = !weapons.kineticSamples().isEmpty()
                && !weapons.beamSamples().isEmpty()
                && !weapons.guidedSamples().isEmpty()
                && !weapons.defenseSamples().isEmpty();
        requirements.add(new RequirementResult(
                RequirementId.WEAPON_PD_SPATIAL_EVIDENCE,
                weaponEvidence ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "kinetic=" + weapons.kineticSamples().size()
                        + ";beam=" + weapons.beamSamples().size()
                        + ";guided=" + weapons.guidedSamples().size()
                        + ";defense=" + weapons.defenseSamples().size()));

        boolean formationEvidence = !formationStation.formationSamples().isEmpty();
        requirements.add(new RequirementResult(
                RequirementId.FORMATION_SPATIAL_EVIDENCE,
                formationEvidence ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "formation_probe_count=" + formationStation.formationSamples().size()
                        + ";authority_remains_probe_not_world_constant"));

        long readyStations = formationStation.stationGeometrySamples().stream()
                .filter(Stage20FormationStationSpatialCalibrationProfile.StationGeometrySample::placementReady)
                .count();
        int stationCount = formationStation.stationGeometrySamples().size();
        boolean stationGeometryClosed = stationCount > 0 && readyStations == stationCount;
        requirements.add(new RequirementResult(
                RequirementId.STATION_PHYSICAL_GEOMETRY,
                stationGeometryClosed
                        ? RequirementStatus.SATISFIED
                        : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "placement_ready_stations=" + readyStations + "/" + stationCount));

        long closedStandOffs = jumpArrival.stationStandOffSamples().stream()
                .filter(value -> value.authority() != ArrivalSpatialAuthority.UNRESOLVED)
                .filter(value -> value.centerStandOffM().isPresent())
                .count();
        int standOffCount = jumpArrival.stationStandOffSamples().size();
        boolean standOffClosed = standOffCount > 0 && closedStandOffs == standOffCount;
        requirements.add(new RequirementResult(
                RequirementId.STATION_JUMP_ARRIVAL_STANDOFF,
                standOffClosed
                        ? RequirementStatus.SATISFIED
                        : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
                "closed_station_stand_offs=" + closedStandOffs + "/" + standOffCount));

        boolean precisionClosed = precision.policy().hierarchicalPhysicalCoordinatesRequired()
                && !precision.policy().legacyGlobalFloatPhysicalAuthorityAllowed()
                && precision.policy().hierarchicalRepresentationWithinBudget();
        requirements.add(new RequirementResult(
                RequirementId.FAR_COORDINATE_PRECISION,
                precisionClosed ? RequirementStatus.SATISFIED : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
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
        requirements.add(new RequirementResult(
                RequirementId.MATERIALIZATION_LOD_CLOSURE,
                materializationClosed
                        ? RequirementStatus.SATISFIED
                        : RequirementStatus.BLOCKING_STAGE20B_ENTRY,
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

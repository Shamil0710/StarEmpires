package com.spacesim.world.calibration;

import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.ArrivalSpatialAuthority;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.DerivedStandOffEnvelope;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.RepresentativeArrivalSample;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.RuntimeArrivalPolicy;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.StandOffGeometryInput;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.StationStandOffSample;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.TacticalResponseEvidence;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile.StationGeometryDesign;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.BeamSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.DefenseSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.GuidedSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.KineticSample;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Derives Stage-20A.7 jump-arrival and infrastructure stand-off evidence. */
public final class Stage20JumpArrivalSpatialCalibrationCalculator {
    private static final String ORDINARY_TOPOLOGY_SEMANTICS = "NEIGHBOR_EDGE_ONLY";
    private static final double CURRENT_RUNTIME_ARRIVAL_SPEED_MPS = 0d;
    private static final double ADDITIONAL_TRAFFIC_CLEARANCE_BEYOND_OPERATIONAL_RADIUS_M = 0d;

    private Stage20JumpArrivalSpatialCalibrationCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the current deterministic Stage-20A.7 profile.
     *
     * <p>The legacy local arrival anchor remains bounded-viewport compatibility geometry. Station
     * stand-off is derived from the accepted Stage-20 station operational radius and the independent
     * provisional defensive exclusion reference. The station operational radius already includes
     * the larger of docking and traffic clearance, so this calculation deliberately supplies zero
     * additional traffic clearance rather than counting the same clearance twice.</p>
     *
     * <p>The current runtime materializes at zero speed, therefore the braking term is zero for every
     * representative. A positive conservative braking acceleration is still preserved in provenance
     * and input validation so a future non-zero arrival-speed revision immediately activates the
     * physical stopping-distance term instead of introducing a second formula.</p>
     *
     * @return immutable current jump-arrival calibration profile
     */
    public static Stage20JumpArrivalSpatialCalibrationProfile calibrate() {
        RuntimeArrivalPolicy runtimePolicy = new RuntimeArrivalPolicy(
                ORDINARY_TOPOLOGY_SEMANTICS,
                true,
                true,
                LocalSystemCoordinates.ARRIVAL_X,
                LocalSystemCoordinates.ARRIVAL_Y,
                ArrivalSpatialAuthority.LEGACY_BOUNDED_VIEWPORT_COMPATIBILITY,
                CURRENT_RUNTIME_ARRIVAL_SPEED_MPS,
                "FleetJumpService+LocalSystemCoordinates");

        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        List<RepresentativeArrivalSample> representatives = new ArrayList<>();
        for (RepresentativeShipPropulsionEnvelope envelope : scale.representativeShips()) {
            representatives.add(new RepresentativeArrivalSample(
                    envelope.representativeId(),
                    envelope.authority().name(),
                    envelope.provenanceId() + ":" + envelope.loadCaseId(),
                    CURRENT_RUNTIME_ARRIVAL_SPEED_MPS,
                    envelope.initialAccelerationMps2(),
                    brakingDistance(CURRENT_RUNTIME_ARRIVAL_SPEED_MPS, envelope.initialAccelerationMps2())));
        }
        double conservativeBrakingAccelerationMps2 = representatives.stream()
                .mapToDouble(RepresentativeArrivalSample::accelerationMps2)
                .min()
                .orElseThrow(() -> new IllegalStateException("Stage-20 scale profile has no representative acceleration"));

        Stage20StationPhysicalGeometryProfile stationPhysical = Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        if (!stationPhysical.closesStage20BEntryCoverage() || !stationDefensive.closesStage20BEntryCoverage()) {
            throw new IllegalStateException("Station stand-off requires closed Stage-20 station geometry references");
        }

        List<StationStandOffSample> stationStandOff = new ArrayList<>();
        for (StationGeometryDesign station : stationPhysical.stationDesigns()) {
            StationPlacementEnvelope placement = stationPhysical.placementEnvelope(station.stationArchetypeId());
            Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry defensive =
                    stationDefensive.station(station.stationArchetypeId());
            String provenance = "Stage20StationPhysicalGeometryProfile:" + stationPhysical.version()
                    + ":" + station.provenanceId()
                    + ";Stage20StationDefensiveSensorGeometryProfile:" + stationDefensive.version()
                    + ":" + defensive.defenseProvenance()
                    + ";arrival_policy=" + runtimePolicy.source()
                    + ";braking_acceleration_source=min(Stage20ScaleCalibrationProfile:" + scale.version() + ")";
            DerivedStandOffEnvelope derived = deriveStandOff(new StandOffGeometryInput(
                    station.stationArchetypeId(),
                    provenance,
                    placement.operationalRadiusM(),
                    ADDITIONAL_TRAFFIC_CLEARANCE_BEYOND_OPERATIONAL_RADIUS_M,
                    defensive.defensiveExclusionReferenceM(),
                    runtimePolicy.arrivalVelocityMps(),
                    conservativeBrakingAccelerationMps2));
            stationStandOff.add(new StationStandOffSample(
                    station.stationArchetypeId(),
                    ArrivalSpatialAuthority.PROVISIONAL_STAGE20_DESIGN_REFERENCE,
                    provenance,
                    OptionalDouble.of(derived.requiredCenterStandOffM()),
                    List.of()));
        }

        Stage20WeaponSpatialCalibrationProfile weapons = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        TacticalResponseEvidence tactical = tacticalEvidence(weapons);

        return new Stage20JumpArrivalSpatialCalibrationProfile(
                Stage20JumpArrivalSpatialCalibrationProfile.CURRENT_VERSION,
                runtimePolicy,
                representatives,
                stationStandOff,
                tactical,
                List.of(
                        "legacy_viewport_arrival_anchor_is_not_stage20_physical_world_geometry",
                        "station_center_standoff_is_provisional_stage20_design_geometry_pending_stage22_station_content",
                        "defensive_exclusion_reference_is_not_a_production_station_weapon_range",
                        "station_operational_radius_already_contains_authored_docking_or_traffic_clearance_no_double_counting",
                        "current_runtime_materializes_at_zero_speed_so_post_jump_braking_distance_is_zero",
                        "generated_arrival_to_hub_distance_distribution_remains_stage20_world_authoring"));
    }

    /**
     * Derives one conservative infrastructure-centered stand-off from explicit physical inputs.
     *
     * <p>The required center distance is the maximum of operational/traffic clearance, stopping
     * clearance and an explicit center-based defensive/exclusion reference. Stage-20A.5 probe
     * maxima are never inserted automatically; a caller must supply an accepted exclusion reference
     * with provenance.</p>
     *
     * @param input explicit physical infrastructure, exclusion and arrival-response geometry
     * @return conservative derived stand-off envelope
     */
    public static DerivedStandOffEnvelope deriveStandOff(StandOffGeometryInput input) {
        double brakingDistance = brakingDistance(input.arrivalSpeedMps(), input.brakingAccelerationMps2());
        double trafficDistance = input.operationalRadiusM() + input.trafficClearanceM();
        double brakingDistanceFromCenter = input.operationalRadiusM() + brakingDistance;
        double defensiveDistance = input.defensiveEnvelopeFromCenterM();
        double required = Math.max(trafficDistance, Math.max(brakingDistanceFromCenter, defensiveDistance));
        return new DerivedStandOffEnvelope(
                input.infrastructureId(),
                input.provenance(),
                brakingDistance,
                trafficDistance,
                brakingDistanceFromCenter,
                defensiveDistance,
                required);
    }

    /**
     * Computes ideal constant-deceleration stopping distance for sensitivity/closure calculations.
     *
     * @param speedMps non-negative speed requiring arrest
     * @param accelerationMps2 positive available braking acceleration
     * @return ideal stopping distance in meters
     */
    public static double brakingDistance(double speedMps, double accelerationMps2) {
        if (!Double.isFinite(speedMps) || speedMps < 0d) {
            throw new IllegalArgumentException("speedMps must be non-negative and finite");
        }
        if (!Double.isFinite(accelerationMps2) || accelerationMps2 <= 0d) {
            throw new IllegalArgumentException("accelerationMps2 must be positive and finite");
        }
        return speedMps * speedMps / (2d * accelerationMps2);
    }

    private static TacticalResponseEvidence tacticalEvidence(Stage20WeaponSpatialCalibrationProfile weapons) {
        double maxKinetic = weapons.kineticSamples().stream()
                .mapToDouble(KineticSample::rangeM)
                .max()
                .orElseThrow();
        double maxBeam = weapons.beamSamples().stream()
                .mapToDouble(BeamSample::rangeM)
                .max()
                .orElseThrow();
        double maxGuided = weapons.guidedSamples().stream()
                .mapToDouble(GuidedSample::rangeM)
                .max()
                .orElseThrow();
        double maxAssignedDefense = weapons.defenseSamples().stream()
                .filter(DefenseSample::assigned)
                .mapToDouble(DefenseSample::interceptDistanceFromProtectedCenterM)
                .max()
                .orElse(0d);
        return new TacticalResponseEvidence(
                maxKinetic,
                maxBeam,
                maxGuided,
                maxAssignedDefense,
                ArrivalSpatialAuthority.PROVISIONAL_CALIBRATION_PROBE,
                "Stage20WeaponSpatialCalibrationProfile:" + weapons.version());
    }
}

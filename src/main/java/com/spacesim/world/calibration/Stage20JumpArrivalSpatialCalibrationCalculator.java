package com.spacesim.world.calibration;

import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationGeometrySample;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.ArrivalSpatialAuthority;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.DerivedStandOffEnvelope;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.RepresentativeArrivalSample;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.RuntimeArrivalPolicy;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.StandOffGeometryInput;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.StationStandOffSample;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile.TacticalResponseEvidence;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
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

    private Stage20JumpArrivalSpatialCalibrationCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the current deterministic Stage-20A.7 profile.
     *
     * <p>The legacy local arrival anchor remains explicitly classified as bounded-viewport
     * compatibility geometry. Current station-specific stand-off remains unresolved because Stage
     * 20A.6 found no authoritative station footprint/docking/traffic dimensions.</p>
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

        Stage20FormationStationSpatialCalibrationProfile stationProfile =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        List<StationStandOffSample> stationStandOff = new ArrayList<>();
        for (StationGeometrySample station : stationProfile.stationGeometrySamples()) {
            stationStandOff.add(new StationStandOffSample(
                    station.stationArchetypeId(),
                    ArrivalSpatialAuthority.UNRESOLVED,
                    OptionalDouble.empty(),
                    List.of(
                            "stage20a6_station_operational_radius_unresolved",
                            "stage20a6_docking_approach_clearance_unresolved",
                            "stage20a6_traffic_clearance_unresolved",
                            "stage20a5_weapon_pd_distances_are_calibration_probes_not_station_defense_radius")));
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
                        "station_specific_center_standoff_unresolved_until_station_geometry_closes",
                        "stage20a5_tactical_probe_ranges_cannot_be_promoted_to_station_defense_radius",
                        "current_runtime_materializes_at_zero_speed_so_post_jump_braking_distance_is_zero",
                        "generated_arrival_to_hub_distance_distribution_remains_stage20_world_authoring"));
    }

    /**
     * Derives one conservative infrastructure-centered stand-off from explicit physical inputs.
     *
     * <p>The required center distance is the maximum of traffic clearance, stopping clearance and an
     * explicit center-based defensive envelope. Stage-20A.5 probe maxima are never inserted
     * automatically; a caller must supply an accepted defensive envelope with provenance.</p>
     *
     * @param input explicit physical infrastructure, defense and arrival-response geometry
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

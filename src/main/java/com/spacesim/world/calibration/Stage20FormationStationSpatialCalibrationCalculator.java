package com.spacesim.world.calibration;

import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.ship.TacticalFormationPlanner;
import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.ship.TacticalFormationPlanner.FormationStatus;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.FormationProbeSample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.ShipyardBerthSample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.SpatialAuthority;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationGeometrySample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementGeometryInput;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Derives Stage-20A.6 formation and station spatial evidence from existing production authority. */
public final class Stage20FormationStationSpatialCalibrationCalculator {
    private static final String ESCORT_REPRESENTATIVE_ID = "ESCORT_DESTROYER";
    private static final double BREAK_BOUNDARY_PROBE_MARGIN_M = 1e-6d;

    private Stage20FormationStationSpatialCalibrationCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the current deterministic Stage-20A.6 profile.
     *
     * <p>Formation probes retain Stage-19 authored values with provisional authority. Station
     * archetypes remain unresolved because Stage-18F does not author footprint/docking dimensions.
     * Stage-18G shipyard berth dimensions are exposed separately as physical evidence and are never
     * treated as the whole station footprint.</p>
     *
     * @return current immutable calibration profile
     */
    public static Stage20FormationStationSpatialCalibrationProfile calibrate() {
        RepresentativeShipPropulsionEnvelope escort = productionEscortEnvelope();
        double acceleration = escort.initialAccelerationMps2();
        String accelerationSource = "Stage20ScaleCalibrationProfile:" + escort.provenanceId()
                + ":" + escort.loadCaseId();

        List<FormationProbeSample> formations = List.of(
                deriveFormationProbe(
                        "stage19.compact_4v4",
                        new TacticalFormationPlanner.Objective(FormationMode.COMPACT, 700d, 120d, 5d, 80d),
                        4,
                        acceleration,
                        "docs/stage19i_l_tactical_formation.md;" + accelerationSource),
                deriveFormationProbe(
                        "stage19.dispersed_4v4",
                        new TacticalFormationPlanner.Objective(FormationMode.DISPERSED, 700d, 240d, 5d, 80d),
                        4,
                        acceleration,
                        "docs/stage19i_l_tactical_formation.md;" + accelerationSource),
                deriveFormationProbe(
                        "stage19.compact_16_ship_side",
                        new TacticalFormationPlanner.Objective(FormationMode.COMPACT, 710d, 100d, 5d, 80d),
                        16,
                        acceleration,
                        "docs/stage19i_l_tactical_formation.md;" + accelerationSource));

        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        List<StationGeometrySample> stationSamples = new ArrayList<>();
        for (Stage18StationInfrastructureCatalog.StationArchetypeDefinition station : stations.getArchetypes()) {
            stationSamples.add(new StationGeometrySample(
                    station.id(),
                    SpatialAuthority.UNRESOLVED,
                    "Stage18StationInfrastructureCatalog:" + stations.getFingerprint(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    List.of(
                            "stage18_station_archetype_has_no_physical_footprint_dimensions",
                            "stage18_station_archetype_has_no_docking_approach_clearance",
                            "stage18_station_archetype_has_no_traffic_clearance")));
        }

        Stage18ShipyardCatalog shipyards = Stage18ShipyardCatalogLoader.loadDefault();
        List<ShipyardBerthSample> berthSamples = new ArrayList<>();
        for (Stage18ShipyardCatalog.YardDefinition yard : shipyards.getYards()) {
            Dimensions3d berth = yard.berthDimensionsM();
            berthSamples.add(new ShipyardBerthSample(
                    yard.id(),
                    SpatialAuthority.PRODUCTION_AUTHORITATIVE,
                    "Stage18ShipyardCatalog:" + shipyards.getFingerprint(),
                    berth.lengthM(),
                    berth.widthM(),
                    berth.heightM()));
        }

        return new Stage20FormationStationSpatialCalibrationProfile(
                Stage20FormationStationSpatialCalibrationProfile.CURRENT_VERSION,
                formations,
                stationSamples,
                berthSamples,
                List.of(
                        "stage19_formation_distances_are_acceptance_probes_not_final_world_spacing",
                        "station_footprint_dimensions_missing_from_stage18_authority",
                        "station_docking_approach_geometry_missing_from_stage18_authority",
                        "station_traffic_clearance_missing_from_stage18_authority",
                        "shipyard_berth_envelope_is_not_station_footprint"));
    }

    /**
     * Derives one formation envelope from explicit tactical geometry and physical acceleration.
     *
     * <p>The recovery time is the symmetric bang-bang lower bound from the authored break threshold
     * to the slot tolerance, starting and ending at zero lateral speed. It is calibration evidence,
     * not a promise that the live runtime always recovers in exactly this time.</p>
     */
    public static FormationProbeSample deriveFormationProbe(
            String probeId,
            TacticalFormationPlanner.Objective objective,
            int shipCount,
            double accelerationMps2,
            String source) {
        if (shipCount <= 0) {
            throw new IllegalArgumentException("shipCount must be positive");
        }
        if (!Double.isFinite(accelerationMps2) || accelerationMps2 <= 0d) {
            throw new IllegalArgumentException("accelerationMps2 must be positive and finite");
        }
        TacticalFormationPlanner planner = new TacticalFormationPlanner();
        double lineSpan = (shipCount - 1d) * objective.spacingM();
        double outerOffset = lineSpan / 2d;
        double recoveryDistance = Math.max(0d, objective.breakDistanceM() - objective.slotToleranceM());
        double recoveryTime = recoveryDistance == 0d
                ? 0d
                : 2d * Math.sqrt(recoveryDistance / accelerationMps2);

        int probeSlot = shipCount / 2;
        double centeredIndex = probeSlot - (shipCount - 1d) / 2d;
        double desiredY = objective.centerYM() + centeredIndex * objective.spacingM();
        TacticalFormationPlanner.Command command = planner.plan(
                objective,
                probeSlot,
                shipCount,
                desiredY + objective.breakDistanceM() + BREAK_BOUNDARY_PROBE_MARGIN_M,
                0d,
                accelerationMps2,
                true);
        if (command.status() != FormationStatus.BROKEN) {
            throw new IllegalStateException("Stage-19 planner did not classify beyond-break probe as BROKEN");
        }

        return new FormationProbeSample(
                probeId,
                SpatialAuthority.PROVISIONAL_STAGE19_TACTICAL_PROBE,
                source,
                objective.mode(),
                shipCount,
                objective.spacingM(),
                objective.slotToleranceM(),
                objective.breakDistanceM(),
                lineSpan,
                outerOffset,
                accelerationMps2,
                recoveryDistance,
                recoveryTime);
    }

    /**
     * Derives a conservative top-down placement envelope from explicitly authored physical geometry.
     *
     * <p>This method is deliberately impossible to call from Stage-18 capacity/throughput alone. The
     * caller must provide actual dimensions and clearances with provenance. The larger of docking and
     * traffic clearance expands the footprint half-diagonal into a conservative operational radius.</p>
     */
    public static StationPlacementEnvelope deriveStationPlacementEnvelope(StationPlacementGeometryInput input) {
        double halfDiagonal = Math.hypot(input.footprintLengthM(), input.footprintWidthM()) / 2d;
        double clearance = Math.max(input.dockingApproachClearanceM(), input.trafficClearanceM());
        double radius = halfDiagonal + clearance;
        return new StationPlacementEnvelope(
                input.stationArchetypeId(),
                input.provenance(),
                halfDiagonal,
                clearance,
                radius,
                radius * 2d);
    }

    private static RepresentativeShipPropulsionEnvelope productionEscortEnvelope() {
        for (RepresentativeShipPropulsionEnvelope envelope : Stage20ScaleCalibrationProfile.deriveCurrent().representativeShips()) {
            if (ESCORT_REPRESENTATIVE_ID.equals(envelope.representativeId())) {
                return envelope;
            }
        }
        throw new IllegalStateException("Current Stage-20 scale profile has no production escort representative");
    }
}

package com.spacesim.world;

import com.spacesim.world.Stage20JumpEdgeState.ArrivalEndpoint;
import com.spacesim.world.Stage20JumpEdgeState.DiscoveryPolicy;
import com.spacesim.world.Stage20JumpEdgeState.HazardSecurityMetadata;
import com.spacesim.world.Stage20JumpEdgeState.OperationalAccessState;
import com.spacesim.world.Stage20JumpEdgeState.TransitParameters;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationCalculator;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationProfile;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile;
import com.spacesim.world.calibration.Stage20StationDefensiveSensorGeometryProfile;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministically binds an accepted ordinary topology to Stage-20C destination-local arrival
 * anchors and produces exact-coverage {@link Stage20JumpEdgeCatalog} metadata.
 *
 * <p>One local jump-arrival anchor is required per incident ordinary edge. Current generated worlds
 * use the canonical neighbor-specific identity from {@link Stage20DirectionalJumpAnchorLayout}, so
 * edge-to-anchor binding is independent of lexicographic list ordering. Historical hand-authored
 * Stage-20C fixtures that contain none of those canonical IDs retain the deterministic sorted-anchor
 * compatibility path; a mixed partial canonical mapping fails closed.</p>
 *
 * <p>The materializer never falls back to the legacy viewport {@code (0,0)} arrival and never reuses
 * one local lane across multiple ordinary edges. If upstream local geometry supplies too few
 * calibrated anchors, world assembly fails explicitly and must regenerate/reject before
 * materialization.</p>
 *
 * <p>Stage-20A explicitly does not author a generated per-edge transit-time distribution. V1
 * therefore records multiplier {@code 1.0} over the live fitted edge transit. Hazard/security data
 * remains explicitly unassessed instead of inventing random risk values.</p>
 */
public final class Stage20JumpEdgeStateMaterializer {
    private static final String TRANSIT_SEMANTICS = "LIVE_FITTED_EDGE_TRANSIT_X_MULTIPLIER";

    private Stage20JumpEdgeStateMaterializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Materializes current Stage-20D ordinary-edge metadata from accepted topology and local layouts.
     *
     * @param topology accepted ordinary jump topology
     * @param layoutsBySystem current Stage-20C local layouts for every topology system
     * @return deterministic exact-coverage physical edge catalog
     */
    public static Stage20JumpEdgeCatalog materializeCurrent(
            GalaxyTopology topology,
            Map<StarSystemId, Stage20LocalInfrastructureLayout> layoutsBySystem) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(layoutsBySystem, "layoutsBySystem");

        Stage20TopologyQualityCalibrationProfile quality = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        Stage20IntersystemCadenceCalibrationProfile cadence = Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        Stage20LocalRouteSemanticCalibrationProfile localRoutes = Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20JumpArrivalSpatialCalibrationProfile arrival = Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        if (!quality.closesStage20BEntryCoverage() || !localRoutes.closesStage20BEntryCoverage()) {
            throw new IllegalStateException("Stage-20D edge materialization requires closed Stage-20A topology/local-route calibration");
        }
        if (!Stage20JumpArrivalSpatialCalibrationProfile.CURRENT_VERSION.equals(arrival.version())
                || !"NEIGHBOR_EDGE_ONLY".equals(arrival.runtimeArrivalPolicy().topologySemantics())) {
            throw new IllegalStateException("Stage-20D edge materialization requires current neighbor-only arrival calibration");
        }

        BandDefinition arrivalBand = localRoutes.bands().stream()
                .filter(value -> value.id() == BandId.JUMP_ARRIVAL_TO_MAJOR_HUB)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing current jump-arrival local-route band"));

        Map<StarSystemId, List<JumpConnection>> incidentEdges = incidentEdges(checkedTopology);
        Map<StarSystemId, Map<JumpConnection, InfrastructurePlacement>> assignedAnchors = new HashMap<>();
        for (StarSystemNode system : checkedTopology.systems()) {
            Stage20LocalInfrastructureLayout layout = layoutsBySystem.get(system.id());
            if (layout == null) {
                throw new IllegalArgumentException("missing Stage-20C local layout for system " + system.id());
            }
            assignedAnchors.put(
                    system.id(),
                    assignAnchors(
                            system.id(),
                            layout,
                            incidentEdges.getOrDefault(system.id(), List.of()),
                            localRoutes,
                            arrivalBand));
        }

        ArrayList<Stage20JumpEdgeState> metadata = new ArrayList<>(checkedTopology.connections().size());
        for (JumpConnection connection : checkedTopology.connections()) {
            InfrastructurePlacement firstAnchor = requireAssigned(assignedAnchors, connection.first(), connection);
            InfrastructurePlacement secondAnchor = requireAssigned(assignedAnchors, connection.second(), connection);
            metadata.add(new Stage20JumpEdgeState(
                    Stage20JumpEdgeState.CURRENT_VERSION,
                    Stage20JumpEdgeState.ordinaryEdgeId(connection),
                    connection,
                    OperationalAccessState.OPEN,
                    DiscoveryPolicy.ORDINARY_DISCOVERABLE,
                    new TransitParameters(
                            1d,
                            Stage20FtlCalibrationProfile.CURRENT_VERSION,
                            TRANSIT_SEMANTICS),
                    endpoint(connection.first(), firstAnchor, layoutsBySystem.get(connection.first()), arrival),
                    endpoint(connection.second(), secondAnchor, layoutsBySystem.get(connection.second()), arrival),
                    HazardSecurityMetadata.unassessed(),
                    quality.version(),
                    cadence.version()));
        }
        return new Stage20JumpEdgeCatalog(Stage20JumpEdgeCatalog.CURRENT_VERSION, checkedTopology, metadata);
    }

    private static Map<StarSystemId, List<JumpConnection>> incidentEdges(GalaxyTopology topology) {
        HashMap<StarSystemId, List<JumpConnection>> result = new HashMap<>();
        for (StarSystemNode system : topology.systems()) {
            result.put(system.id(), new ArrayList<>());
        }
        for (JumpConnection connection : topology.connections()) {
            result.get(connection.first()).add(connection);
            result.get(connection.second()).add(connection);
        }
        for (Map.Entry<StarSystemId, List<JumpConnection>> entry : result.entrySet()) {
            entry.getValue().sort(Comparator.naturalOrder());
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<JumpConnection, InfrastructurePlacement> assignAnchors(
            StarSystemId systemId,
            Stage20LocalInfrastructureLayout layout,
            List<JumpConnection> incident,
            Stage20LocalRouteSemanticCalibrationProfile localRoutes,
            BandDefinition arrivalBand) {
        if (!Stage20LocalInfrastructureLayout.CURRENT_VERSION.equals(layout.version())) {
            throw new IllegalArgumentException("Stage-20D requires current Stage-20C local-infrastructure layout");
        }
        if (!Stage20SystemGeometry.CURRENT_VERSION.equals(layout.systemGeometryVersion())
                || !localRoutes.version().equals(layout.routeCalibrationVersion())
                || !Stage20StationPhysicalGeometryProfile.CURRENT_VERSION.equals(layout.stationGeometryVersion())
                || !Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION.equals(layout.stationDefenseVersion())) {
            throw new IllegalArgumentException("Stage-20C layout uses stale physical/calibration provenance");
        }
        if (!systemId.equals(layout.systemId())) {
            throw new IllegalArgumentException("local layout belongs to a different StarSystem: " + systemId);
        }
        List<InfrastructurePlacement> anchors = layout.placements().stream()
                .filter(value -> value.kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR)
                .sorted(Comparator.comparing(InfrastructurePlacement::id))
                .toList();
        if (anchors.size() < incident.size()) {
            throw new IllegalStateException("system " + systemId + " requires " + incident.size()
                    + " distinct jump-arrival anchors but Stage-20C layout supplies " + anchors.size());
        }

        HashMap<String, InfrastructurePlacement> anchorById = new HashMap<>();
        for (InfrastructurePlacement anchor : anchors) {
            anchorById.put(anchor.id(), anchor);
        }
        int canonicalMatches = 0;
        for (JumpConnection edge : incident) {
            StarSystemId neighbor = neighborOf(systemId, edge);
            if (anchorById.containsKey(Stage20DirectionalJumpAnchorLayout.anchorId(systemId, neighbor))) {
                canonicalMatches++;
            }
        }
        if (canonicalMatches > 0 && canonicalMatches != incident.size()) {
            throw new IllegalStateException(
                    "system " + systemId + " contains a partial canonical neighbor-specific jump-anchor mapping");
        }

        HashMap<JumpConnection, InfrastructurePlacement> result = new HashMap<>();
        if (canonicalMatches == incident.size()) {
            for (JumpConnection edge : incident) {
                StarSystemId neighbor = neighborOf(systemId, edge);
                InfrastructurePlacement anchor = anchorById.get(
                        Stage20DirectionalJumpAnchorLayout.anchorId(systemId, neighbor));
                requireCalibratedArrivalConnection(layout, anchor, localRoutes, arrivalBand);
                result.put(edge, anchor);
            }
            return Map.copyOf(result);
        }

        for (int index = 0; index < incident.size(); index++) {
            InfrastructurePlacement anchor = anchors.get(index);
            requireCalibratedArrivalConnection(layout, anchor, localRoutes, arrivalBand);
            result.put(incident.get(index), anchor);
        }
        return Map.copyOf(result);
    }

    private static StarSystemId neighborOf(StarSystemId systemId, JumpConnection edge) {
        if (edge.first().equals(systemId)) {
            return edge.second();
        }
        if (edge.second().equals(systemId)) {
            return edge.first();
        }
        throw new IllegalArgumentException("edge is not incident to system " + systemId + ": " + edge);
    }

    private static void requireCalibratedArrivalConnection(
            Stage20LocalInfrastructureLayout layout,
            InfrastructurePlacement anchor,
            Stage20LocalRouteSemanticCalibrationProfile localRoutes,
            BandDefinition arrivalBand) {
        CalibratedConnection route = layout.connections().stream()
                .filter(value -> value.bandId() == BandId.JUMP_ARRIVAL_TO_MAJOR_HUB)
                .filter(value -> value.fromId().equals(anchor.id()))
                .filter(value -> value.toId().equals(layout.majorHubId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "jump-arrival anchor lacks calibrated arrival-to-hub connection: " + anchor.id()));
        if (Double.compare(route.minDistanceM(), arrivalBand.minDistanceM()) != 0
                || Double.compare(route.maxDistanceM(), arrivalBand.maxDistanceM()) != 0
                || !route.sourceEvidenceId().equals(arrivalBand.sourceEvidenceId())
                || !route.logisticsConsequences().sourceProfileVersion().equals(localRoutes.version())) {
            throw new IllegalArgumentException("jump-arrival anchor does not use the current accepted local-route band: "
                    + anchor.id());
        }
        if (route.distanceM() < localRoutes.maxClosedStationStandOffM()
                || route.distanceM() < arrivalBand.minDistanceM()
                || route.distanceM() > arrivalBand.maxDistanceM()) {
            throw new IllegalArgumentException("jump-arrival anchor violates current accepted physical distance/stand-off: "
                    + anchor.id());
        }
    }

    private static InfrastructurePlacement requireAssigned(
            Map<StarSystemId, Map<JumpConnection, InfrastructurePlacement>> assigned,
            StarSystemId systemId,
            JumpConnection edge) {
        Map<JumpConnection, InfrastructurePlacement> byEdge = assigned.get(systemId);
        InfrastructurePlacement anchor = byEdge == null ? null : byEdge.get(edge);
        if (anchor == null) {
            throw new IllegalStateException("missing deterministic arrival assignment for " + edge + " in " + systemId);
        }
        return anchor;
    }

    private static ArrivalEndpoint endpoint(
            StarSystemId systemId,
            InfrastructurePlacement anchor,
            Stage20LocalInfrastructureLayout layout,
            Stage20JumpArrivalSpatialCalibrationProfile arrival) {
        return new ArrivalEndpoint(
                systemId,
                anchor.id(),
                anchor.position(),
                arrival.runtimeArrivalPolicy().arrivalVelocityMps(),
                layout.version(),
                arrival.version());
    }
}
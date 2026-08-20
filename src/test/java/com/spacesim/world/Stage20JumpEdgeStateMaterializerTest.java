package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20JumpEdgeState.ObservationState;
import com.spacesim.world.Stage20JumpEdgeState.OperationalAccessState;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.LogisticsConsequenceEnvelope;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.calibration.Stage20JumpArrivalSpatialCalibrationCalculator;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile;
import com.spacesim.world.calibration.Stage20StationDefensiveSensorGeometryProfile;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20JumpEdgeStateMaterializerTest {
    @Test
    void materializationIsDeterministicAndUsesDistinctPhysicalAnchorsPerIncidentEdge() {
        GalaxyTopology topology = diamondTopology();
        Map<StarSystemId, Stage20LocalInfrastructureLayout> forward = layouts(topology, false);
        Map<StarSystemId, Stage20LocalInfrastructureLayout> reverse = layouts(topology, true);

        Stage20JumpEdgeCatalog first = Stage20JumpEdgeStateMaterializer.materializeCurrent(topology, forward);
        Stage20JumpEdgeCatalog second = Stage20JumpEdgeStateMaterializer.materializeCurrent(topology, reverse);

        assertEquals(first.edges(), second.edges());
        assertEquals(topology.connections().size(), first.edges().size());
        for (Stage20JumpEdgeState edge : first.edges()) {
            assertEquals(Stage20JumpEdgeState.ordinaryEdgeId(edge.connection()), edge.edgeId());
            assertEquals(1d, edge.transitParameters().fittedTransitMultiplier(), 0d);
            assertEquals(ObservationState.UNASSESSED, edge.hazardSecurityMetadata().observationState());
            assertTrue(edge.hazardSecurityMetadata().hazardTags().isEmpty());
            assertTrue(edge.hazardSecurityMetadata().securityTags().isEmpty());
            assertEquals(0d, edge.firstEndpoint().arrivalVelocityMps(), 0d);
            assertEquals(0d, edge.secondEndpoint().arrivalVelocityMps(), 0d);
        }

        StarSystemId systemA = new StarSystemId(1L);
        List<Stage20JumpEdgeState.ArrivalEndpoint> arrivalsInA = first.edges().stream()
                .filter(value -> value.connection().first().equals(systemA)
                        || value.connection().second().equals(systemA))
                .map(value -> value.arrivalIn(systemA))
                .toList();
        assertEquals(2, arrivalsInA.size());
        assertNotEquals(arrivalsInA.get(0).anchorId(), arrivalsInA.get(1).anchorId());
        assertNotEquals(arrivalsInA.get(0).position(), arrivalsInA.get(1).position());
    }

    @Test
    void insufficientArrivalAnchorsRejectsInsteadOfFallingBackToLegacyCoordinates() {
        GalaxyTopology topology = diamondTopology();
        Map<StarSystemId, Stage20LocalInfrastructureLayout> source = new LinkedHashMap<>(layouts(topology, false));
        StarSystemId a = new StarSystemId(1L);
        source.put(a, layout(a, 1));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> Stage20JumpEdgeStateMaterializer.materializeCurrent(topology, source));
        assertTrue(failure.getMessage().contains("requires 2 distinct jump-arrival anchors"));
    }

    @Test
    void catalogRejectsMissingMetadataCoverageInsteadOfTreatingItAsAnUnknownShortcut() {
        GalaxyTopology topology = diamondTopology();
        Stage20JumpEdgeCatalog catalog = Stage20JumpEdgeStateMaterializer.materializeCurrent(
                topology, layouts(topology, false));
        List<Stage20JumpEdgeState> incomplete = catalog.edges().subList(0, catalog.edges().size() - 1);

        assertThrows(IllegalArgumentException.class,
                () -> new Stage20JumpEdgeCatalog(Stage20JumpEdgeCatalog.CURRENT_VERSION, topology, incomplete));
    }

    @Test
    void physicallyClosedEdgeIsExcludedAndNextHopReplansFromCurrentSystem() {
        StarSystemNode a = system(1, "A", 0, 0);
        StarSystemNode b = system(2, "B", 1, 0);
        StarSystemNode c = system(3, "C", 2, 1);
        StarSystemNode d = system(4, "D", 3, 0);
        JumpConnection ab = new JumpConnection(a.id(), b.id());
        JumpConnection bd = new JumpConnection(b.id(), d.id());
        JumpConnection bc = new JumpConnection(b.id(), c.id());
        JumpConnection cd = new JumpConnection(c.id(), d.id());
        GalaxyTopology topology = topology(List.of(a, b, c, d), List.of(ab, bd, bc, cd));
        Stage20JumpEdgeCatalog catalog = Stage20JumpEdgeStateMaterializer.materializeCurrent(
                topology,
                layouts(topology, false));
        JumpPlan plan = fittedPlan();

        Stage20NextJumpExecutionPlan first = Stage20JumpRouteExecutionPlanner.planNextHop(
                catalog, plan, a.id(), d.id()).orElseThrow();
        assertEquals(b.id(), first.immediateDestination());
        assertEquals(ab, first.connection());
        assertTrue(topology.neighbors(a.id()).contains(first.immediateDestination()));

        Stage20JumpEdgeCatalog changed = catalog.withOperationalAccess(bd, OperationalAccessState.PHYSICALLY_CLOSED);
        Stage20NextJumpExecutionPlan replanned = Stage20JumpRouteExecutionPlanner.planNextHop(
                changed, plan, b.id(), d.id()).orElseThrow();
        assertEquals(c.id(), replanned.immediateDestination());
        assertEquals(bc, replanned.connection());
        assertEquals(2, replanned.currentRoute().jumpCount());
        assertFalse(replanned.connection().equals(bd));
        assertEquals(
                changed.arrivalIn(bc, c.id()).position(),
                replanned.arrivalEndpoint().position());
    }

    private static GalaxyTopology diamondTopology() {
        StarSystemNode a = system(1, "A", 0, 0);
        StarSystemNode b = system(2, "B", 1, 1);
        StarSystemNode c = system(3, "C", 1, -1);
        StarSystemNode d = system(4, "D", 2, 0);
        return topology(
                List.of(a, b, c, d),
                List.of(
                        new JumpConnection(a.id(), b.id()),
                        new JumpConnection(a.id(), c.id()),
                        new JumpConnection(b.id(), d.id()),
                        new JumpConnection(c.id(), d.id())));
    }

    private static GalaxyTopology topology(List<StarSystemNode> systems, List<JumpConnection> edges) {
        return new GalaxyTopology(
                new GalaxyId(77L),
                "Stage20D edge metadata test",
                List.of(new SectorNode(new SectorId(1L), "Core", systems)),
                edges);
    }

    private static StarSystemNode system(long id, String name, double x, double y) {
        return new StarSystemNode(new StarSystemId(id), name, x, y);
    }

    private static Map<StarSystemId, Stage20LocalInfrastructureLayout> layouts(
            GalaxyTopology topology,
            boolean reverseInsertion) {
        ArrayList<StarSystemNode> systems = new ArrayList<>(topology.systems());
        if (reverseInsertion) {
            java.util.Collections.reverse(systems);
        }
        LinkedHashMap<StarSystemId, Stage20LocalInfrastructureLayout> result = new LinkedHashMap<>();
        for (StarSystemNode system : systems) {
            result.put(system.id(), layout(system.id(), topology.neighbors(system.id()).size()));
        }
        return result;
    }

    private static Stage20LocalInfrastructureLayout layout(StarSystemId systemId, int arrivalCount) {
        Stage20LocalRouteSemanticCalibrationProfile routes = Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        BandDefinition arrivalBand = routes.bands().stream()
                .filter(value -> value.id() == BandId.JUMP_ARRIVAL_TO_MAJOR_HUB)
                .findFirst()
                .orElseThrow();
        double distance = arrivalBand.minDistanceM();
        String hubId = "hub-" + systemId.value();
        InfrastructurePlacement hub = new InfrastructurePlacement(
                hubId,
                PlacementKind.MAJOR_HUB_STATION,
                java.util.Optional.of("station.test"),
                LocalPhysicalPosition.origin(),
                1d,
                1d);
        ArrayList<InfrastructurePlacement> placements = new ArrayList<>();
        placements.add(hub);
        ArrayList<CalibratedConnection> connections = new ArrayList<>();
        for (int index = 0; index < arrivalCount; index++) {
            double angle = (index + 1d) * 0.71d;
            String anchorId = "arrival-" + systemId.value() + '-' + String.format(java.util.Locale.ROOT, "%02d", index + 1);
            LocalPhysicalPosition position = LocalPhysicalPosition.origin().translated(
                    Math.cos(angle) * distance,
                    Math.sin(angle) * distance);
            InfrastructurePlacement anchor = new InfrastructurePlacement(
                    anchorId,
                    PlacementKind.JUMP_ARRIVAL_ANCHOR,
                    java.util.Optional.empty(),
                    position,
                    0d,
                    0d);
            placements.add(anchor);
            connections.add(new CalibratedConnection(
                    anchorId,
                    hubId,
                    BandId.JUMP_ARRIVAL_TO_MAJOR_HUB,
                    distance,
                    arrivalBand.minDistanceM(),
                    arrivalBand.maxDistanceM(),
                    arrivalBand.sourceEvidenceId(),
                    consequences(routes.version())));
        }
        return new Stage20LocalInfrastructureLayout(
                Stage20LocalInfrastructureLayout.CURRENT_VERSION,
                systemId,
                12345L,
                hubId,
                placements,
                connections,
                Stage20SystemGeometry.CURRENT_VERSION,
                routes.version(),
                Stage20StationPhysicalGeometryProfile.CURRENT_VERSION,
                Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION);
    }

    private static LogisticsConsequenceEnvelope consequences(String sourceVersion) {
        return new LogisticsConsequenceEnvelope(
                1d, 2d,
                1d, 2d,
                1d, 2d,
                2d, 4d,
                sourceVersion);
    }

    private static JumpPlan fittedPlan() {
        return new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl",
                10_000d,
                1_000d,
                1_000d,
                0d,
                100d,
                10d,
                5d,
                20d,
                50d);
    }
}

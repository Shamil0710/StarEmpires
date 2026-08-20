package com.spacesim.world.generation;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20TopologyQualityReport.ViolationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20TopologyQualityAnalyzerTest {
    @Test
    void accidentalLongChainFailsDeadEndCorridorAndCycleBudgets() {
        Stage20TopologyQualityReport report = Stage20TopologyQualityAnalyzer.analyze(
                chain(8),
                Stage20TopologyQualityCalibrationProfile.deriveCurrent());

        assertFalse(report.accepted());
        assertEquals(1, report.connectedComponents());
        assertEquals(7, report.longestLinearCorridorEdges());
        assertEquals(7, report.bridgeEdges().size());
        assertEquals(0.25d, report.degreeOneFraction(), 0d);
        assertEquals(0d, report.cycleParticipationFraction(), 0d);
        assertTrue(report.violations().stream().anyMatch(value ->
                value.type() == ViolationType.EXCESS_DEGREE_ONE_FRACTION));
        assertTrue(report.violations().stream().anyMatch(value ->
                value.type() == ViolationType.EXCESS_LINEAR_CORRIDOR));
        assertTrue(report.violations().stream().anyMatch(value ->
                value.type() == ViolationType.INSUFFICIENT_CYCLE_COVERAGE));
    }

    @Test
    void ordinarySingleSectorCyclePassesApplicableStructuralBudgets() {
        List<StarSystemNode> systems = systems(6, 0d, 0d, 80d, 1L);
        List<JumpConnection> edges = new ArrayList<>();
        for (int index = 0; index < systems.size(); index++) {
            edges.add(new JumpConnection(
                    systems.get(index).id(),
                    systems.get((index + 1) % systems.size()).id()));
        }
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(2L),
                "Cycle",
                List.of(new SectorNode(new SectorId(1L), "Cycle Sector", systems)),
                edges);

        Stage20TopologyQualityReport report = Stage20TopologyQualityAnalyzer.analyze(
                topology,
                Stage20TopologyQualityCalibrationProfile.deriveCurrent());

        assertTrue(report.accepted());
        assertEquals(0, report.bridgeEdges().size());
        assertEquals(0, report.longestLinearCorridorEdges());
        assertEquals(1d, report.cycleParticipationFraction(), 0d);
        assertTrue(report.regionalHubHopDistances().isEmpty());
    }

    private static GalaxyTopology chain(int count) {
        List<StarSystemNode> systems = new ArrayList<>();
        List<JumpConnection> edges = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            StarSystemNode system = new StarSystemNode(
                    new StarSystemId(index + 1L),
                    "Chain " + index,
                    index * 10d,
                    0d);
            systems.add(system);
            if (index > 0) {
                edges.add(new JumpConnection(systems.get(index - 1).id(), system.id()));
            }
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Chain",
                List.of(new SectorNode(new SectorId(1L), "Chain Sector", systems)),
                edges);
    }

    private static List<StarSystemNode> systems(
            int count,
            double centerX,
            double centerY,
            double radius,
            long firstId) {
        List<StarSystemNode> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = StrictMath.PI * 2d * index / count;
            result.add(new StarSystemNode(
                    new StarSystemId(firstId + index),
                    "System " + (firstId + index),
                    centerX + StrictMath.cos(angle) * radius,
                    centerY + StrictMath.sin(angle) * radius));
        }
        return List.copyOf(result);
    }
}

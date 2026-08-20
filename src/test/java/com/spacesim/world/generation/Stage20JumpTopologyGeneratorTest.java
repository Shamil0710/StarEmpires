package com.spacesim.world.generation;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20JumpTopologyGeneratorTest {
    @Test
    void representativeFourSectorRegionPassesAllCalibratedV1Budgets() {
        Stage20TopologyQualityCalibrationProfile quality = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        Stage20JumpTopologyGenerationResult result = Stage20JumpTopologyGenerator.generate(
                new GalaxyId(700L),
                "Stage20D Representative",
                representativeSectors(),
                0x20D5EEDL,
                quality);

        assertEquals(Status.ACCEPTED, result.status());
        assertTrue(result.qualityReport().accepted());
        assertTrue(result.qualityReport().degreeOneFraction()
                <= quality.structuralBudget().maxDegreeOneFraction());
        assertTrue(result.qualityReport().cycleParticipationFraction()
                >= quality.structuralBudget().minRegionalCycleCoverage());
        assertTrue(result.qualityReport().longestLinearCorridorEdges()
                <= quality.structuralBudget().maxLinearCorridorLengthEdges());
        assertEquals(1d, result.qualityReport().coreRouteRedundancyCoverage(), 0d);
        assertTrue(result.qualityReport().maxSingleGatewayDependency()
                <= quality.structuralBudget().maxSingleGatewayDependency());
        assertTrue(result.qualityReport().sectorExitCounts().values().stream().allMatch(value ->
                value >= quality.sectorExitBand().minInclusive()
                        && value <= quality.sectorExitBand().maxInclusive()));
        assertTrue(result.qualityReport().regionalHubHopDistances().stream().allMatch(value ->
                value >= quality.regionalHopDistanceBand().minInclusive()
                        && value <= quality.regionalHopDistanceBand().maxInclusive()));

        result.candidateTopology().connections().forEach(edge -> {
            assertTrue(result.candidateTopology().neighbors(edge.first()).contains(edge.second()));
            assertTrue(result.candidateTopology().neighbors(edge.second()).contains(edge.first()));
        });
    }

    @Test
    void sameSeedAndGeometryAreInvariantToCallerSectorOrdering() {
        Stage20TopologyQualityCalibrationProfile quality = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        List<SectorNode> ordered = representativeSectors();
        List<SectorNode> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);

        Stage20JumpTopologyGenerationResult first = Stage20JumpTopologyGenerator.generate(
                new GalaxyId(701L), "Determinism", ordered, 77123L, quality);
        Stage20JumpTopologyGenerationResult second = Stage20JumpTopologyGenerator.generate(
                new GalaxyId(701L), "Determinism", reversed, 77123L, quality);

        assertEquals(first.status(), second.status());
        assertEquals(first.repairPasses(), second.repairPasses());
        assertEquals(first.candidateTopology(), second.candidateTopology());
        assertEquals(first.qualityReport(), second.qualityReport());
    }

    @Test
    void impossibleOrdinaryTwoSingletonSectorSeedIsRejectedRatherThanRelaxingBudgets() {
        Stage20TopologyQualityCalibrationProfile quality = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        List<SectorNode> sectors = List.of(
                new SectorNode(
                        new SectorId(1L),
                        "A",
                        List.of(new StarSystemNode(new StarSystemId(1L), "A1", 0d, 0d))),
                new SectorNode(
                        new SectorId(2L),
                        "B",
                        List.of(new StarSystemNode(new StarSystemId(2L), "B1", 100d, 0d))));

        Stage20JumpTopologyGenerationResult result = Stage20JumpTopologyGenerator.generate(
                new GalaxyId(702L), "Rejected", sectors, 9L, quality);

        assertEquals(Status.REJECTED_SEED, result.status());
        assertTrue(!result.qualityReport().accepted());
        assertThrows(IllegalStateException.class, result::requireAcceptedTopology);
    }

    private static List<SectorNode> representativeSectors() {
        int sectorCount = 4;
        int systemsPerSector = 8;
        long nextId = 1L;
        List<SectorNode> result = new ArrayList<>();
        for (int sectorIndex = 0; sectorIndex < sectorCount; sectorIndex++) {
            double sectorAngle = StrictMath.PI * 2d * sectorIndex / sectorCount;
            double centerX = StrictMath.cos(sectorAngle) * 1_000d;
            double centerY = StrictMath.sin(sectorAngle) * 1_000d;
            List<StarSystemNode> systems = new ArrayList<>();
            for (int systemIndex = 0; systemIndex < systemsPerSector; systemIndex++) {
                double localAngle = StrictMath.PI * 2d * systemIndex / systemsPerSector;
                double radius = 120d + (systemIndex % 3) * 35d;
                systems.add(new StarSystemNode(
                        new StarSystemId(nextId),
                        "System " + nextId,
                        centerX + StrictMath.cos(localAngle) * radius,
                        centerY + StrictMath.sin(localAngle) * radius));
                nextId++;
            }
            result.add(new SectorNode(
                    new SectorId(sectorIndex + 1L),
                    "Sector " + (sectorIndex + 1),
                    systems));
        }
        return List.copyOf(result);
    }
}

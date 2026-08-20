package com.spacesim.world.generation;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult.Status;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.GenerationRequest;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.MacroGeometryResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MacroGalaxyGeometryGeneratorTest {
    @Test
    void sameSeedAndRequestProduceExactlySameSpatialRegionsAndSystems() {
        GenerationRequest request = GenerationRequest.representative();

        MacroGeometryResult first = Stage20MacroGalaxyGeometryGenerator.generate(0x20B5EEDL, request);
        MacroGeometryResult second = Stage20MacroGalaxyGeometryGenerator.generate(0x20B5EEDL, request);

        assertEquals(first, second);
        assertEquals(Stage20MacroGalaxyGeometryGenerator.CURRENT_VERSION, first.version());
        assertEquals(Stage20MacroGalaxyGeometryGenerator.COORDINATE_SEMANTICS, first.coordinateSemantics());
        assertEquals(request.sectorCount(), first.sectors().size());
    }

    @Test
    void differentSeedChangesSpatialGeometryWithoutChangingRequestedWorldSizeContract() {
        GenerationRequest request = new GenerationRequest(5, 9, 9);

        MacroGeometryResult first = Stage20MacroGalaxyGeometryGenerator.generate(101L, request);
        MacroGeometryResult second = Stage20MacroGalaxyGeometryGenerator.generate(102L, request);

        assertNotEquals(first.sectorEvidence(), second.sectorEvidence());
        assertEquals(45, first.systemEvidence().size());
        assertEquals(45, second.systemEvidence().size());
        assertEquals(
                first.systemEvidence().stream().map(value -> value.systemId()).toList(),
                second.systemEvidence().stream().map(value -> value.systemId()).toList());
    }

    @Test
    void sectorsAreSpatialClustersRatherThanPartitionsOfOnePreexistingSystemLine() {
        MacroGeometryResult generated = Stage20MacroGalaxyGeometryGenerator.generate(
                77123L,
                new GenerationRequest(6, 8, 12));

        Set<StarSystemId> ids = new HashSet<>();
        for (var sectorEvidence : generated.sectorEvidence()) {
            SectorNode sector = generated.sectors().stream()
                    .filter(value -> value.id().equals(sectorEvidence.sectorId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(sector.systems().size() >= 8 && sector.systems().size() <= 12);
            for (var system : sector.systems()) {
                assertTrue(ids.add(system.id()));
                double distanceFromGeneratedCenter = StrictMath.hypot(
                        system.x() - sectorEvidence.centerX(),
                        system.y() - sectorEvidence.centerY());
                assertTrue(distanceFromGeneratedCenter <= sectorEvidence.clusterRadius() * 1.85d);
            }
        }
        assertEquals(generated.systemEvidence().size(), ids.size());
        assertTrue(generated.sectorEvidence().stream().map(value -> value.centerX()).distinct().count() > 1);
        assertTrue(generated.sectorEvidence().stream().map(value -> value.centerY()).distinct().count() > 1);
    }

    @Test
    void macroCoordinatesFeedRealStage20DTopologyGeneratorWithoutBecomingTravelDistance() {
        GenerationRequest request = GenerationRequest.representative();
        Stage20TopologyQualityCalibrationProfile quality = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        Stage20JumpTopologyGenerationResult accepted = null;
        MacroGeometryResult acceptedGeometry = null;
        for (long seed = 1L; seed <= 128L; seed++) {
            MacroGeometryResult geometry = Stage20MacroGalaxyGeometryGenerator.generate(seed, request);
            Stage20JumpTopologyGenerationResult topology = Stage20JumpTopologyGenerator.generate(
                    new GalaxyId(880L),
                    "Macro generated topology",
                    geometry.sectors(),
                    seed,
                    quality);
            if (topology.status() == Status.ACCEPTED) {
                accepted = topology;
                acceptedGeometry = geometry;
                break;
            }
        }

        assertTrue(accepted != null, "representative macro generator should yield an accepted topology in bounded corpus");
        assertTrue(accepted.qualityReport().accepted());
        assertEquals(Stage20MacroGalaxyGeometryGenerator.COORDINATE_SEMANTICS, acceptedGeometry.coordinateSemantics());
        accepted.candidateTopology().connections().forEach(edge ->
                assertTrue(accepted.candidateTopology().neighbors(edge.first()).contains(edge.second())));
    }

    @Test
    void smallWorldRequestIsNotSilentlyInflatedToRescueFutureTopology() {
        GenerationRequest request = new GenerationRequest(2, 1, 1);

        MacroGeometryResult generated = Stage20MacroGalaxyGeometryGenerator.generate(9L, request);

        assertEquals(2, generated.sectors().size());
        assertEquals(List.of(1, 1), generated.sectors().stream().map(value -> value.systems().size()).toList());
    }

    @Test
    void invalidWorldSizeRequestsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequest(0, 8, 10));
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequest(4, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequest(4, 10, 8));
    }
}

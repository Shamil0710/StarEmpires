package com.spacesim.world;

import com.spacesim.world.Stage20EconomicThroughputAcceptance.AcceptanceReport;
import com.spacesim.world.Stage20EconomicThroughputAcceptance.FailureReason;
import com.spacesim.world.Stage20EconomicThroughputAcceptance.RequirementFailure;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Status;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20GeneratedWorldBatchAcceptance.BatchReport;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.SeedResult;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult;
import com.spacesim.world.generation.Stage20JumpTopologyGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedWorldBatchAcceptanceTest {
    @Test
    void acceptedTopologyAndRealBoundedPlacementComposeIntoAcceptedWholeSeed() {
        long seed = 0x20EBA7CL;
        Stage20JumpTopologyGenerationResult topology = acceptedTopology(seed);
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        List<StarSystemId> candidateSystems = topology.requireAcceptedTopology().systems().stream()
                .limit(6)
                .map(StarSystemNode::id)
                .toList();
        ArrayList<Evaluation> evaluations = new ArrayList<>();
        for (int index = 0; index < candidateSystems.size(); index++) {
            evaluations.add(new Evaluation(
                    Stage20FactionStartCandidateEvaluator.CURRENT_VERSION,
                    profile.version(),
                    Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION,
                    candidateSystems.get(index),
                    Status.ACCEPTED,
                    index * 0.1d,
                    List.of()));
        }
        PlacementResult placement = Stage20FactionStartPlacementGenerator.place(
                seed,
                topology.requireAcceptedTopology(),
                List.of("faction.alpha", "faction.beta"),
                evaluations,
                profile);
        assertEquals(Stage20FactionStartPlacementGenerator.PlacementStatus.ACCEPTED, placement.status());

        SeedResult result = Stage20GeneratedWorldSeedAcceptance.compose(
                topology,
                Optional.of(acceptedEconomics()),
                Optional.of(placement));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED, result.status());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.economicAcceptancePresent());
        assertEquals(placement.status(), result.placementStatus().orElseThrow());
    }

    @Test
    void topologyRejectedSeedStopsBeforeDownstreamMaterialization() {
        Stage20JumpTopologyGenerationResult topology = rejectedTopology(9L);

        SeedResult result = Stage20GeneratedWorldSeedAcceptance.compose(
                topology,
                Optional.empty(),
                Optional.empty());

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.REJECTED_SEED, result.status());
        assertEquals(
                Stage20GeneratedWorldSeedAcceptance.FailureReason.TOPOLOGY_QUALITY_REJECTED,
                result.failures().get(0).reason());
        assertTrue(result.placementStatus().isEmpty());
        assertTrue(!result.economicAcceptancePresent());
    }

    @Test
    void acceptedTopologyMissingDownstreamIsHarnessErrorNotRejectedSeedStatistic() {
        Stage20JumpTopologyGenerationResult topology = acceptedTopology(17L);

        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldSeedAcceptance.compose(
                topology,
                Optional.empty(),
                Optional.empty()));
    }

    @Test
    void economicFailureRejectsSeedAndPreservesMachineReadableFailure() {
        long seed = 21L;
        Stage20JumpTopologyGenerationResult topology = acceptedTopology(seed);
        PlacementResult placement = acceptedPlacement(seed, topology.requireAcceptedTopology());
        StarSystemId start = placement.assignments().get(0).systemId();
        AcceptanceReport rejectedEconomics = new AcceptanceReport(
                false,
                "test.requirements",
                "test.supply",
                List.of(),
                List.of(new RequirementFailure(
                        start,
                        "commodity.essential",
                        FailureReason.INSUFFICIENT_THROUGHPUT,
                        "physical route throughput below requirement")));

        SeedResult result = Stage20GeneratedWorldSeedAcceptance.compose(
                topology,
                Optional.of(rejectedEconomics),
                Optional.of(placement));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.REJECTED_SEED, result.status());
        assertTrue(result.failures().stream().anyMatch(value ->
                value.reason() == Stage20GeneratedWorldSeedAcceptance.FailureReason.ECONOMIC_THROUGHPUT_REJECTED));
    }

    @Test
    void batchCanonicalizesSeedOrderAndReportsMeasuredDistributionWithoutInventedPassThreshold() {
        BatchReport report = Stage20GeneratedWorldBatchAcceptance.run(
                List.of(30L, 10L, 20L),
                seed -> seed == 20L ? rejectedSeed(seed) : acceptedSeed(seed));

        assertEquals(List.of(10L, 20L, 30L), report.requestedSeeds());
        assertEquals(2, report.acceptedSeedCount());
        assertEquals(1, report.rejectedSeedCount());
        assertEquals(0, report.unresolvedAuthoritySeedCount());
        assertEquals(2d / 3d, report.acceptedFraction(), 0d);
        assertEquals(1d / 3d, report.rejectedFraction(), 0d);
        assertEquals(1, report.failureReasonCounts().get(
                Stage20GeneratedWorldSeedAcceptance.FailureReason.TOPOLOGY_QUALITY_REJECTED));
    }

    @Test
    void duplicateSeedsAreRejectedInsteadOfChangingSampleWeighting() {
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldBatchAcceptance.run(
                List.of(5L, 5L),
                Stage20GeneratedWorldBatchAcceptanceTest::acceptedSeed));
    }

    private static SeedResult acceptedSeed(long seed) {
        Stage20JumpTopologyGenerationResult topology = acceptedTopology(seed);
        return Stage20GeneratedWorldSeedAcceptance.compose(
                topology,
                Optional.of(acceptedEconomics()),
                Optional.of(acceptedPlacement(seed, topology.requireAcceptedTopology())));
    }

    private static SeedResult rejectedSeed(long seed) {
        return Stage20GeneratedWorldSeedAcceptance.compose(
                rejectedTopology(seed),
                Optional.empty(),
                Optional.empty());
    }

    private static AcceptanceReport acceptedEconomics() {
        return new AcceptanceReport(true, "test.requirements", "test.supply", List.of(), List.of());
    }

    private static PlacementResult acceptedPlacement(long seed, GalaxyTopology topology) {
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        ArrayList<Evaluation> evaluations = new ArrayList<>();
        int index = 0;
        for (StarSystemNode system : topology.systems()) {
            if (index >= 8) {
                break;
            }
            evaluations.add(new Evaluation(
                    Stage20FactionStartCandidateEvaluator.CURRENT_VERSION,
                    profile.version(),
                    Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION,
                    system.id(),
                    Status.ACCEPTED,
                    index * 0.1d,
                    List.of()));
            index++;
        }
        return Stage20FactionStartPlacementGenerator.place(
                seed,
                topology,
                List.of("faction.alpha", "faction.beta"),
                evaluations,
                profile);
    }

    private static Stage20JumpTopologyGenerationResult acceptedTopology(long seed) {
        return Stage20JumpTopologyGenerator.generate(
                new GalaxyId(900L),
                "Batch Representative",
                representativeSectors(),
                seed,
                Stage20TopologyQualityCalibrationProfile.deriveCurrent());
    }

    private static Stage20JumpTopologyGenerationResult rejectedTopology(long seed) {
        List<SectorNode> sectors = List.of(
                new SectorNode(
                        new SectorId(1L),
                        "A",
                        List.of(new StarSystemNode(new StarSystemId(1L), "A1", 0d, 0d))),
                new SectorNode(
                        new SectorId(2L),
                        "B",
                        List.of(new StarSystemNode(new StarSystemId(2L), "B1", 100d, 0d))));
        return Stage20JumpTopologyGenerator.generate(
                new GalaxyId(901L),
                "Batch Rejected",
                sectors,
                seed,
                Stage20TopologyQualityCalibrationProfile.deriveCurrent());
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

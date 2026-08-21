package com.spacesim.world.generation;

import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.FailureReason;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.Status;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CoordinatedFreightCorpusDiagnosticsTest {
    @Test
    void fixedCorpusKeepsAcceptedInfeasibleAndUnresolvedOutcomesDistinct() {
        Stage20CoordinatedFreightCorpusDiagnostics.Report report =
                Stage20CoordinatedFreightCorpusDiagnostics.evaluateCurrent();

        assertEquals(Stage20CoordinatedFreightCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20CoordinatedWholePlacementFreightPlanner.CURRENT_VERSION, report.plannerVersion());
        assertEquals(Stage20CoordinatedFreightCorpusDiagnostics.SEARCH_NODE_BUDGET_PER_SEED,
                report.searchNodeBudgetPerSeed());
        assertEquals(Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent()
                        .requiredFreighterCountPerFactionStart(),
                report.perStartFreighterBudget());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.fixedSeedCount());
        assertEquals(report.fixedSeedCount(), report.seeds().size());

        long placementRejected = report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20CoordinatedFreightCorpusDiagnostics.SeedStatus.PLACEMENT_REJECTED)
                .count();
        assertEquals(report.fixedSeedCount(), report.acceptedPlacementSeedCount() + placementRejected);
        assertEquals(report.acceptedPlacementSeedCount(),
                report.plannerAcceptedSeedCount()
                        + report.plannerInfeasibleSeedCount()
                        + report.plannerUnresolvedSeedCount());
        assertEquals(report.plannerInfeasibleSeedCount() + report.plannerUnresolvedSeedCount(),
                report.failureReasonCounts().values().stream().mapToInt(Integer::intValue).sum());

        for (Stage20CoordinatedFreightCorpusDiagnostics.SeedEvidence seed : report.seeds()) {
            assertTrue(seed.searchNodesVisited() <= report.searchNodeBudgetPerSeed());
            switch (seed.status()) {
                case PLACEMENT_REJECTED -> {
                    assertTrue(seed.plannerStatus().isEmpty());
                    assertTrue(seed.failureReason().isEmpty());
                    assertEquals(0, seed.searchNodesVisited());
                    assertEquals(0, seed.totalRemoteFreightersUsed());
                }
                case PLANNER_ACCEPTED -> {
                    assertEquals(Status.ACCEPTED, seed.plannerStatus().orElseThrow());
                    assertTrue(seed.failureReason().isEmpty());
                }
                case PLANNER_INFEASIBLE -> {
                    assertEquals(Status.INFEASIBLE, seed.plannerStatus().orElseThrow());
                    assertTrue(seed.failureReason().isPresent());
                    assertTrue(seed.failureReason().orElseThrow()
                            != FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED);
                    assertEquals(0, seed.totalRemoteFreightersUsed());
                }
                case PLANNER_UNRESOLVED -> {
                    assertEquals(Status.UNRESOLVED_SEARCH_BUDGET, seed.plannerStatus().orElseThrow());
                    assertEquals(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED,
                            seed.failureReason().orElseThrow());
                    assertEquals(0, seed.totalRemoteFreightersUsed());
                }
            }
        }

        Map<String, Integer> reasons = report.failureReasonCounts();
        assertEquals(report.plannerUnresolvedSeedCount(),
                reasons.getOrDefault(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED.name(), 0));
        assertTrue(report.maxSearchNodesVisited() <= report.searchNodeBudgetPerSeed());
        assertTrue(report.totalSearchNodesVisited() >= report.maxSearchNodesVisited());

        System.out.println("STAGE20E_COORDINATED_FREIGHT_CORPUS_DIAGNOSTICS_BEGIN");
        System.out.print(Stage20CoordinatedFreightCorpusDiagnostics.toText(report));
        System.out.println("STAGE20E_COORDINATED_FREIGHT_CORPUS_DIAGNOSTICS_END");
    }
}

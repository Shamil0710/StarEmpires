package com.spacesim.world.generation;

import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.FailureReason;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20Seed8FreightSearchConvergenceDiagnosticsTest {
    @Test
    void targetedSeedKeepsBudgetExhaustionDistinctFromPhysicalResolution() {
        Stage20Seed8FreightSearchConvergenceDiagnostics.Report report =
                Stage20Seed8FreightSearchConvergenceDiagnostics.evaluateCurrent();

        assertEquals(Stage20Seed8FreightSearchConvergenceDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20Seed8FreightSearchConvergenceDiagnostics.ROOT_SEED, report.rootSeed());
        assertEquals(Stage20CoordinatedFreightCorpusDiagnostics.SEARCH_NODE_BUDGET_PER_SEED,
                report.baselineCorpusSearchBudget());
        assertFalse(report.attempts().isEmpty());
        assertTrue(report.attempts().size()
                <= Stage20Seed8FreightSearchConvergenceDiagnostics.SEARCH_NODE_BUDGET_LADDER.size());

        List<Integer> ladder = Stage20Seed8FreightSearchConvergenceDiagnostics.SEARCH_NODE_BUDGET_LADDER;
        for (int index = 0; index < report.attempts().size(); index++) {
            var attempt = report.attempts().get(index);
            assertEquals(ladder.get(index).intValue(), attempt.searchNodeBudget());
            assertTrue(attempt.searchNodesVisited() <= attempt.searchNodeBudget());
            if (index < report.attempts().size() - 1) {
                assertEquals(Status.UNRESOLVED_SEARCH_BUDGET, attempt.status());
                assertEquals(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED,
                        attempt.failureReason().orElseThrow());
                assertEquals(0, attempt.totalRemoteFreightersUsed());
            }
        }

        var last = report.attempts().get(report.attempts().size() - 1);
        if (report.firstResolvedBudget().isPresent()) {
            assertEquals(report.firstResolvedBudget().orElseThrow().intValue(), last.searchNodeBudget());
            assertTrue(last.status() != Status.UNRESOLVED_SEARCH_BUDGET);
            if (last.status() == Status.ACCEPTED) {
                assertTrue(last.failureReason().isEmpty());
            } else {
                assertEquals(Status.INFEASIBLE, last.status());
                assertTrue(last.failureReason().isPresent());
                assertTrue(last.failureReason().orElseThrow()
                        != FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED);
                assertEquals(0, last.totalRemoteFreightersUsed());
            }
        } else {
            assertEquals(ladder.size(), report.attempts().size());
            assertEquals(Status.UNRESOLVED_SEARCH_BUDGET, last.status());
            assertEquals(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED,
                    last.failureReason().orElseThrow());
            assertEquals(0, last.totalRemoteFreightersUsed());
        }

        System.out.println("STAGE20E_SEED8_FREIGHT_SEARCH_CONVERGENCE_BEGIN");
        System.out.print(Stage20Seed8FreightSearchConvergenceDiagnostics.toText(report));
        System.out.println("STAGE20E_SEED8_FREIGHT_SEARCH_CONVERGENCE_END");
    }
}

package com.spacesim.world.generation;

import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeFreightPortfolioDiagnosticsTest {
    private static final String BEGIN = "STAGE20E_FREIGHT_PORTFOLIO_DIAGNOSTICS_BEGIN";
    private static final String END = "STAGE20E_FREIGHT_PORTFOLIO_DIAGNOSTICS_END";

    @Test
    void fixedCorpusMeasuresSingleSupplierMismatchWithoutInventingFleetCapacity() {
        var report = Stage20RepresentativeFreightPortfolioDiagnostics.evaluateCurrent();

        assertEquals(Stage20RepresentativeFreightPortfolioDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.seeds().size());
        assertEquals(8, report.configuredFreighterCount());
        assertEquals(15, report.acceptedPlacementSeedCount());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds(),
                report.seeds().stream()
                        .map(Stage20RepresentativeFreightPortfolioDiagnostics.SeedEvidence::rootSeed)
                        .toList());
        assertEquals(1, report.seeds().stream()
                .filter(seed -> seed.placementStatus() != PlacementStatus.ACCEPTED)
                .count());
        assertFalse(report.requirementStatusCounts().isEmpty());

        report.seeds().stream()
                .filter(seed -> seed.placementStatus() == PlacementStatus.ACCEPTED)
                .flatMap(seed -> seed.starts().stream())
                .flatMap(start -> start.requirements().stream())
                .filter(requirement -> requirement.minimumFreightersRequired() > 0)
                .forEach(requirement -> {
                    assertTrue(requirement.minimumFreightersRequired() <= report.configuredFreighterCount());
                    assertTrue(requirement.selectedPortfolioCapacityKgPerSecond() + 1e-9d
                            >= requirement.requiredKgPerSecond());
                });

        System.out.println(BEGIN);
        System.out.print(Stage20RepresentativeFreightPortfolioDiagnostics.toText(report));
        System.out.println(END);
    }
}

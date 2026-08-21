package com.spacesim.world.generation;

import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20WholePlacementCapacityCorpusDiagnosticsTest {
    @Test
    void fixedCorpusMeasuresFiniteStartPortfoliosBeforeSharedProducerReservation() {
        var report = Stage20WholePlacementCapacityCorpusDiagnostics.evaluateCurrent();
        var capacity = Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();

        assertEquals(Stage20WholePlacementCapacityCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfileV2.CURRENT_VERSION,
                report.candidateProfileVersion());
        assertEquals(capacity.version(), report.freightCapacityRequirementVersion());
        assertEquals(capacity.requiredFreighterCountPerFactionStart(), report.perStartFreighterBudget());
        assertEquals(13, report.perStartFreighterBudget());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.fixedSeedCount());
        assertEquals(report.fixedSeedCount(), report.seeds().size());

        long placementRejected = report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.PLACEMENT_REJECTED)
                .count();
        long allocationRejected = report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.START_ALLOCATION_REJECTED)
                .count();
        long reservationAccepted = report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.ACCEPTED)
                .count();
        long reservationConflict = report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.PRODUCER_RESERVATION_CONFLICT)
                .count();

        assertEquals(report.fixedSeedCount(),
                placementRejected + allocationRejected + reservationAccepted + reservationConflict);
        assertEquals(report.acceptedPlacementSeedCount(),
                allocationRejected + reservationAccepted + reservationConflict);
        assertEquals(report.allStartAllocationsAcceptedSeedCount(), reservationAccepted + reservationConflict);
        assertEquals(report.producerReservationAcceptedSeedCount(), reservationAccepted);
        assertEquals(report.producerReservationConflictSeedCount(), reservationConflict);

        assertTrue(report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.PLACEMENT_REJECTED)
                .allMatch(seed -> seed.reservationStatus().isEmpty()));
        assertTrue(report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.START_ALLOCATION_REJECTED)
                .allMatch(seed -> seed.reservationStatus().isEmpty()));
        assertTrue(report.seeds().stream()
                .filter(seed -> seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.ACCEPTED
                        || seed.status()
                        == Stage20WholePlacementCapacityCorpusDiagnostics.SeedStatus.PRODUCER_RESERVATION_CONFLICT)
                .allMatch(seed -> seed.reservationStatus().isPresent()));

        String text = Stage20WholePlacementCapacityCorpusDiagnostics.toText(report);
        assertEquals(text, Stage20WholePlacementCapacityCorpusDiagnostics.toText(report));
        assertFalse(text.isBlank());
        assertTrue(text.contains("perStartFreighterBudget=13"));
        assertTrue(new TreeMap<>(report.allocationFailureCounts()).entrySet().stream()
                .allMatch(entry -> entry.getValue() > 0));

        System.out.println("STAGE20E_WHOLE_PLACEMENT_CAPACITY_CORPUS_DIAGNOSTICS_BEGIN");
        System.out.print(text);
        System.out.println("STAGE20E_WHOLE_PLACEMENT_CAPACITY_CORPUS_DIAGNOSTICS_END");
    }
}
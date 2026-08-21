package com.spacesim.world.generation;

import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateStatus;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.ProcessCandidate;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialSpecializationCandidatePlanProductionIntegrationTest {
    @Test
    void acceptedResolvedSeedReconstructsExactFacilityBoundCandidateEvidence() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);

        CandidateReport report = Stage20IndustrialSpecializationCandidatePlan.reconstruct(resolved);
        CandidateReport repeated = Stage20IndustrialSpecializationCandidatePlan.reconstruct(resolved);

        assertEquals(Stage20IndustrialSpecializationCandidatePlan.CURRENT_VERSION, report.version());
        assertEquals(resolved.rootSeed(), report.rootSeed());
        assertEquals(resolved.version(), report.resolvedProbeVersion());
        assertEquals(resolved.generation().version(), report.generationProbeVersion());
        assertEquals(resolved.generation().supplyThroughput().orElseThrow().profileVersion(),
                report.supplyProfileVersion());
        assertEquals(report, repeated);
        assertEquals(resolved.generation().topology().requireAcceptedTopology().systems().size(),
                report.systems().size());

        int expectedStationCount = resolved.generation().localLayouts().orElseThrow().stream()
                .mapToInt(layout -> (int) layout.placements().stream()
                        .filter(value -> value.isStation()).count())
                .sum();
        assertEquals(expectedStationCount, report.stationCount());
        assertTrue(report.facilitySlotCount() > 0);
        assertTrue(report.systems().stream()
                .flatMap(value -> value.extractionSites().stream())
                .findAny().isPresent());
        assertTrue(report.systems().stream()
                .flatMap(value -> value.stations().stream())
                .flatMap(value -> value.processes().stream())
                .anyMatch(value -> value.status() == CandidateStatus.REACHABLE_UNRESERVED_UPPER_BOUND));
        assertTrue(report.systems().stream()
                .flatMap(value -> value.stations().stream())
                .flatMap(value -> value.processes().stream())
                .allMatch(value -> value.capacity().facilityDefinitionId()
                        .equals(value.throughput().facilityDefinitionId())));

        assertEquals(EnumSet.allOf(MissingAuthority.class), report.missingAuthorities());
        assertFalse(report.operationallyAuthoritative());

        EnumSet<MissingAuthority> incomplete = EnumSet.allOf(MissingAuthority.class);
        incomplete.remove(MissingAuthority.INSTALLED_SHIPYARDS);
        assertThrows(IllegalArgumentException.class, () -> new CandidateReport(
                report.version(),
                report.rootSeed(),
                report.resolvedProbeVersion(),
                report.generationProbeVersion(),
                report.supplyProfileVersion(),
                report.systems(),
                incomplete));

        ProcessCandidate process = report.systems().stream()
                .flatMap(value -> value.stations().stream())
                .flatMap(value -> value.processes().stream())
                .findFirst().orElseThrow();
        CandidateStatus wrong = process.status() == CandidateStatus.INPUT_BLOCKED
                ? CandidateStatus.REACHABLE_UNRESERVED_UPPER_BOUND
                : CandidateStatus.INPUT_BLOCKED;

        assertThrows(IllegalArgumentException.class, () -> new ProcessCandidate(
                process.capacity(), process.throughput(), wrong));
    }
}

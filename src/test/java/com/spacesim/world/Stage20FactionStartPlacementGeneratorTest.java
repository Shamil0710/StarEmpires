package com.spacesim.world;

import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Status;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.ViolationType;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.CommodityDiagnostic;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Report;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.FailureReason;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FactionStartPlacementGeneratorTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private static final StarSystemId C = new StarSystemId(3L);
    private static final StarSystemId D = new StarSystemId(4L);
    private static final String WATER = "resource.water";
    private static final String FAMILY = "volatiles";

    @Test
    void viableStrategicallyDependentCandidateIsAcceptedWithoutInventingOptionalAuthorities() {
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();

        Evaluation evaluation = Stage20FactionStartCandidateEvaluator.evaluate(
                report(A, viableCommodity(), 1, 1, 1),
                profile);

        assertEquals(Status.ACCEPTED, evaluation.status());
        assertTrue(evaluation.violations().isEmpty());
        assertTrue(evaluation.selectionPenalty() > 0d);
    }

    @Test
    void physicalThroughputDeficitRejectsCandidate() {
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        CommodityDiagnostic deficit = new CommodityDiagnostic(
                WATER,
                FAMILY,
                10d,
                4d,
                8d,
                0.4d,
                0.6d,
                0d,
                -2d,
                2,
                1,
                0.7d,
                0.7d,
                0.7d,
                1,
                Optional.empty(),
                OptionalDouble.empty(),
                0.7d,
                OptionalDouble.empty());

        Evaluation evaluation = Stage20FactionStartCandidateEvaluator.evaluate(
                report(A, deficit, 1, 1, 1),
                profile);

        assertEquals(Status.REJECTED, evaluation.status());
        assertTrue(evaluation.violations().stream().anyMatch(value ->
                value.type() == ViolationType.ESSENTIAL_THROUGHPUT_DEFICIT));
    }

    @Test
    void dominantSingleGatewayAndSingleSupplierDependenceRejectsOrdinaryStart() {
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        CommodityDiagnostic concentrated = new CommodityDiagnostic(
                WATER,
                FAMILY,
                10d,
                2d,
                12d,
                0.2d,
                0.8d,
                0d,
                2d,
                2,
                1,
                1d,
                1d,
                1d,
                1,
                Optional.empty(),
                OptionalDouble.empty(),
                1d,
                OptionalDouble.empty());

        Evaluation evaluation = Stage20FactionStartCandidateEvaluator.evaluate(
                report(A, concentrated, 1, 1, 1),
                profile);

        assertEquals(Status.REJECTED, evaluation.status());
        assertTrue(evaluation.violations().stream().anyMatch(value ->
                value.type() == ViolationType.INSUFFICIENT_EXTERNAL_SUPPLIERS));
        assertTrue(evaluation.violations().stream().anyMatch(value ->
                value.type() == ViolationType.EXCESS_GATEWAY_DEPENDENCY));
        assertTrue(evaluation.violations().stream().anyMatch(value ->
                value.type() == ViolationType.INSUFFICIENT_ROUTE_REDUNDANCY));
    }

    @Test
    void requiredButMissingUpstreamAuthorityStaysUnresolvedInsteadOfRejectingSeed() {
        Stage20FactionStartAcceptanceProfile profile = requiredAuthorityProfile();

        Evaluation evaluation = Stage20FactionStartCandidateEvaluator.evaluate(
                report(A, viableCommodity(), 1, 1, 1),
                profile);

        assertEquals(Status.UNRESOLVED_AUTHORITY, evaluation.status());
        assertFalse(evaluation.violations().isEmpty());
        assertTrue(evaluation.violations().stream().allMatch(
                Stage20FactionStartCandidateEvaluator.Violation::unresolvedAuthority));
    }

    @Test
    void boundedPlacementIsDeterministicUniqueAndHopSeparated() {
        GalaxyTopology topology = lineTopology();
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        List<Evaluation> evaluations = List.of(
                accepted(A, 0.10d, profile),
                accepted(B, 0.20d, profile),
                accepted(C, 0.15d, profile),
                accepted(D, 0.30d, profile));
        List<String> factions = List.of("faction.beta", "faction.alpha");

        PlacementResult first = Stage20FactionStartPlacementGenerator.place(
                77L, topology, factions, evaluations, profile);
        PlacementResult second = Stage20FactionStartPlacementGenerator.place(
                77L, topology, factions, evaluations, profile);

        assertEquals(PlacementStatus.ACCEPTED, first.status());
        assertEquals(first, second);
        assertEquals(2, first.assignments().size());
        assertFalse(first.assignments().get(0).systemId().equals(first.assignments().get(1).systemId()));
        int firstIndex = (int) first.assignments().get(0).systemId().value();
        int secondIndex = (int) first.assignments().get(1).systemId().value();
        assertTrue(Math.abs(firstIndex - secondIndex) >= profile.minimumFactionStartHopSeparation());
    }

    @Test
    void impossibleSeparationRejectsSeedWithoutMovingOrRepairingCandidates() {
        GalaxyTopology topology = twoSystemTopology();
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        List<Evaluation> evaluations = List.of(
                accepted(A, 0.1d, profile),
                accepted(B, 0.2d, profile));

        PlacementResult result = Stage20FactionStartPlacementGenerator.place(
                9L,
                topology,
                List.of("faction.alpha", "faction.beta"),
                evaluations,
                profile);

        assertEquals(PlacementStatus.REJECTED_SEED, result.status());
        assertEquals(FailureReason.NO_SEPARATED_ASSIGNMENT, result.failureReason().orElseThrow());
        assertTrue(result.assignments().isEmpty());
    }

    @Test
    void insufficientAcceptedCandidatesWithUnresolvedEvidenceReportsAuthorityBlocker() {
        GalaxyTopology topology = twoSystemTopology();
        Stage20FactionStartAcceptanceProfile profile = Stage20FactionStartAcceptanceProfile.current();
        Evaluation unresolved = new Evaluation(
                Stage20FactionStartCandidateEvaluator.CURRENT_VERSION,
                profile.version(),
                Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION,
                B,
                Status.UNRESOLVED_AUTHORITY,
                0.2d,
                List.of(new Stage20FactionStartCandidateEvaluator.Violation(
                        ViolationType.BUFFER_AUTHORITY_UNRESOLVED,
                        "buffer-authority",
                        1d,
                        0d)));

        PlacementResult result = Stage20FactionStartPlacementGenerator.place(
                12L,
                topology,
                List.of("faction.alpha", "faction.beta"),
                List.of(accepted(A, 0.1d, profile), unresolved),
                profile);

        assertEquals(PlacementStatus.UNRESOLVED_AUTHORITY, result.status());
        assertEquals(FailureReason.REQUIRED_CANDIDATES_UNRESOLVED, result.failureReason().orElseThrow());
    }

    private static CommodityDiagnostic viableCommodity() {
        return new CommodityDiagnostic(
                WATER,
                FAMILY,
                10d,
                4d,
                12d,
                0.4d,
                0.6d,
                0d,
                2d,
                3,
                2,
                0.40d,
                0.50d,
                0.50d,
                2,
                Optional.empty(),
                OptionalDouble.empty(),
                0.50d,
                OptionalDouble.empty());
    }

    private static Report report(
            StarSystemId systemId,
            CommodityDiagnostic commodity,
            int unresolvedCost,
            int unresolvedBuffer,
            int unresolvedOwnership) {
        return new Report(
                Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION,
                systemId,
                List.of(commodity),
                commodity.localSupplyCoverageFraction(),
                Map.of(FAMILY, commodity.importDependencyFraction()),
                Map.of(FAMILY, commodity.localExportPotentialKgPerSecond()),
                commodity.throughputHeadroomKgPerSecond(),
                commodity.supplierConcentrationHhi(),
                commodity.routeConcentrationHhi(),
                commodity.criticalGatewayDependencyFraction(),
                commodity.externalSupplierCount(),
                commodity.alternativePathCountFloor(),
                commodity.accessibleReserveConcentrationHhi(),
                commodity.ownershipConcentrationHhi(),
                unresolvedCost,
                unresolvedBuffer,
                unresolvedOwnership);
    }

    private static Evaluation accepted(
            StarSystemId systemId,
            double penalty,
            Stage20FactionStartAcceptanceProfile profile) {
        return new Evaluation(
                Stage20FactionStartCandidateEvaluator.CURRENT_VERSION,
                profile.version(),
                Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION,
                systemId,
                Status.ACCEPTED,
                penalty,
                List.of());
    }

    private static Stage20FactionStartAcceptanceProfile requiredAuthorityProfile() {
        Stage20FactionStartAcceptanceProfile current = Stage20FactionStartAcceptanceProfile.current();
        return new Stage20FactionStartAcceptanceProfile(
                "test.required-authority",
                current.dominantImportDependencyFraction(),
                current.maximumSupplierConcentrationHhi(),
                current.maximumRouteConcentrationHhi(),
                current.maximumCriticalGatewayDependencyFraction(),
                current.maximumAccessibleReserveConcentrationHhi(),
                current.minimumExternalSuppliersForAnyImport(),
                current.minimumExternalSuppliersForDominantImport(),
                current.minimumAlternativePathsForDominantImport(),
                current.minimumFactionStartHopSeparation(),
                current.maximumSearchNodes(),
                true,
                true,
                true,
                true);
    }

    private static GalaxyTopology lineTopology() {
        List<StarSystemNode> systems = List.of(
                new StarSystemNode(A, "A", 0d, 0d),
                new StarSystemNode(B, "B", 100d, 0d),
                new StarSystemNode(C, "C", 200d, 0d),
                new StarSystemNode(D, "D", 300d, 0d));
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Placement Line",
                List.of(new SectorNode(new SectorId(1L), "Sector", systems)),
                List.of(
                        new JumpConnection(A, B),
                        new JumpConnection(B, C),
                        new JumpConnection(C, D)));
    }

    private static GalaxyTopology twoSystemTopology() {
        List<StarSystemNode> systems = List.of(
                new StarSystemNode(A, "A", 0d, 0d),
                new StarSystemNode(B, "B", 100d, 0d));
        return new GalaxyTopology(
                new GalaxyId(2L),
                "Placement Pair",
                List.of(new SectorNode(new SectorId(1L), "Sector", systems)),
                List.of(new JumpConnection(A, B)));
    }
}

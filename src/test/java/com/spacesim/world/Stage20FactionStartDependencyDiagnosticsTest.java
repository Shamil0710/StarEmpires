package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.BufferState;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.CommodityDiagnostic;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.DeliveredCostBand;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Report;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.ReserveSource;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FactionStartDependencyDiagnosticsTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private static final StarSystemId C = new StarSystemId(3L);
    private static final String WATER = "resource.water";

    @Test
    void lineTopologyMeasuresLocalImportSupplierRouteBufferAndReserveDependence() {
        GalaxyTopology topology = lineTopology();
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, A), 6d,
                new SupplyKey(WATER, B), 2d,
                new SupplyKey(WATER, C), 4d));
        Stage20EconomicBootstrapValidator.RouteEvaluator routes = lineRoutes();
        List<ReserveSource> reserves = List.of(
                reserve("reserve-a", A, 100d, "owner-a"),
                reserve("reserve-b", B, 100d, "owner-b"),
                reserve("reserve-c", C, 100d, "owner-b"));

        Report report = Stage20FactionStartDependencyDiagnostics.analyze(
                topology,
                C,
                List.of(new Requirement(WATER, "volatiles", 10d, 1_000d)),
                supply,
                routes,
                reserves,
                (supplier, candidate, commodity, route) -> OptionalDouble.of(switch ((int) supplier.value()) {
                    case 1 -> 3d;
                    case 2 -> 2d;
                    case 3 -> 1d;
                    default -> throw new AssertionError("unexpected supplier");
                }),
                (candidate, commodity) -> Optional.of(new BufferState(50d, 10d, "buffer-test")));

        assertEquals(Stage20FactionStartDependencyDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(C, report.candidateSystemId());
        assertEquals(1, report.commodities().size());
        CommodityDiagnostic water = report.commodities().get(0);
        assertEquals(4d, water.localSupplyKgPerSecond(), 1e-12);
        assertEquals(12d, water.totalReachableSupplyKgPerSecond(), 1e-12);
        assertEquals(0.4d, water.localSupplyCoverageFraction(), 1e-12);
        assertEquals(0.6d, water.importDependencyFraction(), 1e-12);
        assertEquals(0d, water.localExportPotentialKgPerSecond(), 1e-12);
        assertEquals(2d, water.throughputHeadroomKgPerSecond(), 1e-12);
        assertEquals(3, water.viableSupplierCount());
        assertEquals(2, water.externalSupplierCount());
        assertEquals(56d / 144d, water.supplierConcentrationHhi(), 1e-12);
        assertEquals(1d, water.routeConcentrationHhi(), 1e-12);
        assertEquals(1d, water.criticalGatewayDependencyFraction(), 1e-12);
        assertEquals(1, water.alternativePathCountFloor());

        DeliveredCostBand cost = water.deliveredCostBand().orElseThrow();
        assertEquals(1d, cost.minMilliCreditsPerKg(), 1e-12);
        assertEquals(2d, cost.medianMilliCreditsPerKg(), 1e-12);
        assertEquals(3d, cost.maxMilliCreditsPerKg(), 1e-12);
        assertEquals(1d, cost.authorityCoverageFraction(), 1e-12);
        assertEquals(5d, water.bufferCoverageSeconds().orElseThrow(), 1e-12);
        assertEquals(1d / 3d, water.accessibleReserveConcentrationHhi(), 1e-12);
        assertEquals(5d / 9d, water.ownershipConcentrationHhi().orElseThrow(), 1e-12);

        assertEquals(0.4d, report.essentialLocalSupplyCoverageFraction(), 1e-12);
        assertEquals(0.6d, report.importDependencyByFamily().get("volatiles"), 1e-12);
        assertEquals(0d, report.localExportPotentialKgPerSecondByFamily().get("volatiles"), 1e-12);
        assertEquals(2d, report.minimumThroughputHeadroomKgPerSecond(), 1e-12);
        assertEquals(2, report.minimumExternalSupplierCount());
        assertEquals(1, report.minimumAlternativePathCountFloor());
        assertEquals(0, report.unresolvedDeliveredCostCommodityCount());
        assertEquals(0, report.unresolvedBufferCommodityCount());
        assertEquals(0, report.unresolvedOwnershipCommodityCount());
    }

    @Test
    void absentOptionalAuthoritiesRemainExplicitlyUnresolved() {
        Report report = Stage20FactionStartDependencyDiagnostics.analyze(
                lineTopology(),
                C,
                List.of(new Requirement(WATER, "volatiles", 5d, 1_000d)),
                supply(Map.of(new SupplyKey(WATER, A), 6d)),
                lineRoutes(),
                List.of(new ReserveSource("reserve-a", WATER, A, 100d, Optional.empty())),
                (supplier, candidate, commodity, route) -> OptionalDouble.empty(),
                (candidate, commodity) -> Optional.empty());

        CommodityDiagnostic diagnostic = report.commodities().get(0);
        assertTrue(diagnostic.deliveredCostBand().isEmpty());
        assertTrue(diagnostic.bufferCoverageSeconds().isEmpty());
        assertTrue(diagnostic.ownershipConcentrationHhi().isEmpty());
        assertTrue(report.maximumOwnershipConcentrationHhi().isEmpty());
        assertEquals(1, report.unresolvedDeliveredCostCommodityCount());
        assertEquals(1, report.unresolvedBufferCommodityCount());
        assertEquals(1, report.unresolvedOwnershipCommodityCount());
        assertEquals(1d, diagnostic.accessibleReserveConcentrationHhi(), 1e-12);
    }

    @Test
    void cycleTopologyProvesSecondEdgeDisjointSupplierPath() {
        GalaxyTopology topology = triangleTopology();
        Stage20EconomicBootstrapValidator.RouteEvaluator routes = (origin, destination) -> {
            if (origin.equals(A) && destination.equals(C)) {
                return Optional.of(new RouteAssessment(List.of(A, C), 100d, 10d));
            }
            return Optional.empty();
        };

        Report report = Stage20FactionStartDependencyDiagnostics.analyze(
                topology,
                C,
                List.of(new Requirement(WATER, "volatiles", 5d, 1_000d)),
                supply(Map.of(new SupplyKey(WATER, A), 10d)),
                routes,
                List.of(),
                (supplier, candidate, commodity, route) -> OptionalDouble.empty(),
                (candidate, commodity) -> Optional.empty());

        CommodityDiagnostic diagnostic = report.commodities().get(0);
        assertEquals(2, diagnostic.alternativePathCountFloor());
        assertEquals(2, report.minimumAlternativePathCountFloor());
        assertEquals(1, diagnostic.externalSupplierCount());
    }

    @Test
    void nonNeighborRouteShortcutIsRejected() {
        Stage20EconomicBootstrapValidator.RouteEvaluator illegalRoutes = (origin, destination) ->
                Optional.of(new RouteAssessment(List.of(origin, destination), 100d, 10d));

        assertThrows(IllegalArgumentException.class, () -> Stage20FactionStartDependencyDiagnostics.analyze(
                lineTopology(),
                C,
                List.of(new Requirement(WATER, "volatiles", 5d, 1_000d)),
                supply(Map.of(new SupplyKey(WATER, A), 10d)),
                illegalRoutes,
                List.of(),
                (supplier, candidate, commodity, route) -> OptionalDouble.empty(),
                (candidate, commodity) -> Optional.empty()));
    }

    @Test
    void operationalReserveProjectionIncludesOnlySourcesWithExplicitExtractionSites() {
        Stage20ResourceOccurrenceWorld world = new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                7L,
                List.of(new Stage20ResourceOccurrenceWorld.SystemResourceConditions(
                        A, Map.of("occurrence.water", 0.8d))),
                List.of(
                        occurrence("source-operational", 100d, 0.5d, 0.8d),
                        occurrence("source-uninstalled", 200d, 0.5d, 0.8d)),
                List.of(new Stage20ResourceOccurrenceWorld.InitialExtractionSite(
                        "site-operational",
                        "source-operational",
                        A,
                        "anchor-a",
                        "FREE_BODY",
                        "facility.mine",
                        "method.mine")),
                "ontology",
                "extraction",
                "facility",
                "profile");

        List<ReserveSource> reserves = Stage20FactionStartDependencyDiagnostics.initialOperationalReserves(world);

        assertEquals(1, reserves.size());
        assertEquals("source-operational", reserves.get(0).sourceId());
        assertEquals(40d, reserves.get(0).recoverableMassKg(), 1e-12);
        assertTrue(reserves.get(0).ownerId().isEmpty());
    }

    private static SupplyThroughputReport supply(Map<SupplyKey, Double> capacity) {
        return new SupplyThroughputReport("test-supply", capacity, Set.of(), List.of());
    }

    private static ReserveSource reserve(String id, StarSystemId system, double mass, String owner) {
        return new ReserveSource(id, WATER, system, mass, Optional.of(owner));
    }

    private static Stage20EconomicBootstrapValidator.RouteEvaluator lineRoutes() {
        return (origin, destination) -> {
            if (!destination.equals(C)) {
                return Optional.empty();
            }
            if (origin.equals(A)) {
                return Optional.of(new RouteAssessment(List.of(A, B, C), 200d, 6d));
            }
            if (origin.equals(B)) {
                return Optional.of(new RouteAssessment(List.of(B, C), 100d, 2d));
            }
            if (origin.equals(C)) {
                return Optional.of(new RouteAssessment(List.of(C), 0d, 4d));
            }
            return Optional.empty();
        };
    }

    private static GalaxyTopology lineTopology() {
        return topology(List.of(
                new JumpConnection(A, B),
                new JumpConnection(B, C)));
    }

    private static GalaxyTopology triangleTopology() {
        return topology(List.of(
                new JumpConnection(A, B),
                new JumpConnection(B, C),
                new JumpConnection(A, C)));
    }

    private static GalaxyTopology topology(List<JumpConnection> connections) {
        List<StarSystemNode> systems = List.of(
                new StarSystemNode(A, "A", 0d, 0d),
                new StarSystemNode(B, "B", 100d, 0d),
                new StarSystemNode(C, "C", 200d, 0d));
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Dependency Test",
                List.of(new SectorNode(new SectorId(1L), "Sector", systems)),
                connections);
    }

    private static Stage20ResourceOccurrenceWorld.ResourceOccurrence occurrence(
            String sourceId,
            double mass,
            double grade,
            double recovery) {
        return new Stage20ResourceOccurrenceWorld.ResourceOccurrence(
                sourceId,
                A,
                "anchor-a",
                "host",
                new LocalPhysicalPosition(0d, 0d),
                "occurrence.water",
                com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment.FREE_BODY,
                WATER,
                0.8d,
                mass,
                grade,
                recovery,
                Set.of());
    }
}

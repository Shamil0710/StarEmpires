package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExportHandlingStatus;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExtractionCapacity;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.StationProcessCapacity;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.AnalysisProfile;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20TheoreticalSupplyThroughputAnalyzerTest {
    @Test
    void refiningOutputIsLimitedByPhysicallyDeliveredFeedstockAndRecipeYield() {
        GalaxyTopology topology = topology(1);
        StarSystemId system = new StarSystemId(1L);
        ExtractionCapacity ore = new ExtractionCapacity(
                "site.ore", "source.ore", system, "commodity.feedstock.metallic_ore",
                "facility.extraction.asteroid", "extraction.asteroid_excavation",
                25d, 20d, 1_000d, ExportHandlingStatus.RESOLVED, OptionalDouble.of(20d));
        StationProcessCapacity refinery = new StationProcessCapacity(
                system,
                "refinery",
                "facility.processing.bulk_refinery",
                ProcessKind.REFINING,
                "refining.structural_alloy",
                "commodity.material.structural_alloy",
                100d,
                100d,
                100d);

        var report = Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                new AnalysisProfile("test", 100d),
                (origin, destination) -> Optional.of(new RouteAssessment(List.of(system), 1d, 10d)),
                List.of(ore),
                List.of(refinery),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertEquals(20d, report.capacityKgPerSecond("commodity.feedstock.metallic_ore", system), 1e-9);
        assertEquals(6.8d, report.capacityKgPerSecond("commodity.material.structural_alloy", system), 1e-9);
        assertEquals("facility.processing.bulk_refinery",
                report.processEvidence().get(0).facilityDefinitionId());
        assertEquals(6.8d, report.processEvidence().get(0).inputLimitedOutputKgPerSecond(), 1e-9);

        var input = report.processEvidence().get(0).inputEvidence().get(0);
        assertEquals("commodity.feedstock.metallic_ore", input.commodityId());
        assertEquals(1d / 0.68d, input.inputKgPerOutputKg(), 1e-9);
        assertEquals(10d, input.admittedInputKgPerSecond(), 1e-9);
        assertEquals(6.8d, input.inputSupportedOutputKgPerSecond(), 1e-9);
        assertEquals(1, input.supplyRoutes().size());
        var route = input.supplyRoutes().get(0);
        assertEquals(RouteAdmissionStatus.ADMITTED, route.status());
        assertEquals(20d, route.sourceCapacityKgPerSecond(), 1e-9);
        assertEquals(10d, route.admittedInputKgPerSecond(), 1e-9);
        assertEquals(List.of(system), route.route().orElseThrow().orderedSystems());
    }

    @Test
    void unresolvedSourceExportDoesNotBecomeFreeFeedstockSupply() {
        GalaxyTopology topology = topology(1);
        StarSystemId system = new StarSystemId(1L);
        ExtractionCapacity unresolved = new ExtractionCapacity(
                "site.ore", "source.ore", system, "commodity.feedstock.metallic_ore",
                "facility.extraction.asteroid", "extraction.asteroid_excavation",
                25d, 20d, 1_000d, ExportHandlingStatus.UNRESOLVED, OptionalDouble.empty());

        var report = Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                new AnalysisProfile("test", 100d),
                (origin, destination) -> Optional.of(new RouteAssessment(List.of(system), 1d, 100d)),
                List.of(unresolved),
                List.of(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertEquals(0d, report.capacityKgPerSecond("commodity.feedstock.metallic_ore", system), 1e-9);
        assertTrue(report.unresolvedExtractionSiteIds().contains("site.ore"));
    }

    @Test
    void intermediateSupplyRouteCannotSkipExplicitNeighborSystem() {
        GalaxyTopology topology = topology(3);
        StarSystemId a = new StarSystemId(1L);
        StarSystemId c = new StarSystemId(3L);
        ExtractionCapacity ore = new ExtractionCapacity(
                "site.ore", "source.ore", a, "commodity.feedstock.metallic_ore",
                "facility.extraction.asteroid", "extraction.asteroid_excavation",
                25d, 20d, 1_000d, ExportHandlingStatus.RESOLVED, OptionalDouble.of(20d));
        StationProcessCapacity refinery = new StationProcessCapacity(
                c, "refinery", "facility.processing.bulk_refinery", ProcessKind.REFINING,
                "refining.structural_alloy", "commodity.material.structural_alloy",
                100d, 100d, 100d);

        assertThrows(IllegalArgumentException.class, () -> Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                new AnalysisProfile("test", 100d),
                (origin, destination) -> Optional.of(new RouteAssessment(List.of(a, c), 1d, 100d)),
                List.of(ore),
                List.of(refinery),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault()));
    }

    @Test
    void missingAndOverTimeInputRoutesRemainExplicitNonAdmittedEvidence() {
        GalaxyTopology topology = topology(2);
        StarSystemId source = new StarSystemId(1L);
        StarSystemId processor = new StarSystemId(2L);
        ExtractionCapacity ore = new ExtractionCapacity(
                "site.ore", "source.ore", source, "commodity.feedstock.metallic_ore",
                "facility.extraction.asteroid", "extraction.asteroid_excavation",
                25d, 20d, 1_000d, ExportHandlingStatus.RESOLVED, OptionalDouble.of(20d));
        StationProcessCapacity refinery = new StationProcessCapacity(
                processor,
                "refinery",
                "facility.processing.bulk_refinery",
                ProcessKind.REFINING,
                "refining.structural_alloy",
                "commodity.material.structural_alloy",
                100d,
                100d,
                100d);

        var missing = Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                new AnalysisProfile("test", 100d),
                (origin, destination) -> Optional.empty(),
                List.of(ore),
                List.of(refinery),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());
        var missingRoute = missing.processEvidence().get(0)
                .inputEvidence().get(0).supplyRoutes().get(0);
        assertEquals(RouteAdmissionStatus.NO_FEASIBLE_ROUTE, missingRoute.status());
        assertTrue(missingRoute.route().isEmpty());
        assertEquals(0d, missingRoute.admittedInputKgPerSecond(), 1e-9);
        assertEquals(0d, missing.processEvidence().get(0).inputLimitedOutputKgPerSecond(), 1e-9);

        var overTime = Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                new AnalysisProfile("test", 100d),
                (origin, destination) -> Optional.of(
                        new RouteAssessment(List.of(source, processor), 101d, 100d)),
                List.of(ore),
                List.of(refinery),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());
        var overTimeRoute = overTime.processEvidence().get(0)
                .inputEvidence().get(0).supplyRoutes().get(0);
        assertEquals(RouteAdmissionStatus.ROUTE_TIME_EXCEEDED, overTimeRoute.status());
        assertEquals(List.of(source, processor),
                overTimeRoute.route().orElseThrow().orderedSystems());
        assertEquals(0d, overTimeRoute.admittedInputKgPerSecond(), 1e-9);
        assertEquals(0d, overTime.processEvidence().get(0).inputLimitedOutputKgPerSecond(), 1e-9);
    }

    private static GalaxyTopology topology(int count) {
        java.util.ArrayList<StarSystemNode> systems = new java.util.ArrayList<>();
        java.util.ArrayList<JumpConnection> edges = new java.util.ArrayList<>();
        for (int id = 1; id <= count; id++) {
            systems.add(new StarSystemNode(new StarSystemId(id), "S" + id, id, 0d));
            if (id > 1) {
                edges.add(new JumpConnection(new StarSystemId(id - 1L), new StarSystemId(id)));
            }
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "supply-test",
                List.of(new SectorNode(new SectorId(1L), "sector", systems)),
                edges);
    }
}

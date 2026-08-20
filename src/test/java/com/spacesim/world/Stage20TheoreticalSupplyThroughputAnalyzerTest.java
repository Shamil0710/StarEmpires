package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExportHandlingStatus;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExtractionCapacity;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.StationProcessCapacity;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.AnalysisProfile;
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
                (origin, destination) -> Optional.of(new RouteAssessment(List.of(system), 1d, 100d)),
                List.of(ore),
                List.of(refinery),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertEquals(20d, report.capacityKgPerSecond("commodity.feedstock.metallic_ore", system), 1e-9);
        assertEquals(13.6d, report.capacityKgPerSecond("commodity.material.structural_alloy", system), 1e-9);
        assertEquals(13.6d, report.processEvidence().get(0).inputLimitedOutputKgPerSecond(), 1e-9);
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

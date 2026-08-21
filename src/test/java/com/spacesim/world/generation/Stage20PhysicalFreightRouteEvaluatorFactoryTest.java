package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage20PhysicalFreightRouteEvaluatorFactoryTest {
    private static final int DERIVED_START_CAPACITY = 13;

    @Test
    void explicitAllocationFactoryMatchesTheAcceptedDiagnosticPhysicalRouteReconstruction() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        var probe = Stage20GeneratedWorldProductionProbe.run(1L, profile.inputs());
        var topology = probe.topology().requireAcceptedTopology();
        var edges = probe.jumpEdges().orElseThrow();
        var layouts = probe.localLayouts().orElseThrow();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();

        Stage20PhysicalFreightRouteEvaluator expected =
                Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(
                        topology,
                        edges,
                        layouts,
                        stations,
                        profile.inputs().transport(),
                        DERIVED_START_CAPACITY);
        Stage20PhysicalFreightRouteEvaluator actual = Stage20PhysicalFreightRouteEvaluatorFactory.create(
                topology,
                edges,
                layouts,
                stations,
                profile.inputs().transport(),
                DERIVED_START_CAPACITY);

        List<StarSystemId> sample = topology.systems().stream()
                .map(value -> value.id())
                .sorted()
                .limit(4)
                .toList();
        for (StarSystemId origin : sample) {
            for (StarSystemId destination : sample) {
                assertEquals(
                        expected.assessWithAllocatedFreighters(origin, destination, 1),
                        actual.assessWithAllocatedFreighters(origin, destination, 1));
                assertEquals(
                        expected.assessWithAllocatedFreighters(origin, destination, DERIVED_START_CAPACITY),
                        actual.assessWithAllocatedFreighters(origin, destination, DERIVED_START_CAPACITY));
            }
        }
    }

    @Test
    void factoryRequiresPositiveAllocationAndCompleteLocalLayoutCoverage() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        var probe = Stage20GeneratedWorldProductionProbe.run(1L, profile.inputs());
        var topology = probe.topology().requireAcceptedTopology();
        var edges = probe.jumpEdges().orElseThrow();
        var layouts = probe.localLayouts().orElseThrow();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> Stage20PhysicalFreightRouteEvaluatorFactory.create(
                topology,
                edges,
                layouts,
                stations,
                profile.inputs().transport(),
                0));

        ArrayList<com.spacesim.world.Stage20LocalInfrastructureLayout> incomplete = new ArrayList<>(layouts);
        incomplete.remove(incomplete.size() - 1);
        assertThrows(IllegalArgumentException.class, () -> Stage20PhysicalFreightRouteEvaluatorFactory.create(
                topology,
                edges,
                List.copyOf(incomplete),
                stations,
                profile.inputs().transport(),
                DERIVED_START_CAPACITY));
    }
}

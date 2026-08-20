package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20EconomicThroughputAcceptance.FailureReason;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20EconomicThroughputAcceptanceTest {
    @Test
    void requirementPassesOnlyOnMinimumOfProducerAndRouteCapacity() {
        GalaxyTopology topology = twoSystemTopology();
        StarSystemId producer = new StarSystemId(1L);
        StarSystemId start = new StarSystemId(2L);
        SupplyThroughputReport supply = new SupplyThroughputReport(
                "supply.test",
                Map.of(new SupplyKey("commodity.material.structural_alloy", producer), 5d),
                Set.of(),
                List.of());
        var requirements = new BootstrapRequirementProfile(
                "requirements.test",
                100d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.material.structural_alloy", 100d, 4d)));

        var report = Stage20EconomicThroughputAcceptance.validate(
                topology,
                supply,
                Set.of(start),
                requirements,
                (origin, destination) -> Optional.of(new RouteAssessment(
                        List.of(producer, start), 10d, 10d)));

        assertTrue(report.accepted());
        assertEquals(5d, report.evidence().get(0).deliveredCapacityKgPerSecond(), 1e-9);
        assertEquals(1d, report.evidence().get(0).headroomKgPerSecond(), 1e-9);
    }

    @Test
    void producerExistenceDoesNotHideInsufficientPhysicalThroughput() {
        GalaxyTopology topology = twoSystemTopology();
        StarSystemId producer = new StarSystemId(1L);
        StarSystemId start = new StarSystemId(2L);
        SupplyThroughputReport supply = new SupplyThroughputReport(
                "supply.test",
                Map.of(new SupplyKey("commodity.material.structural_alloy", producer), 5d),
                Set.of(),
                List.of());
        var requirements = new BootstrapRequirementProfile(
                "requirements.test",
                100d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.material.structural_alloy", 100d, 6d)));

        var report = Stage20EconomicThroughputAcceptance.validate(
                topology,
                supply,
                Set.of(start),
                requirements,
                (origin, destination) -> Optional.of(new RouteAssessment(
                        List.of(producer, start), 10d, 100d)));

        assertFalse(report.accepted());
        assertEquals(FailureReason.INSUFFICIENT_THROUGHPUT, report.failures().get(0).reason());
    }

    @Test
    void unresolvedProducerCapacityFailsInsteadOfReceivingFallbackSupply() {
        GalaxyTopology topology = twoSystemTopology();
        StarSystemId start = new StarSystemId(2L);
        SupplyThroughputReport supply = new SupplyThroughputReport(
                "supply.test", Map.of(), Set.of("site.unresolved"), List.of());
        var requirements = new BootstrapRequirementProfile(
                "requirements.test",
                100d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.material.structural_alloy", 100d, 1d)));

        var report = Stage20EconomicThroughputAcceptance.validate(
                topology,
                supply,
                Set.of(start),
                requirements,
                (origin, destination) -> Optional.empty());

        assertFalse(report.accepted());
        assertEquals(FailureReason.NO_RESOLVED_PRODUCER, report.failures().get(0).reason());
    }

    private static GalaxyTopology twoSystemTopology() {
        StarSystemNode a = new StarSystemNode(new StarSystemId(1L), "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(new StarSystemId(2L), "B", 1d, 0d);
        return new GalaxyTopology(
                new GalaxyId(1L),
                "acceptance-test",
                List.of(new SectorNode(new SectorId(1L), "sector", List.of(a, b))),
                List.of(new JumpConnection(a.id(), b.id())));
    }
}

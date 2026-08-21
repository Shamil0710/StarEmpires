package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage20CommodityWholePlacementFrontierLowerBoundTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);

    @Test
    void provenSingleStartMinimaSkipImpossibleLowerCapVectors() {
        var report = Stage20CommodityWholePlacementFrontierGenerator.generate(
                topology(),
                placement(),
                supply(),
                new CommodityRequirement(WATER, 1_000d, 6d),
                Map.of(FACTION_A, 2, FACTION_B, 2),
                5,
                routes());

        assertEquals(Stage20CommodityWholePlacementFrontierGenerator.CURRENT_VERSION, report.version());
        assertEquals(FrontierStatus.COMPLETE, report.status());
        assertEquals(5, report.searchNodesVisited());
        assertEquals(1, report.options().size());
        assertEquals(
                Map.of(FACTION_A, 2, FACTION_B, 2),
                report.options().get(0).remoteFreightersByFaction());
    }

    private static Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator routes() {
        return (origin, destination, ships) -> {
            boolean admitted = origin.equals(SUPPLIER_C) && destination.equals(START_A)
                    || origin.equals(SUPPLIER_D) && destination.equals(START_B);
            if (!admitted) {
                return Optional.empty();
            }
            return Optional.of(new RouteAssessment(
                    List.of(origin, destination),
                    100d,
                    3d * ships));
        };
    }

    private static SupplyThroughputReport supply() {
        return new SupplyThroughputReport(
                "test.frontier-lower-bound.v1",
                Map.of(
                        new SupplyKey(WATER, SUPPLIER_C), 6d,
                        new SupplyKey(WATER, SUPPLIER_D), 6d),
                Set.of(),
                List.of());
    }

    private static PlacementResult placement() {
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.frontier-lower-bound.v1",
                PlacementStatus.ACCEPTED,
                List.of(
                        new Assignment(FACTION_A, START_A, 0d),
                        new Assignment(FACTION_B, START_B, 0d)),
                2,
                Optional.empty());
    }

    private static GalaxyTopology topology() {
        return new GalaxyTopology(
                new GalaxyId(1L),
                "frontier-lower-bound-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(
                                new StarSystemNode(START_A, "A", 0d, 0d),
                                new StarSystemNode(START_B, "B", 1d, 0d),
                                new StarSystemNode(SUPPLIER_C, "C", 2d, 0d),
                                new StarSystemNode(SUPPLIER_D, "D", 3d, 0d)))),
                List.of(
                        new JumpConnection(START_A, START_B),
                        new JumpConnection(START_A, SUPPLIER_C),
                        new JumpConnection(START_B, SUPPLIER_D)));
    }
}

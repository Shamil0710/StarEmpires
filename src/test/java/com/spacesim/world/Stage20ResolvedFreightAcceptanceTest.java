package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResolvedFreightAcceptanceTest {
    private static final String FACTION = "faction.alpha";
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final StarSystemId START = new StarSystemId(1L);
    private static final StarSystemId SOURCE = new StarSystemId(2L);

    @Test
    void exactCommodityFrontiersAreCombinedUnderOneFiniteStartFleet() {
        Stage20ResolvedFreightAcceptance.AcceptanceReport report = Stage20ResolvedFreightAcceptance.evaluate(
                topology(),
                placement(),
                supply(),
                requirements(),
                Map.of(FACTION, 2),
                100,
                this::route);

        assertTrue(report.accepted());
        assertFalse(report.infeasible());
        assertFalse(report.unresolved());
        assertEquals(2, report.commodityFrontiers().size());
        assertEquals(Map.of(FACTION, 2), report.combination().remoteFreightersUsedByFaction());
        assertEquals(List.of(ORE, WATER), report.commodityFrontiers().stream()
                .map(Stage20CommodityWholePlacementFrontierGenerator.FrontierReport::commodityId)
                .toList());
        assertTrue(report.commodityFrontiers().stream().allMatch(frontier -> !frontier.options().isEmpty()));
    }

    @Test
    void completeCommodityFrontiersCanProveSharedFleetCombinationInfeasible() {
        Stage20ResolvedFreightAcceptance.AcceptanceReport report = Stage20ResolvedFreightAcceptance.evaluate(
                topology(),
                placement(),
                supply(),
                requirements(),
                Map.of(FACTION, 1),
                100,
                this::route);

        assertFalse(report.accepted());
        assertTrue(report.infeasible());
        assertFalse(report.unresolved());
        assertEquals(
                Stage20CommodityFreightFrontierCombiner.FailureReason.SHARED_FLEET_COMBINATION_INFEASIBLE,
                report.combination().failureReason().orElseThrow());
    }

    @Test
    void acceptanceRequiresExactPlacedFactionBudgetCoverage() {
        assertThrows(IllegalArgumentException.class, () -> Stage20ResolvedFreightAcceptance.evaluate(
                topology(),
                placement(),
                supply(),
                requirements(),
                Map.of("faction.beta", 2),
                100,
                this::route));
    }

    @Test
    void rejectedPlacementCannotBePromotedIntoFreightAcceptance() {
        PlacementResult rejected = new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "profile.v1",
                PlacementStatus.REJECTED_SEED,
                List.of(),
                1,
                Optional.of(Stage20FactionStartPlacementGenerator.FailureReason.INSUFFICIENT_ACCEPTED_CANDIDATES));

        assertThrows(IllegalArgumentException.class, () -> Stage20ResolvedFreightAcceptance.evaluate(
                topology(),
                rejected,
                supply(),
                requirements(),
                Map.of(FACTION, 2),
                100,
                this::route));
    }

    private Optional<Stage20EconomicBootstrapValidator.RouteAssessment> route(
            StarSystemId origin,
            StarSystemId destination,
            int allocatedFreighters) {
        if (!origin.equals(SOURCE) || !destination.equals(START)) {
            return Optional.empty();
        }
        return Optional.of(new Stage20EconomicBootstrapValidator.RouteAssessment(
                List.of(SOURCE, START),
                100d,
                10d * allocatedFreighters));
    }

    private static GalaxyTopology topology() {
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Resolved freight acceptance test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "Core",
                        List.of(
                                new StarSystemNode(START, "Start", 0d, 0d),
                                new StarSystemNode(SOURCE, "Source", 1d, 0d)))),
                List.of(new JumpConnection(START, SOURCE)));
    }

    private static PlacementResult placement() {
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "profile.v1",
                PlacementStatus.ACCEPTED,
                List.of(new Assignment(FACTION, START, 0d)),
                1,
                Optional.empty());
    }

    private static SupplyThroughputReport supply() {
        return new SupplyThroughputReport(
                "supply.v1",
                Map.of(
                        new SupplyKey(WATER, SOURCE), 100d,
                        new SupplyKey(ORE, SOURCE), 100d),
                Set.of(),
                List.of());
    }

    private static List<CommodityRequirement> requirements() {
        return List.of(
                new CommodityRequirement(WATER, 1_000d, 10d),
                new CommodityRequirement(ORE, 1_000d, 10d));
    }
}

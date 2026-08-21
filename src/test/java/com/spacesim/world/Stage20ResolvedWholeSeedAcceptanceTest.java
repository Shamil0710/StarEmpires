package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierOption;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResolvedWholeSeedAcceptanceTest {
    private static final int BUDGET = 13;
    private static final int SEARCH_BUDGET = 2_000;
    private static final String COMMODITY = "commodity.test.bootstrap";
    private static final String SUPPLY_VERSION = "supply.test.v1";

    @Test
    void acceptedCoordinatedFreightAcceptsWholeSeed() {
        Fixture fixture = fixture();
        Stage20ResolvedFreightAcceptance.AcceptanceReport freight = acceptedFreight(fixture.placement());

        Stage20GeneratedWorldSeedAcceptance.SeedResult result =
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.of(freight), Optional.of(fixture.placement()));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.RESOLVED_FREIGHT_VERSION, result.version());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED, result.status());
        assertTrue(result.economicAcceptancePresent());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void completeFreightInfeasibilityRejectsWholeSeed() {
        Fixture fixture = fixture();
        Stage20ResolvedFreightAcceptance.AcceptanceReport freight = failedFreight(
                fixture.placement(), FrontierStatus.COMPLETE,
                Stage20CommodityFreightFrontierCombiner.Status.INFEASIBLE,
                Stage20CommodityFreightFrontierCombiner.FailureReason.COMMODITY_INFEASIBLE);

        Stage20GeneratedWorldSeedAcceptance.SeedResult result =
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.of(freight), Optional.of(fixture.placement()));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.REJECTED_SEED, result.status());
        assertEquals(1, result.failures().size());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.FailureReason.COORDINATED_FREIGHT_INFEASIBLE,
                result.failures().get(0).reason());
        assertFalse(result.failures().get(0).unresolvedAuthority());
    }

    @Test
    void unresolvedFreightFrontierBlocksWithoutRejectingSeed() {
        Fixture fixture = fixture();
        Stage20ResolvedFreightAcceptance.AcceptanceReport freight = failedFreight(
                fixture.placement(), FrontierStatus.UNRESOLVED_SEARCH_BUDGET,
                Stage20CommodityFreightFrontierCombiner.Status.UNRESOLVED_FRONTIER,
                Stage20CommodityFreightFrontierCombiner.FailureReason.FRONTIER_INCOMPLETE);

        Stage20GeneratedWorldSeedAcceptance.SeedResult result =
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.of(freight), Optional.of(fixture.placement()));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.UNRESOLVED_AUTHORITY, result.status());
        assertEquals(1, result.failures().size());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.FailureReason.COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED,
                result.failures().get(0).reason());
        assertTrue(result.failures().get(0).unresolvedAuthority());
    }

    @Test
    void rejectedPlacementDoesNotRequireOrPermitSyntheticFreightResult() {
        Fixture fixture = fixture();
        PlacementResult rejected = new PlacementResult(
                fixture.placement().version(),
                fixture.placement().rootSeed(),
                fixture.placement().profileVersion(),
                PlacementStatus.REJECTED_SEED,
                List.of(),
                1,
                Optional.of(Stage20FactionStartPlacementGenerator.FailureReason.INSUFFICIENT_ACCEPTED_CANDIDATES));

        Stage20GeneratedWorldSeedAcceptance.SeedResult result =
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.empty(), Optional.of(rejected));

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.REJECTED_SEED, result.status());
        assertFalse(result.economicAcceptancePresent());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.FailureReason.FACTION_START_PLACEMENT_REJECTED,
                result.failures().get(0).reason());

        assertThrows(IllegalArgumentException.class, () ->
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.of(acceptedFreight(fixture.placement())), Optional.of(rejected)));
    }

    @Test
    void acceptedPlacementCannotSilentlySkipFreightAuthority() {
        Fixture fixture = fixture();
        assertThrows(IllegalArgumentException.class, () ->
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        fixture.probe().topology(), Optional.empty(), Optional.of(fixture.placement())));
    }

    private static Fixture fixture() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20GeneratedWorldProductionProbe.ProbeResult probe =
                Stage20GeneratedWorldProductionProbe.run(1L, profile.inputs());
        PlacementResult placement = probe.placement().orElseThrow();
        if (placement.status() != PlacementStatus.ACCEPTED) {
            throw new AssertionError("fixed seed 1 must retain accepted representative placement");
        }
        return new Fixture(probe, placement);
    }

    private static Stage20ResolvedFreightAcceptance.AcceptanceReport acceptedFreight(PlacementResult placement) {
        Map<String, Integer> budgets = budgets(placement);
        ArrayList<StartPlan> starts = new ArrayList<>();
        ArrayList<ProducerUsage> producers = new ArrayList<>();
        for (Assignment assignment : placement.assignments()) {
            SupplierCommitment commitment = new SupplierCommitment(
                    COMMODITY,
                    assignment.systemId(),
                    true,
                    0,
                    1d,
                    Optional.empty());
            DemandPlan demand = new DemandPlan(COMMODITY, 1d, 1d, 0, List.of(commitment));
            starts.add(new StartPlan(
                    assignment.stableFactionId(),
                    assignment.systemId(),
                    BUDGET,
                    0,
                    List.of(demand)));
            producers.add(new ProducerUsage(
                    new SupplyKey(COMMODITY, assignment.systemId()), 1d, 1d));
        }
        Map<String, Integer> zeroUsage = new LinkedHashMap<>();
        budgets.keySet().forEach(faction -> zeroUsage.put(faction, 0));
        FrontierOption option = new FrontierOption(
                "option.local-zero",
                COMMODITY,
                zeroUsage,
                starts,
                producers);
        FrontierReport frontier = new FrontierReport(
                Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION,
                placement.version(),
                SUPPLY_VERSION,
                COMMODITY,
                SEARCH_BUDGET,
                1,
                FrontierStatus.COMPLETE,
                budgets,
                List.of(option));
        CombinationReport combination = Stage20CommodityFreightFrontierCombiner.combine(
                List.of(frontier.toCombinerFrontier()), budgets);
        return new Stage20ResolvedFreightAcceptance.AcceptanceReport(
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                placement.version(),
                SUPPLY_VERSION,
                SEARCH_BUDGET,
                budgets,
                List.of(frontier),
                combination);
    }

    private static Stage20ResolvedFreightAcceptance.AcceptanceReport failedFreight(
            PlacementResult placement,
            FrontierStatus frontierStatus,
            Stage20CommodityFreightFrontierCombiner.Status combinationStatus,
            Stage20CommodityFreightFrontierCombiner.FailureReason failureReason) {
        Map<String, Integer> budgets = budgets(placement);
        FrontierReport frontier = new FrontierReport(
                Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION,
                placement.version(),
                SUPPLY_VERSION,
                COMMODITY,
                SEARCH_BUDGET,
                0,
                frontierStatus,
                budgets,
                List.of());
        CombinationReport combination = new CombinationReport(
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                budgets,
                combinationStatus,
                Optional.of(failureReason),
                Map.of(),
                List.of());
        return new Stage20ResolvedFreightAcceptance.AcceptanceReport(
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                placement.version(),
                SUPPLY_VERSION,
                SEARCH_BUDGET,
                budgets,
                List.of(frontier),
                combination);
    }

    private static Map<String, Integer> budgets(PlacementResult placement) {
        LinkedHashMap<String, Integer> budgets = new LinkedHashMap<>();
        placement.assignments().forEach(value -> budgets.put(value.stableFactionId(), BUDGET));
        return Map.copyOf(budgets);
    }

    private record Fixture(
            Stage20GeneratedWorldProductionProbe.ProbeResult probe,
            PlacementResult placement) {
    }
}
